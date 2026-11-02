/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Auto Sign-in task editor dialog (目标设定).
 *
 * Dialog layout mirrors the reference "Huanghun" implementation:
 *   Target Setup
 *     Target   [ Username, ID or t.me ] [ Choose ]
 *     Content  [ multiline                   ]
 *     Time     [ 00:01                       ]
 *                          Cancel    Save
 */
package uz.unnarsx.cherrygram.aiqiyi.signin;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class AiqiyiSignInEditor {

    private final BaseFragment fragment;
    private final int editIndex;
    private final Runnable onSaved;
    private final Context context;

    private EditText targetField;
    private EditText contentField;
    private EditText timeField;

    /**
     * @param fragment  host fragment (used for context, bulletins, dialog)
     * @param editIndex index of the task to edit, or -1 to create a new one
     * @param onSaved   invoked after a successful save/delete so the list can refresh
     */
    public AiqiyiSignInEditor(BaseFragment fragment, int editIndex, Runnable onSaved) {
        this.fragment = fragment;
        this.editIndex = editIndex;
        this.onSaved = onSaved;
        this.context = fragment.getContext() != null
                ? fragment.getContext()
                : fragment.getParentActivity();
    }

    public void show() {
        final int account = UserConfig.selectedAccount;
        final ArrayList<AiqiyiSignInHelper.Task> tasks = AiqiyiSignInHelper.getTasks(account);

        AiqiyiSignInHelper.Task editing = null;
        if (editIndex >= 0 && editIndex < tasks.size()) {
            editing = tasks.get(editIndex);
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(8), dp(22), dp(4));

        // --- Target label + row (field + Choose button) ---
        root.addView(makeLabel(getString(R.string.AQY_SignIn_TargetLabel)));
        LinearLayout targetRow = new LinearLayout(context);
        targetRow.setOrientation(LinearLayout.HORIZONTAL);

        targetField = makeInput(getString(R.string.AQY_SignIn_TargetHint), false);
        targetField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        targetRow.addView(targetField, LayoutHelper.createLinear(0, 48, 1f));

        TextView choose = new TextView(context);
        choose.setText(getString(R.string.AQY_SignIn_Choose));
        choose.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        choose.setGravity(Gravity.CENTER);
        choose.setTextColor(0xFFFFFFFF);
        choose.setTypeface(AndroidUtilities.bold());
        choose.setBackground(Theme.createRoundRectDrawable(dp(10), 0xFF3D9BE9));
        choose.setOnClickListener(v -> pickTarget());
        targetRow.addView(choose, LayoutHelper.createLinear(84, 48, 0, 10, 0, 0, 0));

        root.addView(targetRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // --- Content label + multiline field ---
        root.addView(makeLabel(getString(R.string.AQY_SignIn_ContentLabel)));
        contentField = makeInput(getString(R.string.AQY_SignIn_ContentHint), true);
        contentField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        contentField.setGravity(Gravity.TOP | Gravity.START);
        contentField.setMinLines(3);
        contentField.setMaxLines(6);
        contentField.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        root.addView(contentField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(96)));

        // --- Time label + field ---
        root.addView(makeLabel(getString(R.string.AQY_SignIn_TimeLabel)));
        timeField = makeInput("00:01", false);
        timeField.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        timeField.setSingleLine(true);
        root.addView(timeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        if (editing != null) {
            targetField.setText(editing.target);
            contentField.setText(editing.content);
            timeField.setText(TextUtils.isEmpty(editing.time) ? "00:01" : editing.time);
            // [Aiqiyi FIX-PICK-2] 编辑已有任务时沿用其 dialogId，
            // 用户若重新点选择器则会覆盖成新的。
            pickedDialogId = editing.dialogId;
        } else {
            timeField.setText("00:01");
            pickedDialogId = 0;
        }
        // [Aiqiyi FIX-PICK-3] 从选择器返回后重开弹窗，把暂存的输入恢复回来。
        if (pendingTarget != null) {
            targetField.setText(pendingTarget);
            targetField.setSelection(pendingTarget.length());
        }
        if (pendingContent != null) {
            contentField.setText(pendingContent);
        }
        if (pendingTime != null) {
            timeField.setText(pendingTime);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(getString(R.string.AQY_SignIn_TargetTitle));
        builder.setView(root);
        builder.setNegativeButton(getString(R.string.AQY_SignIn_Cancel), null);

        AlertDialog dialog = builder.create();
        // Validate inside the click listener so the dialog only closes on success.
        dialog.setPositiveButton(getString(R.string.AQY_SignIn_Save), (d, which) -> {
            if (save(account, tasks, editIndex)) {
                d.dismiss();
            }
        });
        fragment.showDialog(dialog);
    }

    private boolean save(int account, ArrayList<AiqiyiSignInHelper.Task> tasks, int index) {
        String target = targetField.getText().toString().trim();
        String content = contentField.getText().toString();
        String rawTime = timeField.getText().toString().trim();

        if (TextUtils.isEmpty(target)) {
            bulletin(getString(R.string.AQY_SignIn_BadTarget));
            return false;
        }
        if (TextUtils.isEmpty(content)) {
            bulletin(getString(R.string.AQY_SignIn_BadContent));
            return false;
        }
        String time = AiqiyiSignInHelper.normalizeTime(rawTime);
        if (time == null) {
            bulletin(getString(R.string.AQY_SignIn_BadTime));
            return false;
        }

        AiqiyiSignInHelper.Task task;
        if (index >= 0 && index < tasks.size()) {
            task = tasks.get(index);
        } else {
            task = new AiqiyiSignInHelper.Task();
            task.id = System.currentTimeMillis();
            tasks.add(task);
        }

        task.target = target;
        task.content = content;
        task.time = time;
        // [Aiqiyi FIX-PICK-2/3] 选择器给出的 id 最可信，但只在「目标文本与选择结果一致」
        // 时才采用；用户把 @abc 手动改成 @xyz 之后必须重新解析，否则会发给上一个。
        long resolved = resolveDialogId(target);
        if (pickedDialogId != 0) {
            String pickedHandle = resolveHandle(pickedDialogId);
            if (!TextUtils.isEmpty(pickedHandle) && pickedHandle.equals(target)) {
                task.dialogId = pickedDialogId;
            } else {
                task.dialogId = resolved;
            }
        } else {
            task.dialogId = resolved;
        }
        // A changed schedule deserves a clean slate.
        if (task.status == null || "未执行".equals(task.status) || task.status.startsWith("失败")) {
            task.status = "等待执行";
        }
        // [Aiqiyi FIX-RESOLVE] 本地缓存没解析到，不直接判失败 ——
        // 交给服务器 contacts.resolveUsername 去查，查到了再回填。
        // 注意必须放在状态重置之后，否则「正在解析目标…」会被上面那段覆盖掉。
        if (task.dialogId == 0) {
            scheduleRemoteResolve(account, task, target);
        }
        task.lastRunDay = 0;
        task.retryDay = 0;
        task.pendingMessageId = 0;

        AiqiyiSignInHelper.saveTasks(account, tasks);
        bulletin(getString(R.string.AQY_SignIn_Saved));
        if (onSaved != null) {
            onSaved.run();
        }
        return true;
    }

    /**
     * [Aiqiyi FIX-RESOLVE] 异步解析用户名。
     * <p>
     * 原来的 resolveDialogId 只查本地缓存，从来没私聊过的账号一律返回 0，
     * 于是手动输入 @SpamBot 也会报「目标无法解析」。
     * 这里改为向服务器发起 contacts.resolveUsername，成功后回填 dialogId。
     */
    private void scheduleRemoteResolve(int account, AiqiyiSignInHelper.Task task, String target) {
        try {
            String userName = target.trim();
            if (userName.startsWith("https://t.me/") || userName.startsWith("t.me/")) {
                userName = userName.substring(userName.lastIndexOf('/') + 1);
            }
            while (userName.startsWith("@")) {
                userName = userName.substring(1);
            }
            if (TextUtils.isEmpty(userName) || userName.matches("\\d+")) {
                return;
            }
            final String finalName = userName;
            task.status = "正在解析目标…";
            org.telegram.messenger.FileLog.d("[Aiqiyi] resolve remote: " + finalName);
            fragment.getMessagesController().getUserNameResolver().resolve(finalName, peerId -> {
                try {
                    if (peerId == null || peerId == 0 || peerId == Long.MAX_VALUE) {
                        task.status = "失败：目标无法解析，请检查用户名";
                        org.telegram.messenger.FileLog.e("[Aiqiyi] resolve failed: " + finalName);
                    } else {
                        task.dialogId = peerId;
                        task.status = "等待执行";
                        org.telegram.messenger.FileLog.d("[Aiqiyi] resolved " + finalName + " -> " + peerId);
                    }
                    AiqiyiSignInHelper.saveTasks(account, AiqiyiSignInHelper.getTasks(account));
                    AiqiyiSignInScheduler.reschedule(account);
                    if (onSaved != null) {
                        onSaved.run();
                    }
                } catch (Throwable e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            });
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    /**
     * Best-effort resolution of a typed target into a dialog id.
     * Falls back to 0 (unresolved) which simply means the task cannot fire
     * until the peer is resolved by the sender at run time.
     */
    /**
     * [Aiqiyi FIX-PICK-2] 选择器直接拿到的 dialogId。
     * 存下来可以让 save() 跳过「字符串反解析」这一步，
     * 避免对没有本地缓存的用户名解析失败返回 0 的情况。
     */
    private long pickedDialogId = 0;

    /**
     * [Aiqiyi FIX-PICK-3] 弹窗被 presentFragment 挤到后台后会被销毁，
     * 直接往 targetField 写值等于写进一个死掉的 View。
     * 改成「暂存三件套 + 重开弹窗」：回调只负责把结果存进这里，
     * 由 show() 下次创建界面时读回，不依赖 View 是否还活着。
     */
    private String pendingTarget = null;
    private String pendingContent = null;
    private String pendingTime = null;

    private long resolveDialogId(String target) {
        try {
            String t = target.trim();
            if (t.startsWith("https://t.me/") || t.startsWith("t.me/")) {
                t = t.substring(t.lastIndexOf('/') + 1);
            }
            if (t.startsWith("@")) {
                t = t.substring(1);
            }
            if (TextUtils.isEmpty(t)) {
                return 0;
            }
            // All-digits -> user id
            if (t.matches("\\d+")) {
                return Long.parseLong(t);
            }
            // Username resolution happens through the controller's cache.
            var resolved = fragment.getMessagesController().getUserOrChat("@" + t);
            if (resolved instanceof org.telegram.tgnet.TLRPC.User) {
                return ((org.telegram.tgnet.TLRPC.User) resolved).id;
            }
            if (resolved instanceof org.telegram.tgnet.TLRPC.Chat) {
                return -((org.telegram.tgnet.TLRPC.Chat) resolved).id;
            }
            // [Aiqiyi FIX-RESOLVE] getUserOrChat 走的是本地缓存，
            // 从没私聊过的账号（比如第一次用的 @SpamBot）必然 miss。
            // 这里再扫一遍用户/群列表，命中就返回，避免无谓的网络请求。
            org.telegram.messenger.MessagesController mc = fragment.getMessagesController();
            for (org.telegram.tgnet.TLRPC.User u : mc.getUsers()) {
                if (u != null && t.equalsIgnoreCase(u.username)) {
                    return u.id;
                }
            }
            for (org.telegram.tgnet.TLRPC.Chat c : mc.getChats()) {
                if (c != null && t.equalsIgnoreCase(c.username)) {
                    return -c.id;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * Opens the built-in single-peer picker and prefills the target field.
     * DialogsActivity is a BaseFragment with a delegate callback, which is how
     * Cherrygram itself implements "pick one chat" flows.
     */
    private void pickTarget() {
        try {
            // [Aiqiyi FIX-PICK] 关键节点日志：原实现整段被 catch(Throwable) 静默吞掉，
            // 用户只看到“点了没反应”，无法定位是构造失败、delegate 未回调，还是 dids 为空。
            org.telegram.messenger.FileLog.d("[Aiqiyi] pickTarget: enter");
            // [Aiqiyi FIX-PICK-3] 先把用户已填的内容暂存，选完回来重开弹窗时恢复。
            if (targetField != null) {
                pendingTarget = targetField.getText().toString();
            }
            if (contentField != null) {
                pendingContent = contentField.getText().toString();
            }
            if (timeField != null) {
                pendingTime = timeField.getText().toString();
            }
            android.os.Bundle args = new android.os.Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("canSelectTopics", false);
            args.putBoolean("allowSwitchAccount", false);
            args.putInt("dialogsType", org.telegram.ui.DialogsActivity.DIALOGS_TYPE_FORWARD);
            // [Aiqiyi FIX-PICK-2] 原先这里传了 selectAlertString / selectAlertStringGroup，
            // 导致 DialogsActivity 走进两条致命分支：
            //   1) onItemClick 里 `initialDialogsType == FORWARD && selectAlertString != null`
            //      -> 仅执行 addOrRemoveSelectedDialog() 打勾，不回调 delegate；
            //   2) 即便到达 didSelectResult，也会命中
            //      `useAlert && selectAlertString != null && selectAlertStringGroup != null`
            //      -> 弹出「发送到 xxx？」确认框，与签到无关。
            // 而 doneItem（右上角完成）只在 `!onlySelect` 时才创建，
            // 于是勾上了却没有任何按钮可以确认 —— 用户看到的就是「点了没反应」。
            // 置空后点击直接落到 didSelectResult 的纯 delegate 分支，一次即回。
            args.putString("selectAlertString", null);
            args.putString("selectAlertStringGroup", null);

            org.telegram.ui.DialogsActivity picker = new org.telegram.ui.DialogsActivity(args);
            picker.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                org.telegram.messenger.FileLog.d("[Aiqiyi] pickTarget: delegate called, size=" + (dids == null ? -1 : dids.size()));
                if (dids == null || dids.isEmpty()) {
                    // [Aiqiyi FIX-PICK-2] 空结果说明选择流程没把 TopicKey 带回来，
                    // 保底也要退出选择页，否则会卡在 picker 里。
                    fragment1.finishFragment();
                    return true;
                }
                long dialogId = dids.get(0).dialogId;
                pickedDialogId = dialogId;
                String handle = resolveHandle(dialogId);
                org.telegram.messenger.FileLog.d("[Aiqiyi] pickTarget: dialogId=" + dialogId + " handle=" + handle);
                if (!TextUtils.isEmpty(handle)) {
                    // [Aiqiyi FIX-PICK-3] 不写 targetField —— 那个弹窗此刻已经不在前台了。
                    // 存进 pending，重新 show() 时会读回来。
                    pendingTarget = handle;
                }
                if (fragment1 != null) {
                    fragment1.finishFragment();
                }
                // 重开编辑弹窗，把选择结果与原有输入一起带回来。
                fragment.getParentActivity().runOnUiThread(() -> {
                    try {
                        show();
                    } catch (Throwable e) {
                        org.telegram.messenger.FileLog.e(e);
                    }
                });
                return true;
            });
            // [Aiqiyi FIX-PICK-4] 不用 fragment.presentFragment：Editor 只是个 AlertDialog，
            // 它的宿主 AiqiyiSignInTasksEntry 一旦被压到栈底，弹窗与 picker 的层级关系就乱了，
            // 实机上表现为「点了没反应」。改走 Activity 层压栈，与弹窗解耦。
            org.telegram.ui.LaunchActivity la =
                    (org.telegram.ui.LaunchActivity) fragment.getParentActivity();
            boolean pushed;
            if (la != null) {
                // LaunchActivity 只有 (fragment, removeLast, forceWithoutAnimation) 三参版本。
                pushed = la.presentFragment(picker, false, false);
            } else {
                pushed = fragment.presentFragment(picker);
            }
            org.telegram.messenger.FileLog.d("[Aiqiyi] pickTarget: pushed=" + pushed);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            // [Aiqiyi FIX-PICK] 原先只 FileLog.e，正式包里看不到；补一条明确标记便于现场取证。
            org.telegram.messenger.FileLog.e("[Aiqiyi] pickTarget FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Turns a dialog id into something the task can store (@username or numeric id). */
    private String resolveHandle(long dialogId) {
        try {
            if (dialogId > 0) {
                org.telegram.tgnet.TLRPC.User user = fragment.getMessagesController().getUser(dialogId);
                if (user != null) {
                    if (!TextUtils.isEmpty(user.username)) {
                        return "@" + user.username;
                    }
                    return String.valueOf(user.id);
                }
            } else if (dialogId < 0) {
                org.telegram.tgnet.TLRPC.Chat chat = fragment.getMessagesController().getChat(-dialogId);
                if (chat != null) {
                    if (!TextUtils.isEmpty(chat.username)) {
                        return "@" + chat.username;
                    }
                    return String.valueOf(chat.id);
                }
            }
        } catch (Throwable ignored) {
        }
        return String.valueOf(dialogId);
    }


    private TextView makeLabel(CharSequence text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTextColor(0xFF3D9BE9);
        tv.setPadding(0, dp(14), 0, dp(6));
        return tv;
    }

    private EditText makeInput(CharSequence hint, boolean multiline) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        et.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        et.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        et.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundWhite)));
        et.setPadding(dp(14), multiline ? dp(12) : 0, dp(14), multiline ? dp(12) : 0);
        et.setSingleLine(!multiline);
        return et;
    }

    private void bulletin(CharSequence text) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, text).show();
    }
}