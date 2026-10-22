package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig;
import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;
import uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData;
import uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftStore;
import uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftConverter;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean active = true;
    public void setActive(boolean f) {
        this.active = f;
    }

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;
    }

    public float expandProgress, collapseProgress;
    public boolean isOpening;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidate();
        }
    }

    public void setCollapseProgress(float progress, boolean isOpening) {
        this.isOpening = isOpening;
        float newProgress = Utilities.clamp01((progress - 0.3f) / 0.7f);
        if (this.collapseProgress != newProgress) {
            this.collapseProgress = newProgress;
            invalidate();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidate();
    }

    private float left, right, cy;
    private float expandY, maxExpandY;
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        this.maxExpandY = maxExpandY + cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float y) {
        this.expandY = y;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        // Aiqiyi: 监听本地礼物变更
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.aiqiyiLocalGiftUpdated);

        for (Gift gift : gifts) {
            if (gift.emojiDrawable != null) gift.emojiDrawable.addView(this);
        }

        update();
        // Aiqiyi: update() 里才生成 localGifts，必须在此之后补挂图片，否则只剩色块。
        for (Gift gift : localGifts) {
            if (gift.emojiDrawable != null) gift.emojiDrawable.addView(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        // Aiqiyi: 移除本地礼物监听
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.aiqiyiLocalGiftUpdated);

        // Aiqiyi: 同步解绑本地礼物 emojiDrawable
        for (Gift gift : localGifts) {
            if (gift.emojiDrawable != null) gift.emojiDrawable.removeView(this);
        }
        for (Gift gift : gifts) {
            if (gift.emojiDrawable != null) gift.emojiDrawable.removeView(this);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        } else if (id == NotificationCenter.aiqiyiLocalGiftUpdated) {
            // Aiqiyi: 本地礼物变更，刷新当前资料页
            if (args.length > 0 && (long) args[0] == dialogId) {
                update();
            }
        }
    }

    public final class Gift {

        public final long id;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;
        private StarsReactionsSheet.Particles particles;

        public int position = -1;

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop.center_color | 0xFF000000;
            slug = gift.slug;
            initParticles();
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            slug = status.slug;
            initParticles();
        }
        /**
         * Aiqiyi: 带 Document 的构造器。
         * 本地虚拟礼物的 document_id 经常为 0，直接走上面那个构造器会让
         * emojiDrawable 拿不到图案、整格画成空白，视觉上等于「没显示」。
         */
        public Gift(TLRPC.TL_emojiStatusCollectible status, TLRPC.Document doc) {
            id = status.collectible_id;
            document = doc;
            documentId = doc != null ? doc.id : status.document_id;
            color = status.center_color | 0xFF000000;
            slug = status.slug;
            initParticles();
        }

        private void initParticles() {
            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 6);
            final float gsz = dp(36);
            particles.bounds.set(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f);
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id;
        }

        public RadialGradient gradient;
        public final Matrix gradientMatrix = new Matrix();
        public Paint gradientPaint;
        public AnimatedEmojiDrawable emojiDrawable;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);

        public void copy(Gift b) {
            gradient = b.gradient;
            emojiDrawable = b.emojiDrawable;
            gradientPaint = b.gradientPaint;
            animatedFloat = b.animatedFloat;
            particles = b.particles;
            position = b.position;
        }

        public void draw(
                Canvas canvas,
                float cx, float cy,
                float ascale, float rotate,
                float alpha,
                float gradientAlpha
        ) {
            if (alpha <= 0.0f) return;
            final float gsz = dp(45);
            bounds.set(cx - gsz / 2, cy - gsz / 2, cx + gsz / 2, cy + gsz / 2);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale, scale);
            particles.process();
            particles.draw(canvas, color, alpha);
            if (gradientPaint != null) {
                gradientPaint.setAlpha((int) (0xFF * alpha * gradientAlpha));
                canvas.drawRect(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f, gradientPaint);
            }
            if (emojiDrawable != null) {
                final int sz = dp(24);
                emojiDrawable.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
                emojiDrawable.setAlpha((int) (0xFF * alpha));
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    public final ArrayList<Gift> gifts = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    /**
     * Aiqiyi: 本地礼物独立列表。
     *
     * 与 gifts（服务端真实佩戴礼物）完全分开：
     *  - gifts 走官方 0-5 六个位置，一格不多，保持原版表现
     *  - localGifts 走自己的坐标段，不占佩戴区槽位，互不影响
     * 这样服务端礼物排满时本地礼物依然能画出来，两套逻辑不打架。
     */
    public final ArrayList<Gift> localGifts = new ArrayList<>();
    /** Aiqiyi: 上一次本地礼物数量，用于判断是否需要重绘 */
    private int lastLocalGiftCount = -1;
    /** Aiqiyi: 本地礼物行数变化标记，用于触发 header 重新测量 */
    private boolean aiqiyiLocalRowsChanged = false;
    /**
     * Aiqiyi: 旧数据修复是否已在本进程调度过。
     *
     * repairGiftNumsFromSlug / repairPatternDocuments 是历史数据的一次性补丁，
     * 不需要每次进资料页都跑。这里做进程内去重，LocalGiftStore 内部另有
     * PREF 级标记做跨进程/重启去重。
     */
    private static boolean aiqiyiRepairScheduled = false;

    public void update() {
        if (CherrygramAppearanceConfig.INSTANCE.getDisablePremiumStatuses()) return;

        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            return;
        }

        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        // Aiqiyi: 注入本地虚拟礼物（独立列表，不碰 gifts / maxCount）
        injectLocalGifts();
        android.util.Log.d("AQY_LOCAL_GIFT", "after inject: real=" + gifts.size()
                + " local=" + localGifts.size() + " maxCount=" + maxCount);

        boolean changed = false;
        // Aiqiyi: 本地礼物数量变化也要触发重绘
        if (localGifts.size() != lastLocalGiftCount) {
            changed = true;
            lastLocalGiftCount = localGifts.size();
            aiqiyiLocalRowsChanged = true;
            android.util.Log.d("AQY_LOCAL_GIFT", "changed by local count = " + localGifts.size());
        }
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null) {
                g.copy(oldGift);
            } else {
                g.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{g.color, Theme.multAlpha(g.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                if (g.document != null) {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.document);
                } else {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.documentId);
                }
                g.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                g.animatedFloat.force(0.0f);
                if (isAttachedToWindow()) {
                    g.emojiDrawable.addView(this);
                }
            }
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            positions.add(i);
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                g.emojiDrawable.removeView(this);
                g.emojiDrawable = null;
                g.gradient = null;
            } else {
                positions.remove((Object) g.position);
            }
        }

        if (!positions.isEmpty()) {
            BagRandomizer<Integer> bagRandomizer = new BagRandomizer<>(positions);
            for (int i = 0; i < gifts.size(); ++i) {
                final Gift g = gifts.get(i);
                if (g.position == -1) {
                    g.position = bagRandomizer.next();
                }
            }
        }
        // Aiqiyi: 官方礼物位置分配完成后，再给本地礼物挑空槽 —— 此时 gifts 的 position 已确定。
        // 本地礼物与官方佩戴礼物共用 0..maxCount-1 的环绕坐标，显示在头像周围，
        // 样式、动画、点击判定全部复用官方逻辑。
        try {
            if (!localGifts.isEmpty() && maxCount > 0) {
                final java.util.ArrayList<Integer> aiqiyiFree = new java.util.ArrayList<>();
                for (int p = 0; p < maxCount; p++) aiqiyiFree.add(p);
                for (int i = 0; i < gifts.size(); i++) {
                    final int up = gifts.get(i).position;
                    if (up >= 0) aiqiyiFree.remove((Object) up);
                }
                int aiqiyiDup = 0;
                for (int i = 0; i < localGifts.size(); i++) {
                    final Gift lg = localGifts.get(i);
                    if (lg.position >= 0) continue;
                    if (!aiqiyiFree.isEmpty()) {
                        lg.position = aiqiyiFree.remove(0);
                    } else {
                        // 槽位已满：不再覆盖官方礼物，本地礼物不显示（在列表里保持已佩戴状态）
                        lg.position = -2;
                        aiqiyiDup++;
                    }
                }
                if (aiqiyiDup > 0) {
                    android.util.Log.d("AQY_LOCAL_GIFT", "slots full, hidden local=" + aiqiyiDup
                            + " real=" + gifts.size() + " max=" + maxCount);
                }
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < localGifts.size(); i++) sb.append(localGifts.get(i).position).append(',');
                android.util.Log.d("AQY_LOCAL_GIFT", "local positions=" + sb + " maxCount=" + maxCount);
            }
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e("Aiqiyi assign local positions failed", t);
        }

        if (changed)
            invalidate();
        // Aiqiyi: 本地礼物行数变化会影响 ProfileActivity.getHeaderOnlyExtraHeight() 的计算，
        // 必须让 header 重新测量，否则多出来的行会被 clipRect 裁掉。
        if (aiqiyiLocalRowsChanged) {
            aiqiyiLocalRowsChanged = false;
            post(() -> {
                try {
                    final android.view.ViewParent vp = getParent();
                    if (vp instanceof android.view.View) {
                        ((android.view.View) vp).requestLayout();
                    }
                    requestLayout();
                    android.util.Log.d("AQY_LOCAL_GIFT", "requestLayout for local rows");
                } catch (Throwable t) {
                    org.telegram.messenger.FileLog.e("Aiqiyi requestLayout failed", t);
                }
            });
        }
        // Aiqiyi: update() 所有出口统一补挂 drawable。
        // 原先只在 isAttachedToWindow() 为真时 addView，但 update() 常发生在视图 attach 之前，
        // 导致本地礼物图案永远画不出来（只剩色块）。addView 幂等，重复调用无副作用。
        if (isAttachedToWindow()) {
            for (int i = 0; i < gifts.size(); i++) {
                final Gift g = gifts.get(i);
                if (g.emojiDrawable != null) g.emojiDrawable.addView(this);
            }
            for (int i = 0; i < localGifts.size(); i++) {
                final Gift g = localGifts.get(i);
                if (g.emojiDrawable != null) g.emojiDrawable.addView(this);
            }
        }
    }

    /**
     * Aiqiyi: 把本地虚拟礼物注入到展示列表。
     *
     * 只在「礼物区」「总开关」「展示开关」同时开启时生效。
     * 走 Gift(TLRPC.TL_emojiStatusCollectible) 轻量构造器，
     * 不需要构造复杂的 TL_starGiftUnique。
     */
    private void injectLocalGifts() {
        try {
            localGifts.clear();
            if (!AiqiyiLocalDataConfig.INSTANCE.getLocalGiftEnabled()
                    || !AiqiyiLocalDataConfig.INSTANCE.getLocalGiftShowOnProfile()) {
                android.util.Log.d("AQY_LOCAL_GIFT", "injectLocalGifts skip: switch off");
                lastLocalGiftCount = 0;
                return;
            }
            // Aiqiyi: 旧数据回填（giftNum + pattern 底图）。
            //
            // 这两个修复原本在主线程同步跑：遍历 PREF 里所有 userId 的所有礼物，
            // pattern 那条还要逐个去礼物目录查文档、序列化、写盘、发通知。
            // 条目一多，进资料页就是肉眼可见的卡。而且数据修好之后仍然每次都跑。
            //
            // 现在改成：本进程内只调度一次，丢到后台线程跑，跑完再发一次
            // aiqiyiLocalGiftUpdated 触发重绘。首帧可能拿到的是修正前的编号，
            // 但那一帧本来就要等文档下载，用户感知不到这一跳。
            // LocalGiftStore 内部另有 PREF 级完成标记，跨进程/重启也不会重跑。
            if (!aiqiyiRepairScheduled) {
                aiqiyiRepairScheduled = true;
                org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                    boolean changed = false;
                    try {
                        if (LocalGiftStore.INSTANCE.repairGiftNumsFromSlug() > 0) changed = true;
                    } catch (Throwable ignore) {}
                    try {
                        if (LocalGiftStore.INSTANCE.repairPatternDocuments() > 0) changed = true;
                    } catch (Throwable ignore) {}
                    if (changed) {
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                            try {
                                LocalGiftStore.INSTANCE.notifyChanged(dialogId);
                            } catch (Throwable ignore) {}
                        });
                    }
                });
            }
            // Aiqiyi: 只绘制「已佩戴」的本地礼物。
            // 未佩戴的本地礼物只在礼物列表里躺着，不参与资料页绘制。
            // 违规礼物佩戴入口写入时 pinned 直接为 true，所以表现上是「添加即挂上」。
            final ArrayList<LocalGiftData> localList = LocalGiftStore.getPinnedForUser(dialogId);
            android.util.Log.d("AQY_LOCAL_GIFT", "injectLocalGifts enter dialogId=" + dialogId
                    + " pinned.size=" + localList.size() + " real=" + gifts.size());
            if (localList.isEmpty()) {
                lastLocalGiftCount = 0;
                return;
            }
            // Aiqiyi: 贴纸文档多半没在本地缓存，首次进页会现下载，肉眼可见空白。
            // 这里先丢给 FileLoader 低优先级后台预取，绘制时能直接命中。
            try {
                LocalGiftStore.INSTANCE.prefetchStickers(dialogId);
            } catch (Throwable ignore) {}

            // Aiqiyi: 与官方佩戴区共用同一批槽位，上限 maxCount（官方默认 6）。
            // 官方礼物优先占位，本地礼物只能补剩余空槽；槽位满了多出来的本地礼物不画。
            final int aiqiyiRealCount = gifts.size();
            final int aiqiyiRoom = Math.max(0, maxCount - aiqiyiRealCount);
            if (aiqiyiRoom <= 0) {
                android.util.Log.d("AQY_LOCAL_GIFT", "injectLocalGifts: no free slot, real=" + aiqiyiRealCount + " max=" + maxCount);
                lastLocalGiftCount = 0;
                return;
            }
            final int aiqiyiLimit = Math.min(localList.size(), aiqiyiRoom);
            android.util.Log.d("AQY_LOCAL_GIFT", "injectLocalGifts slots: real=" + aiqiyiRealCount
                    + " room=" + aiqiyiRoom + " willDraw=" + aiqiyiLimit + " of " + localList.size());
            for (int i = 0; i < aiqiyiLimit; i++) {
                final LocalGiftData data = localList.get(i);
                final TLRPC.EmojiStatus status = LocalGiftConverter.toStatus(data);
                if (!(status instanceof TLRPC.TL_emojiStatusCollectible)) {
                    android.util.Log.d("AQY_LOCAL_GIFT", "skip: not collectible");
                    continue;
                }
                TLRPC.Document doc = null;
                try {
                    final TL_stars.StarGift sg = LocalGiftConverter.resolveStarGift(data);
                    if (sg != null) doc = sg.getDocument();
                } catch (Throwable ignore) {}
                if (doc == null) {
                    doc = LocalGiftConverter.findDocument(data.getDocumentId());
                }
                final Gift gift = new Gift((TLRPC.TL_emojiStatusCollectible) status, doc);
                // Aiqiyi: 本地礼物与官方佩戴礼物共用同一套 0..maxCount-1 环绕坐标。
                // 分配顺序：先让官方礼物吃满自己的槽位，本地礼物再取剩下的空位，
                // 这样本地礼物出现在头像周围那六个点上，样式/动画/点击全部复用官方逻辑。
                gift.position = -1;   // 先标记未分配，循环结束后统一分配
                // Aiqiyi: 本地礼物不走 update() 的初始化分支，这里补齐绘制所需字段
                if (gift.gradient == null) {
                    gift.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{gift.color, Theme.multAlpha(gift.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                    gift.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    gift.gradientPaint.setShader(gift.gradient);
                }
                if (gift.emojiDrawable == null) {
                    if (gift.document != null) {
                        gift.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, gift.document);
                    } else {
                        gift.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, gift.documentId);
                    }
                    // Aiqiyi: 本地礼物的贴纸文档首次显示时不在缓存里，要靠 ImageReceiver
                    // 走 FileLoader 异步下载。不 preload 的话要等到真正绘制那一刻才发起请求，
                    // 网络慢就是好几秒空白。这里建完壳立刻 preload，把下载和后续布局/绘制并行。
                    if (gift.emojiDrawable != null) {
                        try {
                            gift.emojiDrawable.preload();
                        } catch (Throwable ignore) {}
                    }
                    if (gift.emojiDrawable != null && isAttachedToWindow()) {
                        gift.emojiDrawable.addView(this);
                    }
                }
                if (gift.animatedFloat == null) {
                    gift.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                    gift.animatedFloat.force(0.0f);
                }
                localGifts.add(gift);
                android.util.Log.d("AQY_LOCAL_GIFT", "ADDED local idx=" + i + " gift.id=" + gift.id
                        + " pos=" + gift.position + " hasDoc=" + (gift.document != null));
            }
            lastLocalGiftCount = localGifts.size();
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e("Aiqiyi injectLocalGifts failed", t);
            lastLocalGiftCount = 0;
        }
    }

    /**
     * Aiqiyi: 本地礼物坐标基准值，避开官方 0-5 六个位置。
     */
    private static final int LOCAL_POS_BASE = 100;
    /** Aiqiyi: 本地礼物每行个数（对齐官方九宫格风格，一行三个） */
    private static final int LOCAL_PER_ROW = 3;

    private final TimeInterpolator giftCollapseXInterpolator = new DecelerateInterpolator();
    private final TimeInterpolator giftCollapseYInterpolator = new LinearInterpolator();

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if ((gifts.isEmpty() && localGifts.isEmpty()) || expandProgress >= 1.0f || collapseProgress <= 0f) return;

        final float realX = avatarContainer.getX();
        final float realY = avatarContainer.getY();
        final float realW = (avatarContainer.getWidth()) * avatarContainer.getScaleX();
        final float realH = (avatarContainer.getHeight()) * avatarContainer.getScaleY();

        final float sz = dpf2(96);
        final float ax = Math.min(realX, (getWidth() - sz) / 2f);
        final float ay = Math.max(realY, (maxExpandY - sz) / 2f);
        final float aw = Math.max(realW, sz);
        final float ah = Math.max(realH, sz);

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandY);

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;

        final float realCX = realX + realW / 2.0f;
        final float realCY = realY + realH / 2.0f;

        float expandScale = expandY / maxExpandY;
        float closedAlpha = Utilities.clamp01((expandY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / dp(50));

        // Aiqiyi: 统一遍历「真实佩戴礼物 + 本地礼物」两套列表
        final ArrayList<Gift> aiqiyiAllDraw = new ArrayList<>(gifts.size() + localGifts.size());
        aiqiyiAllDraw.addAll(gifts);
        aiqiyiAllDraw.addAll(localGifts);
        for (int i = 0; i < aiqiyiAllDraw.size(); ++i) {
            final Gift gift = aiqiyiAllDraw.get(i);
            // Aiqiyi: position == -2 表示槽位已被官方礼物占满，按容量规则不绘制。
            if (gift.position == -2) {
                continue;
            }
            // Aiqiyi: 兜底，避免任何路径漏初始化导致 NPE 崩掉主页
            if (gift.animatedFloat == null) {
                gift.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                gift.animatedFloat.force(0.0f);
            }
            final float enter = gift.animatedFloat.set(1.0f);
            float scale = lerp(0.5f, 1.0f, enter);
            final float alpha = enter * (1.0f - expandProgress) * (1.0f - actionBarProgress) * (closedAlpha);

            float gx, gy;
            float delayValue;

            // Aiqiyi: 兜底 —— 若本地礼物还没拿到槽位（update 提前 return 等极端情况），
            // 按它在 localGifts 里的顺序取模，保证仍然落在官方那六个环绕点上。
            int aiqiyiPos = gift.position;
            if (aiqiyiPos < 0) {
                // 只有「未分配」(-1) 才走兜底；-2 已在上面 continue 掉
                final int li = localGifts.indexOf(gift);
                aiqiyiPos = li >= 0 ? (li % Math.max(maxCount, 6)) : 0;
            }
            switch (aiqiyiPos) {
                case 0: // left
                    gx = acx / 2f - (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 1: // top left
                    gx = acx * 2f / 3f - (dp(6) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0;
                    break;
                case 2: // bottom left
                    gx = acx * 2f / 3f - (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0.9f;
                    break;
                case 3: // right
                    gx = acx * 1.5f + (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 4: // top right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0.9f;
                    break;
                default: // bottom right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0;
                    break;
            }
            // Aiqiyi: 本地礼物已在上方 switch(gift.position) 中获得 0..maxCount-1 的环绕坐标，
            // 与官方佩戴礼物走完全相同的定位逻辑，此处不再单独计算。

            final float collapseProgressWithEnter;
            if (isOpening || enter >= 1f) {
                collapseProgressWithEnter = collapseProgress;
            } else {
                collapseProgressWithEnter = Math.min(enter, collapseProgress);
            }

            final float delayFraction = 0.2f;
            final float maxDelayFraction = 1.6f * delayFraction;
            final float intervalFraction = 1f - maxDelayFraction;

            float delay = delayValue * delayFraction;
            float collapse = collapseProgressWithEnter >= 1f - delay ? 1f
                    : Utilities.clamp01((collapseProgressWithEnter - maxDelayFraction + delay) / intervalFraction);
            if (collapse < 1f) {
                gx = AndroidUtilities.lerp(realCX, gx, giftCollapseXInterpolator.getInterpolation(collapse));
                gy = AndroidUtilities.lerp(realCY, gy, giftCollapseYInterpolator.getInterpolation(collapse));
                scale = AndroidUtilities.lerp(scale / 2f, scale, collapse);
            }

            gift.draw(canvas, gx, gy, scale, 0, alpha, 1f);
        }

        canvas.restore();
    }

    public Gift getGiftUnder(float x, float y) {
        for (int i = 0; i < gifts.size(); ++i) {
            if (gifts.get(i).bounds.contains(x, y))
                return gifts.get(i);
        }
        // Aiqiyi: 本地礼物也要能点
        for (int i = 0; i < localGifts.size(); ++i) {
            if (localGifts.get(i).bounds.contains(x, y))
                return localGifts.get(i);
        }
        return null;
    }

    private Gift pressedGift;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        final Gift hit = getGiftUnder(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedGift = hit;
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(true);
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedGift != hit && pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressedGift != null) {
                onGiftClick(pressedGift);
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                pressedGift = null;
            }
        }
        return pressedGift != null;
    }

    public void onGiftClick(Gift gift) {
        // Aiqiyi: 本地虚拟礼物没有真实 NFT，直接跳 t.me/nft/<slug> 会打开别人的官方礼物页
        // （如图中 Amirhosein 的 Resistance Dog #4,692）。这里先按 collectibleId 反查本地数据，
        // 命中就走本地详情卡 StarGiftSheet，未命中才是真正的官方礼物，才跳浏览器。
        if (aiqiyiOpenLocalGift(gift)) {
            return;
        }
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }

    /** Aiqiyi: 本地礼物详情。命中返回 true，未命中返回 false。 */
    private boolean aiqiyiOpenLocalGift(Gift gift) {
        try {
            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            if (!uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftStore.INSTANCE.hasLocalGifts(selfId)) {
                return false;
            }
            final java.util.ArrayList<uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData> data =
                    uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftStore.INSTANCE.getDataForUser(selfId);
            if (data == null || data.isEmpty()) {
                return false;
            }
            uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData hit = null;
            for (int i = 0; i < data.size(); i++) {
                final uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData d = data.get(i);
                if (d.getCollectibleId() == gift.id) {
                    hit = d;
                    break;
                }
            }
            if (hit == null && gift.slug != null) {
                for (int i = 0; i < data.size(); i++) {
                    final uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData d = data.get(i);
                    if (gift.slug.equals(d.getSlug())) {
                        hit = d;
                        break;
                    }
                }
            }
            if (hit == null) {
                return false;
            }
            final android.content.Context ctx = getContext();
            if (ctx == null) {
                return false;
            }
            final TL_stars.SavedStarGift saved =
                    uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftConverter.toSavedGift(hit, true);
            new org.telegram.ui.Stars.StarGiftSheet(ctx, currentAccount, selfId, resourcesProvider)
                    .set(saved, null)
                    .show();
            return true;
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e("Aiqiyi aiqiyiOpenLocalGift failed", t);
            return false;
        }
    }

}
