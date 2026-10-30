/**
 * 爱琦艺客户端 - 本地坐标伪造 设置页
 *
 * 只影响本机发出的位置消息，不改系统定位、不影响其它应用。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.location;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiLocationEntry extends BaseCGPreferencesEntry {

    private final int enabledRow = 720;
    private final int pointRow = 721;
    private final int inputRow = 722;
    private final int presetsRow = 723;
    private final int savePresetRow = 724;
    private final int liveRow = 725;
    private final int resetRow = 726;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_FakeLocation);
    }

    @Override
    protected String getKey() {
        return "aqy_fake_location";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean on = AiqiyiLocationStore.isEnabled();

        items.add(UItem.asHeader(getString(R.string.AQY_Loc_Header)));
        items.add(SettingsHelper.asSwitchCG(enabledRow,
                        getString(R.string.AQY_FakeLocation),
                        getString(R.string.AQY_FakeLocation_Desc))
                .setChecked(on)
                .slug("aqy_loc_enabled")
        );
        items.add(SettingsHelper.asTextDetail(pointRow, 0,
                        getString(R.string.AQY_Loc_Point),
                        AiqiyiLocationStore.formatPoint())
                .slug("aqy_loc_point")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Loc_Note)));

        if (on) {
            items.add(UItem.asHeader(getString(R.string.AQY_Loc_Set_Header)));
            items.add(SettingsHelper.asTextDetail(inputRow, 0,
                            getString(R.string.AQY_Loc_Input),
                            getString(R.string.AQY_Loc_Input_Desc))
                    .slug("aqy_loc_input")
            );
            items.add(SettingsHelper.asTextDetail(presetsRow, 0,
                            getString(R.string.AQY_Loc_Presets),
                            getString(R.string.AQY_Loc_Presets_Desc))
                    .slug("aqy_loc_presets")
            );
            items.add(SettingsHelper.asTextDetail(savePresetRow, 0,
                            getString(R.string.AQY_Loc_Save_Preset),
                            getString(R.string.AQY_Loc_Save_Preset_Desc))
                    .slug("aqy_loc_save_preset")
            );
            items.add(UItem.asShadow(getString(R.string.AQY_Loc_Preset_Note)));

            items.add(UItem.asHeader(getString(R.string.AQY_Loc_Behavior_Header)));
            items.add(SettingsHelper.asSwitchCG(liveRow,
                            getString(R.string.AQY_Loc_Live),
                            getString(R.string.AQY_Loc_Live_Desc))
                    .setChecked(AiqiyiLocationStore.isLiveEnabled())
                    .slug("aqy_loc_live")
            );
            items.add(SettingsHelper.asTextDetail(resetRow, 0,
                            getString(R.string.AQY_Loc_Reset),
                            getString(R.string.AQY_Loc_Reset_Desc))
                    .slug("aqy_loc_reset")
            );
            items.add(UItem.asShadow(getString(R.string.AQY_Loc_Behavior_Note)));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == enabledRow) {
            if (!AiqiyiLocationStore.isEnabled() && !AiqiyiLocationStore.hasValidPoint()) {
                showTip(getString(R.string.AQY_Loc_Need_Point));
                return;
            }
            AiqiyiLocationStore.setEnabled(!AiqiyiLocationStore.isEnabled());
            SettingsHelper.updateCheckState(view, AiqiyiLocationStore.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == pointRow || item.id == inputRow) {
            showInputDialog();
        } else if (item.id == presetsRow) {
            showPresetDialog();
        } else if (item.id == savePresetRow) {
            showSavePresetDialog();
        } else if (item.id == liveRow) {
            AiqiyiLocationStore.setLiveEnabled(!AiqiyiLocationStore.isLiveEnabled());
            SettingsHelper.updateCheckState(view, AiqiyiLocationStore.isLiveEnabled());
            listView.adapter.update(true);
        } else if (item.id == resetRow) {
            AiqiyiLocationStore.setPoint(0d, 0d, "");
            AiqiyiLocationStore.setEnabled(false);
            showTip(getString(R.string.AQY_Loc_Reset_Done));
            listView.adapter.update(true);
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    // ==================== 输入 ====================

    private void showInputDialog() {
        if (getParentActivity() == null) return;
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(1, 15);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Loc_Input_Hint));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.requestFocus();
        if (AiqiyiLocationStore.hasValidPoint()) {
            editText.setText(String.format(java.util.Locale.US, "%.6f,%.6f",
                    AiqiyiLocationStore.getLat(), AiqiyiLocationStore.getLon()));
        }

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Loc_Input));
        b.setView(container);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            double[] pt = AiqiyiLocationStore.parseInput(editText.getText().toString());
            if (pt == null) {
                showTip(getString(R.string.AQY_Loc_Invalid));
                return;
            }
            AiqiyiLocationStore.setPoint(pt[0], pt[1], "");
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    // ==================== 预设 ====================

    private void showPresetDialog() {
        if (getParentActivity() == null) return;
        final String[][] presets = AiqiyiLocationStore.getPresets();
        if (presets == null || presets.length == 0) {
            showTip(getString(R.string.AQY_Loc_No_Preset));
            return;
        }
        CharSequence[] options = new CharSequence[presets.length];
        for (int i = 0; i < presets.length; i++) {
            options[i] = presets[i][0];
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Loc_Presets));
        b.setItems(options, (d, which) -> {
            double[] pt = AiqiyiLocationStore.parseInput(presets[which][1]);
            if (pt == null) {
                showTip(getString(R.string.AQY_Loc_Invalid));
                return;
            }
            AiqiyiLocationStore.setPoint(pt[0], pt[1], presets[which][0]);
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showSavePresetDialog() {
        if (getParentActivity() == null) return;
        if (!AiqiyiLocationStore.hasValidPoint()) {
            showTip(getString(R.string.AQY_Loc_Need_Point));
            return;
        }
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(1, 15);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Loc_Preset_Name_Hint));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.requestFocus();

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Loc_Save_Preset));
        b.setView(container);
        b.setPositiveButton(getString(R.string.Save), (d, w) -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                return;
            }
            AiqiyiLocationStore.addPreset(name,
                    AiqiyiLocationStore.getLat(), AiqiyiLocationStore.getLon());
            showTip(getString(R.string.AQY_Loc_Preset_Saved));
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showTip(String text) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_FakeLocation));
        b.setMessage(text);
        b.setPositiveButton(getString(R.string.OK), null);
        showDialog(b.create());
    }
}