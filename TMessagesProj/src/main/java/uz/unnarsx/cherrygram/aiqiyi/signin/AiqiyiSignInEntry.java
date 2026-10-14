/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Auto Sign-in entry page (自动签到功能区).
 *
 * Layout mirrors the reference "Huanghun" implementation:
 *   [ Auto Sign-in Area ]            <- gray section header
 *   Enable Auto Sign-in   (switch)   <- master switch
 *   Auto Sign-in Tasks  >  N target(s) set   -> opens the task page
 */
package uz.unnarsx.cherrygram.aiqiyi.signin;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiSignInEntry extends BaseCGPreferencesEntry {

    private final int rowMasterSwitch = 1;
    private final int rowTasks = 2;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_AutoSignIn);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final int account = UserConfig.selectedAccount;

        items.add(UItem.asGraySection(getString(R.string.AQY_SignIn_Zone)));
        items.add(SettingsHelper.asSwitchCG(rowMasterSwitch,
                        getString(R.string.AQY_AutoSignInEnabled),
                        getString(R.string.AQY_AutoSignInEnabled_Desc))
                .setChecked(AiqiyiSignInHelper.isEnabled(account))
                .slug("aiqiyi_auto_sign_in"));
        items.add(SettingsHelper.asTextDetail(rowTasks, 0,
                        getString(R.string.AQY_SignIn_Function),
                        getTasksSummary(account))
                .slug("aiqiyi_auto_sign_in_tasks"));
        items.add(UItem.asShadow(null));
    }

    private CharSequence getTasksSummary(int account) {
        int count = AiqiyiSignInHelper.getTasks(account).size();
        if (count <= 0) {
            return getString(R.string.AQY_SignIn_TargetsNone);
        }
        return org.telegram.messenger.LocaleController.formatString(R.string.AQY_SignIn_TargetsSet, count);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int account = UserConfig.selectedAccount;
        if (item.id == rowMasterSwitch) {
            boolean next = !AiqiyiSignInHelper.isEnabled(account);
            AiqiyiSignInHelper.setEnabled(account, next);
            AiqiyiSignInScheduler.reschedule(account);
            if (next) {
                AiqiyiSignInHelper.executeDueTasks(account);
            }
            SettingsHelper.updateCheckState(view, next);
            showRestartBulletin();
        } else if (item.id == rowTasks) {
            presentFragment(new AiqiyiSignInTasksEntry());
        }
    }

    @Override
    protected String getKey() {
        return "aiqiyi_auto_sign_in";
    }

    @Override
    public void onResume() {
        super.onResume();
        // Task count may have changed while the task page was open.
        updateRows(false);
    }
}
