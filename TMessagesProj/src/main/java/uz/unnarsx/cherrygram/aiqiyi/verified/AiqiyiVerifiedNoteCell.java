package uz.unnarsx.cherrygram.aiqiyi.verified;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import org.telegram.ui.Components.ColoredImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

/**
 * [Aiqiyi] 认证说明行（独立 cell，位于资料卡片外侧下方）
 *
 * 对齐官方视觉（参考图三）：
 *   星标在文字左侧，浅灰小字，左对齐；
 *   与「添加至群组或频道」等卡片外元素同一层级；
 *   左右缩进与卡片内的行保持一致（18dp）。
 *
 * 不复用 TextDetailCell：那个 cell 强制 60dp 两行布局，
 * 塞第三行会和 DC 行重叠（已踩过）。
 */
public class AiqiyiVerifiedNoteCell extends LinearLayout {

    private static final float TEXT_SIZE_DP = 12.5f;
    /**
     * [Aiqiyi] 前置星标的视觉尺寸（dp）。
     *
     * 不能直接用字号：12.5sp 的 cap height 只有约 9dp，
     * 行高约 16~17dp，字号本身比文字视觉高度大一圈，
     * 直接拿 12.5dp 当图标高度会显得又大又飘。
     * 取字号 0.88 倍左右，和文字的行内视觉高度吻合。
     */
    private static final float ICON_SIZE_DP = 11f;

    private final TextView textView;
    private final Theme.ResourcesProvider resourcesProvider;

    public AiqiyiVerifiedNoteCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);
        setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        // 与卡片内部行的左右缩进一致
        setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10),
                AndroidUtilities.dp(18), AndroidUtilities.dp(14));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DP);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        updateColors();
    }

    /**
     * 设置文案。icon 为前置星标，可为 null。
     * 每次调用传入的 Drawable 应当是新建实例：
     * span 会持有它，复用会让多个 cell 的 bounds 互相干扰。
     */
    public void setText(CharSequence text, Drawable icon) {
        if (TextUtils.isEmpty(text)) {
            textView.setText(null);
            return;
        }
        if (icon == null) {
            textView.setText(text);
            return;
        }

        /*
         * [Aiqiyi] 用 ColoredImageSpan + ALIGN_CENTER 代替 ImageSpan.ALIGN_BOTTOM。
         *
         * ImageSpan.ALIGN_BOTTOM 把图标贴到行框底边（bottom），
         * 而 bottom 含 descender 空白，这行文字又全在 baseline 之上，
         * 结果图标相对文字明显偏高。
         *
         * ColoredImageSpan 的 ALIGN_CENTER（该类第 163-164 行）：
         *     transY = top + (bottom - top) / 2 - drawableHeight / 2;
         * 按行框真正中点算，不受 descender 影响，与官方内联图标一致。
         */
        final int iconSize = Math.max(1, Math.round(AndroidUtilities.dp(ICON_SIZE_DP)));
        ColoredImageSpan span = new ColoredImageSpan(icon, ColoredImageSpan.ALIGN_CENTER);
        // 星标自带颜色，禁止 span 按 paint 颜色二次重染（public 字段，不是方法）
        span.recolorDrawable = false;
        span.setSize(iconSize);

        // 前面留一个不断行空格，保证图标跟着第一行文字走
        SpannableString sp = new SpannableString("\u00A0" + text);
        sp.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(sp);
    }

    public void updateColors() {
        // 比普通副标题更淡，符合官方「附属说明」的观感
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
        textView.setTextColor(gray);
        textView.setAlpha(0.85f);
    }
}
