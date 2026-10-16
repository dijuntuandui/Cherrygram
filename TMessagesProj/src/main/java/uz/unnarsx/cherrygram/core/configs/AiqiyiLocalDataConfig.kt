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

    /** 本地修改时间 */
    var localTimeOverride by sharedPreferences.boolean("AQY_LocalTime_Override", false)

    // ============ 本地虚拟礼物 ============

    /** 本地虚拟礼物记录数量（仅用于展示） */
    var localGiftCount by sharedPreferences.int("AQY_LocalGift_Count", 0)

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
}
