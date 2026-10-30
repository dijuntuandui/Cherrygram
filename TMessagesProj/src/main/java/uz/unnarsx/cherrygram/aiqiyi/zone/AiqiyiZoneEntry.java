/**
 * 爱琦艺客户端 - 活跃专区 设置页
 * 仅本地生效：设置保存在本机，不会上传服务器。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.zone;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig;
import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiZoneEntry extends BaseCGPreferencesEntry {

    private final int enabledRow = 420;
    private final int emojiRow = 421;
    private final int directionRow = 422;
    private final int targetRow = 423;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_ActiveZone);
    }

    @Override
    protected String getKey() {
        return "aqy_active_zone";
    }

    private CherrygramAppearanceConfig cfg() {
        return CherrygramAppearanceConfig.INSTANCE;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean on = cfg().getAiqiyiActiveZone();

        items.add(UItem.asHeader(getString(R.string.AQY_Zone_Header)));
        items.add(SettingsHelper.asSwitchCG(enabledRow,
                        getString(R.string.AQY_ActiveZone),
                        getString(R.string.AQY_ActiveZone_Desc))
                .setChecked(on)
                .slug("aqy_zone_enabled")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Zone_Note)));

        if (on) {
            items.add(UItem.asHeader(getString(R.string.AQY_Zone_Behavior_Header)));
            items.add(SettingsHelper.asTextDetail(emojiRow, 0,
                            getString(R.string.AQY_Zone_Emoji),
                            cfg().getAiqiyiActiveZoneEmoji())
                    .slug("aqy_zone_emoji")
            );
            items.add(SettingsHelper.asTextDetail(directionRow, 0,
                            getString(R.string.AQY_Zone_Direction),
                            getZoneDirectionText())
                    .slug("aqy_zone_direction")
            );
            items.add(SettingsHelper.asTextDetail(targetRow, 0,
                            getString(R.string.AQY_Zone_Target),
                            getZoneTargetText())
                    .slug("aqy_zone_target")
            );
            items.add(UItem.asShadow(getString(R.string.AQY_Zone_Behavior_Note)));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == enabledRow) {
            cfg().setAiqiyiActiveZone(!cfg().getAiqiyiActiveZone());
            SettingsHelper.updateCheckState(view, cfg().getAiqiyiActiveZone());
            listView.adapter.update(true);
        } else if (item.id == emojiRow) {
            showZoneEmojiDialog();
        } else if (item.id == directionRow) {
            showZoneDirectionDialog();
        } else if (item.id == targetRow) {
            showZoneTargetDialog();
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    // ==================== 文案 ====================

    private String getZoneDirectionText() {
        int d = cfg().getAiqiyiActiveZoneDirection();
        if (d == 1) return getString(R.string.AQY_Zone_Dir_Self);
        if (d == 2) return getString(R.string.AQY_Zone_Dir_All);
        return getString(R.string.AQY_Zone_Dir_Others);
    }

    private String getZoneTargetText() {
        int mode = cfg().getAiqiyiActiveZoneTargetMode();
        if (mode == 1) {
            String raw = cfg().getAiqiyiActiveZoneTargetUsers();
            int n = 0;
            if (raw != null) {
                for (String p : raw.split(",")) {
                    if (p.trim().length() > 0) n++;
                }
            }
            return n + " " + getString(R.string.AQY_Zone_Users_Count);
        }
        return getString(R.string.AQY_Zone_Target_All);
    }

    // ==================== 弹窗 ====================

    private EditTextBoldCursor buildInput(String value, int sizeDp, String hint) {
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(value);
        editText.setTextSize(1, sizeDp);
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintText(hint);
        editText.setFocusable(true);
        editText.requestFocus();
        return editText;
    }

    private android.widget.LinearLayout wrap(EditTextBoldCursor editText) {
        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT));
        return container;
    }

    private void showZoneEmojiDialog() {
        if (getParentActivity() == null) return;
        EditTextBoldCursor editText = buildInput(
                cfg().getAiqiyiActiveZoneEmoji(), 18,
                getString(R.string.AQY_Zone_Emoji_Hint));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Zone_Emoji));
        b.setView(wrap(editText));
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            String v = editText.getText().toString().trim();
            cfg().setAiqiyiActiveZoneEmoji(v.isEmpty() ? "\uD83D\uDC4D" : v);
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showZoneDirectionDialog() {
        if (getParentActivity() == null) return;
        int cur = cfg().getAiqiyiActiveZoneDirection();
        CharSequence[] options = new CharSequence[]{
                (cur == 0 ? "\u2713 " : "") + getString(R.string.AQY_Zone_Dir_Others),
                (cur == 1 ? "\u2713 " : "") + getString(R.string.AQY_Zone_Dir_Self),
                (cur == 2 ? "\u2713 " : "") + getString(R.string.AQY_Zone_Dir_All)
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Zone_Direction));
        b.setItems(options, (d, which) -> {
            cfg().setAiqiyiActiveZoneDirection(which);
            d.dismiss();
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showZoneTargetDialog() {
        if (getParentActivity() == null) return;
        EditTextBoldCursor editText = buildInput(
                cfg().getAiqiyiActiveZoneTargetUsers(), 16,
                getString(R.string.AQY_Zone_Target_Hint));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Zone_Target));
        b.setMessage(getString(R.string.AQY_Zone_Target_Desc));
        b.setView(wrap(editText));
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            String v = editText.getText().toString().trim();
            cfg().setAiqiyiActiveZoneTargetUsers(v);
            cfg().setAiqiyiActiveZoneTargetMode(v.isEmpty() ? 0 : 1);
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        b.setNeutralButton(getString(R.string.AQY_Zone_Target_Clear), (d, w) -> {
            cfg().setAiqiyiActiveZoneTargetUsers("");
            cfg().setAiqiyiActiveZoneTargetMode(0);
            listView.adapter.update(true);
        });
        showDialog(b.create());
    }
}
