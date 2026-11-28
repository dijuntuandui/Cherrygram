package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_fragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * [Aiqiyi] 典藏号码弹窗调度层。
 *
 * 设计原则：不克隆官方弹窗，直接复用 FragmentUsernameBottomSheet，
 * 只负责「造一个假 TL_collectibleInfo 塞给它」。
 *
 * 号码本身永远来自当前生效的本地号码（由调用方传入），不另存一份。
 */
public final class AiqiyiCollectibleSheet {

    private AiqiyiCollectibleSheet() {
    }

    /**
     * 展示典藏号码弹窗（仿官方）。
     *
     * @param fragment ProfileActivity 实例，用来拿 Context / ResourceProvider
     * @param user     归属用户（头像、名字取自它）
     * @param phone    当前生效号码（含 + 和格式，直接透传给官方弹窗做标题）
     */
    public static void show(final ProfileActivity fragment, final int account, final TLRPC.User user, final String phone) {
        if (fragment == null || user == null || TextUtils.isEmpty(phone)) {
            return;
        }
        final AiqiyiCollectibleInfo cfg =
                AiqiyiCollectibleInfo.load(account, user.id, phone);

        final TL_fragment.TL_collectibleInfo info = buildInfo(cfg);

        AndroidUtilities.runOnUIThread(() -> {
            try {
                FragmentUsernameBottomSheet.open(
                        fragment.getContext(),
                        FragmentUsernameBottomSheet.TYPE_PHONE,
                        phone,
                        user,
                        info,
                        fragment.getResourceProvider());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    /**
     * 把本地配置拼成官方弹窗认的 TL_collectibleInfo。
     *
     * 字段语义：
     *  - purchase_date  秒级时间戳
     *  - crypto_amount  以「万分之一」为单位的长整型（1837.5 TON → 18375000）
     *  - crypto_currency 币种符号
     *  - amount         以「分」为单位的长整型（1782.69 USD → 178269）
     *  - currency       法币代码
     *  - url            按钮链接，用 Fragment 官网
     */
    private static TL_fragment.TL_collectibleInfo buildInfo(AiqiyiCollectibleInfo cfg) {
        TL_fragment.TL_collectibleInfo info = new TL_fragment.TL_collectibleInfo();
        info.purchase_date = AiqiyiCollectibleInfo.parseDate(
                cfg.date, defaultPurchaseDate());
        info.crypto_currency = "TON";
        info.crypto_amount = parseCryptoAmount(cfg.crypto);
        info.currency = "USD";
        info.amount = parseFiatAmount(cfg.usd);
        info.url = "https://fragment.com";
        return info;
    }

    /** 默认时间戳：2026-04-02 10:31:38 本地时间 */
    private static int defaultPurchaseDate() {
        return AiqiyiCollectibleInfo.parseDate(
                AiqiyiCollectibleInfo.DEFAULT_DATE, (int) (System.currentTimeMillis() / 1000L));
    }

    /**
     * "1837.5" → 1837500000000。
     * 官方 BillingController.formatCurrency 对 TON 用 amount / 1_000_000_000.0（十亿分之一 / nanoton），
     * 所以这里小数位必须是 9，否则会少几个数量级。
     */
    private static long parseCryptoAmount(String raw) {
        return scaleToLong(raw, 9);
    }

    /** "1782.69" → 178269（分） */
    private static long parseFiatAmount(String raw) {
        return scaleToLong(raw, 2);
    }

    private static long scaleToLong(String raw, int decimals) {
        if (TextUtils.isEmpty(raw)) return 0L;
        try {
            String s = raw.trim().replace(",", "").replace(" ", "");
            // 去掉非数字非小数点字符（$ 、TON、空格等）
            StringBuilder sb = new StringBuilder(s.length());
            boolean dotSeen = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                } else if (c == '.' && !dotSeen) {
                    sb.append('.');
                    dotSeen = true;
                }
            }
            if (sb.length() == 0) return 0L;
            java.math.BigDecimal bd = new java.math.BigDecimal(sb.toString());
            bd = bd.movePointRight(decimals);
            return bd.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        } catch (Throwable e) {
            FileLog.e(e);
            return 0L;
        }
    }

    /**
     * 「编辑典藏信息」弹窗：三个输入框，改日期/资金，不碰号码。
     *
     * @param onSaved 保存成功回调（用来刷新界面，可以为 null）
     */
    public static void showEditor(final ProfileActivity fragment,
                                  final int account,
                                  final TLRPC.User user,
                                  final String phone,
                                  final Runnable onSaved) {
        if (fragment == null || user == null || TextUtils.isEmpty(phone)) return;
        final Activity activity = fragment.getParentActivity();
        if (activity == null) return;

        final AiqiyiCollectibleInfo cfg =
                AiqiyiCollectibleInfo.load(account, user.id, phone);

        final Context ctx = activity;
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        box.setPadding(pad, AndroidUtilities.dp(8), pad, 0);

        final EditText edDate = makeField(ctx, box, "成交日期",
                "例如 2026-04-02 10:31:38", cfg.date, InputType.TYPE_CLASS_TEXT);
        final EditText edCrypto = makeField(ctx, box, "成交资金（TON）",
                AiqiyiCollectibleInfo.DEFAULT_CRYPTO, cfg.crypto,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText edUsd = makeField(ctx, box, "美元金额（USD）",
                AiqiyiCollectibleInfo.DEFAULT_USD, cfg.usd,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("编辑典藏信息");
        builder.setView(box);
        builder.setNegativeButton(LocaleController.getString("Cancel"), null);
        builder.setPositiveButton(LocaleController.getString("Save"), (d, which) -> {
            AiqiyiCollectibleInfo.save(account, user.id, phone,
                    edDate.getText().toString(),
                    edCrypto.getText().toString(),
                    edUsd.getText().toString());
            if (onSaved != null) onSaved.run();
        });
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
    }

    private static EditText makeField(Context ctx, LinearLayout parent,
                                      String label, String hint, String value,
                                      int inputType) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        parent.addView(tv, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT, 0, 12, 0, 0));

        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setText(value);
        et.setTextSize(16);
        et.setSingleLine(true);
        et.setInputType(inputType);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setBackground(null);
        et.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        parent.addView(et, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 44,
                Gravity.LEFT, 0, 4, 0, 0));
        return et;
    }
}
