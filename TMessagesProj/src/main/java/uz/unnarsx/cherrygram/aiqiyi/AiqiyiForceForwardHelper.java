package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

/**
 * [Aiqiyi] 强制破解转发（移植自芒果客户端）
 *
 * 原理：服务端会拒绝携带 fwd_from 转发的 noforwards 内容。
 * 因此受保护消息不能走 messages.forwardMessages，只能把内容「重新发送」一遍：
 *   - 文本 / 网页预览 → 直接取 message 文本重发
 *   - 照片 / 文档    → 取 file_reference 重新上传（会在对端生成一条新消息，不带转发来源）
 *
 * 注意：重新发送会丢失「转发自 XXX」的来源信息，这是服务端限制决定的，无法避免。
 */
public final class AiqiyiForceForwardHelper {

    private AiqiyiForceForwardHelper() {
    }

    /** 该消息是否处于「禁止转发」状态 */
    public static boolean isRestricted(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.messageOwner.noforwards) {
            return true;
        }
        long dialogId = messageObject.getDialogId();
        return MessagesController.getInstance(messageObject.currentAccount).isPeerNoForwards(dialogId);
    }

    /** 开关是否开启 */
    public static boolean isEnabled() {
        return uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig.INSTANCE.getAiqiyiForceForward();
    }

    /**
     * 是否可以走重新发送通道。
     * 这里排除掉无法重发的内容：阅后即焚、模糊预览、直播位置、通话、广告、故事等。
     */
    public static boolean canResend(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.messageOwner instanceof TLRPC.TL_message_secret) {
            return false;
        }
        if (messageObject.needDrawBluredPreview() || messageObject.isLiveLocation() || messageObject.isSponsored()) {
            return false;
        }
        if (messageObject.type == MessageObject.TYPE_PHONE_CALL) {
            return false;
        }
        // 纯文本、照片、文档、网页预览可重发
        TLRPC.MessageMedia media = MessageObject.getMedia(messageObject.messageOwner);
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return media.photo instanceof TLRPC.TL_photo;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument) {
            return media.document instanceof TLRPC.TL_document;
        }
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage) {
            return messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() > 0;
        }
        return false;
    }
}
