/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Auto Sign-in (自动签到).
 *
 * Ported from the reference "Huanghun" implementation, keeping the exact same
 * storage semantics so existing user data keeps working:
 *
 *   SharedPreferences "huanghun_sign_in"
 *     enabled_<account>  -> boolean, master switch per account
 *     tasks_<account>    -> JSON array of serialized Task objects
 *
 * All scheduling times are evaluated in Beijing time (Asia/Shanghai),
 * regardless of the device timezone.
 */
package uz.unnarsx.cherrygram.aiqiyi.signin;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public final class AiqiyiSignInHelper {

    private static final String PREFS = "huanghun_sign_in";
    private static final String KEY_ENABLED = "enabled_";
    private static final String KEY_TASKS = "tasks_";

    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm", Locale.US);

    static {
        TIME.setTimeZone(BEIJING);
    }

    private AiqiyiSignInHelper() {
    }

    /** A single auto sign-in task. Field names mirror the reference JSON layout. */
    public static final class Task {
        public long id;
        public String target = "";
        public String content = "";
        public String time = "00:01";
        public long dialogId;
        public String status = "未执行";
        public long lastRunDay;
        public long retryDay;
        public int pendingMessageId;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    /** Whether the auto sign-in feature is enabled for the given account. */
    public static boolean isEnabled(int account) {
        try {
            return prefs().getBoolean(KEY_ENABLED + account, false);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Toggle the master switch. Rescheduling is handled by the caller. */
    public static void setEnabled(int account, boolean enabled) {
        try {
            prefs().edit().putBoolean(KEY_ENABLED + account, enabled).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Load all tasks for an account. Never returns null. */
    public static ArrayList<Task> getTasks(int account) {
        return getTasksWithoutObserver(account);
    }

    private static ArrayList<Task> getTasksWithoutObserver(int account) {
        ArrayList<Task> result = new ArrayList<>();
        try {
            String raw = prefs().getString(KEY_TASKS + account, null);
            if (TextUtils.isEmpty(raw)) {
                return result;
            }
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Task t = new Task();
                t.id = o.optLong("id");
                if (t.id == 0) {
                    // Legacy rows without an id get a stable one derived from index+time.
                    t.id = System.currentTimeMillis() + i;
                }
                t.target = o.optString("target", "");
                t.content = o.optString("content", "");
                t.time = o.optString("time", "00:01");
                t.dialogId = o.optLong("dialogId");
                t.status = o.optString("status", "未执行");
                t.lastRunDay = o.optLong("lastRunDay");
                t.retryDay = o.optLong("retryDay");
                t.pendingMessageId = o.optInt("pendingMessageId");
                result.add(t);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    /** Persist tasks and let the scheduler recompute the next alarm. */
    public static void saveTasks(int account, ArrayList<Task> tasks) {
        saveTasksWithoutReschedule(account, tasks);
        AiqiyiSignInScheduler.reschedule(account);
    }

    private static void saveTasksWithoutReschedule(int account, ArrayList<Task> tasks) {
        try {
            JSONArray arr = new JSONArray();
            if (tasks != null) {
                for (Task t : tasks) {
                    if (t == null) {
                        continue;
                    }
                    JSONObject o = new JSONObject();
                    o.put("id", t.id);
                    o.put("target", t.target);
                    o.put("content", t.content);
                    o.put("time", t.time);
                    o.put("dialogId", t.dialogId);
                    o.put("status", t.status);
                    o.put("lastRunDay", t.lastRunDay);
                    o.put("retryDay", t.retryDay);
                    o.put("pendingMessageId", t.pendingMessageId);
                    arr.put(o);
                }
            }
            prefs().edit().putString(KEY_TASKS + account, arr.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Current Beijing wall-clock time as HH:mm. */
    private static String formatCurrentTime() {
        return TIME.format(new java.util.Date());
    }

    /**
     * Normalize a user-typed time string to HH:mm, or return null when invalid.
     * Accepts H:mm as well as HH:mm.
     */
    public static String normalizeTime(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2}):([0-5]\\d)$").matcher(s);
        if (!m.matches()) {
            return null;
        }
        try {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h < 0 || h > 23) {
                return null;
            }
            return String.format(Locale.US, "%02d:%02d", h, min);
        } catch (Throwable e) {
            return null;
        }
    }

    /** A stable per-day key (days since epoch in Beijing time) for dedup. */
    public static long todayKey(Calendar cal) {
        Calendar c = (Calendar) (cal != null ? cal : Calendar.getInstance()).clone();
        c.setTimeZone(BEIJING);
        long dayMillis = 24L * 60L * 60L * 1000L;
        long local = c.getTimeInMillis() + c.getTimeZone().getOffset(c.getTimeInMillis());
        return local / dayMillis;
    }

    /**
     * Next trigger timestamp for the given HH:mm, evaluated in Beijing time.
     *
     * @param time      HH:mm string
     * @param nowMillis current wall-clock millis
     * @return epoch millis of the next occurrence, or -1 when the time is invalid
     */
    public static long nextTriggerMillis(String time, long nowMillis) {
        String norm = normalizeTime(time);
        if (norm == null) {
            return -1;
        }
        try {
            String[] parts = norm.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            Calendar now = Calendar.getInstance(BEIJING);
            now.setTimeInMillis(nowMillis);

            Calendar target = (Calendar) now.clone();
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            if (target.getTimeInMillis() <= nowMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1);
            }
            return target.getTimeInMillis();
        } catch (Throwable e) {
            FileLog.e(e);
            return -1;
        }
    }

    // ------------------------------------------------------------------
    //  Status observer: keeps task status in sync with real network receipts
    // ------------------------------------------------------------------

    private static final HashMap<Integer, StatusObserver> STATUS_OBSERVERS = new HashMap<>();

    private static synchronized void ensureStatusObserver(int account) {
        if (STATUS_OBSERVERS.containsKey(account)) {
            return;
        }
        StatusObserver observer = new StatusObserver(account);
        STATUS_OBSERVERS.put(account, observer);

        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(observer, NotificationCenter.messageReceivedByServer);
        center.addObserver(observer, NotificationCenter.messageSendError);
        center.addObserver(observer, NotificationCenter.didUpdateConnectionState);
    }

    /** 未确认提交的状态集合：这些状态代表「可能还没真正发出去」。 */
    private static boolean isUnconfirmedSubmission(String status) {
        if (TextUtils.isEmpty(status)) {
            return true;
        }
        return "发送中".equals(status)
                || "发送请求已提交".equals(status)
                || "已提交发送".equals(status)
                || "已提交发送，等待确认".equals(status)
                || "重新发送中".equals(status)
                || "重新发送请求已提交，等待确认".equals(status)
                || "等待执行".equals(status)
                || "未执行".equals(status);
    }

    /** Whether the account currently has a usable network connection. */
    private static boolean isConnected(int account) {
        try {
            int state = AccountInstance.getInstance(account)
                    .getConnectionsManager().getConnectionState();
            return state == ConnectionsManager.ConnectionStateConnected
                    || state == ConnectionsManager.ConnectionStateUpdating;
        } catch (Throwable e) {
            return false;
        }
    }

    private static final class StatusObserver implements NotificationCenter.NotificationCenterDelegate {
        private final int account;

        StatusObserver(int account) {
            this.account = account;
        }

        @Override
        public void didReceivedNotification(int event, int eventAccount, Object... args) {
            if (eventAccount != account) {
                return;
            }

            // 连接恢复 → 立刻跑一轮到期任务
            if (event == NotificationCenter.didUpdateConnectionState) {
                if (isConnected(account)) {
                    executeDueTasks(account);
                }
                return;
            }

            // 消息回执 → 用 messageId 匹配任务并更新状态
            if (args == null || args.length == 0 || !(args[0] instanceof Number)) {
                return;
            }
            int messageId = ((Number) args[0]).intValue();
            if (messageId == 0) {
                return;
            }

            ArrayList<Task> tasks = getTasksWithoutObserver(account);
            boolean changed = false;
            for (Task task : tasks) {
                if (task.pendingMessageId != messageId) {
                    continue;
                }
                task.pendingMessageId = 0;

                if (event == NotificationCenter.messageSendError) {
                    task.retryDay = task.lastRunDay;
                    task.lastRunDay = 0;
                    task.status = "失败：消息发送失败，等待重试";
                } else if (event == NotificationCenter.messageReceivedByServer) {
                    task.retryDay = 0;
                    task.status = "成功（" + formatCurrentTime() + "）";
                }
                changed = true;
            }
            if (changed) {
                saveTasksWithoutReschedule(account, tasks);
            }
        }
    }

    /**
     * Scan all tasks for the account and fire the ones that are due.
     * Called from the alarm receiver, connection observer and app launch.
     */
    public static synchronized void executeDueTasks(int account) {
        if (!isEnabled(account)) {
            return;
        }
        UserConfig config = UserConfig.getInstance(account);
        if (config == null || !config.isClientActivated()) {
            return;
        }

        ensureStatusObserver(account);
        ArrayList<Task> tasks = getTasks(account);

        Calendar now = Calendar.getInstance(BEIJING);
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        long day = todayKey(now);
        boolean changed = false;

        for (Task task : tasks) {
            String norm = normalizeTime(task.time);
            if (norm == null) {
                task.status = "失败：时间格式错误";
                changed = true;
                continue;
            }

            String[] parts = norm.split(":");
            int targetMinute;
            try {
                targetMinute = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } catch (Throwable e) {
                task.status = "失败：时间格式错误";
                changed = true;
                continue;
            }

            // 跨天重置：重试日不是今天就清空重试标记
            if (task.retryDay != 0 && task.retryDay != day) {
                task.retryDay = 0;
                if (task.status != null && task.status.startsWith("失败")) {
                    task.status = "等待执行";
                }
                changed = true;
            }

            // 到点 && 今天没跑过 && 没有待确认消息 && 处于未确认状态
            if (targetMinute <= minutes
                    && task.lastRunDay != day
                    && task.pendingMessageId == 0
                    && isUnconfirmedSubmission(task.status)) {

                SendMessagesHelper helper = AccountInstance.getInstance(account).getSendMessagesHelper();
                int queuedMessageId = helper.getSendingMessageId(task.dialogId);

                if (queuedMessageId != 0) {
                    // 消息其实还在队列中 → 认领它
                    task.pendingMessageId = queuedMessageId;
                    task.status = "发送中";
                    changed = true;
                    continue;
                }

                // 队列里没有 → 上次提交未成功，重置等下一轮
                task.lastRunDay = 0;
                task.status = "等待执行";
                changed = true;
                continue;
            }

            // 到点 && 今天没跑过 && 状态不是未确认（即真正的「待发送」）
            if (targetMinute <= minutes
                    && task.lastRunDay != day
                    && task.pendingMessageId == 0
                    && !isUnconfirmedSubmission(task.status)) {

                if (!isConnected(account)) {
                    task.retryDay = day;
                    task.pendingMessageId = 0;
                    task.status = "失败：无网络或 Telegram 未连接，等待重试";
                    changed = true;
                    continue;
                }

                task.lastRunDay = day;
                task.retryDay = 0;
                task.pendingMessageId = 0;
                task.status = "发送请求已提交";
                saveTasksWithoutReschedule(account, tasks);

                try {
                    SendMessagesHelper helper = AccountInstance.getInstance(account).getSendMessagesHelper();
                    int previousMessageId = helper.getSendingMessageId(task.dialogId);

                    SendMessagesHelper.SendMessageParams params =
                            SendMessagesHelper.SendMessageParams.of(task.content, task.dialogId);
                    helper.sendMessage(params);

                    int messageId = helper.getSendingMessageId(task.dialogId);
                    task.pendingMessageId = (messageId != 0 && messageId != previousMessageId) ? messageId : 0;
                    task.status = task.pendingMessageId == 0 ? "发送请求已提交" : "发送中";
                } catch (Throwable e) {
                    task.pendingMessageId = 0;
                    task.retryDay = day;
                    task.status = "失败【目标永远存在禁言无法发送】";
                    FileLog.e(e);
                }
                changed = true;
            }
        }

        if (changed) {
            saveTasksWithoutReschedule(account, tasks);
            AiqiyiSignInScheduler.reschedule(account);
        }
    }

    // ------------------------------------------------------------------
    //  Manual execution (used by the task page buttons)
    // ------------------------------------------------------------------

    /**
     * Fire a single task right now, ignoring the master switch, the scheduled
     * time and the once-per-day guard. Used by the card's "Resend" action.
     *
     * @return true when the send request was submitted
     */
    public static synchronized boolean runTaskNow(int account, long taskId) {
        ArrayList<Task> tasks = getTasks(account);
        for (Task task : tasks) {
            if (task.id != taskId) {
                continue;
            }
            boolean ok = submit(account, task);
            saveTasksWithoutReschedule(account, tasks);
            AiqiyiSignInScheduler.reschedule(account);
            return ok;
        }
        return false;
    }

    /**
     * Fire every task right now, ignoring the master switch and schedule.
     * Used by the "Run All" action.
     *
     * @return the number of tasks whose send request was submitted
     */
    public static synchronized int runAllTasksNow(int account) {
        ArrayList<Task> tasks = getTasks(account);
        int submitted = 0;
        for (Task task : tasks) {
            if (submit(account, task)) {
                submitted++;
            }
        }
        saveTasksWithoutReschedule(account, tasks);
        AiqiyiSignInScheduler.reschedule(account);
        return submitted;
    }

    /** Shared submission path for manual execution. */
    private static boolean submit(int account, Task task) {
        try {
            if (task.dialogId == 0) {
                task.status = "失败：目标无法解析，请重新选择";
                return false;
            }
            if (!isConnected(account)) {
                task.status = "失败：无网络或 Telegram 未连接，等待重试";
                task.retryDay = todayKey(null);
                return false;
            }
            task.lastRunDay = todayKey(null);
            task.retryDay = 0;
            task.pendingMessageId = 0;
            task.status = "发送请求已提交";

            SendMessagesHelper helper = AccountInstance.getInstance(account).getSendMessagesHelper();
            int previousMessageId = helper.getSendingMessageId(task.dialogId);

            SendMessagesHelper.SendMessageParams params =
                    SendMessagesHelper.SendMessageParams.of(task.content, task.dialogId);
            helper.sendMessage(params);

            int messageId = helper.getSendingMessageId(task.dialogId);
            task.pendingMessageId = (messageId != 0 && messageId != previousMessageId) ? messageId : 0;
            task.status = task.pendingMessageId == 0 ? "发送请求已提交" : "发送中";
            ensureStatusObserver(account);
            return true;
        } catch (Throwable e) {
            task.pendingMessageId = 0;
            task.retryDay = todayKey(null);
            task.status = "失败【目标永远存在禁言无法发送】";
            FileLog.e(e);
            return false;
        }
    }
}
