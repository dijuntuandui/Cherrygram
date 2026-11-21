/**
 * 爱琦艺客户端 - 他人资料本地覆盖（姓名 / 简介）
 *
 * 设计对齐黄昏 Huanghun-TG 的实现思路，关键原则：
 *   1. 存储键只使用 userId（带账号隔离），绝不使用姓名文本做键；
 *   2. 姓名覆盖在 MessagesController.putUser 阶段直接改写 TLRPC.User 的
 *      first_name / last_name 字段，因此天然按「对象」隔离，不可能跨用户串号；
 *   3. 简介覆盖只在 ProfileActivity 的资料页读取点按 userId 查询，属纯展示层；
 *   4. 禁止对当前登录用户自身写入覆盖值。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class AiqiyiLocalPeerName {

    private static final String PREF_NAME = "aqy_local_peer_name";
    private static final String KEY_NAME_PREFIX = "peerName_";
    private static final String KEY_BIO_PREFIX = "peerBio_";

    /** userId -> 覆盖后的姓名；空串表示「已查询过，无覆盖」 */
    private static final ConcurrentHashMap<Long, String> nameCache = new ConcurrentHashMap<>();
    /** userId -> 覆盖后的简介；空串表示「已查询过，无覆盖」 */
    private static final ConcurrentHashMap<Long, String> bioCache = new ConcurrentHashMap<>();

    private AiqiyiLocalPeerName() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, 0);
    }

    private static long selfId() {
        try {
            UserConfig cfg = UserConfig.getInstance(UserConfig.selectedAccount);
            return cfg == null ? 0 : cfg.getClientUserId();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static String key(String prefix, long userId) {
        return prefix + UserConfig.selectedAccount + "_" + userId;
    }

    // ==================== 姓名 ====================

    /**
     * 取姓名覆盖值。无覆盖返回 null。
     * 结果进内存缓存，putUser 高频调用也不会反复读盘。
     */
    public static String getName(long userId) {
        if (userId <= 0 || userId == selfId()) {
            return null;
        }
        String cached = nameCache.computeIfAbsent(userId, k -> {
            String v = prefs().getString(key(KEY_NAME_PREFIX, k), null);
            return TextUtils.isEmpty(v) ? "" : v;
        });
        return cached.isEmpty() ? null : cached;
    }

    /** 设置姓名覆盖。传空表示清除。 */
    public static void setName(long userId, String value) {
        if (userId <= 0 || userId == selfId()) {
            return;
        }
        String v = value == null ? "" : value.trim();
        SharedPreferences.Editor editor = prefs().edit();
        if (v.isEmpty()) {
            editor.remove(key(KEY_NAME_PREFIX, userId));
            nameCache.put(userId, "");
        } else {
            editor.putString(key(KEY_NAME_PREFIX, userId), v);
            nameCache.put(userId, v);
        }
        editor.apply();
    }

    // ==================== 简介 ====================

    public static String getBio(long userId) {
        if (userId <= 0 || userId == selfId()) {
            return null;
        }
        String cached = bioCache.computeIfAbsent(userId, k -> {
            String v = prefs().getString(key(KEY_BIO_PREFIX, k), null);
            return TextUtils.isEmpty(v) ? "" : v;
        });
        return cached.isEmpty() ? null : cached;
    }

    public static void setBio(long userId, String value) {
        if (userId <= 0 || userId == selfId()) {
            return;
        }
        String v = value == null ? "" : value.trim();
        SharedPreferences.Editor editor = prefs().edit();
        if (v.isEmpty()) {
            editor.remove(key(KEY_BIO_PREFIX, userId));
            bioCache.put(userId, "");
        } else {
            editor.putString(key(KEY_BIO_PREFIX, userId), v);
            bioCache.put(userId, v);
        }
        editor.apply();
    }

    public static boolean hasAny(long userId) {
        return getName(userId) != null || getBio(userId) != null;
    }

    public static void clearAll(long userId) {
        setName(userId, null);
        setBio(userId, null);
    }

    /** 账号切换或数据清理后调用，丢弃内存缓存 */
    public static void invalidateCache() {
        nameCache.clear();
        bioCache.clear();
    }
}
