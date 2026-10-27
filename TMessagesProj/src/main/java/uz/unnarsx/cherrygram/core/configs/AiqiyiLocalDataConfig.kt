/**
 * 爱琦艺客户端 - 本地数据修改模块配置
 *
 * 本模块仅修改本机显示与本地测试数据，不会修改 Telegram 服务器内容。
 * 虚拟星星 / Gram 不能真实购买；本地礼物也不会产生真实订单或扣款。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.core.configs

import android.app.Activity
import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import uz.unnarsx.cherrygram.preferences.boolean
import uz.unnarsx.cherrygram.preferences.int
import uz.unnarsx.cherrygram.preferences.long
import uz.unnarsx.cherrygram.preferences.string

object AiqiyiLocalDataConfig {

    private val sharedPreferences: SharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("aiqiyi_local", Activity.MODE_PRIVATE)

    // ============ 本地功能专区总开关 ============

    /** 本地功能专区 - 总开关 */
    var localZoneEnabled by sharedPreferences.boolean("AQY_LocalZone_Enabled", true)

    // ============ 虚拟星星 ============

    /** 本地虚拟星星余额（仅本机显示） */
    var localStarsBalance by sharedPreferences.long("AQY_LocalStars_Balance", 9999999L)

    /** 是否已初始化过星星余额 */
    var localStarsInitialized by sharedPreferences.boolean("AQY_LocalStars_Initialized", false)

    /** 是否启用本地星星余额覆盖（关闭则读真实余额） */
    var localStarsOverride by sharedPreferences.boolean("AQY_LocalStars_Override", true)

    // ============ 虚拟 Gram ============

    /** 本地虚拟 Gram 余额（仅本机显示） */
    var localGramBalance by sharedPreferences.long("AQY_LocalGram_Balance", 9999999L)

    /** 是否启用本地 Gram 余额覆盖 */
    var localGramOverride by sharedPreferences.boolean("AQY_LocalGram_Override", true)

    // ============ 本地高级版 ============

    /** 本地高级版（伪装 Premium 状态，仅本机生效） */
    var localPremiumOverride by sharedPreferences.boolean("AQY_LocalPremium_Override", false)

    /** 本地企业功能 */
    var localBusinessOverride by sharedPreferences.boolean("AQY_LocalBusiness_Override", false)

    // ============ 本地资料修改 ============

    /** 本地修改用户简介 */
    var localBioOverride by sharedPreferences.boolean("AQY_LocalBio_Override", false)

    /** 本地简介内容 */
    var localBioText by sharedPreferences.string("AQY_LocalBio_Text", "")

    /** 本地修改头像 */
    var localAvatarOverride by sharedPreferences.boolean("AQY_LocalAvatar_Override", false)

    /** 本地头像路径（本机文件） */
    var localAvatarPath by sharedPreferences.string("AQY_LocalAvatar_Path", "")

    /** 本地修改消息 */
    var localMessageOverride by sharedPreferences.boolean("AQY_LocalMessage_Override", false)

    // ============ 本地修改 ID（仅自己可见） ============

    /** 本地自定义 ID 开关 */
    var localUserIdOverride by sharedPreferences.boolean("AQY_LocalUserId_Override", false)

    /** 本地自定义 ID 内容（随意填，纯数字时自动空格分组） */
    var localUserIdText by sharedPreferences.string("AQY_LocalUserId_Text", "")

    /**
     * 本地消息替换规则。
     * 每行一条，格式：关键词=替换值
     * 例：
     *   你好=heihei
     *   hello=hi
     */
    var localMessageRules by sharedPreferences.string("AQY_LocalMessage_Rules", "")

    /** 本地修改时间 */
    var localTimeOverride by sharedPreferences.boolean("AQY_LocalTime_Override", false)

    /** 时间模式：0=关闭 1=固定偏移 2=固定时刻 */
    var localTimeMode by sharedPreferences.int("AQY_LocalTime_Mode", 1)

    /** 偏移秒数（模式1，可正可负） */
    var localTimeOffset by sharedPreferences.int("AQY_LocalTime_Offset", 0)

    /** 固定时刻（模式2，当天秒数 0~86399） */
    var localTimeFixedSeconds by sharedPreferences.int("AQY_LocalTime_FixedSeconds", 12 * 3600)

    // ============ 本地虚拟礼物 ============

    /** 本地虚拟礼物记录数量（仅用于展示） */
    var localGiftCount by sharedPreferences.int("AQY_LocalGift_Count", 0)

    /** 本地虚拟礼物总开关（关闭后资料页不注入任何本地礼物） */
    var localGiftEnabled by sharedPreferences.boolean("AQY_LocalGift_Enabled", true)

    /** 本地礼物是否在资料页礼物区展示 */
    var localGiftShowOnProfile by sharedPreferences.boolean("AQY_LocalGift_ShowOnProfile", true)

    /** 本地礼物是否在礼物列表（ProfileGiftsContainer）中展示 */
    var localGiftShowInList by sharedPreferences.boolean("AQY_LocalGift_ShowInList", true)

    /** 是否允许本地假送（点送出时不发真实请求、不扣真星星） */
    var localGiftAllowFakeSend by sharedPreferences.boolean("AQY_LocalGift_AllowFakeSend", true)

    /** 本地假送确认提示（true 时送出前弹窗提示这是本地虚拟） */
    var localGiftFakeSendConfirm by sharedPreferences.boolean("AQY_LocalGift_FakeSendConfirm", true)

    /** 是否使用本地星星余额支付（关闭则走真实星星余额） */
    var localGiftUseLocalStars by sharedPreferences.boolean("AQY_LocalGift_UseLocalStars", true)

    /**
     * 重置本地星星余额为默认值
     */
    fun resetLocalStars() {
        localStarsBalance = 9999999L
        localStarsInitialized = true
    }

    /**
     * 重置本地 Gram 余额为默认值
     */
    fun resetLocalGram() {
        localGramBalance = 9999999L
    }

    /**
     * 清空本地虚拟礼物记录
     */
    fun clearLocalGifts() {
        localGiftCount = 0
    }

    // ============ 本地资料颜色 / 背景图案 覆盖 ============
    // 说明：本地高级版打开时，用户在「资料颜色」页改的颜色/背景 emoji 只写到本地 currentUser，
    // 服务端会拒绝（账号非真会员），下次拉取 user 时会被覆盖。这里把本地改动单独存一份，
    // putUser 收到服务端数据时再盖回来，保证重启后不丢。

    /** 是否有本地资料颜色覆盖 */
    var localProfileColorOverride by sharedPreferences.boolean("AQY_LocalProfileColor_Override", false)

    /**
     * 当前背景图案是否由「佩戴的礼物」接管。
     * true 时 profile_color.background_emoji_id 跟随佩戴礼物变化，用户手动选的背景 emoji 不生效；
     * false 时用 localProfileColorEmojiId（用户手动选的值）。
     */
    var localProfileEmojiFromGift by sharedPreferences.boolean("AQY_LocalProfileEmoji_FromGift", false)

    /** 资料颜色 id，-1 表示未设置 */
    var localProfileColorId by sharedPreferences.int("AQY_LocalProfileColor_Id", -1)

    /** 资料背景 emoji（图案文档 id），0 表示未设置 */
    var localProfileColorEmojiId by sharedPreferences.long("AQY_LocalProfileColor_EmojiId", 0L)

    /** 名字颜色 id（资料页「名字」那一栏），-1 表示未设置 */
    var localNameColorId by sharedPreferences.int("AQY_LocalNameColor_Id", -1)

    /** 名字背景 emoji，0 表示未设置 */
    var localNameColorEmojiId by sharedPreferences.long("AQY_LocalNameColor_EmojiId", 0L)

    /** 清掉本地资料颜色覆盖 */
    fun clearLocalProfileColorOverride() {
        localProfileColorOverride = false
        localProfileColorId = -1
        localProfileColorEmojiId = 0L
        localNameColorId = -1
        localNameColorEmojiId = 0L
    }
}
