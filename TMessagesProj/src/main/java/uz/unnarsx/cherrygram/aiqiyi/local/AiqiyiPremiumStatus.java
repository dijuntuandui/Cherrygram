package uz.unnarsx.cherrygram.aiqiyi.local;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

/**
 * [Aiqiyi] 本地会员状态行后缀
 *
 * 判定口径 = 乙 + 丙，即直接复用 UserConfig#isPremium()：
 *   乙：本地功能专区总闸 AQY_LocalZone_Enabled + 本地高级版 AQY_LocalPremium_Override 同时开启
 *   丙：账号本身真实 premium（user.premium）
 * 任一成立即视为会员。以后 isPremium() 规则调整，这里自动跟随，
 * 不会出现「会员角标亮了、状态行却没字」的错位。
 *
 * 显示范围：仅本人（user.id == clientUserId），他人状态行一律原样返回。
 * 追加内容：" | TG Premium"。状态主体（在线 / 最近曾上线 / 一周内 / 一月内 / 具体时间）
 * 完全沿用官方文案，此处不做任何写死。
 */
public final class AiqiyiPremiumStatus {

    private static final String SUFFIX = " | TG Premium";

    private AiqiyiPremiumStatus() {
    }

    public static String append(int currentAccount, TLRPC.User user, String base) {
        if (base == null || base.length() == 0) {
            return base;
        }
        if (user == null) {
            return base;
        }
        try {
            final UserConfig userConfig = UserConfig.getInstance(currentAccount);
            if (userConfig == null) {
                return base;
            }
            // 丙（真会员）与乙（专区总闸 + 本地高级版）都收敛在 isPremium() 里
            if (!userConfig.isPremium()) {
                return base;
            }
            if (user.id != userConfig.clientUserId) {
                return base;
            }
        } catch (Exception e) {
            return base;
        }
        if (base.endsWith(SUFFIX)) {
            return base;
        }
        return base + SUFFIX;
    }
}
