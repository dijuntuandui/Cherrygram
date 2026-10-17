/**
 * 爱琦艺客户端 - 本地数据输入弹窗（仅自己可见）
 *
 * 只用一个 EditText 直接给 AlertDialog.setView(view, height)，
 * 不套 FrameLayout，避开自定义容器高度测量为 0 的问题。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.text.InputType;
import android.text.TextUtils;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;

public final class AiqiyiLocalDataInputHelper {

    public static final int TYPE_USER_ID = 1;

    private AiqiyiLocalDataInputHelper() {
    }

    public static void show(BaseFragment fragment, int type, Runnable onDone) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final int titleRes;
        final int hintRes;
        final String current;
        if (type == TYPE_USER_ID) {
            titleRes = R.string.AQY_Menu_CustomId;
            hintRes = R.string.AQY_Menu_CustomIdHint;
            current = AiqiyiLocalProfileHelper.hasUserId()
                    ? AiqiyiLocalProfileHelper.getFormattedUserId() : "";
        } else {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(titleRes));

        final EditTextBoldCursor editText = new EditTextBoldCursor(fragment.getParentActivity());
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(LocaleController.getString(hintRes));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setSingleLine(true);
        editText.setBackground(null);
        editText.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(4));
        if (!TextUtils.isEmpty(current)) {
            editText.setText(current);
            editText.setSelection(current.length());
        }

        // 关键：直接把 EditText 当 customView，并显式给高度
        builder.setView(editText, AndroidUtilities.dp(64));
        builder.setPositiveButton(LocaleController.getString(R.string.AQY_Menu_OK), (dialog, which) -> {
            if (type == TYPE_USER_ID) {
                AiqiyiLocalProfileHelper.setUserId(editText.getText().toString());
            }
            if (onDone != null) {
                onDone.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
