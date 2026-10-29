package uz.unnarsx.cherrygram.aiqiyi.bookmark;

import static org.telegram.messenger.LocaleController.getString;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;

/**
 * [Aiqiyi] 消息书签 - 列表页
 *
 * 按会话聚合展示：每个有书签的会话一行，显示会话名 + 条数。
 * 点击进入该会话并落到最早的一条书签消息。
 */
public class AiqiyiBookmarksEntry extends BaseCGPreferencesEntry {

    private static final int ROW_CHAT = 1000;
    private static final int ROW_CLEAR = 902;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_BookmarksTitle);
    }

    @Override
    protected String getKey() {
        return "aqy_bookmarks";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final int account = UserConfig.selectedAccount;

        Map<Long, Integer> counts = AiqiyiBookmarksHelper.getBookmarkCounts(account);
        if (counts == null || counts.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.AQY_BookmarksEmpty)));
            return;
        }

        List<Map.Entry<Long, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : Long.compare(a.getKey(), b.getKey());
        });

        for (Map.Entry<Long, Integer> e : list) {
            final long dialogId = e.getKey();
            final int count = e.getValue();
            if (count <= 0) {
                continue;
            }
            String cnt = String.valueOf(count);
            items.add(UItem.asButton(ROW_CHAT, getDialogTitle(account, dialogId),
                    getString(R.string.AQY_BookmarksCount).replace("%1$d", cnt))
                    .slug("bookmark_" + dialogId));
        }

        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ROW_CLEAR, getString(R.string.AQY_BookmarksClearAll))
                .slug("bookmarks_clear_all"));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_CLEAR) {
            confirmClearAll();
            return;
        }
        if (item.id >= ROW_CHAT) {
            long dialogId = dialogIdFromSlug(item.slug);
            if (dialogId != 0) {
                openBookmarksInChat(dialogId);
            }
        }
    }

    private long dialogIdFromSlug(String slug) {
        if (slug == null || !slug.startsWith("bookmark_")) {
            return 0;
        }
        try {
            return Long.parseLong(slug.substring("bookmark_".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private CharSequence getDialogTitle(int account, long dialogId) {
        try {
            MessagesController mc = MessagesController.getInstance(account);
            long absId = Math.abs(dialogId);
            if (dialogId > 0) {
                TLRPC.User u = mc.getUser(absId);
                if (u != null) {
                    String name = UserObject.getUserName(u);
                    if (name != null && name.length() > 0) {
                        return name;
                    }
                }
            } else if (dialogId < 0) {
                TLRPC.Chat c = mc.getChat(absId);
                if (c != null && c.title != null && c.title.length() > 0) {
                    return c.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return String.valueOf(dialogId);
    }

    private void openBookmarksInChat(long dialogId) {
        int[] ids = AiqiyiBookmarksHelper.getBookmarkedIds(UserConfig.selectedAccount, dialogId);
        if (ids.length == 0) {
            showBulletin(getString(R.string.AQY_BookmarksJumpFail));
            return;
        }
        try {
            Bundle args = new Bundle();
            args.putLong("dialog_id", dialogId);
            args.putInt("message_id", ids[0]);
            presentFragment(new ChatActivity(args));
        } catch (Throwable t) {
            showBulletin(getString(R.string.AQY_BookmarksJumpFail));
        }
    }

    private void confirmClearAll() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_BookmarksClearAll));
        b.setMessage(getString(R.string.AQY_BookmarksClearAllConfirm));
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            AiqiyiBookmarksHelper.clearAll(UserConfig.selectedAccount);
            AiqiyiBookmarksHelper.invalidateCache();
            showBulletin(getString(R.string.AQY_BookmarksCleared));
            updateRows(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showBulletin(CharSequence text) {
        try {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(org.telegram.messenger.R.raw.contact_check, text)
                    .show();
        } catch (Throwable ignore) {
        }
    }
}
