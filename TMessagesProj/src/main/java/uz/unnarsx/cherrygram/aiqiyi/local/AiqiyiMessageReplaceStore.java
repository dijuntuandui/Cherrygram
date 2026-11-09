/**
 * 爱琦艺客户端 - 单条消息替换存储
 *
 * 按 chatId + messageId 保存「这条消息在本机显示成什么」，
 * 只影响本机渲染，不修改任何消息内容，不发送任何数据。
 *
 * 存储格式（SharedPreferences, String）：
 *   key   = "AQY_MR_<chatId>_<messageId>"
 *   value = 被替换后的文字（原文本身不作改动）
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Map;

public class AiqiyiMessageReplaceStore {

    private static final String PREFIX = "AQY_MR_";
    /** 单条时间伪装：value = 当天秒数（0~86399），显示为 HH:mm */
    private static final String T_PREFIX = "AQY_MT_";
    /** 替换前原文快照：value = 这条消息被替换之前的原始文字 */
    private static final String O_PREFIX = "AQY_MO_";
    /** 编辑历史栈：value = JSON 数组 [{t:text,e:editDate,s:stamp},...]，按时间升序 */
    private static final String H_PREFIX = "AQY_MH_";
    /** 最后已知编辑戳：value = edit_date（或 0 表示未编辑），用于判断是否需要压栈 */
    private static final String E_PREFIX = "AQY_ME_";
    private static SharedPreferences sp;

    private static SharedPreferences sp() {
        if (sp == null) {
            synchronized (AiqiyiMessageReplaceStore.class) {
                if (sp == null) {
                    sp = ApplicationLoader.applicationContext.getSharedPreferences("aqy_local_replace", 0);
                }
            }
        }
        return sp;
    }

    private static String key(long chatId, int messageId) {
        return PREFIX + chatId + "_" + messageId;
    }

    /**
     * 存一条替换。
     */
    public static void put(long chatId, int messageId, String text) {
        if (text == null || text.isEmpty()) {
            remove(chatId, messageId);
            return;
        }
        sp().edit().putString(key(chatId, messageId), text).apply();
    }

    /**
     * 取一条替换；没有则返回 null。
     */
    public static String get(long chatId, int messageId) {
        try {
            return sp().getString(key(chatId, messageId), null);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 这条消息是否被替换过。
     */
    public static boolean has(long chatId, int messageId) {
        return get(chatId, messageId) != null;
    }

    /**
     * 移除一条替换（恢复原文）。
     */
    public static void remove(long chatId, int messageId) {
        sp().edit().remove(key(chatId, messageId)).apply();
    }

    /**
     * 当前一共有多少条替换（设置页显示用）。
     */
    public static int count() {
        int n = 0;
        try {
            for (Map.Entry<String, ?> e : sp().getAll().entrySet()) {
                if (e.getKey().startsWith(PREFIX) && e.getValue() instanceof String) {
                    n++;
                }
            }
        } catch (Throwable ignore) {
        }
        return n;
    }

    /**
     * 清空所有替换。
     */
    public static void clearAll() {
        try {
            SharedPreferences.Editor ed = sp().edit();
            for (String k : sp().getAll().keySet()) {
                if (k.startsWith(PREFIX)) {
                    ed.remove(k);
                }
            }
            ed.apply();
        } catch (Throwable ignore) {
        }
    }
    /**
     * 取这条消息「当前应该显示的文字」（考虑替换后的结果）。
     * 用于通知时直接刷 messageText 缓存。
     */
    public static CharSequence resolve(long chatId, int messageId, CharSequence original) {
        String replaced = get(chatId, messageId);
        if (replaced != null) {
            return replaced;
        }
        return original;
    }

    // ======================================================================
    // 单条时间伪装
    // ======================================================================

    private static String tkey(long chatId, int messageId) {
        return T_PREFIX + chatId + "_" + messageId;
    }

    /**
     * 给一条消息设置伪装时间。
     *
     * @param seconds 当天秒数（0~86399），负数表示清除
     */
    public static void putTime(long chatId, int messageId, int seconds) {
        if (seconds < 0) {
            removeTime(chatId, messageId);
            return;
        }
        sp().edit().putInt(tkey(chatId, messageId), seconds).apply();
    }

    /**
     * 取一条消息的伪装时间；没设过返回 -1。
     */
    public static int getTime(long chatId, int messageId) {
        try {
            return sp().getInt(tkey(chatId, messageId), -1);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * 这条消息有没有伪装时间。
     */
    public static boolean hasTime(long chatId, int messageId) {
        return getTime(chatId, messageId) >= 0;
    }

    /**
     * 移除一条消息的伪装时间。
     */
    public static void removeTime(long chatId, int messageId) {
        sp().edit().remove(tkey(chatId, messageId)).apply();
    }

    /**
     * 当前一共有多少条伪装时间（设置页显示用）。
     */
    public static int timeCount() {
        int n = 0;
        try {
            for (String k : sp().getAll().keySet()) {
                if (k.startsWith(T_PREFIX)) {
                    n++;
                }
            }
        } catch (Throwable ignore) {
        }
        return n;
    }

    /**
     * 清空所有时间伪装。
     */
    public static void clearAllTime() {
        try {
            SharedPreferences.Editor ed = sp().edit();
            for (String k : sp().getAll().keySet()) {
                if (k.startsWith(T_PREFIX)) {
                    ed.remove(k);
                }
            }
            ed.apply();
        } catch (Throwable ignore) {
        }
    }

    // ======================================================================
    // 替换前原文快照（A 方案：查看我替换之前是什么）
    // ======================================================================

    private static String okey(long chatId, int messageId) {
        return O_PREFIX + chatId + "_" + messageId;
    }

    /**
     * 记录「替换前的原文」。已存在则不覆盖，保证永远是最初那份。
     */
    public static void putOriginal(long chatId, int messageId, String original) {
        if (original == null) {
            return;
        }
        String k = okey(chatId, messageId);
        if (sp().contains(k)) {
            return; // 已存过最初原文，不覆盖
        }
        sp().edit().putString(k, original).apply();
    }

    /**
     * 取替换前的原文；没存过返回 null。
     */
    public static String getOriginal(long chatId, int messageId) {
        try {
            return sp().getString(okey(chatId, messageId), null);
        } catch (Throwable e) {
            return null;
        }
    }

    public static void removeOriginal(long chatId, int messageId) {
        sp().edit().remove(okey(chatId, messageId)).apply();
    }

    // ======================================================================
    // 编辑历史快照（B 方案：本地记录每次文本变化前的版本）
    // ======================================================================

    /** 一条历史版本 */
    public static class HistoryEntry {
        public final String text;
        public final int editDate;
        public final long stamp;

        public HistoryEntry(String text, int editDate, long stamp) {
            this.text = text;
            this.editDate = editDate;
            this.stamp = stamp;
        }
    }

    private static String hkey(long chatId, int messageId) {
        return H_PREFIX + chatId + "_" + messageId;
    }

    private static String ekey(long chatId, int messageId) {
        return E_PREFIX + chatId + "_" + messageId;
    }

    /**
     * 每当消息文本可能变化时调用。
     * 逻辑：拿当前 edit_date 和上次记录的比对，
     *   - 没变 → 什么都不做
     *   - 变了 → 把「上次的文本」压进历史栈，再更新基线
     *
     * 注意：只能记录「本机装上这个版本之后」发生的编辑，
     * 服务端不提供历史版本接口，翻不出更早的内容。
     */
    public static void trackEdit(long chatId, int messageId, String currentText, int editDate) {
        if (currentText == null) {
            return;
        }
        try {
            String ek = ekey(chatId, messageId);
            String hk = hkey(chatId, messageId);
            boolean hasBaseline = sp().contains(ek);
            int lastEdit = sp().getInt(ek, 0);
            if (!hasBaseline) {
                // 第一次见到这条消息：只记基线，不压栈
                sp().edit()
                    .putInt(ek, editDate)
                    .putString(hk + "_last", currentText)
                    .apply();
                return;
            }
            if (lastEdit == editDate) {
                return; // 没编辑过
            }
            // 发生了编辑：把上一次的文本压进历史
            String lastText = sp().getString(hk + "_last", null);
            java.util.List<HistoryEntry> list = getHistory(chatId, messageId);
            if (lastText != null && !lastText.equals(currentText)) {
                list.add(new HistoryEntry(lastText, lastEdit, System.currentTimeMillis()));
                // 上限 30 条，防无限增长
                while (list.size() > 30) {
                    list.remove(0);
                }
                saveHistory(chatId, messageId, list);
            }
            sp().edit()
                .putInt(ek, editDate)
                .putString(hk + "_last", currentText)
                .apply();
        } catch (Throwable ignore) {
        }
    }

    /**
     * 取编辑历史（时间升序，最早的在前）。
     */
    public static java.util.List<HistoryEntry> getHistory(long chatId, int messageId) {
        java.util.List<HistoryEntry> out = new java.util.ArrayList<>();
        try {
            String raw = sp().getString(hkey(chatId, messageId), null);
            if (raw == null || raw.isEmpty()) {
                return out;
            }
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                out.add(new HistoryEntry(
                        o.optString("t", ""),
                        o.optInt("e", 0),
                        o.optLong("s", 0)));
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    private static void saveHistory(long chatId, int messageId, java.util.List<HistoryEntry> list) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (HistoryEntry e : list) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("t", e.text);
                o.put("e", e.editDate);
                o.put("s", e.stamp);
                arr.put(o);
            }
            sp().edit().putString(hkey(chatId, messageId), arr.toString()).apply();
        } catch (Throwable ignore) {
        }
    }

    /** 这条消息有没有记到过历史版本。 */
    public static boolean hasHistory(long chatId, int messageId) {
        return !getHistory(chatId, messageId).isEmpty();
    }

    public static void removeHistory(long chatId, int messageId) {
        sp().edit()
            .remove(hkey(chatId, messageId))
            .remove(hkey(chatId, messageId) + "_last")
            .remove(ekey(chatId, messageId))
            .apply();
    }

    // ======================================================================
    // 列表查询（设置页「已替换 N 条」点击后展示用）
    // ======================================================================

    /**
     * 列出所有有记录（文字替换或时间伪装）的 chatId。
     */
    public static java.util.ArrayList<Long> listChatIds() {
        java.util.HashSet<Long> set = new java.util.HashSet<>();
        try {
            for (String k : sp().getAll().keySet()) {
                String body;
                if (k.startsWith(PREFIX)) {
                    body = k.substring(PREFIX.length());
                } else if (k.startsWith(T_PREFIX)) {
                    body = k.substring(T_PREFIX.length());
                } else {
                    continue;
                }
                int idx = body.indexOf('_');
                if (idx <= 0) {
                    continue;
                }
                try {
                    set.add(Long.parseLong(body.substring(0, idx)));
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }
        return new java.util.ArrayList<>(set);
    }

    /**
     * 列出某会话下所有有记录的消息 id（文字替换 + 时间伪装）。
     * 返回 int[]{messageId}，去重。
     */
    public static java.util.ArrayList<int[]> listMessageIds(long chatId) {
        java.util.HashSet<Integer> set = new java.util.HashSet<>();
        final String p1 = PREFIX + chatId + "_";
        final String p2 = T_PREFIX + chatId + "_";
        try {
            for (String k : sp().getAll().keySet()) {
                String body = null;
                if (k.startsWith(p1)) {
                    body = k.substring(p1.length());
                } else if (k.startsWith(p2)) {
                    body = k.substring(p2.length());
                }
                if (body == null) {
                    continue;
                }
                int idx = body.indexOf('_');
                if (idx >= 0) {
                    body = body.substring(0, idx);
                }
                try {
                    set.add(Integer.parseInt(body));
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
        for (Integer i : set) {
            out.add(new int[]{i});
        }
        return out;
    }


    /**
     * 替换数据变更后由界面调用：
     * 遍历可见消息，把 messageText 缓存按最新替换表刷一遍。
     *
     * MessageObject.messageText 在构造时算好并缓存，
     * 不清掉的话界面上看不到变化。
     *
     * @param messages 当前聊天窗口里的消息列表
     */
    public static void applyTo(java.util.List<org.telegram.messenger.MessageObject> messages) {
        if (messages == null) {
            return;
        }
        for (org.telegram.messenger.MessageObject mo : messages) {
            if (mo == null || mo.messageOwner == null) {
                continue;
            }
            long chatId = mo.messageOwner.dialog_id;
            int messageId = mo.messageOwner.id;
            String replaced = get(chatId, messageId);
            if (replaced != null) {
                mo.messageText = replaced;
            } else if (mo.messageOwner.message != null) {
                // 恢复成规则式替换后的原文
                mo.messageText = uz.unnarsx.cherrygram.aiqiyi.local.AiqiyiLocalMessageHelper
                        .apply(mo.messageOwner.message);
            }
        }
    }
}