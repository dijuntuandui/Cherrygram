/**
 * 爱琦艺客户端 - 本地资料页伪装 Helper（仅自己可见）
 *
 * 覆盖资料页三行的显示值：手机号 / 用户名 / User ID。
 * 只改本机渲染，不改服务器数据、不发请求。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.text.TextUtils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;

public final class AiqiyiLocalProfileHelper {

    private AiqiyiLocalProfileHelper() {
    }

    private static boolean zoneOn() {
        return AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled();
    }

    // ============ 手机号 ============

    /** 是否有生效中的本地自定义手机号 */
    public static boolean hasPhone() {
        return zoneOn()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalPhoneOverride()
                && !TextUtils.isEmpty(AiqiyiLocalDataConfig.INSTANCE.getLocalPhoneText());
    }

    /** 取本地自定义手机号；无则返回 null */
    public static String getPhone() {
        return hasPhone() ? AiqiyiLocalDataConfig.INSTANCE.getLocalPhoneText() : null;
    }

    // ============ 用户名 ============

    /** 是否有生效中的本地自定义用户名 */
    public static boolean hasUsername() {
        return zoneOn()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalUsernameOverride()
                && !TextUtils.isEmpty(AiqiyiLocalDataConfig.INSTANCE.getLocalUsernameText());
    }

    /** 取本地自定义用户名（不带 @）；无则返回 null */
    public static String getUsername() {
        return hasUsername() ? AiqiyiLocalDataConfig.INSTANCE.getLocalUsernameText() : null;
    }

    /**
     * 取本地自定义用户名，已带 @ 前缀；无则返回 null。
     */
    public static String getUsernameWithAt() {
        String name = getUsername();
        if (name == null) {
            return null;
        }
        return name.startsWith("@") ? name : "@" + name;
    }

    // ============ User ID ============

    /** 是否有生效中的本地自定义 User ID */
    public static boolean hasUserId() {
        return zoneOn()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalUserIdOverride()
                && !TextUtils.isEmpty(AiqiyiLocalDataConfig.INSTANCE.getLocalUserIdText());
    }

    /**
     * 取本地自定义 User ID，按原版同样的空格分组格式排版；无则返回 null。
     * 例：123456789 -> "123 456 789"
     */
    public static String getFormattedUserId() {
        if (!hasUserId()) {
            return null;
        }
        String raw = AiqiyiLocalDataConfig.INSTANCE.getLocalUserIdText().replace(" ", "").trim();
        try {
            DecimalFormat df = new DecimalFormat("#,###", new DecimalFormatSymbols(Locale.US) {{
                setGroupingSeparator(' ');
            }});
            return df.format(Long.parseLong(raw));
        } catch (Throwable ignore) {
            // 非纯数字就原样返回
            return raw;
        }
    }

    // ============ 写入 ============

    public static void setPhone(String value) {
        AiqiyiLocalDataConfig cfg = AiqiyiLocalDataConfig.INSTANCE;
        if (TextUtils.isEmpty(value)) {
            cfg.setLocalPhoneOverride(false);
            cfg.setLocalPhoneText("");
        } else {
            cfg.setLocalPhoneText(value.trim());
            cfg.setLocalPhoneOverride(true);
        }
    }

    public static void setUsername(String value) {
        AiqiyiLocalDataConfig cfg = AiqiyiLocalDataConfig.INSTANCE;
        if (TextUtils.isEmpty(value)) {
            cfg.setLocalUsernameOverride(false);
            cfg.setLocalUsernameText("");
        } else {
            String v = value.trim();
            if (v.startsWith("@")) {
                v = v.substring(1);
            }
            cfg.setLocalUsernameText(v);
            cfg.setLocalUsernameOverride(true);
        }
    }

    public static void setUserId(String value) {
        AiqiyiLocalDataConfig cfg = AiqiyiLocalDataConfig.INSTANCE;
        if (TextUtils.isEmpty(value)) {
            cfg.setLocalUserIdOverride(false);
            cfg.setLocalUserIdText("");
        } else {
            // 随便填，不强制数字；纯数字时显示会自动加空格分组
            cfg.setLocalUserIdText(value.trim());
            cfg.setLocalUserIdOverride(true);
        }
    }
}
