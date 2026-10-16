/**
 * 爱琦艺客户端 - 长按菜单「替换显示文字 / 伪装发送时间」注入
 *
 * 参考 Cherrygram 的 CGMessageMenuInjector 机制：
 * 独立往 popupLayout 里追加 Cell，不碰原生 options 数组，
 * 因此不会干扰原有菜单项。
 *
 * 行为：
 *   长按文本消息   → 菜单多出「替换显示文字」
 *   已替换过的消息 → 额外多出「恢复原文」
 *   长按任意消息   → 菜单多出「伪装发送时间」
 *   已伪装过的消息 → 额外多出「恢复原始时间」
 * 设置只作用于本机渲染，不发送、不上传。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.text.InputType;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;

public class AiqiyiMessageMenuInjector {

    /**
     * 往长按菜单里追加爱琦艺条目。
     *
     * @param chatActivity   当前聊天页
     * @param popupLayout    菜单容器
     * @param selectedObject 长按选中的消息
     */
    public static void showReplaceItems(ChatActivity chatActivity,
                                        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout,
                                        MessageObject selectedObject) {
        if (chatActivity == null || popupLayout == null || selectedObject == null
                || selectedObject.messageOwner == null) {
            return;
        }
        if (chatActivity.getParentActivity() == null) {
            return;
        }

        final long chatId = selectedObject.messageOwner.dialog_id;
        final int messageId = selectedObject.messageOwner.id;

        final boolean canReplaceText = AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalMessageOverride()
                && selectedObject.messageOwner.message != null
                && selectedObject.messageOwner.message.length() > 0;

        final boolean canFakeTime = AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOverride();

        if (!canReplaceText && !canFakeTime) {
            return;
        }

        final boolean hasReplace = AiqiyiMessageReplaceStore.has(chatId, messageId);
        final boolean hasTime = AiqiyiMessageReplaceStore.hasTime(chatId, messageId);

        LinearLayout sections = new LinearLayout(chatActivity.getParentActivity());
        sections.setOrientation(LinearLayout.VERTICAL);

        // ---- 分隔线 ----
        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(chatActivity.getContext(), chatActivity.getResourceProvider());
        gap.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        sections.addView(gap);

        // ---- 替换显示文字（仅文本消息） ----
        if (canReplaceText) {
            ActionBarMenuSubItem replaceCell = new ActionBarMenuSubItem(chatActivity.getParentActivity(), true, false, chatActivity.getResourceProvider());
            replaceCell.setTextAndIcon(
                    LocaleController.getString(R.string.AQY_Menu_ReplaceText),
                    R.drawable.msg_edit
            );
            replaceCell.setOnClickListener(v -> {
                chatActivity.closeMenu();
                showReplaceInput(chatActivity, chatId, messageId);
            });
            sections.addView(replaceCell);

            if (hasReplace) {
                ActionBarMenuSubItem restoreCell = new ActionBarMenuSubItem(chatActivity.getParentActivity(), false, false, chatActivity.getResourceProvider());
                restoreCell.setTextAndIcon(
                        LocaleController.getString(R.string.AQY_Menu_RestoreText),
                        R.drawable.chats_undo
                );
                restoreCell.setOnClickListener(v -> {
                    chatActivity.closeMenu();
                    AiqiyiMessageReplaceStore.remove(chatId, messageId);
                    refreshChat(chatActivity);
                });
                sections.addView(restoreCell);
            }
        }

        // ---- 伪装发送时间（任意消息） ----
        if (canFakeTime) {
            ActionBarMenuSubItem timeCell = new ActionBarMenuSubItem(chatActivity.getParentActivity(), !canReplaceText, false, chatActivity.getResourceProvider());
            timeCell.setTextAndIcon(
                    LocaleController.getString(R.string.AQY_Menu_FakeTime),
                    R.drawable.msg_contacts_time
            );
            timeCell.setOnClickListener(v -> {
                chatActivity.closeMenu();
                showTimeInput(chatActivity, chatId, messageId);
            });
            sections.addView(timeCell);

            if (hasTime) {
                ActionBarMenuSubItem restoreTimeCell = new ActionBarMenuSubItem(chatActivity.getParentActivity(), false, false, chatActivity.getResourceProvider());
                restoreTimeCell.setTextAndIcon(
                        LocaleController.getString(R.string.AQY_Menu_RestoreTime),
                        R.drawable.chats_undo
                );
                restoreTimeCell.setOnClickListener(v -> {
                    chatActivity.closeMenu();
                    AiqiyiMessageReplaceStore.removeTime(chatId, messageId);
                    refreshChat(chatActivity);
                });
                sections.addView(restoreTimeCell);
            }
        }

        popupLayout.addView(sections, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /**
     * 弹出输入框，让用户填写这条消息要显示成什么文字。
     */
    private static void showReplaceInput(ChatActivity chatActivity, long chatId, int messageId) {
        if (chatActivity.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(chatActivity.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AQY_Menu_ReplaceText));

        final EditTextBoldCursor editText = new EditTextBoldCursor(chatActivity.getParentActivity());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setHint(LocaleController.getString(R.string.AQY_Menu_ReplaceHint));
        editText.setTextSize(16);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        String exist = AiqiyiMessageReplaceStore.get(chatId, messageId);
        if (exist != null) {
            editText.setText(exist);
            editText.setSelection(exist.length());
        }

        builder.setView(editText);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String text = editText.getText().toString();
            if (text.trim().isEmpty()) {
                AiqiyiMessageReplaceStore.remove(chatId, messageId);
            } else {
                AiqiyiMessageReplaceStore.put(chatId, messageId, text);
            }
            refreshChat(chatActivity);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    /**
     * 弹出输入框，让用户填写这条消息要显示成几点（HH:mm）。
     */
    private static void showTimeInput(ChatActivity chatActivity, long chatId, int messageId) {
        if (chatActivity.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(chatActivity.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AQY_Menu_FakeTime));

        final EditTextBoldCursor editText = new EditTextBoldCursor(chatActivity.getParentActivity());
        editText.setInputType(InputType.TYPE_CLASS_DATETIME);
        editText.setHint(LocaleController.getString(R.string.AQY_Menu_FakeTimeHint));
        editText.setTextSize(16);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        int exist = AiqiyiMessageReplaceStore.getTime(chatId, messageId);
        if (exist >= 0) {
            editText.setText(String.format("%02d:%02d", (exist / 3600) % 24, (exist % 3600) / 60));
            editText.setSelection(editText.getText().length());
        }

        builder.setView(editText);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String raw = editText.getText().toString().trim();
            if (raw.isEmpty()) {
                AiqiyiMessageReplaceStore.removeTime(chatId, messageId);
            } else {
                int sec = parseHhMm(raw);
                if (sec >= 0) {
                    AiqiyiMessageReplaceStore.putTime(chatId, messageId, sec);
                }
            }
            refreshChat(chatActivity);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    /**
     * 解析 "HH:mm" / "H:m" / "HH:mm:ss" 为当天秒数；失败返回 -1。
     */
    private static int parseHhMm(String raw) {
        try {
            String[] parts = raw.split(":");
            if (parts.length < 2) {
                return -1;
            }
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            int s = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59 || s < 0 || s > 59) {
                return -1;
            }
            return h * 3600 + m * 60 + s;
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * 数据变更后刷新当前聊天页：
     * 1) 先按最新替换表刷 messageText 缓存
     * 2) 再让列表重绘（时间走渲染层，重绘即可生效）
     */
    private static void refreshChat(ChatActivity chatActivity) {
        try {
            AiqiyiMessageReplaceStore.applyTo(chatActivity.messages);
            if (chatActivity.chatAdapter != null) {
                chatActivity.chatAdapter.updateRowsSafe();
            }
        } catch (Throwable ignore) {
        }
    }
}