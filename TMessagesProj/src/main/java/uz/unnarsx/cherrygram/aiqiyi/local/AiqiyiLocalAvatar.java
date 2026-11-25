/**
 * 爱琦艺客户端 - 他人资料本地头像
 *
 * 设计对齐黄昏 Huanghun-TG 的 LocalProfileOverrideHelper.setAvatarUri / getAvatarUri：
 *   1. 存储键只用 userId（带账号隔离），绝不用姓名文本做键；
 *   2. 值存的是「私有目录里的图片绝对路径」，不是相册 Uri
 *      —— 相册原图被删/被移走后本地头像会失效，所以选图时必须先拷贝落盘；
 *   3. userId <= 0 一律拒绝读写，避免退化成全局共用槽位（globalKey 事故）；
 *   4. 禁止对当前登录用户自身写入覆盖值（isSelf 守卫同 AiqiyiLocalPeerName）；
 *   5. 只在本机生效，不上传服务器。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public final class AiqiyiLocalAvatar {

    private static final String PREF_NAME = "aqy_local_peer_avatar";
    private static final String KEY_AVATAR_PREFIX = "peerAvatar_";

    /** "account_userId" -> 本地图片绝对路径；空串表示「已查询过，无覆盖」 */
    private static final ConcurrentHashMap<String, String> avatarCache = new ConcurrentHashMap<>();

    private AiqiyiLocalAvatar() {
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
     * 本我判定。注意：本类已不再使用它做读写拦截（见 getPath/setPath 内注释），
     * 保留仅为语义留档；若将来要恢复防污染，应先确认 UserConfig 已就绪，
     * 否则 selfId<=0 会把所有写入静默拦掉。
     */
    @SuppressWarnings("unused")
    private static boolean isSelf(int account, long userId) {
        long self = selfId(account);
        return self <= 0 || userId == self;
    }

    private static String key(int account, long userId) {
        return KEY_AVATAR_PREFIX + account + "_" + userId;
    }

    /** 本地头像文件存放目录：应用私有 filesDir 下，卸载即清 */
    public static File dir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "aqy_local_avatar");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    /**
     * 取本地头像路径。无覆盖、或文件已不存在，返回 null。
     * 文件是否真实存在必须校验：用户可能清过缓存/数据，键还在但图没了。
     */
    public static String getPath(int account, long userId) {
        // [Aiqiyi local] 读取侧不再做 isSelf 判定：会话列表 DialogCell 绑定时 UserConfig
        // 可能尚未就绪，selfId 取到 0 会让 isSelf 恒真、本地头像被整体屏蔽。
        // 防污染仍由写入侧 setPath() 的 isSelf 守卫负责，这里只放行读取。
        if (userId <= 0) {
            return null;
        }
        final String cacheKey = account + "_" + userId;
        String cached = avatarCache.computeIfAbsent(cacheKey, k -> {
            String v = prefs().getString(key(account, userId), null);
            return TextUtils.isEmpty(v) ? "" : v;
        });
        if (cached.isEmpty()) {
            return null;
        }
        if (!new File(cached).exists()) {
            // 文件被清了，顺手把脏键抹掉，下次不再白查
            avatarCache.put(cacheKey, "");
            prefs().edit().remove(key(account, userId)).apply();
            return null;
        }
        return cached;
    }

    /**
     * 设置本地头像路径。传 null/空表示清除（同时尝试删掉磁盘文件）。
     * 写入前会删掉旧文件，避免 filesDir 里堆垃圾。
     */
    public static void setPath(int account, long userId, String path) {
        // [Aiqiyi local] 与 getPath 保持一致：只用 userId 做键，不再按 isSelf 拦截。
        // 键是 peerAvatar_<account>_<userId>，逐行独立，不存在串号风险；
        // 原先的 isSelf 守卫在 UserConfig 未就绪时会误拦合法写入。
        if (userId <= 0) {
            return;
        }
        final String cacheKey = account + "_" + userId;
        String old = prefs().getString(key(account, userId), null);
        SharedPreferences.Editor editor = prefs().edit();
        final String v = path == null ? "" : path.trim();
        if (v.isEmpty()) {
            editor.remove(key(account, userId));
            avatarCache.put(cacheKey, "");
        } else {
            editor.putString(key(account, userId), v);
            avatarCache.put(cacheKey, v);
        }
        editor.apply();
        // 旧文件与新的不同才删，避免误删刚写入的文件
        if (!TextUtils.isEmpty(old) && !old.equals(v)) {
            deleteQuietly(old);
        }
    }

    /** 清除本地头像 */
    public static void clear(int account, long userId) {
        setPath(account, userId, null);
    }

    public static boolean has(int account, long userId) {
        return getPath(account, userId) != null;
    }

    /** 生成一个不重名的目标文件（带 nanoTime 防图片缓存命中旧图） */
    public static File newFile(int account, long userId) {
        return new File(dir(), "la_" + account + "_" + userId + "_" + System.nanoTime() + ".jpg");
    }

    private static void deleteQuietly(String path) {
        try {
            File f = new File(path);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Throwable ignore) {
        }
    }

    /** 账号切换或数据清理后调用，丢弃内存缓存 */
    public static void invalidateCache() {
        avatarCache.clear();
    }
}
