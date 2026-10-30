/*
 * 爱琦艺客户端 - 对白气泡 · 剧本匹配引擎
 *
 * 规则（用户确认版）：
 *   1. 按发送者名字匹配剧本角色，匹配不上就跳过这条消息（不消费剧本）；
 *   2. 连续 3 条匹配失败，自动停止本会话的剧本播放；
 *   3. 剧本用完后，后续消息显示原文，不循环。
 *
 * 整个过程只是把分配结果记在内存表里，渲染时读取；
 * 不改 messageText 之外任何字段，不回写服务器。
 */
package uz.unnarsx.cherrygram.aiqiyi.dialogue;

import android.text.TextUtils;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiqiyiDialogueEngine {

    /** 连续失败上限，达到即停止（用户指定 3） */
    private static final int MAX_MISS_STREAK = 3;

    /**
     * 本会话的失败计数。
     * key = account|dialogId，value = 连续匹配失败次数。
     * 一旦停止，会在 STOPPED 里留标记，避免反复重试。
     */
    private static final Map<String, Integer> MISS_STREAK = new HashMap<>();
    /** 已停止的会话 */
    private static final Map<String, Boolean> STOPPED = new HashMap<>();

    private static String key(int account, long dialogId) {
        return account + "|" + dialogId;
    }

    /** 是否因为连续失败而停摆 */
    public static boolean isStopped(int account, long dialogId) {
        synchronized (STOPPED) {
            Boolean v = STOPPED.get(key(account, dialogId));
            return v != null && v;
        }
    }

    public static void clearStopped(int account, long dialogId) {
        String k = key(account, dialogId);
        synchronized (STOPPED) {
            STOPPED.remove(k);
        }
        synchronized (MISS_STREAK) {
            MISS_STREAK.remove(k);
        }
    }

    public static void clearAllStopped() {
        synchronized (STOPPED) {
            STOPPED.clear();
        }
        synchronized (MISS_STREAK) {
            MISS_STREAK.clear();
        }
    }

    /** 当前失败计数，设置页展示用 */
    public static int getMissStreak(int account, long dialogId) {
        synchronized (MISS_STREAK) {
            Integer v = MISS_STREAK.get(key(account, dialogId));
            return v == null ? 0 : v;
        }
    }

    // ======================================================================
    // 主流程
    // ======================================================================

    /**
     * 尝试为一条消息分配台词。
     *
     * @return 分配到的台词；null 表示没分配
     */
    public static String assign(int account, int messageId, String senderName, long dialogId) {
        if (messageId <= 0) {
            return null;
        }
        // 已经分配过，直接返回缓存
        String cached = AiqiyiScriptStore.getAssigned(account, dialogId, messageId);
        if (cached != null) {
            return cached;
        }
        if (!AiqiyiScriptStore.isEnabled()) {
            return null;
        }
        if (!AiqiyiScriptStore.inScope(dialogId)) {
            return null;
        }
        if (isStopped(account, dialogId)) {
            return null;
        }
        if (TextUtils.isEmpty(senderName)) {
            return null;
        }
        List<AiqiyiScriptStore.Line> lines = AiqiyiScriptStore.getLines();
        if (lines.isEmpty()) {
            return null;
        }
        int cursor = AiqiyiScriptStore.getCursor(account, dialogId);
        if (cursor >= lines.size()) {
            // 剧本用完了，往后一律显示原文
            return null;
        }
        AiqiyiScriptStore.Line line = lines.get(cursor);
        if (!roleMatches(line.role, senderName)) {
            onMiss(account, dialogId);
            return null;
        }
        // 命中：消费这一行
        AiqiyiScriptStore.setCursor(account, dialogId, cursor + 1);
        onHit(account, dialogId);
        AiqiyiScriptStore.putAssigned(account, dialogId, messageId, line.text);
        return line.text;
    }

    /**
     * 角色名匹配。
     * 包容三种常见写法，避免用户为了对齐而改剧本：
     *   完全相等 / 剧本角色是发送者名的子串（如剧本写「小明」对方叫「小明同学」）
     */
    private static boolean roleMatches(String role, String senderName) {
        if (role == null || senderName == null) {
            return false;
        }
        String a = role.trim();
        String b = senderName.trim();
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        // 忽略大小写
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        // 剧本角色是发送者名的子串，且长度不小于 2，避免单字误匹配
        return a.length() >= 2 && b.contains(a);
    }

    private static void onHit(int account, long dialogId) {
        synchronized (MISS_STREAK) {
            MISS_STREAK.remove(key(account, dialogId));
        }
    }

    private static void onMiss(int account, long dialogId) {
        String k = key(account, dialogId);
        int streak;
        synchronized (MISS_STREAK) {
            Integer v = MISS_STREAK.get(k);
            streak = (v == null ? 0 : v) + 1;
            MISS_STREAK.put(k, streak);
        }
        if (streak >= MAX_MISS_STREAK) {
            synchronized (STOPPED) {
                STOPPED.put(k, true);
            }
        }
    }

    /** 该会话的停止原因，设置页展示用 */
    public static String getStatusText(int account, long dialogId) {
        if (!AiqiyiScriptStore.isEnabled()) {
            return "已关闭";
        }
        if (!AiqiyiScriptStore.inScope(dialogId)) {
            return "不在生效会话";
        }
        List<AiqiyiScriptStore.Line> lines = AiqiyiScriptStore.getLines();
        if (lines.isEmpty()) {
            return "剧本为空";
        }
        int cursor = AiqiyiScriptStore.getCursor(account, dialogId);
        if (cursor >= lines.size()) {
            return "已播完";
        }
        if (isStopped(account, dialogId)) {
            return "已停止（连续 " + MAX_MISS_STREAK + " 条对不上）";
        }
        return "播放中 " + cursor + "/" + lines.size();
    }

    // ======================================================================
    // 发送者名片
    // ======================================================================

    /**
     * 取消息发送者的显示名，用于跟剧本角色对齐。
     * 群聊里取发送人名字；私聊里取对方名字；自己的消息取「我」+ 本机名。
     */
    public static String resolveSenderName(int account, TLRPC.Message message) {
        if (message == null) {
            return null;
        }
        try {
            MessagesController controller = MessagesController.getInstance(account);
            // 自己的消息
            if (message.out) {
                int myId = UserConfig.getInstance(account).getClientUserId();
                TLRPC.User me = controller.getUser(Long.valueOf(myId));
                if (me != null && !TextUtils.isEmpty(UserObject.getUserName(me))) {
                    return UserObject.getUserName(me);
                }
                return null;
            }
            // 来自用户
            if (message.from_id instanceof TLRPC.TL_peerUser) {
                long uid = message.from_id.user_id;
                TLRPC.User user = controller.getUser(Long.valueOf(uid));
                if (user != null) {
                    return UserObject.getUserName(user);
                }
                return null;
            }
            // 来自频道/群
            if (message.from_id instanceof TLRPC.TL_peerChannel) {
                long cid = message.from_id.channel_id;
                TLRPC.Chat chat = controller.getChat(Long.valueOf(cid));
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
                return null;
            }
            // 老字段兜底
            if (message.from_id instanceof TLRPC.TL_peerChat) {
                long cid = message.from_id.chat_id;
                TLRPC.Chat chat = controller.getChat(Long.valueOf(cid));
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
                return null;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}