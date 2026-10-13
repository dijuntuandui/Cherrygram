/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Auto Sign-in scheduler.
 *
 * Uses AlarmManager to wake the app at the earliest task time of the day.
 * Android 12+ may deny exact alarms; in that case we fall back to an inexact
 * alarm which still fires within a reasonable window.
 */
package uz.unnarsx.cherrygram.aiqiyi.signin;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;

public final class AiqiyiSignInScheduler {

    private static final String ACTION = "uz.unnarsx.cherrygram.aiqiyi.SIGN_IN";
    private static final int REQUEST_BASE = 0xbbf8;

    private AiqiyiSignInScheduler() {
    }

    private static PendingIntent pendingIntent(Context context, int account) {
        Intent intent = new Intent(context, AiqiyiSignInReceiver.class);
        intent.setAction(ACTION);
        intent.putExtra("account", account);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_BASE + account, intent, flags);
    }

    /** Reschedule for a single account. */
    public static void reschedule(int account) {
        schedule(account);
    }

    public static void schedule(int account) {
        try {
            Context context = ApplicationLoader.applicationContext;
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) {
                return;
            }

            PendingIntent pending = pendingIntent(context, account);
            alarm.cancel(pending);

            if (!AiqiyiSignInHelper.isEnabled(account)) {
                return;
            }
            ArrayList<AiqiyiSignInHelper.Task> tasks = AiqiyiSignInHelper.getTasks(account);
            if (tasks.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            long trigger = AiqiyiSignInHelper.nextTriggerMillis("00:01", now);
            for (AiqiyiSignInHelper.Task task : tasks) {
                long t = AiqiyiSignInHelper.nextTriggerMillis(task.time, now);
                if (t > 0) {
                    trigger = Math.min(trigger, t);
                }
            }
            if (trigger <= 0) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Reschedule alarms for every activated account. */
    public static void scheduleAll() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            UserConfig config = UserConfig.getInstance(account);
            if (config != null && config.isClientActivated()) {
                schedule(account);
            }
        }
    }
}