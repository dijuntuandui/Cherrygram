/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

public class TextDetailCell extends FrameLayout {

    public final SpoilersTextView textView;
    public final LinkSpanDrawable.LinksTextView valueTextView;
    public final LinkSpanDrawable.LinksTextView rightValueTextView;
    private final TextView showMoreTextView = null;
    private final ImageView imageView;
    private boolean needDivider;
    // [Aiqiyi] 二代认证第三行
    private TextView thirdLineTextView;
    private boolean thirdLineVisible;
    private boolean contentDescriptionValueFirst;
    private boolean multiline;

    private Theme.ResourcesProvider resourcesProvider;

    public TextDetailCell(Context context) {
        this(context, null);
    }

    public TextDetailCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false, false);
    }

    public TextDetailCell(Context context, Theme.ResourcesProvider resourcesProvider, boolean textMultiline, boolean valueMultiline) {
        this(context, 23, resourcesProvider, textMultiline, valueMultiline);
    }

    public TextDetailCell(Context context, int horizPadding, Theme.ResourcesProvider resourcesProvider, boolean textMultiline, boolean valueMultiline) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.multiline = textMultiline || valueMultiline;

        textView = new SpoilersTextView(context, resourcesProvider);
        textView.setOnLinkLongPressListener(span -> {
            if (span != null) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {};
                span.onClick(textView);
            }
        });
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (textMultiline) {
            setMinimumHeight(dp(60));
        } else {
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
        }
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setPadding(dp(6), dp(2), dp(6), dp(5));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, horizPadding - 6, 8 - 2, horizPadding - 6, textMultiline ? 27 : 0));

        valueTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
            @Override
            protected int processColor(int color) {
                return TextDetailCell.this.processColor(color);
            }
            @Override
            public int overrideColor() {
                return processColor(super.overrideColor());
            }
        };
        valueTextView.setOnLinkLongPressListener(span -> {
            if (span != null) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {};
                span.onClick(valueTextView);
            }
        });
        if (valueMultiline) {
            setMinimumHeight(dp(60));
        } else {
            valueTextView.setLines(1);
            valueTextView.setSingleLine(true);
        }
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setPadding(0, dp(1), 0, dp(6));
        if (textMultiline) {
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), horizPadding, 33 - 1, horizPadding, 10 - 6));
        } else {
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, horizPadding, 33 - 1, horizPadding, 10 - 6));
        }

        rightValueTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
            @Override
            protected int processColor(int color) {
                return TextDetailCell.this.processColor(color);
            }
            @Override
            public int overrideColor() {
                return processColor(super.overrideColor());
            }
        };
        rightValueTextView.setOnLinkLongPressListener(span -> {
            if (span != null) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {};
                span.onClick(valueTextView);
            }
        });
        if (this.multiline = multiline) {
            setMinimumHeight(dp(60));
        } else {
            rightValueTextView.setLines(1);
            rightValueTextView.setSingleLine(true);
        }
        rightValueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        rightValueTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        rightValueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        rightValueTextView.setEllipsize(TextUtils.TruncateAt.END);
        rightValueTextView.setPadding(0, dp(1), 0, dp(6));
        if (textMultiline) {
            addView(rightValueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), horizPadding, 33 - 1, horizPadding, 10 - 6));
        } else {
            addView(rightValueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, horizPadding, 33 - 1, horizPadding, 10 - 6));
        }

        // [Aiqiyi] 第三行：默认隐藏，仅二代认证角标场景使用
        thirdLineTextView = new TextView(context);
        thirdLineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        thirdLineTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        thirdLineTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        thirdLineTextView.setEllipsize(TextUtils.TruncateAt.END);
        thirdLineTextView.setLines(1);
        thirdLineTextView.setSingleLine(true);
        thirdLineTextView.setPadding(0, 0, 0, dp(8));
        thirdLineTextView.setVisibility(GONE);
        addView(thirdLineTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, horizPadding, 0, horizPadding, 6));

        updateColors();

        imageView = new ImageView(context);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrameRelatively(48, 48, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
    }

    protected int processColor(int color) {
        return color;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean hit = valueTextView.hit((int) ev.getX() - valueTextView.getLeft(), (int) ev.getY() - valueTextView.getTop()) != null;
        if (!hit) {
            hit = textView.hit((int) ev.getX() - textView.getLeft(), (int) ev.getY() - textView.getTop()) != null;
        }
        if (hit) {
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // [Aiqiyi] 第三行可见时，基础高度上再加一个第三行的高度
        final boolean extraLine = thirdLineVisible && thirdLineTextView != null
                && thirdLineTextView.getVisibility() == VISIBLE;
        final int baseHeight;
        if (multiline) {
            baseHeight = -1; // 走自由测量，沿用原逻辑
        } else if (extraLine) {
            baseHeight = dp(60) + getThirdLineHeight() + (needDivider ? 1 : 0);
        } else {
            baseHeight = dp(60) + (needDivider ? 1 : 0);
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            baseHeight < 0 ? heightMeasureSpec
                    : MeasureSpec.makeMeasureSpec(baseHeight, MeasureSpec.EXACTLY)
        );
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        rightValueTextView.setVisibility(View.GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, CharSequence value2, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        rightValueTextView.setVisibility(View.VISIBLE);
        rightValueTextView.setText(value2);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    /**
     * [Aiqiyi] 设置第三行内容（传 null 或空串等同隐藏）
     * 与现有 setTextAndValue 兼容，可安全叠加调用
     */
    public void setThirdLine(CharSequence text) {
        if (thirdLineTextView == null) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            clearThirdLine();
            return;
        }
        thirdLineTextView.setText(text);
        thirdLineTextView.setVisibility(VISIBLE);
        thirdLineVisible = true;
        applyThirdLineLayout();
    }

    /**
     * [Aiqiyi] 设置第三行，带前置图标
     *
     * 图标用 ImageSpan 内联到文字前，跟随文字基线布局，
     * 这样 RTL 情况下也自然靠对（不需要手动翻转）。
     * 图标尺寸按文字行高计算，保证视觉对齐。
     */
    public void setThirdLine(CharSequence text, Drawable icon) {
        if (thirdLineTextView == null) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            clearThirdLine();
            return;
        }
        if (icon == null) {
            setThirdLine(text);
            return;
        }

        // [Aiqiyi] 图标尺寸严格不超过 13sp 字号的视觉高度
        // 这里不读 paint 度量：setText 之前 paint 未必已按新字号刷新，
        // 直接用 dp(13) 换算，结果确定且和 getThirdLineHeight 口径一致
        final int iconH = Math.max(1, Math.round(dp(13) * 0.95f));
        int iconW = iconH;
        final int iw = icon.getIntrinsicWidth();
        final int ih = icon.getIntrinsicHeight();
        if (iw > 0 && ih > 0) {
            iconW = Math.max(1, Math.round(iconH * (float) iw / ih));
        }
        icon.setBounds(0, 0, iconW, iconH);

        SpannableString sp = new SpannableString("\u00A0" + text);
        sp.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        thirdLineTextView.setText(sp);
        thirdLineTextView.setVisibility(VISIBLE);
        thirdLineVisible = true;
        applyThirdLineLayout();
    }

    /**
     * [Aiqiyi] 清除第三行并恢复标准 2 行高度
     */
    public void clearThirdLine() {
        if (thirdLineTextView == null) {
            return;
        }
        boolean wasVisible = thirdLineVisible;
        thirdLineTextView.setText(null);
        thirdLineTextView.setVisibility(GONE);
        thirdLineVisible = false;
        if (wasVisible) {
            applyThirdLineLayout();
        }
    }

    public boolean hasThirdLine() {
        return thirdLineVisible;
    }

    /**
     * [Aiqiyi] 第三行出现/消失时重排上下两行的位置
     *
     * 原布局：valueTextView 用 topMargin=32dp 从顶部定位（非 BOTTOM），
     * 而 thirdLineTextView 用 Gravity.BOTTOM 贴底。
     * 二者各自独立定位，第三行一出现就和 DC 行叠在一起。
     *
     * 处理：第三行可见时把 valueTextView 整体下移一个第三行的高度，
     * 同时第三行保持贴底，视觉上就变成 ID / DC / 认证行 的垂直堆叠。
     */
    private void applyThirdLineLayout() {
        if (valueTextView == null || thirdLineTextView == null) {
            requestLayout();
            return;
        }
        final MarginLayoutParams lp = (MarginLayoutParams) valueTextView.getLayoutParams();
        if (lp == null) {
            requestLayout();
            return;
        }
        if (thirdLineVisible) {
            // 第三行高度：字体度量 + 上下 padding，留一点呼吸空间
            final int lineH = getThirdLineHeight();
            // 原始 topMargin 是 33-1=32dp，第三行出现时整体下移一个行高
            lp.topMargin = dp(33 - 1) + lineH;
        } else {
            lp.topMargin = dp(33 - 1);
        }
        valueTextView.setLayoutParams(lp);
        requestLayout();
        invalidate();
    }

    /** 估算第三行占用的垂直高度（13sp 字号，行高系数 1.45 + 上下留白） */
    private int getThirdLineHeight() {
        final int textH = Math.round(dp(13) * 1.45f);
        return textH + dp(4);
    }

    public void setImage(Drawable drawable) {
        setImage(drawable, null);
    }

    public void setImage(Drawable drawable, CharSequence imageContentDescription) {
        ((MarginLayoutParams) valueTextView.getLayoutParams()).rightMargin = !LocaleController.isRTL && drawable != null ? dp(28 + 12 + 12 + 6) : dp(23);
        imageView.setImageDrawable(drawable);
        imageView.setFocusable(drawable != null);
        imageView.setContentDescription(imageContentDescription);
        if (drawable == null) {
            imageView.setBackground(null);
            imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        } else {
            imageView.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(48), Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        int margin = dp(23) + (drawable == null ? 0 : dp(48));
        if (LocaleController.isRTL) {
            ((MarginLayoutParams) textView.getLayoutParams()).leftMargin = margin;
        } else {
            ((MarginLayoutParams) textView.getLayoutParams()).rightMargin = margin;
        }
        textView.requestLayout();
    }

    public boolean hasImage() {
        return imageView.getDrawable() != null;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setImageClickListener(View.OnClickListener clickListener) {
        imageView.setOnClickListener(clickListener);
        if (clickListener == null) {
            imageView.setClickable(false);
        }
    }

    public void setTextWithEmojiAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setContentDescriptionValueFirst(boolean contentDescriptionValueFirst) {
        this.contentDescriptionValueFirst = contentDescriptionValueFirst;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        textView.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : Theme.dividerPaint;
            if (paint == null) paint = Theme.dividerPaint;
            canvas.drawLine(
                LocaleController.isRTL ? 0 : dp(20),
                getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? dp(20) : 0),
                getMeasuredHeight() - 1,
                paint
            );
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final CharSequence text = textView.getText();
        final CharSequence valueText = valueTextView.getText();
        if (!TextUtils.isEmpty(text) && !TextUtils.isEmpty(valueText)) {
            info.setText((contentDescriptionValueFirst ? valueText : text) + ": " + (contentDescriptionValueFirst ? text : valueText));
        }
    }

    public void updateColors() {
        textView.setLinkTextColor(processColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider)));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.invalidate();
        valueTextView.setLinkTextColor(processColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider)));
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        rightValueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        // [Aiqiyi] 第三行用更淡的灰，视觉上作为附属说明
        if (thirdLineTextView != null) {
            thirdLineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            thirdLineTextView.setAlpha(0.85f);
        }
        valueTextView.invalidate();
    }

    public static final class Factory extends UItem.UItemFactory<TextDetailCell> {
        static { setup(new Factory()); }

        @Override
        public TextDetailCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new TextDetailCell(context, 18, resourcesProvider, false, false);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            view.setId(item.id);
            ((TextDetailCell) view).setTextAndValue(item.text, item.subtext, divider);
        }

        public static UItem of(int id, CharSequence title, CharSequence value) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.text = title;
            item.subtext = value;
            return item;
        }
    }
}
