/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Saved Deleted Messages (anti-revoke) skeleton.
 * NOTE: the storage / database layer is NOT implemented yet.
 *       This class only exposes the API surface so the UI toggles can be wired
 *       up and compiled. Hook points (MessagesStorage / ChatActivity) come later.
 */
package uz.unnarsx.cherrygram.aiqiyi;

import uz.unnarsx.cherrygram.core.configs.CherrygramMessagesConfig;

public class AiqiyiSaveDeleted {

    /** Whether revoked messages should be retained locally. */
    public static boolean isEnabled() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether retained deleted messages should be rendered translucent. */
    public static boolean isTranslucent() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslucentDeleted();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether the default "deleted" text mark should be replaced by an icon. */
    public static boolean useIcon() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiUseDeletedIcon();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * The text mark to render for a deleted message.
     *
     * TODO(saved-deleted): once the storage layer exists, this should also be
     *       used by the message cell renderer. For now it only serves the
     *       settings preview.
     */
    public static String getDeletedMark() {
        try {
            String custom = CherrygramMessagesConfig.INSTANCE.getAiqiyiCustomDeletedMark();
            if (custom != null && !custom.trim().isEmpty()) {
                return custom;
            }
        } catch (Throwable ignored) {
        }
        return "已删除";
    }

    /**
     * Called when a message is about to be deleted/revoked.
     *
     * TODO(saved-deleted): persist the message body here instead of dropping it.
     *       Hook into MessagesStorage.deleteMessage / processDeletedMessages.
     *
     * @return true if the message was kept locally, false if not handled.
     */
    public static boolean onMessageDeleted(int accountId, long dialogId, int messageId) {
        if (!isEnabled()) {
            return false;
        }
        return false;
    }

    private AiqiyiSaveDeleted() {
    }
}