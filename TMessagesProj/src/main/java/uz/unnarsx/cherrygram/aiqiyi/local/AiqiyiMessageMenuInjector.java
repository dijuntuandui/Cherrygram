/**
 * 爱琦艺客户端 - 长按菜单「替换显示文字」注入
 *
 * 参考 Cherrygram 的 CGMessageMenuInjector 机制：
 * 独立往 popupLayout 里追加 Cell，不碰原生 options 数组，
 * 因此不会干扰原有菜单项。
 *
 * 行为：
 *   长按任意文本消息 → 菜单多出「替换显示文字」
 *   已替换过的消息额外多出「恢复原文」
 *   设置只作用于本机渲染，不发送、不上传。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public class AiqiyiMessageMenuInjector {

    /**
     * 往长按菜单里追加爱琦艺条目。
     *
     * @param chatActivity  当前聊天页
     * @param popupLayout   菜单容器
     * @param selectedObject 长按选中的消息
     */
    public static void showReplaceItems(ChatActivity chatActivity,
                                        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout,
                                        MessageObject selectedObject) {
        if (chatActivity == null || popupLayout == null || selectedObject == null
                || selectedObject.messageOwner == null) {
            return;
        }
        // 只对「有文字可替换」的消息生效
        if (selectedObject.messageOwner.message == null || selectedObject.messageOwner.message.length() == 0) {
            return;
        }
        if (chatActivity.getParentActivity() == null) {
            return;
        }

        final long chatId = selectedObject.messageOwner.dialog_id;
        final int messageId = selectedObject.messageOwner.id;
        final boolean hasReplace = AiqiyiMessageReplaceStore.has(chatId, messageId);

        LinearLayout sections = new LinearLayout(chatActivity.getParentActivity());
        sections.setOrientation(LinearLayout.VERTICAL);

        // ---- 分隔线 ----
        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(chatActivity.getContext(), chatActivity.getResourceProvider());
        gap.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        sections.addView(gap);

        // ---- 替换显示文字 ----
        ActionBarMenuSubItem replaceCell = new ActionBarMenuSubItem(chatActivity.getParentActivity(), true, false, chatActivity.getResourceProvider());
        replaceCell.setTextAndIcon(
                LocaleController.getString(R.string.AQY_Menu_ReplaceText),
                R.drawable.msg_edit
        );
        replaceCell.setOnClickListener(v -> {
            chatActivity.closeMenu();
            showReplaceInput(chatActivity, selectedObject, chatId, messageId);
        });
        sections.addView(replaceCell);

        // ---- 恢复原文（仅已替换过时显示） ----
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

        popupLayout.addView(sections, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /**
     * 弹出输入框，让用户填写这条消息要显示成什么。
     */
    private static void showReplaceInput(ChatActivity chatActivity,
                                         MessageObject selectedObject,
                                         long chatId, int messageId) {
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

        // 已有替换则回填
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
     * 替换表变更后刷新当前聊天页：
     * 先按最新替换表刷消息文本缓存，再让列表重绘。
     */
    private static void refreshChat(ChatActivity chatActivity) {
        try {
            // 1) 刷新已加载消息的文本缓存
            if (chatActivity.getMessages() != null) {
                AiqiyiMessageReplaceStore.applyTo(chatActivity.getMessages());
            }
            // 2) 让可见行重绘
            chatActivity.getChatAdapter().updateRowsSafe();
        } catch (Throwable ignore) {
        }
    }
}