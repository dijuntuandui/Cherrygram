package uz.unnarsx.cherrygram.aiqiyi.verified;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
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
     * ImageSpan 会持有它，复用会让多个 cell 的 bounds 互相干扰。
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
        // 图标高度贴合小字视觉高度，宽度按固有比例
        final int iconH = Math.max(1, Math.round(AndroidUtilities.dp(TEXT_SIZE_DP)));
        int iconW = iconH;
        final int iw = icon.getIntrinsicWidth();
        final int ih = icon.getIntrinsicHeight();
        if (iw > 0 && ih > 0) {
            iconW = Math.max(1, Math.round(iconH * (float) iw / ih));
        }
        icon.setBounds(0, 0, iconW, iconH);

        SpannableString sp = new SpannableString("\u00A0" + text);
        sp.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(sp);
    }

    public void updateColors() {
        // 比普通副标题更淡，符合官方「附属说明」的观感
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
        textView.setTextColor(gray);
        textView.setAlpha(0.85f);
    }
}