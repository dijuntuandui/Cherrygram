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

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.NotificationsService;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.UsersSelectActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import uz.unnarsx.cherrygram.core.CherrygramLogger;
import uz.unnarsx.cherrygram.aiqiyi.cleanup.AiqiyiCleanup;
import uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig;
import uz.unnarsx.cherrygram.core.configs.CherrygramCoreConfig;
import uz.unnarsx.cherrygram.core.configs.CherrygramMessagesConfig;
import uz.unnarsx.cherrygram.core.firebase.FirebaseAnalyticsHelper;
import uz.unnarsx.cherrygram.core.helpers.DeeplinkHelper;
import uz.unnarsx.cherrygram.helpers.ui.PopupHelper;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class GeneralPreferencesEntry extends BaseCGPreferencesEntry {

    private final int springAnimationRow = 1;
    private final int actionbarCrossfadeRow = 2;
    private final int predictiveBackRow = 3;

    private final int silenceNonContactsRow = 4;

    private final int ignoreMentionsRow = 5;
    private final int ignoreMentionsExclusionsRow = 6;
    private final int ignoreMentionsAutoReadRow = 7;

    private final int defaultNotificationIconRow = 8;
    private final int residentNotificationRow = 9;

    private final int hideStoriesRow = 10;
    private final int archiveStoriesRow = 11;
    private final int archiveStoriesUsersRow = 12;
    private final int archiveStoriesChannelsRow = 13;

    private final int useSystemEmojiRow = 14;
    private final int useSystemFontsRow = 15;
    private final int tabledModeRow = 16;

    private final int downloadSpeedBoostRow = 17;
    private final int uploadSpeedBoostRow = 18;
    private final int slowNetworkMode = 19;

    private final int aiqiyiCustomTitleRow = 20;
    private final int aiqiyiInputHintEnabledRow = 27;
    private final int aiqiyiInputHintTextRow = 28;
    private final int aiqiyiTranslateBarRow = 21;
    private final int aiqiyiSaveDeletedRow = 22;
    private final int aiqiyiTranslucentDeletedRow = 23;
    private final int aiqiyiUseDeletedIconRow = 24;
    private final int aiqiyiCustomDeletedMarkRow = 25;
    private final int aiqiyiGhostModeRow = 26;
    private final int aiqiyiAutoSignInRow = 30;
    private final int aiqiyiSelfCertRow = 31;
    private final int aiqiyiLocalZoneRow = 32;
    private final int aiqiyiComebackRow = 36;
    private final int aiqiyiUnlimitedPinsRow = 33;
    private final int aiqiyiUnlimitedFoldersRow = 34;
    private final int aiqiyiUnlimitedFavedStickersRow = 35;

    // ===== 第一批（黄昏迁移）=====
    private final int aiqiyiSpecialAttentionRow = 37;
    private final int aiqiyiHourlyChimeRow = 38;
    private final int aiqiyiBookmarksRow = 40;
    private final int aiqiyiBookmarksListRow = 44;
    private final int aiqiyiSyntaxHighlightRow = 41;
    private final int aiqiyiExtensionCleanupRow = 42;
    private final int aiqiyiFakeLocationRow = 43;
    private final int aiqiyiDialogueBubbleRow = 45;
    private final int aiqiyiActiveZoneRow = 46;
    private final int aiqiyiZoneEmojiRow = 47;
    private final int aiqiyiZoneDirectionRow = 48;
    private final int aiqiyiZoneTargetRow = 49;
    private final int aiqiyiCleanupRow = 50;
    private final int aiqiyiCleanupTotalRow = 51;
    /** 分类清理行占 60..64，避开其他 row id */
    private static final int CLEANUP_ROW_BASE = 60;

    private boolean expandedArchiveStoriesSection = false;

    private static final Set<String> ARCHIVE_STORIES_SECTION_SLUGS = Set.of(
            ""
    );

    @Override
    protected CharSequence getTitle() {
        FirebaseAnalyticsHelper.INSTANCE.trackEventWithEmptyBundle("general_preferences_screen");
        return getString(R.string.AP_Header_General);
    }

    @Override
    protected String getKey() {
        return DeeplinkHelper.DeepLinksRepo.CG_General;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.LiteMode)));
        items.add(UItem.asButton(springAnimationRow, getString(R.string.EP_NavigationAnimation), getSpringValue())
                .slug("navigation")
        );
        if (CherrygramCoreConfig.INSTANCE.getSpringAnimation() == CherrygramCoreConfig.ANIMATION_SPRING) {
            items.add(SettingsHelper.asSwitchCG(actionbarCrossfadeRow, getString(R.string.EP_NavigationAnimationCrossfading))
                    .setChecked(CherrygramCoreConfig.INSTANCE.getActionbarCrossfade())
                    .slug("actionbarCrossfade")
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            items.add(SettingsHelper.asSwitchCG(predictiveBackRow, getString(R.string.CG_PredictiveBackAnimation))
                    .setChecked(CherrygramCoreConfig.INSTANCE.getPredictiveBack())
                    .slug("predictiveBack")
            );
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.SettingsNotifications)));
        items.add(SettingsHelper.asSwitchCG(silenceNonContactsRow, getString(R.string.CP_SilenceNonContacts), getString(R.string.CP_SilenceNonContacts_Desc))
                .setChecked(CherrygramCoreConfig.INSTANCE.getSilenceNonContacts())
                .slug("silenceNonContacts")
        );
        items.add(SettingsHelper.asSwitchCG(defaultNotificationIconRow, getString(R.string.AP_Old_Notification_Icon))
                .setChecked(CherrygramCoreConfig.INSTANCE.getOldNotificationIcon())
                .slug("oldNotifIcon")
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            items.add(SettingsHelper.asSwitchCG(residentNotificationRow, getString(R.string.CG_ResidentNotification), getString(R.string.NotificationsService))
                    .setChecked(CherrygramCoreConfig.INSTANCE.getResidentNotification())
                    .slug("residentNotification")
            );
        }
        items.add(UItem.asShadow(null));

        items.add(
                SettingsHelper.asSwitchCG(
                        ignoreMentionsRow,
                        SettingsHelper.applyNewSpan(getString(R.string.CG_IgnoreMentions)),
                        getString(R.string.CG_IgnoreMentionsDesc)
                )
                .setChecked(CherrygramCoreConfig.INSTANCE.getIgnoreMentions())
                .slug("ignoreMentions")
        );

        if (CherrygramCoreConfig.INSTANCE.getIgnoreMentions()) {
            items.add(UItem.asButton(ignoreMentionsExclusionsRow, R.drawable.msg_mention, getString(R.string.CG_IgnoreMentionsIgnoredChats), String.valueOf(getChatsNotificationHelper().getIgnoredChatsCount())));

            items.add(
                    SettingsHelper.asSwitchCG(
                            ignoreMentionsAutoReadRow,
                            getString(R.string.CG_IgnoreMentionsAutoRead),
                            getString(R.string.CG_IgnoreMentionsAutoReadDesc)
                    )
                    .setChecked(CherrygramCoreConfig.INSTANCE.getIgnoreMentionsMarkAsRead())
            );
        }

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.FilterStories)));
        items.add(SettingsHelper.asSwitchCG(hideStoriesRow, getString(R.string.CP_HideStories), getString(R.string.CP_HideStories_Desc))
                .setChecked(CherrygramCoreConfig.INSTANCE.getHideStories())
                .slug("hideStories")
        );

        items.add(
                SettingsHelper.asExpandableSwitch(
                        archiveStoriesRow,
                        R.drawable.msg_archive,
                        getString(R.string.CP_ArchiveStories),
                        getArchiveStoriesCountText()
                )
                .setChecked(CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromUsers() || CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromChannels())
                .setCollapsed(!expandedArchiveStoriesSection)
                .setClickCallback(v -> {
                    boolean newValue = !(CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromUsers() || CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromChannels());

                    CherrygramCoreConfig.INSTANCE.setArchiveStoriesFromUsers(newValue);
                    CherrygramCoreConfig.INSTANCE.setArchiveStoriesFromChannels(newValue);

                    expandedArchiveStoriesSection = !expandedArchiveStoriesSection;
                    updateRows(true);
                })
                .slug("archiveStories")
        );
        if (expandedArchiveStoriesSection) {
            items.add(UItem.asRoundCheckbox(archiveStoriesUsersRow, getString(R.string.FilterContacts))
                    .setChecked(CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromUsers())
                    .setPad(1)
            );

            items.add(UItem.asRoundCheckbox(archiveStoriesChannelsRow, getString(R.string.FilterChannels))
                    .setChecked(CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromChannels())
                    .setPad(1)
            );
        }

        items.add(UItem.asShadow(getString(R.string.CP_ArchiveStories_Desc)));

        items.add(UItem.asHeader(getString(R.string.LocalMiscellaneousCache)));
        items.add(SettingsHelper.asSwitchCG(useSystemEmojiRow, getString(R.string.AP_SystemEmoji))
                .setChecked(CherrygramCoreConfig.INSTANCE.getSystemEmoji())
                .slug("systemEmoji")
        );
        items.add(SettingsHelper.asSwitchCG(useSystemFontsRow, getString(R.string.AP_SystemFonts))
                .setChecked(CherrygramCoreConfig.INSTANCE.getSystemFonts())
                .slug("systemFonts")
        );
        items.add(UItem.asButton(tabledModeRow, getString(R.string.AP_Tablet_Mode), getTabletModeValue())
                .slug("tabletMode")
        );
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.EP_Network)));
        items.add(UItem.asButton(downloadSpeedBoostRow, getString(R.string.EP_DownloadSpeedBoost), getDownloadSpeedBoostText())
                .slug("downloadSpeedBoost")
        );
        items.add(SettingsHelper.asSwitchCG(uploadSpeedBoostRow, getString(R.string.EP_UploadloadSpeedBoost))
                .setChecked(CherrygramCoreConfig.INSTANCE.getUploadSpeedBoost())
                .slug("uploadSpeedBoost")
        );
        items.add(SettingsHelper.asSwitchCG(slowNetworkMode, getString(R.string.EP_SlowNetworkMode))
                .setChecked(CherrygramCoreConfig.INSTANCE.getSlowNetworkMode())
                .slug("slowNetworkMode")
        );
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AQY_Plugins)));
        items.add(SettingsHelper.asSwitchCG(aiqiyiTranslateBarRow, getString(R.string.AQY_TranslateBar), getString(R.string.AQY_TranslateBar_Desc))
                .setChecked(CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslateBarAlways())
                .slug("aiqiyi_translate_bar")
        );
        items.add(SettingsHelper.asTextDetail(aiqiyiCustomTitleRow, 0, getString(R.string.AQY_CustomTitle), getCustomTitleText())
                .slug("aiqiyi_custom_title")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiInputHintEnabledRow, getString(R.string.AQY_InputHint), getString(R.string.AQY_InputHint_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiInputHintEnabled())
                .slug("aiqiyi_input_hint_enabled")
        );
        items.add(SettingsHelper.asTextDetail(aiqiyiInputHintTextRow, 0, getString(R.string.AQY_InputHintText), getInputHintText())
                .slug("aiqiyi_input_hint_text")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiSaveDeletedRow, getString(R.string.AQY_SaveDeleted), getString(R.string.AQY_SaveDeleted_Desc))
                .setChecked(CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages())
                .slug("aiqiyi_save_deleted")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiTranslucentDeletedRow, getString(R.string.AQY_TranslucentDeleted), getString(R.string.AQY_TranslucentDeleted_Desc))
                .setChecked(CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslucentDeleted())
                .slug("aiqiyi_translucent_deleted")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiUseDeletedIconRow, getString(R.string.AQY_UseDeletedIcon), getString(R.string.AQY_UseDeletedIcon_Desc))
                .setChecked(CherrygramMessagesConfig.INSTANCE.getAiqiyiUseDeletedIcon())
                .slug("aiqiyi_use_deleted_icon")
        );
        items.add(SettingsHelper.asTextDetail(aiqiyiCustomDeletedMarkRow, 0, getString(R.string.AQY_CustomDeletedMark), getCustomDeletedMarkText())
                .slug("aiqiyi_custom_deleted_mark")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiGhostModeRow, getString(R.string.AQY_GhostMode), getString(R.string.AQY_GhostMode_Desc))
                .setChecked(CherrygramMessagesConfig.INSTANCE.getAiqiyiGhostMode())
                .slug("aiqiyi_ghost_mode")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiUnlimitedPinsRow, getString(R.string.AQY_UnlimitedPins), getString(R.string.AQY_UnlimitedPins_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedPins())
                .slug("aiqiyi_unlimited_pins")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiUnlimitedFoldersRow, getString(R.string.AQY_UnlimitedFolders), getString(R.string.AQY_UnlimitedFolders_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedFolders())
                .slug("aiqiyi_unlimited_folders")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiUnlimitedFavedStickersRow, getString(R.string.AQY_UnlimitedFavedStickers), getString(R.string.AQY_UnlimitedFavedStickers_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedFavedStickers())
                .slug("aiqiyi_unlimited_faved_stickers")
        );
        // ===== 第一批（黄昏迁移）=====
        items.add(SettingsHelper.asSwitchCG(aiqiyiSpecialAttentionRow, getString(R.string.AQY_SpecialAttention), getString(R.string.AQY_SpecialAttention_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiSpecialAttention())
                .slug("aiqiyi_special_attention")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiHourlyChimeRow, getString(R.string.AQY_HourlyChime), getString(R.string.AQY_HourlyChime_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiHourlyChime())
                .slug("aiqiyi_hourly_chime")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiBookmarksRow, getString(R.string.AQY_Bookmarks), getString(R.string.AQY_Bookmarks_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiBookmarks())
                .slug("aiqiyi_bookmarks")
        );
        // [Aiqiyi bookmark] 开关下方再给一行入口，点了直接看书签列表
        if (CherrygramAppearanceConfig.INSTANCE.getAiqiyiBookmarks()) {
            items.add(SettingsHelper.asTextDetail(aiqiyiBookmarksListRow, 0,
                    getString(R.string.AQY_BookmarksTitle), getBookmarkSummary())
                    .slug("aiqiyi_bookmarks_list")
            );
        }
        items.add(SettingsHelper.asSwitchCG(aiqiyiSyntaxHighlightRow, getString(R.string.AQY_SyntaxHighlight), getString(R.string.AQY_SyntaxHighlight_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiSyntaxHighlight())
                .slug("aiqiyi_syntax_highlight")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiExtensionCleanupRow, getString(R.string.AQY_ExtensionCleanup), getString(R.string.AQY_ExtensionCleanup_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiExtensionCleanup())
                .slug("aiqiyi_extension_cleanup")
        );
        if (true) {
            items.add(SettingsHelper.asTextDetail(aiqiyiCleanupTotalRow, 0,
                    getString(R.string.AQY_Cleanup_Total), getCleanupTotalText())
                    .slug("aiqiyi_cleanup_total")
            );
            for (int ci = 0; ci < AiqiyiCleanup.CAT_COUNT; ci++) {
                items.add(SettingsHelper.asTextDetail(CLEANUP_ROW_BASE + ci, 0,
                        getCleanupCategoryName(ci), getCleanupCategorySize(ci))
                        .slug("aiqiyi_cleanup_cat_" + ci)
                );
            }
        }
        items.add(SettingsHelper.asSwitchCG(aiqiyiFakeLocationRow, getString(R.string.AQY_FakeLocation), getString(R.string.AQY_FakeLocation_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiFakeLocation())
                .slug("aiqiyi_fake_location")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiDialogueBubbleRow, getString(R.string.AQY_DialogueBubble), getString(R.string.AQY_DialogueBubble_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiDialogueBubble())
                .slug("aiqiyi_dialogue_bubble")
        );
        items.add(SettingsHelper.asSwitchCG(aiqiyiActiveZoneRow, getString(R.string.AQY_ActiveZone), getString(R.string.AQY_ActiveZone_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZone())
                .slug("aiqiyi_active_zone")
        );
        if (CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZone()) {
            items.add(SettingsHelper.asTextDetail(aiqiyiZoneEmojiRow, 0,
                    getString(R.string.AQY_Zone_Emoji),
                    CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneEmoji())
                    .slug("aiqiyi_zone_emoji")
            );
            items.add(SettingsHelper.asTextDetail(aiqiyiZoneDirectionRow, 0,
                    getString(R.string.AQY_Zone_Direction), getZoneDirectionText())
                    .slug("aiqiyi_zone_direction")
            );
            items.add(SettingsHelper.asTextDetail(aiqiyiZoneTargetRow, 0,
                    getString(R.string.AQY_Zone_Target), getZoneTargetText())
                    .slug("aiqiyi_zone_target")
            );
        }
items.add(SettingsHelper.asTextDetail(aiqiyiAutoSignInRow, 0, getString(R.string.AQY_AutoSignIn), getAutoSignInSummary())
                .slug("aiqiyi_auto_sign_in"));
        items.add(SettingsHelper.asSwitchCG(aiqiyiSelfCertRow, getString(R.string.AQY_SelfCert), getString(R.string.AQY_SelfCert_Desc))
                .setChecked(CherrygramAppearanceConfig.INSTANCE.getAiqiyiSelfCertBadge())
                .slug("aiqiyi_self_cert")
        );
        items.add(SettingsHelper.asTextDetail(aiqiyiLocalZoneRow, 0, getString(R.string.AQY_LocalZone), getAiqiyiLocalZoneSummary())
                .slug("aqy_local_zone")
        );
        items.add(SettingsHelper.asTextDetail(aiqiyiComebackRow, 0, getString(R.string.AQY_Comeback_Title), getAiqiyiComebackSummary())
                .slug("aqy_comeback")
        );
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == springAnimationRow) {
            ArrayList<String> configStringKeys = new ArrayList<>();
            ArrayList<Integer> configValues = new ArrayList<>();

            configStringKeys.add(getString(R.string.EP_NavigationAnimationSpring));
            configValues.add(CherrygramCoreConfig.ANIMATION_SPRING);

            configStringKeys.add(getString(R.string.EP_NavigationAnimationBezier));
            configValues.add(CherrygramCoreConfig.ANIMATION_CLASSIC);

            PopupHelper.show(configStringKeys, getString(R.string.EP_NavigationAnimation), configValues.indexOf(CherrygramCoreConfig.INSTANCE.getSpringAnimation()), getContext(), i -> {
                CherrygramCoreConfig.INSTANCE.setSpringAnimation(configValues.get(i));
                SettingsHelper.updateButtonValue(view, getSpringValue());

                updateRows(true);

                showRestartBulletin();
            });
        } else if (item.id == actionbarCrossfadeRow) {
            CherrygramCoreConfig.INSTANCE.setActionbarCrossfade(!CherrygramCoreConfig.INSTANCE.getActionbarCrossfade());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getActionbarCrossfade());

            if (CherrygramCoreConfig.INSTANCE.getActionbarCrossfade() && CherrygramCoreConfig.INSTANCE.getPredictiveBack()) {
                CherrygramCoreConfig.INSTANCE.setPredictiveBack(false);
                updateRows(true);
            }

            showRestartBulletin();
        } else if (item.id == predictiveBackRow) {
            CherrygramCoreConfig.INSTANCE.setPredictiveBack(!CherrygramCoreConfig.INSTANCE.getPredictiveBack());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getPredictiveBack());

            if (CherrygramCoreConfig.INSTANCE.getPredictiveBack() && CherrygramCoreConfig.INSTANCE.getActionbarCrossfade()) {
                CherrygramCoreConfig.INSTANCE.setActionbarCrossfade(false);
                updateRows(true);
            }

            showRestartBulletin();
        } else if (item.id == silenceNonContactsRow) {
            CherrygramCoreConfig.INSTANCE.setSilenceNonContacts(!CherrygramCoreConfig.INSTANCE.getSilenceNonContacts());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getSilenceNonContacts());
        } else if (item.id == ignoreMentionsRow) {
            CherrygramCoreConfig.INSTANCE.setIgnoreMentions(!CherrygramCoreConfig.INSTANCE.getIgnoreMentions());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getIgnoreMentions());

            updateRows(true);
        } else if (item.id == ignoreMentionsExclusionsRow) {
            createUsersSelectActivity(view);
        } else if (item.id == ignoreMentionsAutoReadRow) {
            CherrygramCoreConfig.INSTANCE.setIgnoreMentionsMarkAsRead(!CherrygramCoreConfig.INSTANCE.getIgnoreMentionsMarkAsRead());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getIgnoreMentionsMarkAsRead());
        } else if (item.id == defaultNotificationIconRow) {
            CherrygramCoreConfig.INSTANCE.setOldNotificationIcon(!CherrygramCoreConfig.INSTANCE.getOldNotificationIcon());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getOldNotificationIcon());

            showRestartBulletin();
        } else if (item.id == residentNotificationRow) {
            CherrygramCoreConfig.INSTANCE.setResidentNotification(!CherrygramCoreConfig.INSTANCE.getResidentNotification());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getResidentNotification());

            ApplicationLoader.applicationContext.stopService(new Intent(ApplicationLoader.applicationContext, NotificationsService.class));
            ApplicationLoader.startPushService();
            showRestartBulletin();
        } else if (item.id == hideStoriesRow) {
            CherrygramCoreConfig.INSTANCE.setHideStories(!CherrygramCoreConfig.INSTANCE.getHideStories());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getHideStories());

            showRestartBulletin();
        } else if (item.id == archiveStoriesRow) {
            expandedArchiveStoriesSection = !expandedArchiveStoriesSection;
            item.collapsed = !item.collapsed;

            updateRows(true);
        } else if (item.id == archiveStoriesUsersRow) {
            CherrygramCoreConfig.INSTANCE.setArchiveStoriesFromUsers(!CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromUsers());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromUsers());

            updateRows(true);
        } else if (item.id == archiveStoriesChannelsRow) {
            CherrygramCoreConfig.INSTANCE.setArchiveStoriesFromChannels(!CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromChannels());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromChannels());

            updateRows(true);
        } else if (item.id == useSystemEmojiRow) {
            CherrygramCoreConfig.INSTANCE.setSystemEmoji(!CherrygramCoreConfig.INSTANCE.getSystemEmoji());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getSystemEmoji());
        } else if (item.id == useSystemFontsRow) {
            CherrygramCoreConfig.INSTANCE.setSystemFonts(!CherrygramCoreConfig.INSTANCE.getSystemFonts());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getSystemFonts());

            showRestartBulletin();
        } else if (item.id == tabledModeRow) {
            showTabletModeSelector(() -> {
                SettingsHelper.updateButtonValue(view, getTabletModeValue());
                showRestartBulletin();
            });
        } else if (item.id == downloadSpeedBoostRow) {
            ArrayList<String> configStringKeys = new ArrayList<>();
            ArrayList<Integer> configValues = new ArrayList<>();

            configStringKeys.add(getString(R.string.LiteBatteryDisabled));
            configValues.add(CherrygramCoreConfig.BOOST_NONE);

            configStringKeys.add(getString(R.string.LiteBatteryEnabled));
            configValues.add(CherrygramCoreConfig.BOOST_AVERAGE);

            configStringKeys.add(getString(R.string.EP_DownloadSpeedBoostExtreme));
            configValues.add(CherrygramCoreConfig.BOOST_EXTREME);

            PopupHelper.show(configStringKeys, getString(R.string.EP_DownloadSpeedBoost), configValues.indexOf(CherrygramCoreConfig.INSTANCE.getDownloadSpeedBoost()), getContext(), i -> {
                CherrygramCoreConfig.INSTANCE.setDownloadSpeedBoost(configValues.get(i));
                SettingsHelper.updateButtonValue(view, getDownloadSpeedBoostText());

                showRestartBulletin();
            });
        } else if (item.id == uploadSpeedBoostRow) {
            CherrygramCoreConfig.INSTANCE.setUploadSpeedBoost(!CherrygramCoreConfig.INSTANCE.getUploadSpeedBoost());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getUploadSpeedBoost());

            showRestartBulletin();
        } else if (item.id == slowNetworkMode) {
            CherrygramCoreConfig.INSTANCE.setSlowNetworkMode(!CherrygramCoreConfig.INSTANCE.getSlowNetworkMode());
            SettingsHelper.updateCheckState(view, CherrygramCoreConfig.INSTANCE.getSlowNetworkMode());

            showRestartBulletin();
        } else if (item.id == aiqiyiTranslateBarRow) {
            CherrygramMessagesConfig.INSTANCE.setAiqiyiTranslateBarAlways(!CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslateBarAlways());
            SettingsHelper.updateCheckState(view, CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslateBarAlways());
        } else if (item.id == aiqiyiCustomTitleRow) {
            showCustomTitleDialog();
        } else if (item.id == aiqiyiInputHintEnabledRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiInputHintEnabled(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiInputHintEnabled());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiInputHintEnabled());
        } else if (item.id == aiqiyiInputHintTextRow) {
            showInputHintDialog();
        } else if (item.id == aiqiyiSaveDeletedRow) {
            CherrygramMessagesConfig.INSTANCE.setAiqiyiSaveDeletedMessages(!CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages());
            SettingsHelper.updateCheckState(view, CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages());
        } else if (item.id == aiqiyiTranslucentDeletedRow) {
            CherrygramMessagesConfig.INSTANCE.setAiqiyiTranslucentDeleted(!CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslucentDeleted());
            SettingsHelper.updateCheckState(view, CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslucentDeleted());
        } else if (item.id == aiqiyiUseDeletedIconRow) {
            CherrygramMessagesConfig.INSTANCE.setAiqiyiUseDeletedIcon(!CherrygramMessagesConfig.INSTANCE.getAiqiyiUseDeletedIcon());
            SettingsHelper.updateCheckState(view, CherrygramMessagesConfig.INSTANCE.getAiqiyiUseDeletedIcon());
        } else if (item.id == aiqiyiCustomDeletedMarkRow) {
            showCustomDeletedMarkDialog();
        } else if (item.id == aiqiyiGhostModeRow) {
            CherrygramMessagesConfig.INSTANCE.setAiqiyiGhostMode(!CherrygramMessagesConfig.INSTANCE.getAiqiyiGhostMode());
            SettingsHelper.updateCheckState(view, CherrygramMessagesConfig.INSTANCE.getAiqiyiGhostMode());
        } else if (item.id == aiqiyiUnlimitedPinsRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiUnlimitedPins(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedPins());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedPins());
            uz.unnarsx.cherrygram.aiqiyi.AiqiyiLimitsHelper.applyLimits();
        } else if (item.id == aiqiyiUnlimitedFoldersRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiUnlimitedFolders(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedFolders());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedFolders());
            uz.unnarsx.cherrygram.aiqiyi.AiqiyiLimitsHelper.applyLimits();
        } else if (item.id == aiqiyiUnlimitedFavedStickersRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiUnlimitedFavedStickers(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedFavedStickers());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiUnlimitedFavedStickers());
            uz.unnarsx.cherrygram.aiqiyi.AiqiyiLimitsHelper.applyLimits();
        } else if (item.id == aiqiyiSelfCertRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiSelfCertBadge(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiSelfCertBadge());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiSelfCertBadge());
        } else if (item.id == aiqiyiAutoSignInRow) {
            presentFragment(new uz.unnarsx.cherrygram.aiqiyi.signin.AiqiyiSignInEntry());
        } else if (item.id == aiqiyiLocalZoneRow) {
            presentFragment(new uz.unnarsx.cherrygram.aiqiyi.local.AiqiyiLocalZoneEntry());
        } else if (item.id == aiqiyiComebackRow) {
            presentFragment(new uz.unnarsx.cherrygram.aiqiyi.comeback.AiqiyiComebackEntry());
        } else if (item.id == aiqiyiSpecialAttentionRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiSpecialAttention(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiSpecialAttention());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiSpecialAttention());
        } else if (item.id == aiqiyiHourlyChimeRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiHourlyChime(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiHourlyChime());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiHourlyChime());
        } else if (item.id == aiqiyiBookmarksListRow) {
            presentFragment(new uz.unnarsx.cherrygram.aiqiyi.bookmark.AiqiyiBookmarksEntry());
        } else if (item.id == aiqiyiBookmarksRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiBookmarks(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiBookmarks());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiBookmarks());
        } else if (item.id == aiqiyiSyntaxHighlightRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiSyntaxHighlight(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiSyntaxHighlight());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiSyntaxHighlight());
        } else if (item.id == aiqiyiExtensionCleanupRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiExtensionCleanup(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiExtensionCleanup());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiExtensionCleanup());
        } else if (item.id == aiqiyiFakeLocationRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiFakeLocation(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiFakeLocation());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiFakeLocation());
            showRestartBulletin();
        } else if (item.id == aiqiyiDialogueBubbleRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiDialogueBubble(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiDialogueBubble());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiDialogueBubble());
        } else if (item.id == aiqiyiActiveZoneRow) {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZone(!CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZone());
            SettingsHelper.updateCheckState(view, CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZone());
            updateRows(true);
        } else if (item.id == aiqiyiZoneEmojiRow) {
            showZoneEmojiDialog();
        } else if (item.id == aiqiyiZoneDirectionRow) {
            showZoneDirectionDialog();
        } else if (item.id == aiqiyiZoneTargetRow) {
            showZoneTargetDialog();
        } else if (item.id == aiqiyiCleanupTotalRow) {
            showCleanupAllDialog();
        } else if (item.id >= CLEANUP_ROW_BASE && item.id < CLEANUP_ROW_BASE + AiqiyiCleanup.CAT_COUNT) {
            showCleanupCategoryDialog(item.id - CLEANUP_ROW_BASE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Task count may have changed on the sign-in pages.
        updateRows(false);
    }
    private CharSequence getAutoSignInSummary() {
        int count = uz.unnarsx.cherrygram.aiqiyi.signin.AiqiyiSignInHelper.getTasks(UserConfig.selectedAccount).size();
        if (count <= 0) {
            return getString(R.string.AQY_SignIn_TargetsNone);
        }
        return org.telegram.messenger.LocaleController.formatString(R.string.AQY_SignIn_TargetsSet, count);
    }

    // [Aiqiyi local] 本地功能专区入口的摘要（短文案，避免窄屏截断标题）
    private CharSequence getAiqiyiLocalZoneSummary() {
        boolean on = uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled();
        return getString(on ? R.string.AQY_LocalZone_On : R.string.AQY_LocalZone_Off);
    }

    // [Aiqiyi] 回怼系统入口摘要
    private CharSequence getAiqiyiComebackSummary() {
        boolean on = uz.unnarsx.cherrygram.aiqiyi.comeback.AiqiyiComebackHelper.getInstance().isEnabled();
        return getString(on ? R.string.AQY_LocalZone_On : R.string.AQY_LocalZone_Off);
    }


    // --------------------------------------------------------------- 扩展清理

    private String getCleanupTotalText() {
        try {
            return org.telegram.messenger.AndroidUtilities.formatFileSize(AiqiyiCleanup.totalSize());
        } catch (Throwable e) {
            return "—";
        }
    }

    private String getCleanupCategoryName(int cat) {
        switch (cat) {
            case AiqiyiCleanup.CAT_CACHE:    return getString(R.string.AQY_Cleanup_Cache);
            case AiqiyiCleanup.CAT_PHOTO:    return getString(R.string.AQY_Cleanup_Photo);
            case AiqiyiCleanup.CAT_VIDEO:    return getString(R.string.AQY_Cleanup_Video);
            case AiqiyiCleanup.CAT_DOCUMENT: return getString(R.string.AQY_Cleanup_Document);
            case AiqiyiCleanup.CAT_STICKER:  return getString(R.string.AQY_Cleanup_Sticker);
            default: return "?";
        }
    }

    private String getCleanupCategorySize(int cat) {
        try {
            return org.telegram.messenger.AndroidUtilities.formatFileSize(AiqiyiCleanup.sizeOf(cat));
        } catch (Throwable e) {
            return "—";
        }
    }

    private void showCleanupCategoryDialog(int cat) {
        if (getParentActivity() == null) return;
        String size = getCleanupCategorySize(cat);
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(getCleanupCategoryName(cat));
        b.setMessage(getString(R.string.AQY_Cleanup_Confirm).replace("%1$s", size));
        b.setPositiveButton(getString(R.string.AQY_Cleanup_Do), (d, w) -> {
            doCleanup(cat);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showCleanupAllDialog() {
        if (getParentActivity() == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Cleanup_Total));
        b.setMessage(getString(R.string.AQY_Cleanup_Confirm)
                .replace("%1$s", getCleanupTotalText()));
        b.setPositiveButton(getString(R.string.AQY_Cleanup_Do), (d, w) -> doCleanup(-1));
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void doCleanup(int cat) {
        final android.content.Context ctx = getParentActivity();
        if (ctx == null) return;
        if (cat < 0) {
            AiqiyiCleanup.clearAll(freed -> {
                showFreed(ctx, freed);
                updateRows(true);
            });
        } else {
            AiqiyiCleanup.clear(cat, freed -> {
                showFreed(ctx, freed);
                updateRows(true);
            });
        }
    }

    private void showFreed(android.content.Context ctx, long freed) {
        try {
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createSimpleBulletin(org.telegram.messenger.R.raw.contact_check,
                            getString(R.string.AQY_Cleanup_Done)
                                    .replace("%1$s",
                                            org.telegram.messenger.AndroidUtilities.formatFileSize(freed)))
                    .show();
        } catch (Throwable ignore) {
        }
    }

    // --------------------------------------------------------------- 活跃专区

    private String getZoneDirectionText() {
        int d = CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneDirection();
        if (d == 1) return getString(R.string.AQY_Zone_Dir_Self);
        if (d == 2) return getString(R.string.AQY_Zone_Dir_All);
        return getString(R.string.AQY_Zone_Dir_Others);
    }

    private String getZoneTargetText() {
        int mode = CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneTargetMode();
        if (mode == 1) {
            String raw = CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneTargetUsers();
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

    private void showZoneEmojiDialog() {
        if (getParentActivity() == null) return;
        org.telegram.ui.Components.EditTextBoldCursor editText =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        editText.setText(CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneEmoji());
        editText.setTextSize(1, 18);
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Zone_Emoji_Hint));
        editText.setFocusable(true);
        editText.requestFocus();

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT));

        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Zone_Emoji));
        b.setView(container);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            String v = editText.getText().toString().trim();
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZoneEmoji(
                    v.isEmpty() ? "\uD83D\uDC4D" : v);
            updateRows(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showZoneDirectionDialog() {
        if (getParentActivity() == null) return;
        int cur = CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneDirection();
        CharSequence[] options = new CharSequence[]{
                (cur == 0 ? "\u2713 " : "") + getString(R.string.AQY_Zone_Dir_Others),
                (cur == 1 ? "\u2713 " : "") + getString(R.string.AQY_Zone_Dir_Self),
                (cur == 2 ? "\u2713 " : "") + getString(R.string.AQY_Zone_Dir_All)
        };
        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Zone_Direction));
        b.setItems(options, (d, which) -> {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZoneDirection(which);
            d.dismiss();
            updateRows(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showZoneTargetDialog() {
        if (getParentActivity() == null) return;
        org.telegram.ui.Components.EditTextBoldCursor editText =
                new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        editText.setText(CherrygramAppearanceConfig.INSTANCE.getAiqiyiActiveZoneTargetUsers());
        editText.setTextSize(1, 16);
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        editText.setHintText(getString(R.string.AQY_Zone_Target_Hint));
        editText.setFocusable(true);
        editText.requestFocus();

        android.widget.LinearLayout container = new android.widget.LinearLayout(getParentActivity());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(24);
        container.setPadding(pad, 0, pad, 0);
        container.addView(editText, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT));

        org.telegram.ui.ActionBar.AlertDialog.Builder b =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.AQY_Zone_Target));
        b.setMessage(getString(R.string.AQY_Zone_Target_Desc));
        b.setView(container);
        b.setPositiveButton(getString(R.string.OK), (d, w) -> {
            String v = editText.getText().toString().trim();
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZoneTargetUsers(v);
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZoneTargetMode(v.isEmpty() ? 0 : 1);
            updateRows(true);
        });
        b.setNegativeButton(getString(R.string.Cancel), null);
        b.setNeutralButton(getString(R.string.AQY_Zone_Target_Clear), (d, w) -> {
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZoneTargetUsers("");
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiActiveZoneTargetMode(0);
            updateRows(true);
        });
        showDialog(b.create());
    }

    /** [Aiqiyi bookmark] 设置页那一行的右侧摘要：显示已书签的会话数 / 总条数 */
    private String getBookmarkSummary() {
        try {
            java.util.Map<Long, Integer> counts =
                    uz.unnarsx.cherrygram.aiqiyi.bookmark.AiqiyiBookmarksHelper
                            .getBookmarkCounts(org.telegram.messenger.UserConfig.selectedAccount);
            if (counts == null || counts.isEmpty()) {
                return getString(R.string.AQY_Bookmarks_Empty_Summary);
            }
            int total = 0;
            for (Integer c : counts.values()) {
                if (c != null) {
                    total += c;
                }
            }
            return counts.size() + " / " + total;
        } catch (Throwable ignore) {
            return "";
        }
    }

    private String getCustomDeletedMarkText() {
        String t = CherrygramMessagesConfig.INSTANCE.getAiqiyiCustomDeletedMark();
        if (t == null || t.trim().isEmpty()) {
            return "已删除";
        }
        return t;
    }

    private void showCustomDeletedMarkDialog() {
        Context context = getContext();
        if (context == null) return;
        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setHint(getString(R.string.AQY_CustomDeletedMark_Hint));
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2));
        editText.setSingleLine(true);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        String current = CherrygramMessagesConfig.INSTANCE.getAiqiyiCustomDeletedMark();
        if (current != null && !current.isEmpty()) {
            editText.setText(current);
            editText.setSelection(current.length());
        }
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        int pad = AndroidUtilities.dp(24);
        container.setPadding(pad, AndroidUtilities.dp(8), pad, AndroidUtilities.dp(8));
        container.addView(editText, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.AQY_CustomDeletedMark));
        builder.setView(container);
        builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Save), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            CherrygramMessagesConfig.INSTANCE.setAiqiyiCustomDeletedMark(value);
            updateRows(true);
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info, getString(R.string.AQY_DeletedMark_Saved)).show();
        });
        builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        org.telegram.ui.ActionBar.AlertDialog dialog = builder.create();
        dialog.show();
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        if (dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE) != null) {
            android.view.View posBtn = dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE);
            if (posBtn instanceof android.widget.TextView) {
                ((android.widget.TextView) posBtn).setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogButton));
            }
        }
    }

    private String getCustomTitleText() {
        String t = CherrygramAppearanceConfig.INSTANCE.getCustomAppTitle();
        if (t == null || t.trim().isEmpty()) {
            return getString(R.string.CG_AppName);
        }
        return t;
    }

    private String getInputHintText() {
        String t = CherrygramAppearanceConfig.INSTANCE.getAiqiyiInputHintText();
        if (t == null || t.trim().isEmpty()) {
            return getString(R.string.AQY_InputHintText_Empty);
        }
        return t;
    }

    private void showInputHintDialog() {
        Context context = getContext();
        if (context == null) return;

        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setHint(getString(R.string.AQY_InputHintText_Hint));
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2));
        editText.setSingleLine(true);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        String current = CherrygramAppearanceConfig.INSTANCE.getAiqiyiInputHintText();
        if (current != null && !current.isEmpty()) {
            editText.setText(current);
            editText.setSelection(current.length());
        }

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        int pad = AndroidUtilities.dp(24);
        container.setPadding(pad, AndroidUtilities.dp(8), pad, AndroidUtilities.dp(8));
        container.addView(editText, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.AQY_InputHintText));
        builder.setView(container);
        builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Save), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            CherrygramAppearanceConfig.INSTANCE.setAiqiyiInputHintText(value);
            updateRows(true);
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info, getString(value.isEmpty() ? R.string.AQY_InputHintText_ResetDone : R.string.AQY_InputHintText_Saved)).show();
            if (getParentActivity() != null) {
                getParentActivity().recreate();
            }
        });
        builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);

        org.telegram.ui.ActionBar.AlertDialog dialog = builder.create();
        dialog.show();
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        if (dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE) != null) {
            android.view.View posBtn = dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE);
            if (posBtn instanceof android.widget.TextView) {
                ((android.widget.TextView) posBtn).setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogButton));
            }
        }
    }

    private void showCustomTitleDialog() {
        Context context = getContext();
        if (context == null) return;

        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setHint(getString(R.string.AQY_CustomTitle_Hint));
        editText.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText2));
        editText.setSingleLine(true);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        String current = CherrygramAppearanceConfig.INSTANCE.getCustomAppTitle();
        if (current != null && !current.isEmpty()) {
            editText.setText(current);
            editText.setSelection(current.length());
        }

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        int pad = AndroidUtilities.dp(24);
        container.setPadding(pad, AndroidUtilities.dp(8), pad, AndroidUtilities.dp(8));
        container.addView(editText, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.AQY_CustomTitle));
        builder.setView(container);
        builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Save), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            CherrygramAppearanceConfig.INSTANCE.setCustomAppTitle(value);
            try {
                org.telegram.messenger.NotificationCenter.getInstance(org.telegram.messenger.UserConfig.selectedAccount)
                        .postNotificationName(org.telegram.messenger.NotificationCenter.aiqiyiAppTitleChanged);
            } catch (Throwable ignore) {}
            updateRows(true);
            org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info, getString(value.isEmpty() ? R.string.AQY_CustomTitle_ResetDone : R.string.AQY_CustomTitle_Saved)).show();
            if (getParentActivity() != null) {
                getParentActivity().recreate();
            }
        });
        builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);

        org.telegram.ui.ActionBar.AlertDialog dialog = builder.create();
        dialog.show();
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        if (dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE) != null) {
            android.view.View posBtn = dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE);
            if (posBtn instanceof android.widget.TextView) {
                ((android.widget.TextView) posBtn).setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogButton));
            }
        }
    }

    @Override
    public void scrollToRow(String slug, Runnable unknown) {
        boolean needsExpand = false;
        if (ARCHIVE_STORIES_SECTION_SLUGS.contains(slug) && !expandedArchiveStoriesSection) {
            expandedArchiveStoriesSection = true;
            needsExpand = true;
        }
        if (needsExpand) {
            updateRows(true);
        }
        super.scrollToRow(slug, unknown);
    }

    private String getSpringValue()  {
        return switch (CherrygramCoreConfig.INSTANCE.getSpringAnimation()) {
            case CherrygramCoreConfig.ANIMATION_CLASSIC -> getString(R.string.EP_NavigationAnimationBezier);
            default -> getString(R.string.EP_NavigationAnimationSpring);
        };
    }

    private String getArchiveStoriesCountText() {
        int count = 0;

        if (CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromUsers()) count++;
        if (CherrygramCoreConfig.INSTANCE.getArchiveStoriesFromChannels()) count++;

        return count + "/2";
    }

    private String getDownloadSpeedBoostText()  {
        return switch (CherrygramCoreConfig.INSTANCE.getDownloadSpeedBoost()) {
            case CherrygramCoreConfig.BOOST_NONE -> getString(R.string.LiteBatteryDisabled);
            case CherrygramCoreConfig.BOOST_AVERAGE -> getString(R.string.LiteBatteryEnabled);
            default -> getString(R.string.EP_DownloadSpeedBoostExtreme);
        };
    }

    private void showTabletModeSelector(Runnable runnable) {
        ArrayList<String> configStringKeys = new ArrayList<>();
        ArrayList<Integer> configValues = new ArrayList<>();

        configStringKeys.add(getString(R.string.QualityAuto));
        configValues.add(CherrygramCoreConfig.TABLET_MODE_AUTO);

        configStringKeys.add(getString(R.string.LiteBatteryEnabled));
        configValues.add(CherrygramCoreConfig.TABLET_MODE_ENABLE);

        configStringKeys.add(getString(R.string.LiteBatteryDisabled));
        configValues.add(CherrygramCoreConfig.TABLET_MODE_DISABLE);

        PopupHelper.show(configStringKeys, getString(R.string.AP_Tablet_Mode), configValues.indexOf(CherrygramCoreConfig.INSTANCE.getTabletMode()), getContext(), i -> {
            CherrygramCoreConfig.INSTANCE.setTabletMode(configValues.get(i));
            if (runnable != null) runnable.run();
        });
    }

    private String getTabletModeValue()  {
        return switch (CherrygramCoreConfig.INSTANCE.getTabletMode()) {
            case CherrygramCoreConfig.TABLET_MODE_ENABLE -> getString(R.string.LiteBatteryEnabled);
            case CherrygramCoreConfig.TABLET_MODE_DISABLE -> getString(R.string.LiteBatteryDisabled);
            default -> getString(R.string.QualityAuto);
        };
    }

    private void createUsersSelectActivity(View view) {
        AndroidUtilities.runOnUIThread(() -> {
            UsersSelectActivity activity = getUsersSelectActivity();
            activity.setDelegate((ids, type) -> {
                Set<Long> chatIds = new HashSet<>(ids);

                Set<String> ignoredChats = new HashSet<>(getChatsNotificationHelper().getArrayList(getChatsNotificationHelper().getIgnoredArray()));

                CherrygramLogger.d(() -> "old ignored chats array: " + ignoredChats);

                ignoredChats.clear();

                if (!chatIds.isEmpty()) {
                    for (Long id : chatIds) {
                        if (/*DialogObject.isUserDialog(id) ||*/ DialogObject.isChatDialog(id)) {
                            ignoredChats.add(String.valueOf(id));
                        }
                    }
                }

                getChatsNotificationHelper().saveArrayList(
                        new ArrayList<>(ignoredChats),
                        getChatsNotificationHelper().getIgnoredArray()
                );

                CherrygramLogger.d(() -> "new ignored chats array: " + ignoredChats);

                SettingsHelper.updateButtonValue(view, String.valueOf(getChatsNotificationHelper().getIgnoredChatsCount()));
            });

            presentFragment(activity);
        }, 300);
    }

    private UsersSelectActivity getUsersSelectActivity() {
        ArrayList<Long> chatsList = new ArrayList<>();
        ArrayList<String> ignoredChatsIds = getChatsNotificationHelper().getArrayList(getChatsNotificationHelper().getIgnoredArray());

        for (String chatIdStr : ignoredChatsIds) {
            long chatId = Long.parseLong(chatIdStr);

//            TLRPC.User user = getMessagesController().getUser(chatId);
            TLRPC.Chat chat = getMessagesController().getChat(-chatId);

            /*if (user != null) {
                chatsList.add(user.id);
            } else*/ if (chat != null) {
                chatsList.add(-chat.id);
            }
        }

        UsersSelectActivity activity = new UsersSelectActivity(true, chatsList, 0);
        activity.asIgnoredChats();
        return activity;
    }

}
