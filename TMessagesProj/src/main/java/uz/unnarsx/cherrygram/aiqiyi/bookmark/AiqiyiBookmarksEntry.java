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
 * 展开到消息级：每个会话一个分组标题，下面逐条列出收藏的消息，
 * 每行显示「预览正文 + 时间」，点击直接跳到该条消息。
 *
 * 预览正文优先用收藏时存下的快照；没有快照的旧数据（升级前加的书签）
 * 会在渲染时尝试从消息缓存里补，补不到就标注「内容未缓存」，但仍可点击跳转。
 */
public class AiqiyiBookmarksEntry extends BaseCGPreferencesEntry {

    private static final int ROW_MSG_BASE = 5000;
    private static final int ROW_CLEAR = 902;

    /** 每条书签行的绑定信息，供点击时反查：{dialogId, messageId}。 */
    private final List<long[]> rowIndex = new ArrayList<>();

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
        rowIndex.clear();

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

        int rowId = ROW_MSG_BASE;
        for (Map.Entry<Long, Integer> e : list) {
            final long dialogId = e.getKey();
            final int count = e.getValue();
            if (count <= 0) {
                continue;
            }
            String cnt = String.valueOf(count);
            items.add(UItem.asHeader(getDialogTitle(account, dialogId)
                    + "  " + getString(R.string.AQY_BookmarksCount).replace("%1$d", cnt)));

            List<AiqiyiBookmarksHelper.Snapshot> snaps =
                    AiqiyiBookmarksHelper.getSnapshots(account, dialogId);

            for (AiqiyiBookmarksHelper.Snapshot sn : snaps) {
                String preview = sn.text;
                if (preview == null || preview.length() == 0) {
                    preview = getString(R.string.AQY_BookmarksNoSnapshot);
                }
                String time = sn.date > 0
                        ? org.telegram.messenger.LocaleController.formatShortDateTime(sn.date)
                        : "";
                items.add(UItem.asButton(rowId, preview, time));
                rowIndex.add(new long[]{dialogId, sn.messageId});
                rowId++;
            }
        }

        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ROW_CLEAR, getString(R.string.AQY_BookmarksClearAll)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_CLEAR) {
            confirmClearAll();
            return;
        }
        final int idx = item.id - ROW_MSG_BASE;
        if (idx >= 0 && idx < rowIndex.size()) {
            long[] pair = rowIndex.get(idx);
            openMessage(pair[0], (int) pair[1]);
        }
    }

    // ---------------------------------------------------------------- 内部

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

    private void openMessage(long dialogId, int messageId) {
        if (messageId <= 0) {
            showBulletin(getString(R.string.AQY_BookmarksJumpFail));
            return;
        }
        try {
            Bundle args = new Bundle();
            args.putLong("dialog_id", dialogId);
            args.putInt("message_id", messageId);
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