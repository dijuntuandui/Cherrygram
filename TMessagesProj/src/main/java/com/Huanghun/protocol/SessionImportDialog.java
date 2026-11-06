package com.Huanghun.protocol;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * 协议生成的入口面板（第 8 / 9 项共用）。
 *
 * <p>两种方式并存：上面选文件，下面手填。用户有 .session 就选文件，只有一串
 * auth_key 就手打，两条路都能出产物。</p>
 *
 * <p>关于手填：auth_key 与服务端账号之间确实没有可反查的映射，但生成 session
 * 并不需要知道「这是谁」。客户端只要装配出结构合法的凭证（auth_key + dc_id），
 * user_id 允许为 0，服务端在真正登录时按 auth_key_id 查表回填。所以手填这条路
 * 是成立的，缺的只是 dc_id，必须一并给出。</p>
 */
public final class SessionImportDialog {

    /** 面板动作回调。 */
    public interface Callback {
        /** 用户点了文件区，去走系统文件选择器。 */
        void onPickFile();

        /**
         * 用户点了「生成」，填好了手输内容。
         *
         * @param authKeyHex 512 位十六进制私钥（已去空白、统一小写）
         * @param dcId       数据中心编号 1~5
         * @param userId     账号 id，允许 0（表示未知，登录后由服务端回填）
         */
        void onManualInput(String authKeyHex, int dcId, long userId);

        /** 用户取消。 */
        void onCancel();
    }

    /** 第 8 项：生成新会话（用户自备凭据，可填 userId）。 */
    public static final int MODE_NEW_SESSION = 0;
    /** 第 9 项：凭私钥生成（只认私钥 + dc_id）。 */
    public static final int MODE_FROM_KEY = 1;

    private static int dp(float value) {
        return (int) (value * AndroidUtilities.density + 0.5f);
    }

    /**
     * 弹出面板。
     *
     * @param mode 决定标题文案与是否显示 uid 输入框
     */
    public static void show(Activity activity, int mode, final Callback callback) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final boolean needUserId = (mode == MODE_NEW_SESSION);

        BottomSheet.Builder builder = new BottomSheet.Builder(activity, false, null);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---------------- 标题 ----------------
        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(needUserId ? "生成新会话" : "凭私钥生成");
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 16, 22, 2));

        TextView sub = new TextView(activity);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setText("有 .session 文件就选文件；只有私钥就在下面手填。产物落在 /sdcard/Download/爱琦艺/协议转换/ 下。");
        sub.setLineSpacing(dp(2), 1f);
        root.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 4, 22, 14));

        // ---------------- 方式一：选择文件 ----------------
        addSectionHeader(activity, root, "选择文件");
        TextView pickRow = new TextView(activity);
        pickRow.setText(".session / .json / zip");
        pickRow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pickRow.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        pickRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        pickRow.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_windowBackgroundGray)));
        root.addView(pickRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 6));
        TextView pickHint = new TextView(activity);
        pickHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        pickHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        pickHint.setText("勾选 .session 和同名 .json（两个一起选），或直接选打包好的 zip；程序会自动读出 dc / uid / 私钥。");
        pickHint.setLineSpacing(dp(2), 1f);
        root.addView(pickHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 4));
        pickRow.setOnClickListener(v -> {
            if (callback != null) {
                callback.onPickFile();
            }
        });

        // ---------------- 分隔 ----------------
        TextView or = new TextView(activity);
        or.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        or.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        or.setGravity(Gravity.CENTER);
        or.setText("— 或者手动输入 —");
        root.addView(or, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 12, 22, 10));

        // ---------------- 方式二：手动输入 ----------------
        addSectionHeader(activity, root, "手动输入");

        // 私钥
        final EditText keyInput = buildInput(activity, "粘贴 512 位十六进制私钥");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyInput.setTypeface(Typeface.MONOSPACE);
        root.addView(keyInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(48), 22, 0, 22, 8));

        // dc_id
        final EditText dcInput = buildInput(activity, "数据中心 dc_id（1~5）");
        dcInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(dcInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(48), 22, 0, 22, 8));

        // user_id（仅第 8 项）
        EditText uidInput = null;
        if (needUserId) {
            uidInput = buildInput(activity, "账号 user_id（可留空）");
            uidInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            root.addView(uidInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    dp(48), 22, 0, 22, 8));
        }

        TextView note = new TextView(activity);
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        note.setText("私钥无法反查账号，所以 dc_id 必须一并填写；"
                + (needUserId ? "user_id 留空时按 0 处理，登录后由服务端回填。"
                              : "user_id 按 0 处理，登录后由服务端回填。"));
        note.setLineSpacing(dp(2), 1f);
        root.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 16));

        // ---------------- 按钮行 ----------------
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView cancel = new TextView(activity);
        cancel.setText("取消");
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        cancel.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
        cancel.setOnClickListener(v -> {
            if (callback != null) {
                callback.onCancel();
            }
        });
        actions.addView(cancel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT));

        TextView save = new TextView(activity);
        save.setText("生成");
        save.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        save.setPadding(dp(14), dp(10), dp(14), dp(10));
        final EditText uidRef = uidInput;
        save.setOnClickListener(v -> {
            String hex = normalizeHex(keyInput.getText() == null
                    ? "" : keyInput.getText().toString());
            if (hex.length() != 512) {
                keyInput.setError("需要 512 位十六进制，当前 " + hex.length() + " 位");
                keyInput.requestFocus();
                return;
            }
            String dcText = dcInput.getText() == null ? "" : dcInput.getText().toString().trim();
            if (TextUtils.isEmpty(dcText)) {
                dcInput.setError("请填 dc_id");
                dcInput.requestFocus();
                return;
            }
            int dcId;
            try {
                dcId = Integer.parseInt(dcText);
            } catch (NumberFormatException e) {
                dcInput.setError("dc_id 必须是数字");
                dcInput.requestFocus();
                return;
            }
            if (dcId < 1 || dcId > 5) {
                dcInput.setError("dc_id 应在 1~5 之间");
                dcInput.requestFocus();
                return;
            }
            long userId = 0L;
            if (uidRef != null) {
                String uidText = uidRef.getText() == null ? "" : uidRef.getText().toString().trim();
                if (!TextUtils.isEmpty(uidText)) {
                    try {
                        userId = Long.parseLong(uidText);
                    } catch (NumberFormatException e) {
                        uidRef.setError("user_id 必须是数字");
                        uidRef.requestFocus();
                        return;
                    }
                }
            }
            if (callback != null) {
                callback.onManualInput(hex, dcId, userId);
            }
        });
        actions.addView(save, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

        root.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 10, 10));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();
        sheet.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        sheet.show();
    }

    // ---------------------------------------------------------------- 构件

    private static void addSectionHeader(Activity activity, LinearLayout parent, String text) {
        TextView header = new TextView(activity);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        header.setText(text);
        parent.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 6));
    }

    private static EditText buildInput(Activity activity, String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_windowBackgroundGray)));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setSingleLine(true);
        return input;
    }

    /** 去掉所有空白并统一小写，避免用户粘贴时带上换行或空格。 */
    private static String normalizeHex(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t'
                    || c == ':' || c == '-' || c == ',') {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}