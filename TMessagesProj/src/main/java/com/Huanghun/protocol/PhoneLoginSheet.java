/*
 * [Aiqiyi] 手机号 → 验证码 → 二级密码 → 产出 session
 *
 * 设计要点：
 * 1. 复用「当前已登录账号」的网络连接发码，验证码会落到 777000；
 * 2. 全程不接管客户端登录态（不调 onAuthSuccess），只取 auth_key 落文件；
 * 3. 二级密码有则填、没有就留空跳过。
 */
package com.Huanghun.protocol;

import android.app.Activity;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public final class PhoneLoginSheet {

    /** 官方服务号，验证码都从这里来。 */
    private static final long SERVICE_USER_ID = 777000L;

    private PhoneLoginSheet() {
    }

    public static void show(final Activity activity, final int accountNum) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        // auth.* 方法必须在未授权连接上执行，所以借一个空闲槽位跑完整登录流程。
        // 客户端本身是登录着的，777000 的验证码照常能收到。
        final int slot = findFreeAccountSlot();

        final BottomSheet.Builder builder = new BottomSheet.Builder(activity, false, null);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---------------- 标题 ----------------
        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText("手机号生成会话");
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 16, 22, 4));

        TextView sub = new TextView(activity);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setText("用当前已登录账号发码，验证码会发到 Telegram 官方号 777000。"
                + "填完验证码与二级密码后点生成，直接产出 session 文件。");
        sub.setLineSpacing(dp(2), 1f);
        root.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 14));

        // ---------------- 手机号 ----------------
        final EditText phoneInput = buildInput(activity, "手机号（含国家码，如 +8613800138000）");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(phoneInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(44), 20, 0, 20, 8));

        // ---------------- 验证码 ----------------
        final EditText codeInput = buildInput(activity, "验证码（去 777000 查）");
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(codeInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(44), 20, 0, 20, 8));

        // ---------------- 二级密码 ----------------
        final EditText pwdInput = buildInput(activity, "二级密码（没开就留空）");
        pwdInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(pwdInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(44), 20, 0, 20, 12));

        // ---------------- 状态 ----------------
        final TextView status = new TextView(activity);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        status.setVisibility(View.GONE);
        status.setLineSpacing(dp(2), 1f);
        root.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 8));

        // ---------------- 按钮 ----------------
        final TextView sendBtn = new TextView(activity);
        sendBtn.setText("发送验证码");
        sendBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        sendBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        root.addView(sendBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(42), 20, 0, 20, 8));

        final TextView buildBtn = new TextView(activity);
        buildBtn.setText("生成协议文件");
        buildBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        buildBtn.setGravity(Gravity.CENTER);
        buildBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buildBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        root.addView(buildBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                dp(42), 20, 0, 20, 14));

        ScrollView scroll = new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(8));
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        // 面板内容自身上限，超过就在内部滚，避免内容把面板顶出屏幕滑不回来
        final int maxSheetH = (int) (AndroidUtilities.displaySize.y * 0.62f);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        builder.setCustomView(scroll);
        final BottomSheet sheet = builder.create();
        // 输入框要能聚焦拿键盘，所以面板必须可聚焦；
        // 但不能让系统随键盘改窗口尺寸（ADJUST_RESIZE），否则高度被压扁后拉不回来。
        sheet.setFocusable(true);
        if (sheet.getWindow() != null) {
            sheet.getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            sheet.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                            | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        }
        sheet.setAllowNestedScroll(true);
        sheet.show();
        // 显示后按内容量限制高度
        scroll.post(() -> {
            int contentH = root.getMeasuredHeight();
            if (contentH > maxSheetH) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                if (lp != null) {
                    lp.height = maxSheetH;
                    scroll.setLayoutParams(lp);
                }
            }
        });

        final PhoneState state = new PhoneState();

        sendBtn.setOnClickListener(v -> {
            String phone = normalizePhone(phoneInput.getText().toString());
            if (TextUtils.isEmpty(phone)) {
                setStatus(status, "先填手机号", true);
                return;
            }
            setStatus(status, "正在发送验证码…", false);
            sendBtn.setEnabled(false);
            sendCode(activity, slot, phone, state, new SimpleCallback() {
                @Override
                public void onDone() {
                    AndroidUtilities.runOnUIThread(() -> {
                        sendBtn.setEnabled(true);
                        setStatus(status, "验证码已发出，去 777000 查看后填进第二栏", false);
                    });
                }

                @Override
                public void onError(String message) {
                    AndroidUtilities.runOnUIThread(() -> {
                        sendBtn.setEnabled(true);
                        setStatus(status, "发码失败：" + message, true);
                    });
                }
            });
        });

        buildBtn.setOnClickListener(v -> {
            String phone = normalizePhone(phoneInput.getText().toString());
            String code = codeInput.getText().toString().trim();
            String pwd = pwdInput.getText().toString();
            if (TextUtils.isEmpty(phone)) {
                setStatus(status, "先填手机号", true);
                return;
            }
            if (TextUtils.isEmpty(code)) {
                setStatus(status, "先填验证码", true);
                return;
            }
            if (TextUtils.isEmpty(state.phoneCodeHash)) {
                setStatus(status, "先点「发送验证码」，拿到 code hash 才能提交", true);
                return;
            }
            // phone_code_hash 与发码时的号码是成对的，必须原样用发码那个号，
            // 否则服务端直接判 PHONE_CODE_INVALID
            if (!TextUtils.isEmpty(state.phone) && !state.phone.equals(phone)) {
                setStatus(status, "手机号跟发码时不一致（发码用的 " + state.phone
                        + "），请重新点「发送验证码」", true);
                return;
            }
            final String signInPhone = TextUtils.isEmpty(state.phone) ? phone : state.phone;
            setStatus(status, "正在校验验证码…", false);
            buildBtn.setEnabled(false);
            signIn(activity, slot, signInPhone, code, pwd, state, new SimpleCallback() {
                @Override
                public void onDone() {
                    AndroidUtilities.runOnUIThread(() -> {
                        buildBtn.setEnabled(true);
                        setStatus(status, "登录成功，正在取 auth_key 并写文件…", false);
                    });
                    exportAndWrite(activity, slot, state, status, sheet);
                }

                @Override
                public void onError(String message) {
                    AndroidUtilities.runOnUIThread(() -> {
                        buildBtn.setEnabled(true);
                        setStatus(status, "失败：" + message, true);
                    });
                }
            });
        });
    }

    // ---------------------------------------------------------------- 网络

    interface SimpleCallback {
        void onDone();

        void onError(String message);
    }

    /** 发码：占用当前已登录连接，验证码投递到 777000。 */
    private static void sendCode(Activity activity, int accountNum, String phone,
            PhoneState state, SimpleCallback callback) {
        TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
        req.api_hash = org.telegram.messenger.BuildVars.APP_HASH;
        req.api_id = org.telegram.messenger.BuildVars.APP_ID;
        req.phone_number = phone;
        req.settings = new TLRPC.TL_codeSettings();
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
            if (error != null) {
                callback.onError(error.text == null ? "unknown" : error.text);
                return;
            }
            if (response instanceof TLRPC.TL_auth_sentCode) {
                TLRPC.TL_auth_sentCode sent = (TLRPC.TL_auth_sentCode) response;
                state.phoneCodeHash = sent.phone_code_hash;
                state.phone = phone;
                callback.onDone();
                return;
            }
            if (response instanceof TLRPC.TL_auth_sentCodeSuccess) {
                TLRPC.auth_Authorization auth =
                        ((TLRPC.TL_auth_sentCodeSuccess) response).authorization;
                if (auth instanceof TLRPC.TL_auth_authorization) {
                    state.authorization = (TLRPC.TL_auth_authorization) auth;
                    callback.onDone();
                    return;
                }
                callback.onError("该号码无需验证码即可登录");
                return;
            }
            callback.onError("未知响应");
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    /** 校验验证码，必要时再走二级密码。 */
    private static void signIn(Activity activity, int accountNum, String phone, String code,
            String password, PhoneState state, SimpleCallback callback) {
        TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
        req.flags |= 1;   // FLAG_0 控制 phone_code 是否写入，漏了就发空验证码
        req.phone_number = phone;
        req.phone_code = code;
        req.phone_code_hash = state.phoneCodeHash;
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
            if (error == null) {
                if (response instanceof TLRPC.TL_auth_authorization) {
                    state.authorization = (TLRPC.TL_auth_authorization) response;
                    callback.onDone();
                    return;
                }
                if (response instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                    callback.onError("该号码未注册，无法生成");
                    return;
                }
                callback.onError("未知响应");
                return;
            }
            if (error.text != null && error.text.contains("SESSION_PASSWORD_NEEDED")) {
                checkPassword(accountNum, phone, password, state, callback);
                return;
            }
            if (error.text != null && error.text.contains("PHONE_CODE_INVALID")) {
                callback.onError("验证码不对");
                return;
            }
            if (error.text != null && error.text.contains("PHONE_CODE_EXPIRED")) {
                callback.onError("验证码过期，重新发送");
                return;
            }
            callback.onError(error.text == null ? "unknown" : error.text);
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    /** 走二级密码：标准 SRP 流程，和原生密码页同一套算法。 */
    private static void checkPassword(int accountNum, String phone, String password,
            PhoneState state, SimpleCallback callback) {
        org.telegram.tgnet.tl.TL_account.getPassword getPassword = new org.telegram.tgnet.tl.TL_account.getPassword();
        ConnectionsManager.getInstance(accountNum).sendRequest(getPassword, (res1, err1) -> {
            if (err1 != null || !(res1 instanceof org.telegram.tgnet.tl.TL_account.Password)) {
                callback.onError("读取二级密码配置失败");
                return;
            }
            final org.telegram.tgnet.tl.TL_account.Password pwdInfo = (org.telegram.tgnet.tl.TL_account.Password) res1;
            if (TextUtils.isEmpty(password)) {
                callback.onError("该账号开了二级密码，必须填写");
                return;
            }
            if (!(pwdInfo.current_algo
                    instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow)) {
                callback.onError("不支持的密码算法，请更新客户端");
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo =
                        (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow)
                                pwdInfo.current_algo;
                byte[] passwordBytes = AndroidUtilities.getStringBytes(password);
                byte[] xBytes = org.telegram.messenger.SRPHelper.getX(passwordBytes, algo);
                TLRPC.TL_inputCheckPasswordSRP check = org.telegram.messenger.SRPHelper.startCheck(
                        xBytes, pwdInfo.srp_id, pwdInfo.srp_B, algo);
                if (check == null) {
                    callback.onError("二级密码校验参数异常");
                    return;
                }
                TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();
                req.password = check;
                ConnectionsManager.getInstance(accountNum).sendRequest(req, (res2, err2) -> {
                    if (err2 != null) {
                        if (err2.text != null && err2.text.contains("SRP_ID_INVALID")) {
                            callback.onError("二级密码会话过期，重新点生成");
                        } else if (err2.text != null
                                && err2.text.contains("PASSWORD_HASH_INVALID")) {
                            callback.onError("二级密码错误");
                        } else {
                            callback.onError(err2.text == null ? "unknown" : err2.text);
                        }
                        return;
                    }
                    if (res2 instanceof TLRPC.TL_auth_authorization) {
                        state.authorization = (TLRPC.TL_auth_authorization) res2;
                        callback.onDone();
                        return;
                    }
                    callback.onError("未知响应");
                }, ConnectionsManager.RequestFlagFailOnServerErrors
                        | ConnectionsManager.RequestFlagWithoutLogin);
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors
                | ConnectionsManager.RequestFlagWithoutLogin);
    }

    // ---------------------------------------------------------------- 导出

    /** 取 auth_key 并写成文件。 */
    private static void exportAndWrite(Activity activity, int accountNum, PhoneState state,
            TextView status, BottomSheet sheet) {
        new Thread(() -> {
            byte[] key = null;
            for (int i = 0; i < 40; i++) {
                try {
                    key = ConnectionsManager.getInstance(accountNum).getCurrentAuthKey();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                if (key != null && key.length == 256) {
                    break;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            final byte[] finalKey = key;
            // 登录刚完成时 getCurrentDatacenterId() 往往还是默认值 1，
            // updateDcSettings() 是异步的。这里直接问服务端要真实 DC。
            int probedDc = 0;
            try {
                final Object lock = new Object();
                final int[] holder = new int[]{0};
                final boolean[] done = new boolean[]{false};
                TLObject cfgReq = new TLRPC.TL_help_getConfig();
                ConnectionsManager.getInstance(accountNum).sendRequest(cfgReq, (res, err) -> {
                    synchronized (lock) {
                        if (err == null && res instanceof TLRPC.TL_config) {
                            holder[0] = ((TLRPC.TL_config) res).this_dc;
                        }
                        done[0] = true;
                        lock.notifyAll();
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors
                        | ConnectionsManager.RequestFlagWithoutLogin);
                synchronized (lock) {
                    long deadline = System.currentTimeMillis() + 6000;
                    while (!done[0]) {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) {
                            break;
                        }
                        lock.wait(left);
                    }
                }
                probedDc = holder[0];
            } catch (Throwable t) {
                FileLog.e(t);
            }
            int dcFromManager = ConnectionsManager.getInstance(accountNum).getCurrentDatacenterId();
            final int dcId = probedDc >= 1 && probedDc <= 5 ? probedDc : dcFromManager;
            FileLog.d("PhoneLoginSheet: dc probe=" + probedDc
                    + " manager=" + dcFromManager + " used=" + dcId);
            final long userId = state.authorization != null && state.authorization.user != null
                    ? state.authorization.user.id : 0L;

            AndroidUtilities.runOnUIThread(() -> {
                if (finalKey == null || finalKey.length != 256) {
                    setStatus(status, "拿不到 auth_key，可能被服务端切走了，重试一次", true);
                    return;
                }
                try {
                    setStatus(status, "auth_key 已取到，正在生成文件…", false);
                } catch (Throwable ignore) {
                }
            });

            if (finalKey == null || finalKey.length != 256) {
                return;
            }

            String error = null;
            ProtocolConverter.Result result = null;
            java.io.File runDir = null;
            try {
                SessionWriter.Account account = new SessionWriter.Account(finalKey, dcId, userId);
                account.source = "phone_login";
                // [Aiqiyi] 把服务端签发的会话续期凭证一并写进产物，导入时恢复，防止会话被判不完整而掉线
                byte[] futToken = null;
                try {
                    if (state.authorization != null && state.authorization.future_auth_token != null
                            && state.authorization.future_auth_token.length > 0) {
                        futToken = state.authorization.future_auth_token;
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                account.futureAuthToken = futToken;
                FileLog.d("[Aiqiyi/Token] export uid=" + userId + " dc=" + dcId
                        + " keyLen=" + (finalKey == null ? -1 : finalKey.length)
                        + " futToken=" + (futToken == null ? "null" : ("len:" + futToken.length)));
                runDir = ConvertReport.createRunDir();
                result = ProtocolConverter.convert(account, runDir, true);
            } catch (Throwable e) {
                FileLog.e(e);
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String failure = error;
            final ProtocolConverter.Result done = result;
            final java.io.File dir = runDir;
            // 文件已经落盘，临时槽位就没用了，清掉避免账号列表多出空壳
            releaseSlot(accountNum);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    sheet.dismiss();
                } catch (Throwable ignore) {
                }
                if (failure != null) {
                    ProtocolConverterSheet.showResult(activity,
                            ConvertReport.failure(failure));
                    return;
                }
                ProtocolConverterSheet.showResult(activity, toReport(done, dir));
            });
        }, "phone-login-export").start();
    }

    private static ConvertReport toReport(ProtocolConverter.Result result, java.io.File runDir) {
        ConvertReport report = new ConvertReport();
        report.runDir = runDir;
        if (result == null) {
            report.addFail("没有产出任何结果");
            return report;
        }
        report.title = "手机号登录生成完成";
        report.addOk(result.account == null ? "账号 ?" : result.account.label());
        if (result.telethon != null) {
            report.outputs.add(result.telethon);
            report.addNote("telethon: " + result.telethon.getName());
        }
        if (result.pyrogram != null) {
            report.outputs.add(result.pyrogram);
            report.addNote("pyrogram: " + result.pyrogram.getName());
        }
        if (result.string != null) {
            report.outputs.add(result.string);
            report.addNote("string: " + result.string.getName());
        }
        if (result.json != null) {
            report.outputs.add(result.json);
            report.addNote("json: " + result.json.getName());
        }
        if (result.tdataDir != null) {
            report.outputs.add(result.tdataDir);
            report.addNote("tdata: " + result.tdataDir.getName());
        }
        return report;
    }

    // ---------------------------------------------------------------- 构件

    static class PhoneState {
        String phone;
        String phoneCodeHash;
        TLRPC.TL_auth_authorization authorization;
    }

    /**
     * 找一个没登录的槽位。auth.* 必须在未授权连接上执行，
     * 已登录的槽位发码会被服务端拒绝（AUTH_RESTART）。
     */
    private static int findFreeAccountSlot() {
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        // 全都占满了就退回当前账号，至少能给出明确报错
        return 0;
    }

    /** 用完的临时槽位清掉，别在账号列表里留个空壳。 */
    private static void releaseSlot(int slot) {
        try {
            org.telegram.messenger.UserConfig.getInstance(slot).clearConfig();
            org.telegram.messenger.UserConfig.getInstance(slot).saveConfig(true);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void setStatus(TextView view, String text, boolean isError) {
        if (view == null) {
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(text);
        view.setTextColor(Theme.getColor(isError
                ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText3));
    }

    /** 去掉空格与横线，补上 + 号。 */
    private static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '-' || c == '(' || c == ')' || c == '\t') {
                continue;
            }
            sb.append(c);
        }
        String v = sb.toString();
        if (v.isEmpty()) {
            return "";
        }
        if (!v.startsWith("+")) {
            v = "+" + v;
        }
        return v;
    }

    private static EditText buildInput(Activity activity, String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        // 浅描边圆角，别做成大黑块
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bg.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)),
                Theme.getColor(Theme.key_divider));
        input.setBackground(bg);
        input.setPadding(dp(14), dp(6), dp(14), dp(6));
        input.setMinHeight(0);
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setClickable(true);
        input.setCursorVisible(true);
        return input;
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}
