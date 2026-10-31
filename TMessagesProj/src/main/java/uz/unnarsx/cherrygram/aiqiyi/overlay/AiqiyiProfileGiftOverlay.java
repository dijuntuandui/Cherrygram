package uz.unnarsx.cherrygram.aiqiyi.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

/**
 * 爱琦艺第三套：资料页礼物覆盖层（完全独立，优先级最高）
 *
 * 不管前两套（LocalGiftStore / LocalGiftConverter + ProfileGiftsView），
 * 也不管原生 PeerColor / emoji_status / 各类开关。
 * 自己在 TopView.onDraw 最后一步强行画：渐变底色 -> 底纹 -> 本体。
 * 数据自持，独立 SharedPreferences。
 */
public class AiqiyiProfileGiftOverlay {

    private static final String TAG = "AQY_OVL";
    private static final String PREFS = "aqy_gift_overlay";
    private static final long RELOAD_MS = 800L;

    /** 单条覆盖礼物 */
    public static class Entry {
        public long userId;
        public long collectibleId;
        public long bodyDocId;
        public long patternDocId;
        public int centerColor = 0xFF363738;
        public int edgeColor = 0xFF0E0F0F;
        public int patternColor = 0xFFFFFFFF;
        public int textColor = 0xFFFFFFFF;
        public String title = "";
        public int count = 1;
        public boolean active;

        public boolean isValid() {
            return userId != 0L && (bodyDocId != 0L || patternDocId != 0L);
        }
    }

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient gradient;
    private int gradientW, gradientH, gradientColor1, gradientColor2;

    private AnimatedEmojiDrawable patternDrawable;
    private long patternDrawableId;
    private AnimatedEmojiDrawable bodyDrawable;
    private long bodyDrawableId;

    private Entry lastEntry;
    private long lastLoadTime;
    private long patternRetryAt;
    private long bodyRetryAt;
    private int patternRetryCount;
    /** [Aiqiyi M4-fix] pattern 反查缓存：避免每帧重复查库 */
    private boolean patternResolveMissed;
    private long patternResolveFor;
    private int bodyRetryCount;
    private static final long RETRY_MS = 1500L;

    // ===================== 数据层 =====================

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String keyOf(long userId) {
        return "ovl_" + userId;
    }

    public static void save(Entry e) {
        SharedPreferences p = prefs();
        if (p == null || e == null || e.userId == 0L) return;
        try {
            String s = e.userId + "|" + e.collectibleId + "|" + e.bodyDocId + "|" + e.patternDocId
                    + "|" + e.centerColor + "|" + e.edgeColor + "|" + e.patternColor
                    + "|" + e.textColor + "|" + e.count + "|"
                    + (e.title == null ? "" : e.title.replace("|", " "))
                    + "|" + (e.active ? 1 : 0);
            p.edit().putString(keyOf(e.userId), s).apply();
            Log.d(TAG, "save uid=" + e.userId + " body=" + e.bodyDocId
                    + " pat=" + e.patternDocId + " active=" + e.active);
        } catch (Throwable t) {
            FileLog.e(TAG + " save failed", t);
        }
    }

    public static void clear(long userId) {
        SharedPreferences p = prefs();
        if (p == null || userId == 0L) return;
        p.edit().remove(keyOf(userId)).apply();
        Log.d(TAG, "clear uid=" + userId);
    }

