/**
 * 爱琦艺客户端 - 他人资料本地覆盖 Helper（仅本机可见）
 *
 * 按 userId 分别保存对「任意用户」的本地显示覆盖：
 *   名称 / 简介 / 头像
 *
 * ⚠️ 只改本机渲染，不改服务器数据、不发任何请求、不影响对方。
 *
 * 设计要点（相对于黄昏只注入 ProfileActivity 的做法的改进）：
 *   1. 提供原名反查表（原名 -> 覆盖名），使 ContactsController.formatName
 *      这一条底层路径被挂钩后，聊天列表 / 消息气泡 / 资料页三处同时生效；
 *   2. 反查表用 HashMap，读取复杂度 O(1)，不拖慢渲染；
 *   3. 反查表内容在数据变更时重建，不在渲染路径上查库。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AiqiyiPeerOverrideHelper {

    private static final String PREFS_NAME = "aiqiyi_peer_override";

    private static final String KEY_NAME_PREFIX = "localPeerName_";
    private static final String KEY_BIO_PREFIX = "localPeerBio_";
    private static final String KEY_AVATAR_PREFIX = "localPeerAvatar_";
    /** 记录被覆盖过的 userId 列表，便于遍历重建反查表 */
    private static final String KEY_IDS = "localPeer_ids";

    private static SharedPreferences prefs;

    /** 原名 -> 覆盖名，供 formatName 底层快速反查 */
    private static final HashMap<String, String> nameReverseMap = new HashMap<>();
    /** 是否已初始化过反查表 */
    private static volatile boolean mapReady;

    private AiqiyiPeerOverrideHelper() {
    }

    private static SharedPreferences prefs() {
        if (prefs == null) {
            synchronized (AiqiyiPeerOverrideHelper.class) {
                if (prefs == null) {
                    android.content.Context ctx = ApplicationLoader.applicationContext;
                    if (ctx == null) {
                        // 保底：某些早期调用点 applicationContext 尚未就绪，用 ApplicationLoader 兜底
                        ctx = org.telegram.messenger.ApplicationLoader.applicationContext;
                    }
                    if (ctx == null) {
                        return null;
                    }
                    prefs = ctx.getSharedPreferences(PREFS_NAME, android.app.Activity.MODE_PRIVATE);
                }
            }
        }
        return prefs;
    }

    /** 所有读操作统一入口，prefs 不可用时返回 null 而不是 NPE */
    private static String rawGet(String key) {
        SharedPreferences p = prefs();
        return p == null ? null : p.getString(key, null);
    }

    /** 所有写操作统一入口，返回是否真的提交成功 */
    private static boolean rawPut(String key, String value) {
        SharedPreferences p = prefs();
        if (p == null) return false;
        if (value == null) {
            return p.edit().remove(key).commit();
        }
        return p.edit().putString(key, value).commit();
    }

    // ============ 读写 ============

    public static String getName(long userId) {
        if (userId <= 0) return null;
        String v = rawGet(KEY_NAME_PREFIX + userId);
        return TextUtils.isEmpty(v) ? null : v;
    }

    public static void setName(long userId, String value) {
        if (userId <= 0) return;
        if (TextUtils.isEmpty(value)) {
            rawPut(KEY_NAME_PREFIX + userId, null);
            unregisterId(userId);
        } else {
            rawPut(KEY_NAME_PREFIX + userId, value);
            registerId(userId);
        }
        invalidateCache();
    }

    public static String getBio(long userId) {
        if (userId <= 0) return null;
        String v = rawGet(KEY_BIO_PREFIX + userId);
        return TextUtils.isEmpty(v) ? null : v;
    }

    public static void setBio(long userId, String value) {
        if (userId <= 0) return;
        if (TextUtils.isEmpty(value)) {
            rawPut(KEY_BIO_PREFIX + userId, null);
            unregisterId(userId);
        } else {
            rawPut(KEY_BIO_PREFIX + userId, value);
            registerId(userId);
        }
        invalidateCache();
    }

    public static String getAvatar(long userId) {
        if (userId <= 0) return null;
        String v = rawGet(KEY_AVATAR_PREFIX + userId);
        return TextUtils.isEmpty(v) ? null : v;
    }

    public static void setAvatar(long userId, String value) {
        if (userId <= 0) return;
        if (TextUtils.isEmpty(value)) {
            rawPut(KEY_AVATAR_PREFIX + userId, null);
            unregisterId(userId);
        } else {
            rawPut(KEY_AVATAR_PREFIX + userId, value);
            registerId(userId);
        }
        invalidateCache();
    }

    /** 该用户是否存在任意一项覆盖 */
    public static boolean hasAny(long userId) {
        if (userId <= 0) return false;
        return getName(userId) != null || getBio(userId) != null || getAvatar(userId) != null;
    }

    /** 一键清除某用户的全部覆盖 */
    public static void clearAll(long userId) {
        if (userId <= 0) return;
        rawPut(KEY_NAME_PREFIX + userId, null);
        rawPut(KEY_BIO_PREFIX + userId, null);
        rawPut(KEY_AVATAR_PREFIX + userId, null);
        unregisterId(userId);
        invalidateCache();
    }

    // ============ 反查表 ============

    /**
     * 由调用方传入「原始显示名」，返回被覆盖后的名字。
     *
     * 用法（在 ContactsController.formatName 底层）：
     *   String ov = AiqiyiPeerOverrideHelper.lookupByOriginalName(result);
     *   if (ov != null) return ov;
     */
    public static String lookupByOriginalName(String originalName) {
        if (TextUtils.isEmpty(originalName)) return null;
        ensureMap();
        return nameReverseMap.get(originalName);
    }

    private static void ensureMap() {
        if (mapReady) return;
        synchronized (nameReverseMap) {
            if (mapReady) return;
            nameReverseMap.clear();
            List<Long> ids = readIds();
            for (Long id : ids) {
                if (id == null || id <= 0) continue;
                String ovName = rawGet(KEY_NAME_PREFIX + id);
                String origName = rawGet(KEY_NAME_PREFIX + id + "_orig");
                if (!TextUtils.isEmpty(ovName) && !TextUtils.isEmpty(origName)) {
                    nameReverseMap.put(origName, ovName);
                }
            }
            mapReady = true;
        }
    }

    /**
     * 录入「原名 -> 覆盖名」映射。
     * 由 UI 层在设置名称时调用，原始名从当时拿到的 TLRPC.User 取。
     */
    public static void putNameMapping(long userId, String originalName, String overrideName) {
        if (userId <= 0) return;
        rawPut(KEY_NAME_PREFIX + userId + "_orig", originalName);
        synchronized (nameReverseMap) {
            if (!TextUtils.isEmpty(originalName) && !TextUtils.isEmpty(overrideName)) {
                nameReverseMap.put(originalName, overrideName);
            } else if (!TextUtils.isEmpty(originalName)) {
                nameReverseMap.remove(originalName);
            }
            mapReady = true;
        }
    }

    /** 数据变更后让反查表下次重新构建 */
    public static void invalidateCache() {
        synchronized (nameReverseMap) {
            mapReady = false;
        }
    }

    // ============ id 列表维护 ============

    private static List<Long> readIds() {
        String raw = rawGet(KEY_IDS);
        if (raw == null) raw = "";
        ArrayList<Long> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return out;
        for (String s : raw.split(",")) {
            if (TextUtils.isEmpty(s)) continue;
            try {
                long v = Long.parseLong(s.trim());
                if (v > 0) out.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static void writeIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        rawPut(KEY_IDS, sb.toString());
    }

    private static void registerId(long userId) {
        List<Long> ids = readIds();
        if (!ids.contains(userId)) {
            ids.add(userId);
            writeIds(ids);
        }
    }

    private static void unregisterId(long userId) {
        if (hasAny(userId)) return;
        List<Long> ids = readIds();
        if (ids.remove(Long.valueOf(userId))) {
            writeIds(ids);
        }
        rawPut(KEY_NAME_PREFIX + userId + "_orig", null);
    }

    /** 调试/统计用 */
    public static Map<String, String> debugMap() {
        ensureMap();
        synchronized (nameReverseMap) {
            return new HashMap<>(nameReverseMap);
        }
    }
}