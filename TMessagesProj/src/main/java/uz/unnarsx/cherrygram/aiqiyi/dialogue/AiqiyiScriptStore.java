/*
 * 爱琦艺客户端 - 对白气泡 · 剧本存储层
 *
 * 仅本地：剧本原文、解析结果、绑定表、播放进度全部存在本机私有目录，
 * 不上传服务器，也不修改任何真实消息内容。
 *
 * 剧本格式（逐行）：
 *   角色名: 台词
 *   角色名：台词
 * 空行忽略。以 # 开头的行视为注释。
 */
package uz.unnarsx.cherrygram.aiqiyi.dialogue;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AiqiyiScriptStore {

    /** 剧本原文 */
    private static final String KEY_RAW = "aqy_script_raw";
    /** 是否启用（跟随设置开关，分开存是为了避免循环依赖） */
    private static final String KEY_ENABLED = "aqy_script_enabled";
    /** 只在此会话生效，0 = 全部会话 */
    private static final String KEY_SCOPE = "aqy_script_scope";
    /** 会话绑定表前缀：AQY_SB_<account>_<dialogId> = 播放指针(已消费条数) */
    private static final String BIND_PREFIX = "AQY_SB_";

    private static SharedPreferences sp;

    private static SharedPreferences sp() {
        if (sp == null) {
            synchronized (AiqiyiScriptStore.class) {
                if (sp == null) {
                    sp = ApplicationLoader.applicationContext
                            .getSharedPreferences("aqy_dialogue_script", 0);
                }
            }
        }
        return sp;
    }

    // ======================================================================
    // 剧本原文
    // ======================================================================

    public static String getRaw() {
        try {
            return sp().getString(KEY_RAW, "");
        } catch (Throwable e) {
            return "";
        }
    }

    public static void setRaw(String raw) {
        try {
            sp().edit().putString(KEY_RAW, raw == null ? "" : raw).apply();
            linesCache = null;
        } catch (Throwable ignore) {
        }
    }

    public static void clearRaw() {
        try {
            sp().edit().remove(KEY_RAW).apply();
            linesCache = null;
            resetAllBindings();
        } catch (Throwable ignore) {
        }
    }

    // ======================================================================
    // 开关与会话范围
    // ======================================================================

    public static boolean isEnabled() {
        try {
            return sp().getBoolean(KEY_ENABLED, false);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            sp().edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    public static long getScopeDialogId() {
        try {
            return sp().getLong(KEY_SCOPE, 0L);
        } catch (Throwable e) {
            return 0L;
        }
    }

    public static void setScopeDialogId(long dialogId) {
        try {
            sp().edit().putLong(KEY_SCOPE, dialogId).apply();
        } catch (Throwable ignore) {
        }
    }

    // ======================================================================
    // 解析
    // ======================================================================

    /** 一行剧本 */
    public static class Line {
        public final String role;
        public final String text;

        public Line(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    /** 解析结果缓存，原文变更时失效 */
    private static volatile List<Line> linesCache;

    public static List<Line> getLines() {
        List<Line> cached = linesCache;
        if (cached != null) {
            return cached;
        }
        List<Line> out = new ArrayList<>();
        String raw = getRaw();
        if (!TextUtils.isEmpty(raw)) {
            for (String rawLine : raw.split("\n")) {
                if (rawLine == null) {
                    continue;
                }
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int sep = indexOfSeparator(line);
                if (sep <= 0) {
                    continue;
                }
                String role = line.substring(0, sep).trim();
                String text = line.substring(sep + 1).trim();
                if (role.isEmpty() || text.isEmpty()) {
                    continue;
                }
                out.add(new Line(role, text));
            }
        }
        linesCache = out;
        return out;
    }

    /** 支持半角冒号和全角冒号，取先出现的那个 */
    private static int indexOfSeparator(String line) {
        int a = line.indexOf(':');
        int b = line.indexOf('：');
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    /** 剧本角色去重清单，供设置页展示 */
    public static List<String> getRoles() {
        List<String> roles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Line line : getLines()) {
            if (seen.add(line.role)) {
                roles.add(line.role);
            }
        }
        return roles;
    }

    public static int getLineCount() {
        return getLines().size();
    }

    /** 剧本质量检查：返回可读提示，null 表示没问题 */
    public static String validate() {
        String raw = getRaw();
        if (TextUtils.isEmpty(raw.trim())) {
            return "剧本为空，先导入或粘贴内容";
        }
        List<Line> lines = getLines();
        if (lines.isEmpty()) {
            return "没有解析出任何有效行，格式需要是「角色名: 台词」";
        }
        return null;
    }

    // ======================================================================
    // 会话绑定与播放进度
    // ======================================================================

    private static String bindKey(int account, long dialogId) {
        return BIND_PREFIX + account + "_" + dialogId;
    }

    /** 该会话是否已绑定剧本 */
    public static boolean isBound(int account, long dialogId) {
        try {
            return sp().contains(bindKey(account, dialogId));
        } catch (Throwable e) {
            return false;
        }
    }

    /** 绑定会话，并把指针归零 */
    public static void bind(int account, long dialogId) {
        try {
            sp().edit().putInt(bindKey(account, dialogId), 0).apply();
        } catch (Throwable ignore) {
        }
    }

    public static void unbind(int account, long dialogId) {
        try {
            sp().edit().remove(bindKey(account, dialogId)).apply();
        } catch (Throwable ignore) {
        }
    }

    /** 已消费条数 */
    public static int getCursor(int account, long dialogId) {
        try {
            return sp().getInt(bindKey(account, dialogId), 0);
        } catch (Throwable e) {
            return 0;
        }
    }

    public static void setCursor(int account, long dialogId, int cursor) {
        try {
            sp().edit().putInt(bindKey(account, dialogId), Math.max(0, cursor)).apply();
        } catch (Throwable ignore) {
        }
    }

    /** 重置某会话的播放进度 */
    public static void resetCursor(int account, long dialogId) {
        setCursor(account, dialogId, 0);
    }

    /** 清空全部进度但保留绑定 */
    public static void resetAllCursors() {
        try {
            Map<String, ?> all = sp().getAll();
            SharedPreferences.Editor editor = sp().edit();
            for (String key : all.keySet()) {
                if (key.startsWith(BIND_PREFIX)) {
                    editor.putInt(key, 0);
                }
            }
            editor.apply();
        } catch (Throwable ignore) {
        }
    }

    /** 清空所有会话绑定 */
    public static void resetAllBindings() {
        try {
            Map<String, ?> all = sp().getAll();
            SharedPreferences.Editor editor = sp().edit();
            for (String key : all.keySet()) {
                if (key.startsWith(BIND_PREFIX)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        } catch (Throwable ignore) {
        }
    }

    /** 已绑定的会话数量 */
    public static int boundCount() {
        try {
            int count = 0;
            for (String key : sp().getAll().keySet()) {
                if (key.startsWith(BIND_PREFIX)) {
                    count++;
                }
            }
            return count;
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * 已分配记录：<messageId, 台词>
     *
     * 进程内缓存。消息 id 是稳定的，只要不乱清缓存，刷新列表不会重复消费剧本。
     * 键为 account|dialogId，值为 messageId → 台词。
     */
    private static final ConcurrentHashMap<String, LinkedHashMap<Integer, String>> ASSIGNED =
            new ConcurrentHashMap<>();

    private static String assignKey(int account, long dialogId) {
        return account + "|" + dialogId;
    }

    private static LinkedHashMap<Integer, String> assignedMap(int account, long dialogId) {
        String key = assignKey(account, dialogId);
        LinkedHashMap<Integer, String> map = ASSIGNED.get(key);
        if (map == null) {
            map = new LinkedHashMap<>();
            ASSIGNED.put(key, map);
        }
        return map;
    }

    public static String getAssigned(int account, long dialogId, int messageId) {
        LinkedHashMap<Integer, String> map = ASSIGNED.get(assignKey(account, dialogId));
        if (map == null) {
            return null;
        }
        return map.get(messageId);
    }

    public static void putAssigned(int account, long dialogId, int messageId, String dialogue) {
        LinkedHashMap<Integer, String> map = assignedMap(account, dialogId);
        synchronized (map) {
            // 上限保护，防止长会话无限增长
            if (map.size() > 5000) {
                map.clear();
            }
            map.put(messageId, dialogue);
        }
    }

    public static void clearAssigned(int account, long dialogId) {
        LinkedHashMap<Integer, String> map = ASSIGNED.get(assignKey(account, dialogId));
        if (map != null) {
            synchronized (map) {
                map.clear();
            }
        }
    }

    public static void clearAllAssigned() {
        ASSIGNED.clear();
    }

    /** 调试/统计用：当前会话已分配条数 */
    public static int assignedCount(int account, long dialogId) {
        LinkedHashMap<Integer, String> map = ASSIGNED.get(assignKey(account, dialogId));
        if (map == null) {
            return 0;
        }
        return map.size();
    }

    /**
     * 判断某个会话是否在生效范围内。
     * scope 为 0 表示全部会话生效。
     */
    public static boolean inScope(long dialogId) {
        long scope = getScopeDialogId();
        return scope == 0L || scope == dialogId;
    }
}
