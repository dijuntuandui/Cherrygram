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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

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
    private final int mapRow = 727;

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
            items.add(SettingsHelper.asSwitchCG(mapRow,
                            getString(R.string.AQY_Loc_Map),
                            getString(R.string.AQY_Loc_Map_Desc))
                    .setChecked(AiqiyiLocationStore.isMapEnabled())
                    .slug("aqy_loc_map")
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
            updateRows(true);
        } else if (item.id == pointRow || item.id == inputRow) {
            showInputDialog();
        } else if (item.id == presetsRow) {
            showPresetDialog();
        } else if (item.id == savePresetRow) {
            showSavePresetDialog();
        } else if (item.id == liveRow) {
            AiqiyiLocationStore.setLiveEnabled(!AiqiyiLocationStore.isLiveEnabled());
            SettingsHelper.updateCheckState(view, AiqiyiLocationStore.isLiveEnabled());
            updateRows(true);
        } else if (item.id == mapRow) {
            AiqiyiLocationStore.setMapEnabled(!AiqiyiLocationStore.isMapEnabled());
            SettingsHelper.updateCheckState(view, AiqiyiLocationStore.isMapEnabled());
            updateRows(true);
        } else if (item.id == resetRow) {
            AiqiyiLocationStore.setPoint(0d, 0d, "");
            AiqiyiLocationStore.setEnabled(false);
            showTip(getString(R.string.AQY_Loc_Reset_Done));
            updateRows(true);
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

        // [Aiqiyi] 坐标下面再加一个可选的名称输入框，填了名字就一起表现到当前坐标里。
        final EditTextBoldCursor nameEdit = new EditTextBoldCursor(getParentActivity());
        nameEdit.setTextSize(1, 15);
        nameEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        nameEdit.setHintText(getString(R.string.AQY_Loc_Name_Optional_Hint));
        nameEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        nameEdit.setSingleLine(true);
        if (AiqiyiLocationStore.hasValidPoint()) {
            nameEdit.setText(AiqiyiLocationStore.getLabel());
        }
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = AndroidUtilities.dp(14);
        container.addView(nameEdit, nameLp);

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Loc_Input));
        b.setView(container);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            double[] pt = AiqiyiLocationStore.parseInput(editText.getText().toString());
            if (pt == null) {
                showTip(getString(R.string.AQY_Loc_Invalid));
                return;
            }
            // [Aiqiyi] 不再硬写空 label，把用户填的名字带上
            AiqiyiLocationStore.setPoint(pt[0], pt[1], nameEdit.getText().toString().trim());
            updateRows(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    // ==================== 预设 ====================

    private void showPresetDialog() {
        if (getParentActivity() == null) return;
        showPresetListDialog();
    }

    /**
     * [Aiqiyi] 预设列表弹窗
     *
     * 之前用 AlertDialog.setItems()，那个 API 只能点击，做不到长按。
     * 这里换成自建 ListView 入 Dialog，拿到 setOnItemLongClickListener。
     *
     * 内置预设（BUILTIN_PRESETS）不开放删改，因为它们是代码常量，
     * 删了下次启动还会回来，写进去只会让用户以为没生效。
     */
    private void showPresetListDialog() {
        final String[][] presets = AiqiyiLocationStore.getPresets();
        if (presets == null || presets.length == 0) {
            showTip(getString(R.string.AQY_Loc_No_Preset));
            return;
        }

        // 每行：名字  +  下面一行小字坐标
        final CharSequence[] names = new CharSequence[presets.length];
        final CharSequence[] subs = new CharSequence[presets.length];
        for (int i = 0; i < presets.length; i++) {
            names[i] = presets[i][0];
            String coord = presets[i][1];
            // 名字本身就是坐标（手动输入没填名后保存的）就不重复显示
            subs[i] = coord != null && coord.equals(names[i]) ? "" : coord;
        }

        final ListView listView = new ListView(getParentActivity());
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(Theme.getSelectorDrawable(false));

        final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                getParentActivity(), 0, names) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout ll;
                TextView title;
                TextView sub;
                if (convertView == null) {
                    ll = new LinearLayout(getContext());
                    ll.setOrientation(LinearLayout.VERTICAL);
                    ll.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(11),
                            AndroidUtilities.dp(20), AndroidUtilities.dp(11));

                    title = new TextView(getContext());
                    title.setTextSize(1, 15.5f);
                    title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    ll.addView(title);

                    sub = new TextView(getContext());
                    sub.setTextSize(1, 12.5f);
                    sub.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    slp.topMargin = AndroidUtilities.dp(2);
                    sub.setVisibility(View.GONE);
                    ll.addView(sub, slp);

                    // [Aiqiyi] 固定条目高度，否则 ListView 只测一行时会把那一行撑满整个弹窗。
                    ll.setMinimumHeight(AndroidUtilities.dp(56));
                    ll.setTag(new TextView[]{title, sub});
                } else {
                    ll = (LinearLayout) convertView;
                    TextView[] tags = (TextView[]) ll.getTag();
                    title = tags[0];
                    sub = tags[1];
                }
                title.setText(names[position]);
                CharSequence subText = subs[position];
                if (subText == null || subText.length() == 0) {
                    sub.setVisibility(View.GONE);
                } else {
                    sub.setText(subText);
                    sub.setVisibility(View.VISIBLE);
                }
                // 内置的加个小灰标记，看得出哪些是自建可改的
                if (AiqiyiLocationStore.isBuiltinPreset(names[position].toString())) {
                    title.setAlpha(0.88f);
                } else {
                    title.setAlpha(1f);
                }
                return ll;
            }
        };
        listView.setAdapter(adapter);

        // [Aiqiyi] 关键：ListView 直接 setView 进 Dialog 会被测成一行高（因为
        // 没有用满屏高的约束），剩下的条目全被挤到可见区外。
        // 这里给它一个有上下限的高度：条目少就自适应，条目多就封顶滞动。
        int rowH = AndroidUtilities.dp(56);
        int contentH = presets.length * rowH;
        int maxH = AndroidUtilities.dp(360);
        int listH = Math.min(contentH, maxH);
        FrameLayout wrapper = new FrameLayout(getParentActivity());
        wrapper.addView(listView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, listH));

        final AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.AQY_Loc_Presets))
                .setView(wrapper)
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();

        // ---------- 点击：直接用这个坐标 ----------
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            double[] pt = AiqiyiLocationStore.parseInput(presets[position][1]);
            if (pt == null) {
                showTip(getString(R.string.AQY_Loc_Invalid));
                return;
            }
            AiqiyiLocationStore.setPoint(pt[0], pt[1], presets[position][0]);
            dialog.dismiss();
            updateRows(true);
        });

        // ---------- 长按：重命名 / 删除 ----------
        listView.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            final String presetName = presets[position][0];
            if (AiqiyiLocationStore.isBuiltinPreset(presetName)) {
                // 内置的没得改，但别让用户摸不着头脑，给句话。
                listView.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS);
                showTip(getString(R.string.AQY_Loc_Preset_Builtin_Readonly));
                return true;
            }
            listView.performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS);
            showPresetActionDialog(presetName, dialog, () -> updateRows(true));
            return true;
        });

        showDialog(dialog);
    }

    /** 长按后的操作菜单：重命名 / 删除 */
    private interface Refresh {
        void run();
    }

    private void showPresetActionDialog(final String presetName,
                                        final AlertDialog listDialog,
                                        final Refresh refresh) {
        if (getParentActivity() == null) return;
        CharSequence[] actions = new CharSequence[]{
                getString(R.string.AQY_Loc_Preset_Rename),
                getString(R.string.AQY_Loc_Preset_Delete)
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(presetName);
        b.setItems(actions, (d, which) -> {
            if (which == 0) {
                // 改名前先把列表弹窗关掉，避免两层弹窗叠在一起
                listDialog.dismiss();
                showRenamePresetDialog(presetName, refresh);
            } else {
                listDialog.dismiss();
                showDeletePresetDialog(presetName, refresh);
            }
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showRenamePresetDialog(final String presetName, final Refresh refresh) {
        if (getParentActivity() == null) return;
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(1, 15);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Loc_Preset_Name_Hint));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setSingleLine(true);
        editText.setText(presetName);
        editText.setSelection(presetName.length());
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
        b.setTitle(getString(R.string.AQY_Loc_Preset_Rename));
        b.setView(container);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                showTip(getString(R.string.AQY_Loc_Preset_Name_Empty));
                return;
            }
            if (name.equals(presetName)) {
                refresh.run();
                return;
            }
            // 防重名：自建里已经有这个名字就不覆盖
            for (String[] p : AiqiyiLocationStore.getPresets()) {
                if (p[0].equals(name)) {
                    showTip(getString(R.string.AQY_Loc_Preset_Name_Dup));
                    return;
                }
            }
            if (AiqiyiLocationStore.renamePreset(presetName, name)) {
                showTip(getString(R.string.AQY_Loc_Preset_Renamed));
            }
            refresh.run();
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showDeletePresetDialog(final String presetName, final Refresh refresh) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Loc_Preset_Delete));
        b.setMessage(String.format(getString(R.string.AQY_Loc_Delete_Confirm), presetName));
        b.setPositiveButton(getString(R.string.Delete), (d, w) -> {
            if (AiqiyiLocationStore.removePreset(presetName)) {
                showTip(getString(R.string.AQY_Loc_Preset_Deleted));
            }
            refresh.run();
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
                // [Aiqiyi] 原来这里直接 return，点了保存没任何反应，像死了一样。
                showTip(getString(R.string.AQY_Loc_Preset_Name_Empty));
                return;
            }
            if (AiqiyiLocationStore.isBuiltinPreset(name)) {
                showTip(getString(R.string.AQY_Loc_Preset_Name_Dup));
                return;
            }
            AiqiyiLocationStore.addPreset(name,
                    AiqiyiLocationStore.getLat(), AiqiyiLocationStore.getLon());
            showTip(getString(R.string.AQY_Loc_Preset_Saved));
            updateRows(true);
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