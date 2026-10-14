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
        } else {
            timeField.setText("00:01");
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
        task.dialogId = resolveDialogId(target);
        // A changed schedule deserves a clean slate.
        if (task.status == null || "未执行".equals(task.status) || task.status.startsWith("失败")) {
            task.status = "等待执行";
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
     * Best-effort resolution of a typed target into a dialog id.
     * Falls back to 0 (unresolved) which simply means the task cannot fire
     * until the peer is resolved by the sender at run time.
     */
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
            android.os.Bundle args = new android.os.Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("canSelectTopics", false);
            args.putBoolean("allowSwitchAccount", false);
            args.putInt("dialogsType", org.telegram.ui.DialogsActivity.DIALOGS_TYPE_FORWARD);
            args.putString("selectAlertString", getString(R.string.AQY_SignIn_TargetLabel));
            args.putString("selectAlertStringGroup", getString(R.string.AQY_SignIn_TargetLabel));

            org.telegram.ui.DialogsActivity picker = new org.telegram.ui.DialogsActivity(args);
            picker.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (dids == null || dids.isEmpty()) {
                    return true;
                }
                long dialogId = dids.get(0).dialogId;
                String handle = resolveHandle(dialogId);
                if (!TextUtils.isEmpty(handle) && targetField != null) {
                    targetField.setText(handle);
                    targetField.setSelection(handle.length());
                }
                fragment1.finishFragment();
                return true;
            });
            fragment.presentFragment(picker);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
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