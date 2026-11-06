package com.Huanghun.protocol;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 凭私钥生成（第 9 项）的入口面板。
 *
 * 这里不走「手打 512 位 auth_key」的路线。原因很简单：auth_key 与账号之间没有
 * 可反查的映射关系，服务端是靠 auth_key_id（SHA1 前 8 字节）在服务端表里查表才知道
 * 这是谁。客户端本地无法回答「这串 key 是哪个账号」，所以让用户手打等于让用户盲猜，
 * 必然失败。
 *
 * 唯一成立的形态是：用户给出 .session 文件，从中读出 dc_id + auth_key，原样交给
 * Telegram 验证。登得上就证明凭证有效，登不上就是无效凭证。
 */
public final class SessionImportDialog {

    public interface Callback {
        void onPickFile();
        void onCancel();
    }

    private static int dp(float value) {
        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static TextView label(Activity activity, String text, int color, float size, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    public static void show(final Activity activity, final Callback callback) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(dp(20));
        root.setPadding(pad, Math.round(dp(16)), pad, 0);

        root.addView(label(activity, "凭已有会话导入", 0xFF111111, 16f, true));

        TextView hint = label(activity,
                "勾选 .session 和它同名的 .json（两个一起选），或者直接选打包好的 zip。"
                        + "程序会自己读出数据中心编号、auth_key 与账号 id，不需要你手动填任何内容。",
                0xFF6E6E73, 13f, false);
        hint.setPadding(0, Math.round(dp(8)), 0, 0);
        root.addView(hint);

        // 橙色提示条：讲清楚产物性质
        LinearLayout warn = new LinearLayout(activity);
        warn.setOrientation(LinearLayout.VERTICAL);
        int warnPad = Math.round(dp(12));
        warn.setPadding(warnPad, warnPad, warnPad, warnPad);
        GradientDrawable warnBg = rounded(0x1AFF9500, Math.round(dp(10)));
        warnBg.setStroke(Math.round(dp(1)), 0x66FF9500);
        warn.setBackground(warnBg);
        LinearLayout.LayoutParams warnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        warnParams.topMargin = Math.round(dp(14));
        warn.setLayoutParams(warnParams);

        warn.addView(label(activity, "读出来的凭证会原样使用", 0xFFCC7A00, 13f, true));
        TextView warnBody = label(activity,
                "如果这份文件本身有效，就一定能登上；如果已失效，会直接报错。"
                        + "是否属于某个账号、能问到什么，只有登录之后才知道。",
                0xFF9A6500, 12f, false);
        warnBody.setPadding(0, Math.round(dp(6)), 0, 0);
        warn.addView(warnBody);
        root.addView(warn);

        TextView tip = label(activity,
                "产物：一个平铺的 zip（内含 session、json 与 2fa.txt），"
                        + "落在 /sdcard/Download/爱琦艺/协议转换/<时间戳>/ 下。",
                0xFF8E8E93, 12f, false);
        tip.setPadding(0, Math.round(dp(14)), 0, 0);
        root.addView(tip);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setNegativeButton("取消", (d, w) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .setPositiveButton("选择文件", (d, w) -> {
                    if (callback != null) {
                        callback.onPickFile();
                    }
                })
                .create();
        dialog.show();
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
