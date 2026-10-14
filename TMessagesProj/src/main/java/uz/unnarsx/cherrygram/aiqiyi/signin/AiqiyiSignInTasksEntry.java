/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Auto Sign-in task list (自动签到函数).
 *
 * Layout mirrors the reference "Huanghun" implementation:
 *   [ Auto Sign-in Tasks ]              <- gray section header
 *   [ + Add Target ]  [ Run All ]       <- action row
 *   note text                           <- shadow
 *   task card ...                       <- custom views, one per task
 *   task card ...
 */
package uz.unnarsx.cherrygram.aiqiyi.signin;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;

public class AiqiyiSignInTasksEntry extends BaseCGPreferencesEntry {

    private static final int ROW_ACTIONS = 1000;
    private static final int ROW_NOTE = 1001;
    private static final int ROW_EMPTY = 1002;
    /** Cards use ROW_CARD_BASE + index. */
    private static final int ROW_CARD_BASE = 2000;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_SignIn_Function);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final int account = UserConfig.selectedAccount;
        final ArrayList<AiqiyiSignInHelper.Task> tasks = AiqiyiSignInHelper.getTasks(account);

        items.add(UItem.asGraySection(getString(R.string.AQY_SignIn_Function)));
        items.add(SettingsHelper.asCustomWithBackground(ROW_ACTIONS, buildActionRow()));
        items.add(UItem.asShadow(getString(R.string.AQY_SignIn_Note)));

        if (tasks.isEmpty()) {
            items.add(SettingsHelper.asCustomWithBackground(ROW_EMPTY, buildEmptyHint()));
            items.add(UItem.asShadow(null));
            return;
        }

        for (int i = 0; i < tasks.size(); i++) {
            items.add(SettingsHelper.asCustomWithBackground(ROW_CARD_BASE + i, buildTaskCard(tasks.get(i), i)));
        }
        items.add(UItem.asShadow(null));
    }

    // ------------------------------------------------------------------
    //  Action row: [+ Add Target]  [Run All]
    // ------------------------------------------------------------------

    private View buildActionRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView add = makeBlueButton(getString(R.string.AQY_SignIn_AddTarget));
        add.setOnClickListener(v -> openEditor(-1));
        row.addView(add, LayoutHelper.createLinear(0, 40, 1f, 4, 0, 4, 0));

        TextView runAll = makeBlueButton(getString(R.string.AQY_SignIn_RunAll));
        runAll.setOnClickListener(v -> runAllNow());
        row.addView(runAll, LayoutHelper.createLinear(0, 40, 1f, 4, 0, 4, 0));

        return row;
    }

    private TextView makeBlueButton(CharSequence text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setBackground(Theme.createRoundRectDrawable(dp(10), 0xFF3D9BE9));
        tv.setPadding(dp(8), 0, dp(8), 0);
        return tv;
    }

    private View buildEmptyHint() {
        TextView tv = new TextView(getContext());
        tv.setText(getString(R.string.AQY_SignIn_NoTasks));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(20), dp(28), dp(20), dp(28));
        return tv;
    }

    // ------------------------------------------------------------------
    //  Task card
    // ------------------------------------------------------------------

    @SuppressLint("SetTextI18n")
    private View buildTaskCard(AiqiyiSignInHelper.Task task, int index) {
        final Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(10));

        // Line 1: target
        card.addView(makeKeyValueLine(
                getString(R.string.AQY_SignIn_TargetLabel),
                TextUtils.isEmpty(task.target) ? "-" : task.target));

        // Line 2: content
        card.addView(makeKeyValueLine(
                getString(R.string.AQY_SignIn_ContentLabel),
                TextUtils.isEmpty(task.content) ? "-" : task.content));

        // Line 3: time + status
        String timeText = (TextUtils.isEmpty(task.time) ? "00:01" : task.time) + "（北京时间）";
        String statusText = TextUtils.isEmpty(task.status) ? "未执行" : task.status;
        int statusColor = statusColorFor(statusText);
        card.addView(makeKeyValueLine(getString(R.string.AQY_SignIn_TimeLabel), timeText, statusText, statusColor));

        // Divider + action row: [Edit] [Delete] [Resend]
        View divider = new View(ctx);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams divLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 10, 0, 8);
        card.addView(divider, divLp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        actions.addView(makeTextAction(getString(R.string.AQY_SignIn_Edit), 0xFF3D9BE9,
                v -> openEditor(index)));
        actions.addView(makeTextAction(getString(R.string.AQY_SignIn_Delete), 0xFFE05050,
                v -> confirmDelete(index)));
        actions.addView(makeTextAction(getString(R.string.AQY_SignIn_Resend), 0xFF3D9BE9,
                v -> resendNow(index)));

        card.addView(actions, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END));

        return card;
    }

    private LinearLayout makeKeyValueLine(CharSequence key, CharSequence value) {
        return makeKeyValueLine(key, value, null, 0);
    }

    private LinearLayout makeKeyValueLine(CharSequence key, CharSequence value, CharSequence trailing, int trailingColor) {
        Context ctx = getContext();
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        TextView k = new TextView(ctx);
        k.setText(key);
        k.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        k.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        line.addView(k, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

        TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(v, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (trailing != null) {
            TextView t = new TextView(ctx);
            t.setText(trailing);
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            t.setTextColor(trailingColor);
            t.setSingleLine(true);
            line.addView(t, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        line.setPadding(0, 0, 0, dp(6));
        return line;
    }

    private TextView makeTextAction(CharSequence text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setTextColor(color);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        tv.setOnClickListener(listener);
        return tv;
    }

    private int statusColorFor(String status) {
        if (status.startsWith("成功")) {
            return 0xFF34A853;
        }
        if (status.startsWith("失败")) {
            return 0xFFE05050;
        }
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    private void openEditor(int index) {
        new AiqiyiSignInEditor(this, index, () -> updateRows(true)).show();
    }

    private void confirmDelete(int index) {
        ArrayList<AiqiyiSignInHelper.Task> tasks = AiqiyiSignInHelper.getTasks(UserConfig.selectedAccount);
        if (index < 0 || index >= tasks.size()) {
            return;
        }
        AiqiyiSignInHelper.Task task = tasks.get(index);
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AQY_SignIn_Delete));
        builder.setMessage(task.target + " / " + task.content);
        builder.setPositiveButton(getString(R.string.AQY_SignIn_Delete), (d, w) -> {
            tasks.remove(index);
            AiqiyiSignInHelper.saveTasks(UserConfig.selectedAccount, tasks);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AQY_SignIn_Deleted)).show();
            updateRows(true);
        });
        builder.setNegativeButton(getString(R.string.AQY_SignIn_Cancel), null);
        showDialog(builder.create());
    }

    private void resendNow(int index) {
        final int account = UserConfig.selectedAccount;
        ArrayList<AiqiyiSignInHelper.Task> tasks = AiqiyiSignInHelper.getTasks(account);
        if (index < 0 || index >= tasks.size()) {
            return;
        }
        final long taskId = tasks.get(index).id;
        AiqiyiSignInHelper.runTaskNow(account, taskId);
        updateRows(true);
    }

    private void runAllNow() {
        final int account = UserConfig.selectedAccount;
        AiqiyiSignInHelper.runAllTasksNow(account);
        updateRows(true);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        // Cards and the action row handle their own clicks.
    }

    @Override
    protected String getKey() {
        return "aiqiyi_auto_sign_in_tasks";
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows(false);
    }
}