    public static Entry get(long userId) {
        if (userId == 0L) return null;
        SharedPreferences p = prefs();
        if (p == null) return null;
        try {
            String s = p.getString(keyOf(userId), null);
            if (TextUtils.isEmpty(s)) return null;
            String[] a = s.split("\\|", -1);
            if (a.length < 11) return null;
            Entry e = new Entry();
            e.userId = parseLong(a[0]);
            e.collectibleId = parseLong(a[1]);
            e.bodyDocId = parseLong(a[2]);
            e.patternDocId = parseLong(a[3]);
            e.centerColor = (int) parseLong(a[4]);
            e.edgeColor = (int) parseLong(a[5]);
            e.patternColor = (int) parseLong(a[6]);
            e.textColor = (int) parseLong(a[7]);
            e.count = (int) parseLong(a[8]);
            e.title = a[9];
            e.active = "1".equals(a[10].trim());
            return e.isValid() ? e : null;
        } catch (Throwable t) {
            FileLog.e(TAG + " get failed", t);
            return null;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static Entry make(long userId, long collectibleId, long bodyDocId, long patternDocId,
                             int center, int edge, int patColor, int textColor,
                             String title, int count) {
        Entry e = new Entry();
        e.userId = userId;
        e.collectibleId = collectibleId;
        e.bodyDocId = bodyDocId;
        e.patternDocId = patternDocId;
        e.centerColor = center;
        e.edgeColor = edge;
        e.patternColor = patColor;
        e.textColor = textColor;
        e.title = title == null ? "" : title;
        e.count = count <= 0 ? 1 : count;
        e.active = true;
        return e;
    }

    // ===================== 绘制层 =====================

    public boolean draw(Canvas canvas, int w, int h, long userId) {
        if (canvas == null || w <= 0 || h <= 0 || userId == 0L) return false;
        Entry e = obtain(userId);
        if (e == null || !e.active) return false;
        drawGradient(canvas, w, h, e);
        drawPattern(canvas, w, h, e);
        drawBody(canvas, w, h, e);
        return true;
    }

    private Entry obtain(long userId) {
        long now = System.currentTimeMillis();
        if (lastEntry != null && lastEntry.userId == userId && now - lastLoadTime < RELOAD_MS) {
            return lastEntry;
        }
        Entry e = get(userId);
        lastEntry = e;
        lastLoadTime = now;
        return e;
    }

    private void drawGradient(Canvas canvas, int w, int h, Entry e) {
        final int c1 = e.centerColor | 0xFF000000;
        final int c2 = e.edgeColor | 0xFF000000;
        if (gradient == null || gradientW != w || gradientH != h
                || gradientColor1 != c1 || gradientColor2 != c2) {
            gradientW = w;
            gradientH = h;
            gradientColor1 = c1;
            gradientColor2 = c2;
            float radius = Math.max(w, h) * 0.95f;
            gradient = new RadialGradient(w / 2f, h * 0.55f, radius,
                    new int[]{c1, c2}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        }
        gradientPaint.setShader(gradient);
        gradientPaint.setAlpha(0xFF);
        canvas.drawRect(0, 0, w, h, gradientPaint);
        gradientPaint.setShader(null);
        Log.d(TAG, "drawGradient c=" + Integer.toHexString(c1) + " e=" + Integer.toHexString(c2));
    }

    /**
     * [Aiqiyi M4-fix] 反查真实 pattern 文档 id。
     *
     * 老数据的 patternDocId 往往是 0（存的时候只复制了字段，没把 doc 钉进本地），
     * 原实现直接 return，底纹永远画不出来——表现就是「只有礼物本体、没有底纹」。
     *
     * 这里走 LocalGiftConverter.resolvePatternDocument 的三层兜底：
     *   ① patternDocumentId 命中内存缓存
     *   ② patternDocumentData（本地 base64）反序列化
     *   ③ 缓存文件按哈希捞 / 目录同款 pattern
     * 捞到后把真实 id 回写进内存 Entry，下一次绘制就不用再查。
     */
    private long resolvePatternDocId(Entry e) {
        if (e == null) return 0L;
        if (e.patternDocId != 0L) return e.patternDocId;
        // 已经查过且确认没有，就不再反复查（避免每帧都读 prefs / 反序列化）
        if (patternResolveMissed && patternResolveFor == e.collectibleId) return 0L;
        try {
            uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData d =
                    uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftStore
                            .findLocalGift(e.userId, e.collectibleId);
            if (d == null) {
                Log.d(TAG, "resolvePat: no local gift cid=" + e.collectibleId);
                patternResolveMissed = true;
                patternResolveFor = e.collectibleId;
                return 0L;
            }
            org.telegram.tgnet.TLRPC.Document doc =
                    uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftConverter
                            .resolvePatternDocument(d);
            if (doc != null && doc.id != 0L) {
                e.patternDocId = doc.id;
                Log.d(TAG, "resolvePat: hit cid=" + e.collectibleId + " doc=" + doc.id);
                return doc.id;
            }
            Log.d(TAG, "resolvePat: miss cid=" + e.collectibleId
                    + " patId=" + d.getPatternDocumentId()
                    + " hasB64=" + (!d.getPatternDocumentData().isEmpty()));
            patternResolveMissed = true;
            patternResolveFor = e.collectibleId;
        } catch (Throwable t) {
            FileLog.e(TAG + " resolvePatternDocId failed", t);
        }
        return 0L;
    }

    private void drawPattern(Canvas canvas, int w, int h, Entry e) {
        final long patId = resolvePatternDocId(e);
        if (patId == 0L) {
            Log.d(TAG, "drawPattern skip no patternDocId");
            return;
        }
        if (patternDrawable == null || patternDrawableId != patId) {
            patternDrawableId = patId;
            patternDrawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount,
                    AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS, patId);
            if (patternDrawable != null) patternDrawable.preload();
            Log.d(TAG, "drawPattern new id=" + patId + " ok=" + (patternDrawable != null));
        }
        if (patternDrawable == null) {
            Log.d(TAG, "drawPattern drawable null id=" + patId);
            return;
        }
        // 先画，不要因为「暂时没加载好」就跳过。
        // AnimatedEmojiDrawable 内部会自己发起加载，第一帧没图是正常的。
        // 只有连续多次都没图（真正的文档缺失）才重建，最多 3 次，避免无限重试。
        final long nowPat = System.currentTimeMillis();
        if (patternDrawable.getImageReceiver() != null
                && !patternDrawable.getImageReceiver().hasImageLoaded()
                && patternRetryCount < 3
                && nowPat - patternRetryAt > RETRY_MS) {
            patternRetryAt = nowPat;
            patternRetryCount++;
            patternDrawableId = 0L;
            patternDrawable = null;
            Log.d(TAG, "drawPattern retry #" + patternRetryCount + " id=" + e.patternDocId);
        }
        if (patternDrawable == null) return;
        patternDrawable.setBounds(0, 0, w, h);
        patternDrawable.setAlpha(0xFF);
        patternDrawable.draw(canvas);
        Log.d(TAG, "drawPattern done");
    }

    private void drawBody(Canvas canvas, int w, int h, Entry e) {
        long id = e.bodyDocId != 0L ? e.bodyDocId : e.patternDocId;
        if (id == 0L) {
            Log.d(TAG, "drawBody skip no id");
            return;
        }
        if (bodyDrawable == null || bodyDrawableId != id) {
            bodyDrawableId = id;
            bodyDrawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount,
                    AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, id);
            if (bodyDrawable != null) bodyDrawable.preload();
            Log.d(TAG, "drawBody new id=" + id + " ok=" + (bodyDrawable != null));
        }
        if (bodyDrawable == null) {
            Log.d(TAG, "drawBody drawable null id=" + id);
            return;
        }
        // 与 drawPattern 一致：先画，只在连续失败时重建，绝不 return。
        final long nowBody = System.currentTimeMillis();
        if (bodyDrawable.getImageReceiver() != null
                && !bodyDrawable.getImageReceiver().hasImageLoaded()
                && bodyRetryCount < 3
                && nowBody - bodyRetryAt > RETRY_MS) {
            bodyRetryAt = nowBody;
            bodyRetryCount++;
            bodyDrawableId = 0L;
            bodyDrawable = null;
            Log.d(TAG, "drawBody retry #" + bodyRetryCount + " id=" + id);
        }
        if (bodyDrawable == null) return;
        final int size = Math.min(w, h) / 2;
        final int cx = w / 2;
        final int cy = (int) (h * 0.62f);
        bodyDrawable.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        bodyDrawable.setAlpha(0xFF);
        bodyDrawable.draw(canvas);
        Log.d(TAG, "drawBody done size=" + size);
    }

    /**
     * [Aiqiyi M4] 一次性迁移：把前两套里已佩戴的礼物灌进本套。
     * 用户升级后不用重新点佩戴就能看到效果。
     * 只在本地没有任何覆盖数据时执行，不覆盖用户已选的。
     */
    public static void migrateIfEmpty(long userId) {
        if (userId == 0L) return;
        if (get(userId) != null) return;
        try {
            Object worn = uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftStore
                    .getLastWornForUser(userId);
            if (worn == null) return;
            uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData d =
                    (uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData) worn;
            Entry e = make(userId, d.getCollectibleId(), d.getDocumentId(), d.getPatternDocumentId(),
                    d.getCenterColor() | 0xFF000000, d.getEdgeColor() | 0xFF000000,
                    d.getPatternColor() | 0xFF000000, d.getTextColor() | 0xFF000000,
                    d.getTitle(), d.getGiftNum());
            save(e);
            Log.d(TAG, "migrate uid=" + userId + " body=" + d.getDocumentId()
                    + " pat=" + d.getPatternDocumentId());
        } catch (Throwable t) {
            FileLog.e(TAG + " migrate failed", t);
        }
    }

    public void invalidate() {
        lastEntry = null;
        lastLoadTime = 0L;
    }

    public static void invalidateAll() {
        Log.d(TAG, "invalidateAll");
    }
}
