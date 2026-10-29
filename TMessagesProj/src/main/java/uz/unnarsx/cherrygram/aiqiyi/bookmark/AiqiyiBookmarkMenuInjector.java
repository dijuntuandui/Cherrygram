package uz.unnarsx.cherrygram.aiqiyi.bookmark;

import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * [Aiqiyi] 消息书签 - 长按菜单注入
 *
 * 沿用现有 AiqiyiMessageMenuInjector 的做法：单独建一个 LinearLayout 塞进 popupLayout，
 * 不动原生 options 数组，所以不影响其它菜单项的排序和分隔线。
 *
 * 行为：
 *   单条消息：未书签 -> 「添加书签」；已书签 -> 「移除书签」
 *   多条选中：全部已书签 -> 「移除书签」；否则 -> 「添加书签」
 */
public final class AiqiyiBookmarkMenuInjector {

    private AiqiyiBookmarkMenuInjector() {}

    public static void inject(ChatActivity chatActivity,
                              ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout,
                              List<MessageObject> selected) {
        if (chatActivity == null || popupLayout == null || selected == null || selected.isEmpty()) {
            return;
        }
        if (chatActivity.getParentActivity() == null) {
            return;
        }

        final List<Integer> ids = new ArrayList<>(selected.size());
        long dialogId = 0;
        for (MessageObject mo : selected) {
            if (mo == null || mo.messageOwner == null) {
                continue;
            }
            if (mo.messageOwner.action != null) {
                continue;
            }
            int id = mo.messageOwner.id;
            if (id <= 0) {
                continue;
            }
            ids.add(id);
            if (dialogId == 0) {
                dialogId = mo.messageOwner.dialog_id;
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        for (MessageObject mo : selected) {
            if (mo != null && mo.messageOwner != null && mo.messageOwner.dialog_id != dialogId) {
                return;
            }
        }

        final int account = chatActivity.getCurrentAccount();
        final int[] idArr = new int[ids.size()];
        for (int i = 0; i < idArr.length; i++) {
            idArr[i] = ids.get(i);
        }

        final boolean allBookmarked = AiqiyiBookmarksHelper.areAllBookmarked(account, dialogId, idArr);
        final boolean single = idArr.length == 1;

        LinearLayout sections = new LinearLayout(chatActivity.getParentActivity());
        sections.setOrientation(LinearLayout.VERTICAL);

        ActionBarMenuSubItem cell = new ActionBarMenuSubItem(
                chatActivity.getParentActivity(), true, false, chatActivity.getResourceProvider());

        final int icon = allBookmarked ? R.drawable.msg_unfave : R.drawable.msg_fave;
        final CharSequence label = LocaleController.getString(
                allBookmarked
                        ? (single ? R.string.AQY_Menu_RemoveBookmark : R.string.AQY_Menu_RemoveBookmarks)
                        : (single ? R.string.AQY_Menu_AddBookmark : R.string.AQY_Menu_AddBookmarks));

        cell.setTextAndIcon(label, icon);
        cell.setOnClickListener(v -> {
            chatActivity.closeMenu();
            boolean nowOn = AiqiyiBookmarksHelper.toggleBookmarks(account, dialogId, idArr);
            int res = nowOn
                    ? (single ? R.string.AQY_BookmarkAdded : R.string.AQY_BookmarksAdded)
                    : (single ? R.string.AQY_BookmarkRemoved : R.string.AQY_BookmarksRemoved);
            try {
                org.telegram.ui.Components.BulletinFactory.of(chatActivity)
                        .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(res))
                        .show();
            } catch (Throwable ignore) {
            }
        });

        sections.addView(cell);
        popupLayout.addView(sections,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
