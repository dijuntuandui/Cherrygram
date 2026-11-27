package org.telegram.ui;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

/**
 * [Aiqiyi] 本地典藏号码信息存储层。
 *
 * 完全脱离服务端：
 * - 号码本身走 ProfileActivity 的 local_profile_phone 体系（不归这里管）
 * - 这里只存「成交日期 / TON / USD」三个展示字段
 *
 * 键：aqy_collect_<account>_<uid>_<phone>
 * 值：date + 0x01 + crypto + 0x01 + usd（用控制字符作分隔符，避免用户输入逗号/竖线冲突）
 */
public final class AiqiyiCollectibleInfo {

    private static final char SEP = '\u0001';

    /** 没配置过时的默认展示值，对齐官方截图观感 */
    public static final String DEFAULT_DATE   = "2026-04-02 10:31:38";
    public static final String DEFAULT_CRYPTO = "1837.5";
    public static final String DEFAULT_USD    = "1782.69";

    public String date   = DEFAULT_DATE;
    public String crypto = DEFAULT_CRYPTO;
    public String usd    = DEFAULT_USD;

    private AiqiyiCollectibleInfo() {
    }

    /** 号码是否属于「典藏/匿名号码」范畴：去掉空格与 + 号后以 888 开头 */
    public static boolean isCollectiblePhone(String phone) {
        if (phone == null) return false;
        String raw = phone.replace(" ", "").replace("\u00a0", "");
        if (raw.startsWith("+")) raw = raw.substring(1);
        return raw.startsWith("888");
    }

    /** 规范化成存储用的号码片段（去空格、去 +，只留数字） */
    private static String keyPhone(String phone) {
        if (phone == null) return "";
        StringBuilder sb = new StringBuilder(phone.length());
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    private static String prefsKey(int account, long uid, String phone) {
        return "aqy_collect_" + account + "_" + uid + "_" + keyPhone(phone);
    }

    /** 读取；无记录返回默认值，永远不返回 null */
    public static AiqiyiCollectibleInfo load(int account, long uid, String phone) {
        AiqiyiCollectibleInfo info = new AiqiyiCollectibleInfo();
        if (uid <= 0 || TextUtils.isEmpty(phone)) {
            return info;
        }
        try {
            String v = MessagesController.getGlobalMainSettings()
                    .getString(prefsKey(account, uid, phone), null);
            if (TextUtils.isEmpty(v)) {
                return info;
            }
            String[] parts = v.split(String.valueOf(SEP), -1);
            if (parts.length >= 1 && !TextUtils.isEmpty(parts[0])) info.date = parts[0];
            if (parts.length >= 2 && !TextUtils.isEmpty(parts[1])) info.crypto = parts[1];
            if (parts.length >= 3 && !TextUtils.isEmpty(parts[2])) info.usd = parts[2];
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return info;
    }

    /** 保存；三项全空则视为删除该条记录 */
    public static void save(int account, long uid, String phone,
                            String date, String crypto, String usd) {
        if (uid <= 0 || TextUtils.isEmpty(phone)) {
            FileLog.e("[Aiqiyi] saveCollectible rejected: uid=" + uid + " phone=" + phone);
            return;
        }
        try {
            SharedPreferences.Editor ed = MessagesController.getGlobalMainSettings().edit();
            String d = date == null ? "" : date.trim();
            String c = crypto == null ? "" : crypto.trim();
            String u = usd == null ? "" : usd.trim();
            if (TextUtils.isEmpty(d) && TextUtils.isEmpty(c) && TextUtils.isEmpty(u)) {
                ed.remove(prefsKey(account, uid, phone));
            } else {
                ed.putString(prefsKey(account, uid, phone),
                        d + SEP + c + SEP + u);
            }
            ed.apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** 是否配置过（决定是否显示「恢复默认典藏信息」之类入口用，暂未使用） */
    public static boolean hasCustom(int account, long uid, String phone) {
        if (uid <= 0 || TextUtils.isEmpty(phone)) return false;
        try {
            return !TextUtils.isEmpty(MessagesController.getGlobalMainSettings()
                    .getString(prefsKey(account, uid, phone), null));
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 宽松时间解析：支持
     *   2026-04-02 10:31:38
     *   2026/4/2 10:31
     *   2026-04-02
     * 解析失败返回 fallback（调用方传默认时间戳）。
     */
    public static int parseDate(String input, int fallback) {
        if (TextUtils.isEmpty(input)) return fallback;
        try {
            String s = input.trim().replace('/', '-').replace('.', '-');
            String datePart = s;
            String timePart = "00:00:00";
            int sp = s.indexOf(' ');
            if (sp < 0) sp = s.indexOf('T');
            if (sp > 0) {
                datePart = s.substring(0, sp);
                timePart = s.substring(sp + 1).trim();
            }
            String[] d = datePart.split("-");
            if (d.length < 3) return fallback;
            int year = Integer.parseInt(d[0].trim());
            int month = Integer.parseInt(d[1].trim());
            int day = Integer.parseInt(d[2].trim());
            int hour = 0, minute = 0, second = 0;
            String[] t = timePart.split(":");
            if (t.length >= 1) hour = Integer.parseInt(t[0].trim());
            if (t.length >= 2) minute = Integer.parseInt(t[1].trim());
            if (t.length >= 3) {
                String sec = t[2].trim();
                // 容忍 「38 AM/PM」这种，只取数字部分
                StringBuilder num = new StringBuilder();
                for (int i = 0; i < sec.length(); i++) {
                    char c = sec.charAt(i);
                    if (c >= '0' && c <= '9') num.append(c);
                    else break;
                }
                if (num.length() > 0) second = Integer.parseInt(num.toString());
                String upper = timePart.toUpperCase();
                if (upper.contains("PM") && hour < 12) hour += 12;
                if (upper.contains("AM") && hour == 12) hour = 0;
            }
            if (month < 1 || month > 12 || day < 1 || day > 31) return fallback;
            if (hour > 23) hour = 23;
            if (minute > 59) minute = 59;
            if (second > 59) second = 59;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.clear();
            cal.set(year, month - 1, day, hour, minute, second);
            return (int) (cal.getTimeInMillis() / 1000L);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
