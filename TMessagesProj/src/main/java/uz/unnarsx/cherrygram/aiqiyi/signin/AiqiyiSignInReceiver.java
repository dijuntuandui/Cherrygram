/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Auto Sign-in alarm receiver.
 *
 * Fires when the AlarmManager wakes us at a task time, then asks the helper to
 * run whatever is due and re-arms the next alarm.
 */
package uz.unnarsx.cherrygram.aiqiyi.signin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.FileLog;

public class AiqiyiSignInReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null) {
                return;
            }
            int account = intent.getIntExtra("account", 0);
            AiqiyiSignInHelper.executeDueTasks(account);
            AiqiyiSignInScheduler.reschedule(account);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}