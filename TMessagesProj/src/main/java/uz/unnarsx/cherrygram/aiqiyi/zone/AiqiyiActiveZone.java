package uz.unnarsx.cherrygram.aiqiyi.zone;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig;

/**
 * [Aiqiyi] 活跃专区 - 新消息自动点反应
 *
 * 听到 didReceiveNewMessages 后逐条过滤，符合条件就替用户发一个 reaction。
 * 只发 reaction，不改消息、不发消息。
 *
 * 过滤链：
 *   1. 开关打开
 *   2. 消息 id > 0，非服务消息，非空消息
 *   3. 方向：0=只对别人 1=只对自己 2=全部
 *   4. 会话范围：全部 / 仅白名单
 *   5. 目标：全部 / 仅指定用户
 *   6. 自己还没点过
 *
 * 发送失败静默放弃，不重试。
 */
public final class AiqiyiActiveZone {

    private static final Set<String> HANDLED = new HashSet<>();

    private static volatile long lastSendAt = 0;
    private static final long MIN_INTERVAL_MS = 1200;

    private AiqiyiActiveZone() {}

    public static void onNewMessages(int account, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        CherrygramAppearanceConfig cfg = CherrygramAppearanceConfig.INSTANCE;
        if (!cfg.getAiqiyiActiveZone()) {
            return;
        }
        for (MessageObject mo : messages) {
            try {
                consider(account, mo, cfg);
            } catch (Throwable ignore) {
            }
        }
    }

    private static void consider(int account, MessageObject mo, CherrygramAppearanceConfig cfg) {
        if (mo == null || mo.messageOwner == null) {
            return;
        }
        final TLRPC.Message owner = mo.messageOwner;

        if (owner.id <= 0) {
            return;
        }
        if (owner.action != null) {
            return;
        }
        boolean hasContent = !TextUtils.isEmpty(owner.message)
                || owner.media != null
                || owner.reactions != null;
        if (!hasContent) {
            return;
        }

        final long dialogId = mo.getDialogId();
        final boolean self = mo.isOutOwner();

        int direction = cfg.getAiqiyiActiveZoneDirection();
        if (direction == 0 && self) return;
        if (direction == 1 && !self) return;

        if (cfg.getAiqiyiActiveZonePeerScope() == 1) {
            Set<Long> allow = parseLongSet(cfg.getAiqiyiActiveZonePeerWhitelist());
            if (!allow.contains(dialogId)) {
                return;
            }
        }

        if (cfg.getAiqiyiActiveZoneTargetMode() == 1) {
            Set<Long> users = parseLongSet(cfg.getAiqiyiActiveZoneTargetUsers());
            long fromId = mo.getSenderId();
            if (fromId == 0) {
                return;
            }
            if (!users.contains(fromId)) {
                return;
            }
        }

        String emoji = normalizeEmoji(cfg.getAiqiyiActiveZoneEmoji());
        if (alreadyReacted(owner, emoji)) {
            return;
        }

        String dedupKey = dialogId + "_" + owner.id;
        synchronized (HANDLED) {
            if (HANDLED.contains(dedupKey)) {
                return;
            }
            HANDLED.add(dedupKey);
            if (HANDLED.size() > 2000) {
                HANDLED.clear();
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastSendAt < MIN_INTERVAL_MS) {
            return;
        }
        lastSendAt = now;

        sendReaction(account, dialogId, owner.id, emoji);
    }

    private static boolean alreadyReacted(TLRPC.Message owner, String emoji) {
        if (owner.reactions == null || owner.reactions.results == null) {
            return false;
        }
        for (TLRPC.ReactionCount rc : owner.reactions.results) {
            if (rc == null || rc.reaction == null) {
                continue;
            }
            // 只要这条消息上已经有「我」投过的任意反应，就不再追加
            if (!rc.chosen) {
                continue;
            }
            if (rc.reaction instanceof TLRPC.TL_reactionEmoji) {
                String e = ((TLRPC.TL_reactionEmoji) rc.reaction).emoticon;
                if (e != null && e.equals(emoji)) {
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private static void sendReaction(int account, long dialogId, int messageId, String emoji) {
        try {
            AccountInstance acc = AccountInstance.getInstance(account);
            MessagesController mc = acc.getMessagesController();

            TLRPC.InputPeer peer = mc.getInputPeer(dialogId);
            if (peer == null || peer instanceof TLRPC.TL_inputPeerEmpty) {
                return;
            }

            TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
            req.peer = peer;
            req.msg_id = messageId;
            req.flags |= 1;
            req.add_to_recent = false;

            TLRPC.TL_reactionEmoji re = new TLRPC.TL_reactionEmoji();
            re.emoticon = emoji;
            req.reaction.add(re);

            acc.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    mc.processUpdates((TLRPC.Updates) response, false);
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private static String normalizeEmoji(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "\uD83D\uDC4D";
        }
        String t = raw.trim();
        return t.isEmpty() ? "\uD83D\uDC4D" : t;
    }

    private static Set<Long> parseLongSet(String raw) {
        Set<Long> out = new HashSet<>();
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    public static String describe() {
        CherrygramAppearanceConfig cfg = CherrygramAppearanceConfig.INSTANCE;
        StringBuilder sb = new StringBuilder();
        sb.append(normalizeEmoji(cfg.getAiqiyiActiveZoneEmoji()));
        int dir = cfg.getAiqiyiActiveZoneDirection();
        sb.append(dir == 0 ? " / 别人" : dir == 1 ? " / 自己" : " / 全部");
        if (cfg.getAiqiyiActiveZoneTargetMode() == 1) {
            Set<Long> users = parseLongSet(cfg.getAiqiyiActiveZoneTargetUsers());
            sb.append(" / ").append(users.size()).append(" 位用户");
        }
        return sb.toString();
    }
}
