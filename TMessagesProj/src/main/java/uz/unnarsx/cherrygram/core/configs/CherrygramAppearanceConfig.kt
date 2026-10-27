/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Copyright github.com/arsLan4k1390, 2022-2026.
 */

package uz.unnarsx.cherrygram.core.configs

import android.app.Activity
import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.SharedConfig
import uz.unnarsx.cherrygram.core.icons.icon_replaces.BaseIconReplace
import uz.unnarsx.cherrygram.core.icons.icon_replaces.NoIconReplace
import uz.unnarsx.cherrygram.core.icons.icon_replaces.SolarIconReplace
import uz.unnarsx.cherrygram.preferences.boolean
import uz.unnarsx.cherrygram.preferences.int
import uz.unnarsx.cherrygram.preferences.string

object CherrygramAppearanceConfig {

    private val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)

    /** Header start */
    var centerTitle by sharedPreferences.boolean("AP_CenterTitle", true)
    var hideSearchFiled by sharedPreferences.boolean("AP_HideSearchField", true)
    var drawSnowInActionBar by sharedPreferences.boolean("AP_DrawSnowInActionBar", false && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE)

    /** 爱琦艺插件 - 自定义标题 */
    var customAppTitle by sharedPreferences.string("AP_CustomAppTitle", "")
    /** 爱琦艺插件 - 输入栏下方提示文字（独立开关 + 自定义文案） */
    var aiqiyiInputHintEnabled by sharedPreferences.boolean("AP_AiqiyiInputHintEnabled", false)
    var aiqiyiInputHintText by sharedPreferences.string("AP_AiqiyiInputHintText", "")
    /** 爱琦艺插件 - 无限收起聊天（不限置顶数量） */
    var aiqiyiUnlimitedPins by sharedPreferences.boolean("AP_AiqiyiUnlimitedPins", true)
    /** 爱琦艺插件 - 无限文件夹（不限文件夹数量） */
    var aiqiyiUnlimitedFolders by sharedPreferences.boolean("AP_AiqiyiUnlimitedFolders", true)
    /** 爱琦艺插件 - 无限收藏贴纸（不限收藏贴纸数量） */
    var aiqiyiUnlimitedFavedStickers by sharedPreferences.boolean("AP_AiqiyiUnlimitedFavedStickers", true)

    // ===== 第一批（黄昏迁移）=====
    /** 爱琦艺插件 - 特别关注 */
    var aiqiyiSpecialAttention by sharedPreferences.boolean("AP_AiqiyiSpecialAttention", true)
    /** 爱琦艺插件 - 整点报时 */
    var aiqiyiHourlyChime by sharedPreferences.boolean("AP_AiqiyiHourlyChime", false)
    /** 爱琦艺插件 - 消息书签 */
    var aiqiyiBookmarks by sharedPreferences.boolean("AP_AiqiyiBookmarks", true)
    /** 爱琦艺插件 - 语法高亮 */
    var aiqiyiSyntaxHighlight by sharedPreferences.boolean("AP_AiqiyiSyntaxHighlight", true)
    /** 爱琦艺插件 - 扩展清理 */
    var aiqiyiExtensionCleanup by sharedPreferences.boolean("AP_AiqiyiExtensionCleanup", false)
    /** 爱琦艺插件 - 本地坐标伪造 */
    var aiqiyiFakeLocation by sharedPreferences.boolean("AP_AiqiyiFakeLocation", false)
    /** 爱琦艺插件 - 对白气泡 */
    var aiqiyiDialogueBubble by sharedPreferences.boolean("AP_AiqiyiDialogueBubble", false)
    /** 爱琦艺插件 - 活跃专区 */
    var aiqiyiActiveZone by sharedPreferences.boolean("AP_AiqiyiActiveZone", false)
    /** Header finish */

    /** Appearance start */
    const val ICON_REPLACE_NONE = 0
    const val ICON_REPLACE_SOLAR = 1

    var iconReplacement by sharedPreferences.int("AP_Icon_Replacements1", ICON_REPLACE_SOLAR)
    fun getCurrentIconPack(): BaseIconReplace {
        return when (iconReplacement) {
            ICON_REPLACE_SOLAR -> SolarIconReplace()
            else -> NoIconReplace()
        }
    }

    var oneUI_SwitchStyle by sharedPreferences.boolean("AP_OneUI_SwitchStyle", true)
    var disableDividers by sharedPreferences.boolean("AP_DisableDividers", true)
    /** Appearance finish */

    /** Folders start */
    var tabsHideAllChats by sharedPreferences.boolean("CP_NewTabs_RemoveAllChats", false)
    var tabsNoUnread by sharedPreferences.boolean("CP_NewTabs_NoCounter", false)
    var tabsIncludeMutedInCounter by sharedPreferences.boolean("CP_TabsIncludeMutedInCounter", true)

    const val TAB_TYPE_MIX = 0
    const val TAB_TYPE_TEXT = 1
    const val TAB_TYPE_ICON = 2
    var tabMode by sharedPreferences.int("AP_TabMode", TAB_TYPE_MIX)

    var tabStyleStroke by sharedPreferences.boolean("AP_TabStyleAddStroke", false)
    var folderNameInHeader by sharedPreferences.boolean("AP_FolderNameInHeader", false)
    var foldersAtBottom by sharedPreferences.boolean("AP_FoldersAtBottom", false)
    /** Folders finish */

    /** Main tabs start */
    var showMainTabs by sharedPreferences.boolean("AP_ShowMainTabs", true)
    var openSettingsBySwipe by sharedPreferences.boolean("AP_OpenSettingsBySwipe", false)
    var mainTabsOrder by sharedPreferences.string("AP_MainTabsPosition_New", "SETTINGS,CHATS,!PROFILE,!CONTACTS,!CALLS,SEARCH")
    var showSearchInTabs by sharedPreferences.boolean("AP_ShowSearchInTabs_New", true)
    var showMainTabsTitle by sharedPreferences.boolean("AP_ShowMainTabsTitle", true)
    var mainTabsForceOpenChats by sharedPreferences.boolean("AP_MainTabsForceOpenChats", false)
    /** Main tabs finish */

    /** Messages and profiles start */
    var showSeconds by sharedPreferences.boolean("CP_ShowSeconds", false)
    var disablePremiumStatuses by sharedPreferences.boolean("CP_DisablePremiumStatuses", SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW)
    var replyBackground by sharedPreferences.boolean("CP_ReplyBackground", SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE)
    var replyCustomColors by sharedPreferences.boolean("CP_ReplyCustomColors", SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE)
    var replyBackgroundEmoji by sharedPreferences.boolean("CP_ReplyBackgroundEmoji", SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE)
    var profileHidePhoneNumber by sharedPreferences.boolean("CP_ProfileHidePhoneNumber", true)
    var profileChannelPreview by sharedPreferences.boolean("CP_ProfileChannelPreview", true)

    /** 爱琦艺插件 - 本人资料页名字左侧显示认证角标 */
    var aiqiyiSelfCertBadge by sharedPreferences.boolean("CP_AiqiyiSelfCertBadge", true)

    const val ID_DC_NONE = 0
    const val ID_DC = 1
    var showIDDC_old by sharedPreferences.int("AP_ShowID_DC", ID_DC_NONE) // Not used anymore, use only for migration
    var showIDDC by sharedPreferences.boolean("AP_ShowID_DC_new", false)

    var profileBirthDatePreview by sharedPreferences.boolean("CP_ProfileBirthDatePreview", true)
    var profileBusinessPreview by sharedPreferences.boolean("CP_ProfileBusinessPreview", true)
    var profileBackgroundColor by sharedPreferences.boolean("CP_ProfileBackgroundColor", SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE)
    var profileBackgroundEmoji by sharedPreferences.boolean("CP_ProfileBackgroundEmoji", SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE)
    /** Messages and profiles finish */

    /** Drawer items start */
    var showAccounts by sharedPreferences.boolean("AP_ShowAccounts", true)
    var marketPlaceDrawerButton by sharedPreferences.boolean("AP_MarketplaceDrawerButton", true)
    /** Drawer items finish */

}