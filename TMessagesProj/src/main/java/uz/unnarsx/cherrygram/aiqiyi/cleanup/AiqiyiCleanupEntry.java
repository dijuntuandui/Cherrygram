/**
 * 爱琦艺客户端 - 扩展清理 设置页
 *
 * 安全档：只清理本地缓存，不碰服务器数据。
 * 不删聊天记录、不退群、不改资料、不删联系人。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.cleanup;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiCleanupEntry extends BaseCGPreferencesEntry {

    private final int totalRow = 500;
    private final int clearAllRow = 501;
    private final int catBase = 510;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_Cleanup_Title);
    }

    @Override
    protected String getKey() {
        return "aqy_cleanup";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AQY_Cleanup_Header)));
        items.add(SettingsHelper.asTextDetail(totalRow, 0,
                        getString(R.string.AQY_Cleanup_Total), getTotalText())
                .slug("aqy_cleanup_total")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Cleanup_Note)));

        items.add(UItem.asHeader(getString(R.string.AQY_Cleanup_Cat_Header)));
        for (int i = 0; i < AiqiyiCleanup.CAT_COUNT; i++) {
            items.add(SettingsHelper.asTextDetail(catBase + i, 0,
                            getCategoryName(i), getCategorySize(i))
                    .slug("aqy_cleanup_cat_" + i)
            );
        }
        items.add(UItem.asShadow(getString(R.string.AQY_Cleanup_Cat_Note)));

        items.add(UItem.asHeader(getString(R.string.AQY_Cleanup_Action_Header)));
        items.add(UItem.asButton(clearAllRow, getString(R.string.AQY_Cleanup_All)));
        items.add(UItem.asShadow(getString(R.string.AQY_Cleanup_All_Note)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == totalRow) {
            showClearAllDialog();
        } else if (item.id == clearAllRow) {
            showClearAllDialog();
        } else if (item.id >= catBase && item.id < catBase + AiqiyiCleanup.CAT_COUNT) {
            showCategoryDialog(item.id - catBase);
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    // ==================== 文案 ====================

    private String getTotalText() {
        try {
            return AndroidUtilities.formatFileSize(AiqiyiCleanup.totalSize());
        } catch (Throwable e) {
            return "-";
        }
    }

    private String getCategorySize(int cat) {
        try {
            return AndroidUtilities.formatFileSize(AiqiyiCleanup.sizeOf(cat));
        } catch (Throwable e) {
            return "-";
        }
    }

    private String getCategoryName(int cat) {
        switch (cat) {
            case AiqiyiCleanup.CAT_CACHE:    return getString(R.string.AQY_Cleanup_Cache);
            case AiqiyiCleanup.CAT_PHOTO:    return getString(R.string.AQY_Cleanup_Photo);
            case AiqiyiCleanup.CAT_VIDEO:    return getString(R.string.AQY_Cleanup_Video);
            case AiqiyiCleanup.CAT_DOCUMENT: return getString(R.string.AQY_Cleanup_Document);
            case AiqiyiCleanup.CAT_STICKER:  return getString(R.string.AQY_Cleanup_Sticker);
            default: return "?";
        }
    }

    // ==================== 弹窗 ====================

    private void showCategoryDialog(final int cat) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getCategoryName(cat));
        b.setMessage(getString(R.string.AQY_Cleanup_Confirm)
                .replace("%1$s", getCategorySize(cat)));
        b.setPositiveButton(getString(R.string.AQY_Cleanup_Do),
                (d, w) -> performClear(cat));
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showClearAllDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Cleanup_All));
        b.setMessage(getString(R.string.AQY_Cleanup_Confirm)
                .replace("%1$s", getTotalText()));
        b.setPositiveButton(getString(R.string.AQY_Cleanup_Do),
                (d, w) -> performClear(-1));
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    // ==================== 执行 ====================

    private void performClear(int cat) {
        AiqiyiCleanup.Progress cb = freed -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            try {
                BulletinFactory.of(AiqiyiCleanupEntry.this)
                        .createSimpleBulletin(R.raw.contact_check,
                                getString(R.string.AQY_Cleanup_Done)
                                        .replace("%1$s",
                                                AndroidUtilities.formatFileSize(freed)))
                        .show();
            } catch (Throwable ignore) {
            }
        };
        if (cat < 0) {
            AiqiyiCleanup.clearAll(cb);
        } else {
            AiqiyiCleanup.clear(cat, cb);
        }
    }
}
