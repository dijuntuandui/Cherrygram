/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Copyright github.com/arsLan4k1390, 2022-2026.
 */

package uz.unnarsx.cherrygram.preferences;

import static org.telegram.messenger.LocaleController.getString;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.community.CommunitySheet;
import org.telegram.ui.community.cells.CommunityLinkView;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.core.configs.CherrygramCoreConfig;
import uz.unnarsx.cherrygram.core.firebase.FirebaseAnalyticsHelper;
import uz.unnarsx.cherrygram.core.helpers.CGResourcesHelper;
import uz.unnarsx.cherrygram.core.helpers.DeeplinkHelper;
import uz.unnarsx.cherrygram.misc.Constants;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AboutPreferencesEntry extends BaseCGPreferencesEntry {

    private final int readmeRow = 1;
    private final int updatesRow = 2;
    private final int debugPrefsRow = 3;

    private final int channelRow = 4;
    private final int betaChannelRow = 5;
    private final int chatRow = 6;
    private final int offtopicChatRow = 7;
    private final int communityRow = 8;
    private final int githubRow = 9;
    private final int crowdinRow = 10;
    private final int policyRow = 11;

    private TLRPC.Chat cherrygramSupportChat;
    private boolean cherrygramSupportChatLoaded;

    @Override
    protected CharSequence getTitle() {
        FirebaseAnalyticsHelper.INSTANCE.trackEventWithEmptyBundle("about_preferences_screen");
        return getString(R.string.CGP_Header_About);
    }

    @Override
    protected String getKey() {
        return DeeplinkHelper.DeepLinksRepo.CG_About;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.Info)));
        items.add(
                SettingsHelper.asTextDetail(
                        readmeRow,
                        0,
                        CGResourcesHelper.getAppName() + " " + CGResourcesHelper.getCherryVersion() + " | " + "Telegram " + BuildVars.BUILD_VERSION_STRING,
                        getString(R.string.CGP_About_Desc)
                )
        );
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        // Aiqiyi: 关于页仅保留版本号展示，移除全部官方跳转入口
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        // Aiqiyi: 已移除全部官方链接复制入口
        return false;
    }

    public static String getLastCheckUpdateTime() {
        return getString(R.string.UP_LastCheck) + ": " + LocaleController.formatDateTime(SharedConfig.lastUpdateCheckTime / 1000, true);
    }

    /*private boolean isInChannel(long chatID) {
        TLRPC.Chat chat = getMessagesController().getChat(chatID);
        return chat != null && ChatObject.isInChat(chat);
    }

    private void openChannel(long chatID) {
        if (isInChannel(chatID)) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatID);
            presentFragment(new ChatActivity(args));
        }
    }*/

    protected void getChatWithFallback(
            long chatID,
            String username,
            Utilities.Callback<TLRPC.Chat> callback
    ) {
        TLRPC.Chat chat = getMessagesController().getChat(-chatID);

        if (chat != null) {
            callback.run(chat);
            return;
        }

        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;

        getConnectionsManager().sendRequest(req, (res, err) -> {
            if (err != null || !(res instanceof TLRPC.TL_contacts_resolvedPeer resolvedPeer)) {
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }

            if (resolvedPeer.chats == null || resolvedPeer.chats.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }

            getMessagesController().putUsers(resolvedPeer.users, false);
            getMessagesController().putChats(resolvedPeer.chats, false);
            getMessagesStorage().putUsersAndChats(resolvedPeer.users, resolvedPeer.chats, false, true);

            TLRPC.Chat loadedChat = resolvedPeer.chats.get(0);
            AndroidUtilities.runOnUIThread(() -> callback.run(loadedChat));
        });
    }

}
