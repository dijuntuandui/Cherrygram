/**
 * 爱琦艺客户端 - 回怼系统 设置页
 *
 * 仅本地生效：所有词库与触发词均保存在本机，不上传服务器。
 * 请自行承担使用产生的后果，注意服务端频率限制。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.comeback;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiComebackEntry extends BaseCGPreferencesEntry {

    private final int enabledRow = 340;
    private final int triggerRow = 341;
    private final int stopTriggerRow = 342;
    private final int sendModeRow = 343;
    private final int sendSpeedRow = 344;
    private final int corpusManageRow = 345;
    private final int corpusAddRow = 346;
    private final int corpusClearRow = 347;

    private AiqiyiComebackHelper helper;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_Comeback_Title);
    }

    @Override
    protected String getKey() {
        return "aqy_comeback";
    }

    private AiqiyiComebackHelper helper() {
        if (helper == null) {
            helper = AiqiyiComebackHelper.getInstance();
        }
        return helper;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AiqiyiComebackHelper h = helper();

        items.add(UItem.asHeader(getString(R.string.AQY_Comeback_Header)));
        items.add(SettingsHelper.asSwitchCG(enabledRow,
                        getString(R.string.AQY_Comeback_Enabled),
                        getString(R.string.AQY_Comeback_Enabled_Desc))
                .setChecked(h.isEnabled())
                .slug("aqy_comeback_enabled")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Comeback_Note)));

        items.add(UItem.asHeader(getString(R.string.AQY_Comeback_Trigger_Header)));
        items.add(SettingsHelper.asTextDetail(triggerRow, 0,
                        getString(R.string.AQY_Comeback_Trigger), getTriggerText())
                .slug("aqy_comeback_trigger")
        );
        items.add(SettingsHelper.asTextDetail(stopTriggerRow, 0,
                        getString(R.string.AQY_Comeback_StopTrigger), getStopTriggerText())
                .slug("aqy_comeback_stop_trigger")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Comeback_Trigger_Note)));

        items.add(UItem.asHeader(getString(R.string.AQY_Comeback_Send_Header)));
        items.add(SettingsHelper.asTextDetail(sendModeRow, 0,
                        getString(R.string.AQY_Comeback_Mode), getSendModeText())
                .slug("aqy_comeback_mode")
        );
        items.add(SettingsHelper.asTextDetail(sendSpeedRow, 0,
                        getString(R.string.AQY_Comeback_Speed), getSendSpeedText())
                .slug("aqy_comeback_speed")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Comeback_Send_Note)));

        items.add(UItem.asHeader(getString(R.string.AQY_Comeback_Corpus_Header)));
        items.add(SettingsHelper.asTextDetail(corpusManageRow, 0,
                        getString(R.string.AQY_Comeback_Corpus_Manage), getCorpusCountText())
                .slug("aqy_comeback_corpus_manage")
        );
        items.add(SettingsHelper.asTextDetail(corpusAddRow, 0,
                        getString(R.string.AQY_Comeback_Corpus_Add), getString(R.string.AQY_Comeback_Corpus_Add_Desc))
                .slug("aqy_comeback_corpus_add")
        );
        items.add(UItem.asButton(corpusClearRow,
                        getString(R.string.AQY_Comeback_Corpus_Clear),
                        getString(R.string.AQY_Comeback_Corpus_Clear_Desc))
                .slug("aqy_comeback_corpus_clear")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Comeback_Corpus_Note)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        AiqiyiComebackHelper h = helper();

        if (item.id == enabledRow) {
            boolean next = !h.isEnabled();
            h.setEnabled(next);
            if (!next) {
                h.stopPendingMessages();
            }
            listView.adapter.update(true);
        } else if (item.id == triggerRow) {
            showInputDialog(getString(R.string.AQY_Comeback_Trigger), h.getTrigger(), value -> {
                h.setTrigger(value);
                listView.adapter.update(true);
            });
        } else if (item.id == stopTriggerRow) {
            showInputDialog(getString(R.string.AQY_Comeback_StopTrigger), h.getStopTrigger(), value -> {
                h.setStopTrigger(value);
                listView.adapter.update(true);
            });
        } else if (item.id == sendModeRow) {
            showSendModeDialog();
        } else if (item.id == sendSpeedRow) {
            showSendSpeedDialog();
        } else if (item.id == corpusManageRow) {
            presentFragment(new AiqiyiComebackCorpusEntry());
        } else if (item.id == corpusAddRow) {
            showCorpusEditor(null);
        } else if (item.id == corpusClearRow) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.AQY_Comeback_Corpus_Clear))
                    .setMessage(getString(R.string.AQY_Comeback_Corpus_Clear_Confirm))
                    .setPositiveButton(getString(R.string.OK), (d, w) -> {
                        helper().deleteAllCorpora();
                        listView.adapter.update(true);
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    // ==================== 文案 ====================

    private CharSequence getTriggerText() {
        String t = helper().getTrigger();
        return TextUtils.isEmpty(t) ? getString(R.string.AQY_Comeback_NotSet) : t;
    }

    private CharSequence getStopTriggerText() {
        String t = helper().getStopTrigger();
        return TextUtils.isEmpty(t) ? getString(R.string.AQY_Comeback_NotSet) : t;
    }

    private CharSequence getSendModeText() {
        return getString(helper().getSendMode() == AiqiyiComebackHelper.MODE_RANDOM
                ? R.string.AQY_Comeback_Mode_Random
                : R.string.AQY_Comeback_Mode_Sequential);
    }

    private CharSequence getSendSpeedText() {
        int speed = helper().getSendSpeed();
        if (speed <= 1) {
            return getString(R.string.AQY_Comeback_Speed_Normal);
        }
        return String.format(getString(R.string.AQY_Comeback_Speed_Format), speed);
    }

    private CharSequence getCorpusCountText() {
        int count = helper().getEnabledCorpusCount();
        if (count <= 0) {
            return getString(R.string.AQY_Comeback_Corpus_None);
        }
        return String.format(getString(R.string.AQY_Comeback_Corpus_CountFormat), count);
    }

    // ==================== 弹窗 ====================

    private interface OnTextResult {
        void onResult(String value);
    }

    private void showInputDialog(CharSequence title, String current, OnTextResult callback) {
        if (getParentActivity() == null) {
            return;
        }
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(current == null ? "" : current);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setHint(getString(R.string.AQY_Comeback_Input_Hint));

        new AlertDialog.Builder(getParentActivity())
                .setTitle(title)
                .setView(editText)
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    callback.onResult(editText.getText().toString().trim());
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void showSendModeDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AiqiyiComebackHelper h = helper();
        String[] baseOptions = new String[]{
                getString(R.string.AQY_Comeback_Mode_Sequential),
                getString(R.string.AQY_Comeback_Mode_Random)
        };
        int current = h.getSendMode() == AiqiyiComebackHelper.MODE_RANDOM ? 1 : 0;

        // 该版本 AlertDialog.Builder 无 setSingleChoiceItems，用勾选标记表示当前值
        CharSequence[] options = new CharSequence[baseOptions.length];
        for (int i = 0; i < baseOptions.length; i++) {
            options[i] = (i == current ? "✓ " : "     ") + baseOptions[i];
        }

        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.AQY_Comeback_Mode))
                .setItems(options, (dialog, which) -> {
                    dialog.dismiss();
                    h.setSendMode(which == 0
                            ? AiqiyiComebackHelper.MODE_SEQUENTIAL
                            : AiqiyiComebackHelper.MODE_RANDOM);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void showSendSpeedDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AiqiyiComebackHelper h = helper();
        String[] baseOptions = new String[]{
                getString(R.string.AQY_Comeback_Speed_1),
                getString(R.string.AQY_Comeback_Speed_2),
                getString(R.string.AQY_Comeback_Speed_3),
                getString(R.string.AQY_Comeback_Speed_5)
        };
        final int[] values = new int[]{1, 2, 3, 5};
        int current = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == h.getSendSpeed()) {
                current = i;
                break;
            }
        }

        // 同上，用勾选标记表示当前倍速
        CharSequence[] options = new CharSequence[baseOptions.length];
        for (int i = 0; i < baseOptions.length; i++) {
            options[i] = (i == current ? "✓ " : "     ") + baseOptions[i];
        }

        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.AQY_Comeback_Speed))
                .setItems(options, (dialog, which) -> {
                    dialog.dismiss();
                    h.setSendSpeed(values[which]);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** 词库编辑，id 为 null 表示新建 */
    void showCorpusEditor(AiqiyiComebackHelper.Corpus corpus) {
        if (getParentActivity() == null) {
            return;
        }
        boolean isNew = corpus == null;

        EditTextBoldCursor nameInput = new EditTextBoldCursor(getParentActivity());
        nameInput.setSingleLine(true);
        nameInput.setHint(getString(R.string.AQY_Comeback_Corpus_Name_Hint));
        nameInput.setText(isNew ? "" : corpus.name);

        EditTextBoldCursor textInput = new EditTextBoldCursor(getParentActivity());
        textInput.setHint(getString(R.string.AQY_Comeback_Corpus_Text_Hint));
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setMinLines(6);
        textInput.setMaxLines(12);
        textInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        textInput.setText(isNew ? "" : corpus.text);

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, org.telegram.messenger.AndroidUtilities.dp(8), pad, org.telegram.messenger.AndroidUtilities.dp(8));
        container.addView(nameInput);
        container.addView(textInput);

        new AlertDialog.Builder(getParentActivity())
                .setTitle(isNew
                        ? getString(R.string.AQY_Comeback_Corpus_Add)
                        : getString(R.string.AQY_Comeback_Corpus_Edit))
                .setView(container)
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String text = textInput.getText().toString();
                    if (TextUtils.isEmpty(name)) {
                        name = getString(R.string.AQY_Comeback_Corpus_Unnamed);
                    }
                    AiqiyiComebackHelper h = helper();
                    if (isNew) {
                        h.addCorpus(name, text);
                    } else {
                        h.updateCorpus(corpus.id, name, text);
                    }
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }
}