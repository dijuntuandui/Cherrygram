/**
 * 爱琦艺客户端 - 回怼系统 词库列表页
 *
 * 列出本机保存的所有词库，可单独启停、编辑、删除。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.comeback;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiComebackCorpusEntry extends BaseCGPreferencesEntry {

    /** 词库条目的 id 基数，用基数 + 索引避免与固定条目冲突 */
    private static final int ROW_BASE = 400;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_Comeback_Corpus_Manage);
    }

    @Override
    protected String getKey() {
        return "aqy_comeback_corpus";
    }

    private AiqiyiComebackHelper helper() {
        return AiqiyiComebackHelper.getInstance();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ArrayList<AiqiyiComebackHelper.Corpus> corpora = helper().getCorpora();

        if (corpora.isEmpty()) {
            items.add(UItem.asHeader(getString(R.string.AQY_Comeback_Corpus_Header)));
            items.add(UItem.asShadow(getString(R.string.AQY_Comeback_Corpus_Empty)));
            return;
        }

        items.add(UItem.asHeader(getString(R.string.AQY_Comeback_Corpus_Header)));
        for (int i = 0; i < corpora.size(); i++) {
            AiqiyiComebackHelper.Corpus corpus = corpora.get(i);
            int lines = corpus.lines().size();
            items.add(SettingsHelper.asSwitchCG(ROW_BASE + i,
                            corpus.name,
                            String.format(getString(R.string.AQY_Comeback_Corpus_LinesFormat), lines))
                    .setChecked(corpus.enabled)
                    .slug("aqy_comeback_corpus_" + corpus.id)
            );
        }
        items.add(UItem.asShadow(getString(R.string.AQY_Comeback_Corpus_Manage_Note)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        ArrayList<AiqiyiComebackHelper.Corpus> corpora = helper().getCorpora();
        int index = item.id - ROW_BASE;
        if (index < 0 || index >= corpora.size()) {
            super.onClick(item, view, position, x, y);
            return;
        }

        final AiqiyiComebackHelper.Corpus corpus = corpora.get(index);

        // 点开关切换启用状态，长按或点其他区域再弹操作菜单
        helper().setCorpusEnabled(corpus.id, !corpus.enabled);
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        ArrayList<AiqiyiComebackHelper.Corpus> corpora = helper().getCorpora();
        int index = item.id - ROW_BASE;
        if (index < 0 || index >= corpora.size()) {
            return super.onLongClick(item, view, position, x, y);
        }
        showCorpusMenu(corpora.get(index));
        return true;
    }

    private void showCorpusMenu(AiqiyiComebackHelper.Corpus corpus) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(corpus.name)
                .setItems(new CharSequence[]{
                        getString(R.string.AQY_Comeback_Corpus_Edit),
                        getString(R.string.AQY_Comeback_Corpus_Delete)
                }, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        // 本页自己实现编辑，不借助未挂载的 Entry 实例
                        showCorpusEditorInline(corpus);
                    } else {
                        confirmDelete(corpus);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** 词库编辑弹窗（与 AiqiyiComebackEntry 中保持同一套字段，供本页复用） */
    private void showCorpusEditorInline(AiqiyiComebackHelper.Corpus corpus) {
        if (getParentActivity() == null || corpus == null) {
            return;
        }
        org.telegram.ui.Components.EditTextBoldCursor nameInput =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        nameInput.setSingleLine(true);
        nameInput.setHint(getString(R.string.AQY_Comeback_Corpus_Name_Hint));
        nameInput.setText(corpus.name);

        org.telegram.ui.Components.EditTextBoldCursor textInput =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        textInput.setHint(getString(R.string.AQY_Comeback_Corpus_Text_Hint));
        textInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setMinLines(6);
        textInput.setMaxLines(12);
        textInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        textInput.setText(corpus.text);

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, org.telegram.messenger.AndroidUtilities.dp(8),
                pad, org.telegram.messenger.AndroidUtilities.dp(8));
        container.addView(nameInput);
        container.addView(textInput);

        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.AQY_Comeback_Corpus_Edit))
                .setView(container)
                .setPositiveButton(getString(R.string.OK), (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String text = textInput.getText().toString();
                    if (android.text.TextUtils.isEmpty(name)) {
                        name = getString(R.string.AQY_Comeback_Corpus_Unnamed);
                    }
                    helper().updateCorpus(corpus.id, name, text);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void confirmDelete(AiqiyiComebackHelper.Corpus corpus) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.AQY_Comeback_Corpus_Delete))
                .setMessage(getString(R.string.AQY_Comeback_Corpus_Delete_Confirm))
                .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                    helper().deleteCorpus(corpus.id);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }
}