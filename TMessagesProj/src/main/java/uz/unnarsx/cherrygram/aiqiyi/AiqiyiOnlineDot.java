/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: 在线状态三色点（共用工具）。
 *
 * 蓝 = 在线；黄 = 刚离开（30 分钟内或服务端 Recently）；红 = 离开超过 30 分钟。
 * 返回 0 表示不显示圆点。
 *
 * 设计要点：
 *   - 纯静态、无状态，DialogCell 与 ChatMessageCell 共用同一套判定，
 *     避免两处逻辑漂移。
 *   - 不发起任何网络请求，只读本地缓存（MessagesController.getUser /
 *     onlinePrivacy），因此零流量、零风控。
 */
package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public final class AiqiyiOnlineDot {

    /** 在线（蓝） */
    public static final int DOT_ONLINE = 0xFF2196F3;
    /** 刚离开（黄） */
    public static final int DOT_RECENT = 0xFFFFC107;
    /** 离线（红） */
    public static final int DOT_OFFLINE = 0xFFF44336;

    /** 「刚离开」的时间阈值（秒），与服务端 Recently 档位的语义保持一致。 */
    private static final int RECENT_THRESHOLD = 30 * 60;

    private AiqiyiOnlineDot() {
    }

    /**
     * 计算某个用户应显示的状态点颜色。
     *
     * @param account 当前账号
     * @param user    目标用户；为 null / 自己 / 机器人 / 官方支持账号时返回 0
     * @return 颜色值，或 0 表示不显示
     *
     * 三色规则（与单聊统一）：
     *   - 蓝：服务端给出精确过期时间且未过期，或对方开了在线状态可见
     *   - 黄：服务端明确说 Recently，或精确时间戳在 30 分钟内
     *   - 红：明确的离线（精确时间戳超出 30 分钟）或隐私模糊档位
     *   - 0：无任何状态依据（null / Empty / 自己 / 机器人 / 支持账号）
     */
    public static int getDotColor(int account, TLRPC.User user) {
        if (user == null || user.self || user.bot) {
            return 0;
        }
        try {
            if (MessagesController.isSupportUser(user)) {
                return 0;
            }
        } catch (Throwable ignore) {
            // isSupportUser 在部分构建里可能不可用，忽略即可
        }

        final int now = ConnectionsManager.getInstance(account).getCurrentTime();
        final TLRPC.UserStatus status = user.status;
        if (status == null || status instanceof TLRPC.TL_userStatusEmpty) {
            return 0;
        }

        // 有精确过期时间且尚未过期 => 在线
        if (status.expires > now) {
            return DOT_ONLINE;
        }
        // 服务端标记为「对方开启了在线状态可见」的例外通道
        try {
            if (MessagesController.getInstance(account).onlinePrivacy.containsKey(user.id)) {
                return DOT_ONLINE;
            }
        } catch (Throwable ignore) {
        }

        // 服务端模糊档位：Recently => 黄
        if (status instanceof TLRPC.TL_userStatusRecently) {
            return DOT_RECENT;
        }

        // 有精确时间戳：用 30 分钟阈值判「刚离开」
        if (status instanceof TLRPC.TL_userStatusOffline) {
            final int diff = now - status.expires;
            return diff <= RECENT_THRESHOLD ? DOT_RECENT : DOT_OFFLINE;
        }

        // LastWeek / LastMonth / 其他未知 => 红
        return DOT_OFFLINE;
    }
}
