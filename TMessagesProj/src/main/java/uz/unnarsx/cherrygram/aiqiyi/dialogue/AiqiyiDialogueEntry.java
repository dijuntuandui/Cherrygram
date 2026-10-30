/**
 * 爱琦艺客户端 - 对白气泡 设置页
 *
 * 三段式：
 *   剧本 —— 导入 / 粘贴 / 预览 / 清空
 *   生效 —— 总开关 / 限定会话 / 播放状态
 *   进度 —— 重置指针 / 解除连续失败停止
 *
 * 仅本地生效：剧本保存在本机，不会上传服务器，也不修改真实消息。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.dialogue;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiDialogueEntry extends BaseCGPreferencesEntry {

    private final int enabledRow = 700;
    private final int importRow = 701;
    private final int pasteRow = 702;
    private final int previewRow = 703;
    private final int scopeRow = 704;
    private final int statusRow = 705;
    private final int resetCursorRow = 706;
    private final int resetStopRow = 707;
    private final int clearRow = 708;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_DialogueBubble);
    }

    @Override
    protected String getKey() {
        return "aqy_dialogue_bubble";
    }

    private int account() {
        return UserConfig.selectedAccount;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean on = AiqiyiScriptStore.isEnabled();

        // ==================== 剧本 ====================
        items.add(UItem.asHeader(getString(R.string.AQY_Dialogue_Script_Header)));
        items.add(SettingsHelper.asTextDetail(importRow, 0,
                        getString(R.string.AQY_Dialogue_Import),
                        getScriptSummary())
                .slug("aqy_dialog_import")
        );
        items.add(SettingsHelper.asTextDetail(pasteRow, 0,
                        getString(R.string.AQY_Dialogue_Paste),
                        getString(R.string.AQY_Dialogue_Paste_Desc))
                .slug("aqy_dialog_paste")
        );
        items.add(SettingsHelper.asTextDetail(previewRow, 0,
                        getString(R.string.AQY_Dialogue_Preview),
                        getRoleSummary())
                .slug("aqy_dialog_preview")
        );
        items.add(SettingsHelper.asTextDetail(clearRow, 0,
                        getString(R.string.AQY_Dialogue_Clear),
                        getString(R.string.AQY_Dialogue_Clear_Desc))
                .slug("aqy_dialog_clear")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Dialogue_Script_Note)));

        // ==================== 生效 ====================
        items.add(UItem.asHeader(getString(R.string.AQY_Dialogue_Enable_Header)));
        items.add(SettingsHelper.asSwitchCG(enabledRow,
                        getString(R.string.AQY_DialogueBubble),
                        getString(R.string.AQY_DialogueBubble_Desc))
                .setChecked(on)
                .slug("aqy_dialog_enabled")
        );
        items.add(SettingsHelper.asTextDetail(scopeRow, 0,
                        getString(R.string.AQY_Dialogue_Scope),
                        getScopeText())
                .slug("aqy_dialog_scope")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_Dialogue_Enable_Note)));

        // ==================== 播放控制 ====================
        if (on) {
            items.add(UItem.asHeader(getString(R.string.AQY_Dialogue_Status_Header)));
            items.add(UItem.asShadow(getStatusText()));
            items.add(SettingsHelper.asTextDetail(resetCursorRow, 0,
                            getString(R.string.AQY_Dialogue_Reset_Cursor),
                            getString(R.string.AQY_Dialogue_Reset_Cursor_Desc))
                    .slug("aqy_dialog_reset_cursor")
            );
            items.add(SettingsHelper.asTextDetail(resetStopRow, 0,
                            getString(R.string.AQY_Dialogue_Reset_Stop),
                            getString(R.string.AQY_Dialogue_Reset_Stop_Desc))
                    .slug("aqy_dialog_reset_stop")
            );
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == enabledRow) {
            String err = AiqiyiScriptStore.validate();
            if (!AiqiyiScriptStore.isEnabled() && err != null) {
                showTip(err);
                return;
            }
            AiqiyiScriptStore.setEnabled(!AiqiyiScriptStore.isEnabled());
            SettingsHelper.updateCheckState(view, AiqiyiScriptStore.isEnabled());
            listView.adapter.update(true);
        } else if (item.id == importRow) {
            showScriptEditor();
        } else if (item.id == pasteRow) {
            showPasteEditor();
        } else if (item.id == previewRow) {
            showPreviewDialog();
        } else if (item.id == clearRow) {
            showClearDialog();
        } else if (item.id == scopeRow) {
            showScopeDialog();
        } else if (item.id == resetCursorRow) {
            AiqiyiScriptStore.resetAllCursors();
            AiqiyiScriptStore.clearAllAssigned();
            showTip(getString(R.string.AQY_Dialogue_Reset_Cursor_Done));
            listView.adapter.update(true);
        } else if (item.id == resetStopRow) {
            AiqiyiDialogueEngine.clearAllStopped();
            showTip(getString(R.string.AQY_Dialogue_Reset_Stop_Done));
            listView.adapter.update(true);
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    // ==================== 摘要文案 ====================

    private String getScriptSummary() {
        int n = AiqiyiScriptStore.getLineCount();
        if (n <= 0) {
            return getString(R.string.AQY_Dialogue_No_Script);
        }
        return n + " " + getString(R.string.AQY_Dialogue_Lines);
    }

    private String getRoleSummary() {
        List<String> roles = AiqiyiScriptStore.getRoles();
        if (roles.isEmpty()) {
            return getString(R.string.AQY_Dialogue_No_Script);
        }
        if (roles.size() <= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < roles.size(); i++) {
                if (i > 0) sb.append(" · ");
                sb.append(roles.get(i));
            }
            return sb.toString();
        }
        return roles.get(0) + " · " + roles.get(1) + " · +" + (roles.size() - 2);
    }

    private String getScopeText() {
        long scope = AiqiyiScriptStore.getScopeDialogId();
        if (scope == 0L) {
            return getString(R.string.AQY_Dialogue_Scope_All);
        }
        return getString(R.string.AQY_Dialogue_Scope_One);
    }

    private String getStatusText() {
        long scope = AiqiyiScriptStore.getScopeDialogId();
        if (scope == 0L) {
            return getString(R.string.AQY_Dialogue_Status_All_Note);
        }
        int acc = account();
        StringBuilder sb = new StringBuilder();
        sb.append(AiqiyiDialogueEngine.getStatusText(acc, scope));
        int miss = AiqiyiDialogueEngine.getMissStreak(acc, scope);
        if (miss > 0 && !AiqiyiDialogueEngine.isStopped(acc, scope)) {
            sb.append("\n").append(getString(R.string.AQY_Dialogue_Miss_Prefix)).append(miss);
        }
        return sb.toString();
    }

    // ==================== 剧本编辑 ====================

    private void showScriptEditor() {
        if (getParentActivity() == null) return;
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(1, 15);
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Dialogue_Script_Hint));
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(8);
        editText.setMaxLines(14);
        editText.setFocusable(true);
        editText.requestFocus();
        editText.setText(AiqiyiScriptStore.getRaw());

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Dialogue_Script_Header));
        b.setView(container);
        b.setPositiveButton(getString(R.string.Save), (d, w) -> {
            AiqiyiScriptStore.setRaw(editText.getText().toString());
            AiqiyiScriptStore.clearAllAssigned();
            AiqiyiDialogueEngine.clearAllStopped();
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showPasteEditor() {
        if (getParentActivity() == null) return;
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(1, 15);
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Dialogue_Paste_Hint));
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(6);
        editText.setMaxLines(12);
        editText.setFocusable(true);
        editText.requestFocus();

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Dialogue_Paste));
        b.setView(container);
        b.setPositiveButton(getString(R.string.AQY_Dialogue_Append_Btn), (d, w) -> {
            String add = editText.getText().toString();
            if (add.trim().isEmpty()) {
                return;
            }
            String old = AiqiyiScriptStore.getRaw();
            AiqiyiScriptStore.setRaw(old.isEmpty() ? add : (old + "\n" + add));
            AiqiyiScriptStore.clearAllAssigned();
            AiqiyiDialogueEngine.clearAllStopped();
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showPreviewDialog() {
        if (getParentActivity() == null) return;
        List<AiqiyiScriptStore.Line> lines = AiqiyiScriptStore.getLines();
        if (lines.isEmpty()) {
            showTip(getString(R.string.AQY_Dialogue_No_Script));
            return;
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(lines.size(), 60);
        for (int i = 0; i < limit; i++) {
            AiqiyiScriptStore.Line line = lines.get(i);
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(line.role).append("：").append(line.text);
        }
        if (lines.size() > limit) {
            sb.append("\n…").append(getString(R.string.AQY_Dialogue_Preview_More))
                    .append(lines.size() - limit).append(getString(R.string.AQY_Dialogue_Lines));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Dialogue_Preview));
        b.setMessage(sb.toString());
        b.setPositiveButton(getString(R.string.OK), null);
        showDialog(b.create());
    }

    private void showClearDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Dialogue_Clear));
        b.setMessage(getString(R.string.AQY_Dialogue_Clear_Confirm));
        b.setPositiveButton(getString(R.string.Delete), (d, w) -> {
            AiqiyiScriptStore.clearRaw();
            AiqiyiScriptStore.setEnabled(false);
            AiqiyiScriptStore.clearAllAssigned();
            AiqiyiDialogueEngine.clearAllStopped();
            listView.adapter.update(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showScopeDialog() {
        if (getParentActivity() == null) return;
        CharSequence[] options = new CharSequence[]{
                getString(R.string.AQY_Dialogue_Scope_All),
                getString(R.string.AQY_Dialogue_Scope_Pick)
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Dialogue_Scope));
        b.setItems(options, (d, which) -> {
            if (which == 0) {
                AiqiyiScriptStore.setScopeDialogId(0L);
                AiqiyiDialogueEngine.clearAllStopped();
                listView.adapter.update(true);
            } else {
                showTip(getString(R.string.AQY_Dialogue_Scope_Pick_Hint));
            }
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showTip(String text) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_DialogueBubble));
        b.setMessage(text);
        b.setPositiveButton(getString(R.string.OK), null);
        showDialog(b.create());
    }
}