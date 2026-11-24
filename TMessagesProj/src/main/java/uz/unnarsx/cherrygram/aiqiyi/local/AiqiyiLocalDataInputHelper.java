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
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;

public final class AiqiyiLocalDataInputHelper {

    public static final int TYPE_USER_ID = 1;
    /** 他人资料 - 本地名称 */
    public static final int TYPE_PEER_NAME = 2;
    /** 他人资料 - 本地简介 */
    public static final int TYPE_PEER_BIO = 3;

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

    /** 本人主页 ID 行点击后弹出的菜单 */
    public static void showIdMenu(org.telegram.ui.ActionBar.BaseFragment fragment, android.view.View anchor, Runnable onChanged) {
        if (fragment == null || fragment.getParentActivity() == null || anchor == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        options.add(R.drawable.msg_edit, LocaleController.getString(R.string.AQY_Menu_CustomId),
                () -> show(fragment, TYPE_USER_ID, onChanged));
        options.add(R.drawable.msg_reset, LocaleController.getString(R.string.AQY_Menu_CustomIdClear), () -> {
            AiqiyiLocalProfileHelper.setUserId("");
            if (onChanged != null) onChanged.run();
        });
        options.show();
    }

    // ========================================================================
    // [Aiqiyi local] 他人资料本地覆盖：名称 / 简介
    // 与「本人资料」的分支差别是这里需要携带目标 userId。
    // 写盘时一律走 AiqiyiLocalPeerName，键为 userId，不涉及任何姓名文本。
    // ========================================================================

    public static void showPeer(BaseFragment fragment, long userId, int type, Runnable onDone) {
        if (fragment == null || fragment.getParentActivity() == null || userId <= 0) {
            return;
        }
        if (type != TYPE_PEER_NAME && type != TYPE_PEER_BIO) {
            return;
        }
        final int titleRes;
        final int hintRes;
        final String current;
        if (type == TYPE_PEER_NAME) {
            titleRes = R.string.AQY_Menu_PeerName;
            hintRes = R.string.AQY_Menu_PeerNameHint;
            String v = AiqiyiLocalPeerName.getName(UserConfig.selectedAccount, userId);
            current = v == null ? "" : v;
        } else {
            titleRes = R.string.AQY_Menu_PeerBio;
            hintRes = R.string.AQY_Menu_PeerBioHint;
            String v = AiqiyiLocalPeerName.getBio(UserConfig.selectedAccount, userId);
            current = v == null ? "" : v;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(titleRes));

        final EditTextBoldCursor editText = new EditTextBoldCursor(fragment.getParentActivity());
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHint(LocaleController.getString(hintRes));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        if (type == TYPE_PEER_NAME) {
            editText.setSingleLine(true);
        } else {
            editText.setSingleLine(false);
            editText.setMaxLines(5);
        }
        editText.setBackground(null);
        editText.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(4));
        if (!TextUtils.isEmpty(current)) {
            editText.setText(current);
            editText.setSelection(current.length());
        }

        builder.setView(editText, AndroidUtilities.dp(type == TYPE_PEER_NAME ? 64 : 120));
        builder.setPositiveButton(LocaleController.getString(R.string.AQY_Menu_OK), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            if (type == TYPE_PEER_NAME) {
                AiqiyiLocalPeerName.setName(UserConfig.selectedAccount, userId, value);
            } else {
                AiqiyiLocalPeerName.setBio(UserConfig.selectedAccount, userId, value);
            }
            if (onDone != null) {
                onDone.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    /** 他人资料页长按 / 三点菜单 */
    public static void showPeerMenu(BaseFragment fragment, android.view.View anchor, long userId, Runnable onChanged) {
        if (fragment == null || fragment.getParentActivity() == null || anchor == null || userId <= 0) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        options.add(R.drawable.msg_edit, LocaleController.getString(R.string.AQY_Menu_PeerName),
                () -> showPeer(fragment, userId, TYPE_PEER_NAME, onChanged));
        options.add(R.drawable.msg_edit, LocaleController.getString(R.string.AQY_Menu_PeerBio),
                () -> showPeer(fragment, userId, TYPE_PEER_BIO, onChanged));
        if (AiqiyiLocalPeerName.hasAny(UserConfig.selectedAccount, userId)) {
            options.addGap();
            options.add(R.drawable.msg_reset, LocaleController.getString(R.string.AQY_Menu_PeerResetAll), () -> {
                AiqiyiLocalPeerName.clearAll(UserConfig.selectedAccount, userId);
                if (onChanged != null) onChanged.run();
            });
        }
        options.show();
    }
}
