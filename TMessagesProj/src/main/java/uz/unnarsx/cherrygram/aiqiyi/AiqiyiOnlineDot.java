/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: 在线状态圆点（判定与颜色，全部集中在这一次，绘制在调用方）。
 *
 * 五档语义，按「离线时长」分色：
 *   绿     = 在线
 *   亮黄   = 离线 15 分钟内
 *   橙     = 离线 30 分钟内
 *   红     = 离线 60 分钟内
 *   不显示 = 离线超过 60 分钟，或拿不到任何状态依据
 *
 * 设计要点：
 *   - 纯静态、无状态，列表页 / 聊天页 / 群头像汇总共用同一套判定，避免逻辑漂移。
 *   - 不发起任何网络请求，只读本地缓存（MessagesController.getUser /
 *     getChatFull / onlinePrivacy），因此零流量、零风控。
 *   - 判定和绘制严格分离：这里只返回颜色值，0 表示不画。
 */
package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public final class AiqiyiOnlineDot {

    /** 在线（绿） */
    public static final int DOT_ONLINE = 0xFF4CAF50;
    /** 离线 15 分钟内（亮黄） */
    public static final int DOT_JUST_LEFT = 0xFFEAEA1E;
    /** 离线 30 分钟内（橙） */
    public static final int DOT_AWAY = 0xFFEA841E;
    /** 离线 60 分钟内（红） */
    public static final int DOT_OFFLINE = 0xFFEA1E1E;

    /** 15 分钟 */
    private static final int AWAY_1 = 15 * 60;
    /** 30 分钟 */
    private static final int AWAY_2 = 30 * 60;
    /** 60 分钟 */
    private static final int AWAY_3 = 60 * 60;
    /** 成员超过这个数量就不做在线统计，避免列表滑动卡顿 */
    private static final int MAX_MEMBER_SCAN = 200;

    private AiqiyiOnlineDot() {
    }

    /**
     * 计算某个用户应显示的状态点颜色。
     *
     * @param account 当前账号
     * @param user    目标用户；为 null / 自己 / 机器人 / 官方支持账号时返回 0
     * @return 颜色值，或 0 表示不显示
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
        final TLRPC.UserStatus status = user.status;
        if (status == null || status instanceof TLRPC.TL_userStatusEmpty) {
            return 0;
        }
        final int now = ConnectionsManager.getInstance(account).getCurrentTime();

        // 服务端标记为「对方开启了在线状态可见」的例外通道
        if (status.expires <= 0) {
            try {
                if (MessagesController.getInstance(account).onlinePrivacy.containsKey(user.id)) {
                    return DOT_ONLINE;
                }
            } catch (Throwable ignore) {
            }
        }

        // 用「距离上次活跃过了多久」分档：diff > 0 表示尚未过期，即在线
        final int diff = status.expires - now;
        if (diff > 0) {
            return DOT_ONLINE;
        }
        if (status instanceof TLRPC.TL_userStatusRecently) {
            return DOT_JUST_LEFT;
        }
        final int left = -diff;
        if (left <= AWAY_1) {
            return DOT_JUST_LEFT;
        }
        if (left <= AWAY_2) {
            return DOT_AWAY;
        }
        if (left <= AWAY_3) {
            return DOT_OFFLINE;
        }
        // 超过一小时或未知档位（LastWeek / LastMonth）不再显示
        return 0;
    }

    /**
     * 群组 / 频道头像用的状态色。
     *
     * 群头像代表的是「群」而不是某个人，所以不画某个成员的精确状态，
     * 只回答一个问题：这个群现在有没有人在线。
     *
     * 数据来源按可靠性排序：
     *   1. 群成员列表（fullChats 缓存）里在线的人数 —— 最准，但只有打开过群资料才有
     *   2. 最近一条消息的发送者 —— 列表项自带，任何时候都在，但需要排除机器人
     *
     * @param account 当前账号
     * @param chat    会话对应的群/频道；为 null 返回 0
     * @param lastMsg 列表项上最后一条消息，可为 null
     * @return 颜色值，或 0 表示不显示
     */
    public static int getChatStatusColor(int account, TLRPC.Chat chat, MessageObject lastMsg) {
        if (chat == null) {
            return 0;
        }
        // 1. 成员汇总优先
        final int countColor = getChatDotColor(account, chat.id);
        if (countColor != 0) {
            return countColor;
        }
        // 2. 退回看最近发言者（必须是真人，客服机器人永远不该给群点灯）
        if (lastMsg != null && lastMsg.messageOwner != null) {
            try {
                final long fromId = lastMsg.getFromChatId();
                if (fromId > 0) {
                    final TLRPC.User sender = MessagesController.getInstance(account).getUser(fromId);
                    if (sender != null && !sender.bot && !sender.self) {
                        return getDotColor(account, sender);
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        return 0;
    }

    /**
     * 统计群内在线人数，再换算成颜色。
     *
     * @return 颜色值；成员列表未缓存、人数过多或没人时返回 0
     */
    public static int getChatDotColor(int account, long chatId) {
        final int onlineCount = getChatOnlineCount(account, chatId);
        if (onlineCount <= 0) {
            return 0;
        }
        // 人数越多越亮，1~2 人用亮黄，3 人以上用绿
        return onlineCount >= 3 ? DOT_ONLINE : DOT_JUST_LEFT;
    }

    /**
     * 统计群内在线人数。
     *
     * @return 在线成员数量；0 表示没人或拿不到数据
     */
    public static int getChatOnlineCount(int account, long chatId) {
        try {
            final MessagesController controller = MessagesController.getInstance(account);
            final TLRPC.Chat chat = controller.getChat(chatId);
            if (chat == null) {
                return 0;
            }
            if (chat.participants_count > MAX_MEMBER_SCAN) {
                return 0;
            }
            final TLRPC.ChatFull full = controller.getChatFull(chat.id);
            if (full == null || full.participants == null || full.participants.participants == null
                    || full.participants.participants.isEmpty()) {
                // 成员列表还没缓存到本地，就先不画，不做任何网络请求
                return 0;
            }
            final int now = ConnectionsManager.getInstance(account).getCurrentTime();
            int count = 0;
            for (int i = 0; i < full.participants.participants.size(); i++) {
                final TLRPC.ChatParticipant p = full.participants.participants.get(i);
                if (p == null) {
                    continue;
                }
                final TLRPC.User u = controller.getUser(p.user_id);
                if (u == null || u.bot || u.self) {
                    continue;
                }
                if (u.status != null && u.status.expires > now) {
                    count++;
                } else if (u.status != null && u.status.expires <= 0
                        && controller.onlinePrivacy.containsKey(u.id)) {
                    count++;
                }
            }
            return count;
        } catch (Throwable ignore) {
            return 0;
        }
    }
}
