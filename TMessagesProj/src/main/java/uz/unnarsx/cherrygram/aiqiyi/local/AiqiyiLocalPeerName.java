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

    private static long selfId(int account) {
        try {
            UserConfig cfg = UserConfig.getInstance(account);
            return cfg == null ? 0 : cfg.getClientUserId();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /**
     * [Aiqiyi] 严格的本我判定。
     *
     * 不能用 userId == selfId(account) 直接比：账号信息尚未加载完时
     * getClientUserId() 可能返回 0，selfId 就是 0，任何 userId 都不等于 0，
     * 于是「拒绝改自己」这条底线直接失效。而 currentUser 会被 saveConfig()
     * 持久化到磁盘，一旦它的 first_name 被覆盖名污染，就永久写死进本地配置。
     * 宁可保守：selfId 取不到（<=0）时一律按「可能是本人」处理，不放行。
     */
    private static boolean isSelf(int account, long userId) {
        long self = selfId(account);
        return self <= 0 || userId == self;
    }

    private static String key(int account, String prefix, long userId) {
        return prefix + account + "_" + userId;
    }

    // ==================== 姓名 ====================

    /**
 * 取姓名覆盖值。无覆盖返回 null。
 * 结果进内存缓存，putUser 高频调用也不会反复读盘。
 *
 * [Aiqiyi] account 必须由调用方显式传入（通常是 BaseController.currentAccount）。
 * 绝不能用 UserConfig.selectedAccount：那是全局「当前选中账号」，
 * 而 putUser 可能跑在任意账号的 MessagesController 实例上，
 * 多账号场景或切换账号后两者会不一致，拼出的存储键就查不到，
 * 表现为「写了但读不出来」。
 */
public static String getName(int account, long userId) {
    if (userId <= 0 || isSelf(account, userId)) {
        return null;
    }
    final String cacheKey = account + "_" + userId;
    String cached = nameCache.computeIfAbsent(cacheKey, k -> {
        String v = prefs().getString(key(account, KEY_NAME_PREFIX, userId), null);
        return TextUtils.isEmpty(v) ? "" : v;
    });
    return cached.isEmpty() ? null : cached;
}

    /** 设置姓名覆盖。传空表示清除。 */
    public static void setName(int account, long userId, String value) {
        if (userId <= 0 || isSelf(account, userId)) {
            return;
        }
        String v = value == null ? "" : value.trim();
        SharedPreferences.Editor editor = prefs().edit();
        if (v.isEmpty()) {
            editor.remove(key(account, KEY_NAME_PREFIX, userId));
            nameCache.put(account + "_" + userId, "");
        } else {
            editor.putString(key(account, KEY_NAME_PREFIX, userId), v);
            nameCache.put(account + "_" + userId, v);
        }
        editor.apply();
    }

    // ==================== 简介 ====================

    public static String getBio(int account, long userId) {
        if (userId <= 0 || isSelf(account, userId)) {
            return null;
        }
        final String cacheKey = account + "_" + userId;
        String cached = bioCache.computeIfAbsent(cacheKey, k -> {
            String v = prefs().getString(key(account, KEY_BIO_PREFIX, userId), null);
            return TextUtils.isEmpty(v) ? "" : v;
        });
        return cached.isEmpty() ? null : cached;
    }

    public static void setBio(int account, long userId, String value) {
        if (userId <= 0 || isSelf(account, userId)) {
            return;
        }
        String v = value == null ? "" : value.trim();
        SharedPreferences.Editor editor = prefs().edit();
        if (v.isEmpty()) {
            editor.remove(key(account, KEY_BIO_PREFIX, userId));
            bioCache.put(account + "_" + userId, "");
        } else {
            editor.putString(key(account, KEY_BIO_PREFIX, userId), v);
            bioCache.put(account + "_" + userId, v);
        }
        editor.apply();
    }

    public static boolean hasAny(int account, long userId) {
        return getName(account, userId) != null || getBio(account, userId) != null;
    }

    public static void clearAll(int account, long userId) {
        setName(account, userId, null);
        setBio(account, userId, null);
    }

    /** 账号切换或数据清理后调用，丢弃内存缓存 */
    public static void invalidateCache() {
        nameCache.clear();
        bioCache.clear();
    }
}
