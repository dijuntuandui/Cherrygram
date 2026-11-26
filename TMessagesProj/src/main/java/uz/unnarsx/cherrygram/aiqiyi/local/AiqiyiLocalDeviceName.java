/*
 * [Aiqiyi] 本地设备名称覆盖（纯显示层）。
 *
 * 只影响本机 SessionsActivity / SessionBottomSheet 里「正在使用的设备」列表的
 * 名称与图标渲染，不发送任何网络请求，不修改服务器上的 authorization 数据。
 *
 * 关键设计：存三样而不是一样。
 *   displayName —— 用户填的名字，直接显示
 *   platformKey —— 官方 createDrawable() 用来推导图标的字符串
 *   typeLabel   —— 用户选的类型（iPhone/Android/...），用于回填选择器
 *
 * 为什么 platformKey 必须单独存：
 *   SessionCell.createDrawable() 是纯粹靠 platform 字符串 contains 关键字
 *   来选图标的（ios -> device_phone_ios，windows -> device_desktop_win）。
 *   如果只存一个名字，用户把 iPhone 改成 Windows，图标不会跟着变，
 *   就会出现「名字写 Windows、图标还是 iPhone」的错位。
 *   把 platformKey 一起存下来，渲染时喂给 createDrawable，
 *   名称和图标就永远同源、不可能对不上。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.ConcurrentHashMap;

public final class AiqiyiLocalDeviceName {

    private static final String PREF_NAME = "aqy_local_device_name";
    private static final String KEY_NAME_PREFIX = "devName_";
    private static final String KEY_PLATFORM_PREFIX = "devPlatform_";
    private static final String KEY_TYPE_PREFIX = "devType_";

    /**
     * 设备类型定义。顺序即选择器里的展示顺序。
     * platformKey 的取值必须能命中 SessionCell.createDrawable() 里的
     * contains 判断，否则会掉进 device_web_other 兜底变成地球图标。
     */
    public static final String[][] TYPES = {
            // {typeLabel,  platformKey,  modelHint}
            {"iPhone",   "iOS",        ""},
            {"iPad",     "iOS",        "iPad"},
            {"Android",  "Android",    ""},
            {"平板",     "Android",    "Tablet"},
            {"Windows",  "Windows",    ""},
            {"Mac",      "macOS",      ""},
            {"Linux",    "Linux",      ""},
            {"网页",     "Firefox",    ""},
            {"未知",     "?",          ""},
    };

    private static final ConcurrentHashMap<Long, String[]> cache = new ConcurrentHashMap<>();

    private AiqiyiLocalDeviceName() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, 0);
    }

    /**
     * 用 authorization.hash 做键。
     *
     * 为什么不用 device_model / platform 拼：
     * 同一账号可能有两个完全相同的设备（比如两台同型号手机），
     * 用设备信息拼键会导致改名时互相覆盖。hash 是服务器给每条
     * authorization 的唯一标识，不会撞。
     *
     * hash 为 0（理论上不该出现，但防御一下）时不落库、不生效，
     * 避免所有异常记录共用一个键集体串名。
     */
    private static boolean valid(long key) {
        // keyOf() 会把所有 hash 归一成非 0 的键，这里只防御真正的空键。
        return key != 0;
    }

    /**
     * [Aiqiyi] hash 归一：把「当前正在使用的会话」映射成一个稳定的伪键。
     *
     * 背景：account.getAuthorizations 返回的列表里，当前会话（flags & 1）那条
     * 服务端不下发 hash（实测恒为 0），它靠 flags 标记身份、不需要 hash 去操作。
     * 原实现用 hash 做键 + hash==0 直接拒，等于把唯一真正想改的那台挡在门外。
     *
     * 方案：hash != 0 原样用；hash == 0 时按 currentAccount 派生一个极负区间伪键。
     *   - 极负区间避让真实 hash（服务端 hash 是随机 long，几乎不可能落在这里）
     *   - 带上 account 维度，多账号之间互不串名
     *   - 存储层/缓存层仍是 Long，不用换类型
     */
    private static final long SELF_KEY_BASE = -0x4A51595F00000000L;

    public static long keyOf(long hash, int account) {
        return hash != 0 ? hash : (SELF_KEY_BASE - account);
    }

    public static boolean isSelfKey(long key) {
        return key <= SELF_KEY_BASE && key > SELF_KEY_BASE - 1000L;
    }

    private static String key(String prefix, long hash) {
        return prefix + hash;
    }

    /** 取显示名。无覆盖返回 null（调用方回落到原始 device_model）。 */
    public static String getName(long hash) {
        if (!valid(hash)) {
            return null;
        }
        String[] v = load(hash);
        return TextUtils.isEmpty(v[0]) ? null : v[0];
    }

    /** 取 platform 覆盖值，喂给 createDrawable() 决定图标。无覆盖返回 null。 */
    public static String getPlatform(long hash) {
        if (!valid(hash)) {
            return null;
        }
        String[] v = load(hash);
        return TextUtils.isEmpty(v[1]) ? null : v[1];
    }

    /** 取用户选的类型标签，用于重新打开选择器时回填。 */
    public static String getType(long hash) {
        if (!valid(hash)) {
            return null;
        }
        String[] v = load(hash);
        return TextUtils.isEmpty(v[2]) ? null : v[2];
    }

    /** 取 modelHint（iPad / Tablet），覆盖 device_model 让图标走平板分支。 */
    public static String getModelHint(long hash) {
        if (!valid(hash)) {
            return null;
        }
        String[] v = load(hash);
        return TextUtils.isEmpty(v[3]) ? null : v[3];
    }

    /**
     * 保存覆盖。typeLabel 必须是 TYPES 里的某一项，platformKey 由它推导，
     * 不接受调用方自选，从源头杜绝「名字和图标不同源」。
     *
     * @param name 显示名称，传空表示删除整条覆盖
     */
    public static void save(long hash, String name, String typeLabel) {
        if (!valid(hash)) {
            return;
        }
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            clear(hash);
            return;
        }
        String platform = null;
        String matchedType = null;
        String modelHint = null;
        for (String[] t : TYPES) {
            if (t[0].equals(typeLabel)) {
                platform = t[1];
                matchedType = t[0];
                modelHint = TextUtils.isEmpty(t[2]) ? null : t[2];
                break;
            }
        }
        if (platform == null) {
            // 类型非法，退回到 Android 而不是静默失败，至少图标是对的。
            platform = "Android";
            matchedType = "Android";
            modelHint = null;
        }
        prefs().edit()
                .putString(key(KEY_NAME_PREFIX, hash), n)
                .putString(key(KEY_PLATFORM_PREFIX, hash), platform)
                .putString(key(KEY_TYPE_PREFIX, hash), matchedType)
                .apply();
        cache.put(hash, new String[]{n, platform, matchedType, modelHint});
    }

    public static void clear(long hash) {
        if (!valid(hash)) {
            return;
        }
        prefs().edit()
                .remove(key(KEY_NAME_PREFIX, hash))
                .remove(key(KEY_PLATFORM_PREFIX, hash))
                .remove(key(KEY_TYPE_PREFIX, hash))
                .apply();
        cache.remove(hash);
    }

    /** 按类型标签给出配套的应用名，用于顶替 app_name 绕过 Cherrygram 图标硬编码。 */
    private static String appNameFor(String typeLabel) {
        if (typeLabel == null) {
            return "Telegram";
        }
        switch (typeLabel) {
            case "iPhone":
            case "iPad":
                return "Telegram iOS";
            case "Android":
            case "平板":
                return "Telegram Android";
            case "Windows":
                return "Telegram Desktop";
            case "Mac":
                return "Telegram macOS";
            case "Linux":
                return "Telegram Desktop";
            case "网页":
                return "Telegram Web";
            default:
                return "Telegram";
        }
    }

    public static boolean has(long hash) {
        return getName(hash) != null;
    }

    /**
     * 原地把本地覆盖写回一个已存在的 TL_authorization。
     *
     * 关键：不新建对象，只改 device_model / platform 两个字段。
     * 上次的教训是复制整个对象替换进渲染链路，一处字段不对就整行崩、列表全空。
     * 原地改字段的话，渲染侧拿到的还是同一个对象，它根本不知道被改过，
     * 出错面只剩「名字显示得对不对」，最坏也能改回来。
     *
     * @return 是否做了覆盖
     */
    public static boolean applyTo(TLRPC.TL_authorization session, int account) {
        if (session == null) {
            return false;
        }
        final long key = keyOf(session.hash, account);
        final String[] v = load(key);
        if (TextUtils.isEmpty(v[0])) {
            return false;
        }
        // 名称进 device_model：setSession 优先用它拼显示名。
        // 但平板档要额外带 modelHint，否则 createDrawable 认不出平板。
        final String modelHint = v[3];
        session.device_model = TextUtils.isEmpty(modelHint) ? v[0] : (v[0] + " " + modelHint);

        // platform 决定图标分支，必须是 TYPES 里的 platformKey。
        if (!TextUtils.isEmpty(v[1])) {
            session.platform = v[1];
        }

        // app_name 必须一起改。
        // createDrawable() 里有一句硬编码：
        //     if (session.app_name.contains("Cherrygram")) 图标强推成 Cherrygram logo
        // 本客户端上报的 app_name 就是 Cherrygram，不改这一行，platform 改什么都没用，
        // 图标永远停在 cg_logo。详情页那行「应用名 版本号」也会一起变成对应平台的名字，
        // 反而比原来更贴合。不碰 app_version，版本号保持真实。
        session.app_name = appNameFor(v[2]);

        return true;
    }

    /**
     * 读缓存。命中直接返回；未命中读盘并落缓存（含空值，避免反复 miss）。
     * 返回长度固定 4 的数组：{name, platform, typeLabel, modelHint}
     */
    private static String[] load(long hash) {
        String[] cached = cache.get(hash);
        if (cached != null) {
            return cached;
        }
        SharedPreferences p = prefs();
        String name = p.getString(key(KEY_NAME_PREFIX, hash), "");
        String platform = p.getString(key(KEY_PLATFORM_PREFIX, hash), "");
        String type = p.getString(key(KEY_TYPE_PREFIX, hash), "");
        String modelHint = null;
        if (!TextUtils.isEmpty(type)) {
            for (String[] t : TYPES) {
                if (t[0].equals(type)) {
                    modelHint = TextUtils.isEmpty(t[2]) ? null : t[2];
                    break;
                }
            }
        }
        String[] v = {
                name == null ? "" : name,
                platform == null ? "" : platform,
                type == null ? "" : type,
                modelHint
        };
        cache.put(hash, v);
        return v;
    }

    public static void invalidateCache() {
        cache.clear();
    }
}
