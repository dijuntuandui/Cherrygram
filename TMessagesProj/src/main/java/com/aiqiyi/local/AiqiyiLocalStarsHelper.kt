/**
 * 爱琦艺客户端 - 本地星星余额 Helper
 *
 * 仅修改本机显示与本地测试数据，不会修改 Telegram 服务器内容。
 * 虚拟星星不能真实购买、充值或扣款。
 *
 * @author Aiqiyi
 */
package com.aiqiyi.local

import org.telegram.messenger.NotificationCenter
import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig

object AiqiyiLocalStarsHelper {

    private const val DEFAULT_BALANCE = 9999999L

    /**
     * 获取本地星星余额
     *
     * 首次访问时若余额 <= 0，自动填充默认值（9999999）
     */
    @JvmStatic
    fun getBalance(): Long {
        if (!AiqiyiLocalDataConfig.localStarsInitialized) {
            if (AiqiyiLocalDataConfig.localStarsBalance <= 0L) {
                AiqiyiLocalDataConfig.localStarsBalance = DEFAULT_BALANCE
            }
            AiqiyiLocalDataConfig.localStarsInitialized = true
        }
        return AiqiyiLocalDataConfig.localStarsBalance.coerceAtLeast(0L)
    }

    /**
     * 本地星星覆盖是否启用
     */
    @JvmStatic
    fun isEnabled(): Boolean {
        return AiqiyiLocalDataConfig.localZoneEnabled && AiqiyiLocalDataConfig.localStarsOverride
    }

    /**
     * 是否买得起（仅本地判定）
     */
    @JvmStatic
    fun canAfford(amount: Long): Boolean {
        return amount >= 0L && getBalance() >= amount
    }

    /**
     * 本地扣减星星（仅本地，不产生真实扣款）
     */
    @JvmStatic
    @Synchronized
    fun spend(currentAccount: Int, amount: Long): Boolean {
        if (!canAfford(amount)) {
            return false
        }
        setBalance(currentAccount, getBalance() - amount)
        return true
    }

    /**
     * 设置本地星星余额，并通知 UI 刷新
     */
    @JvmStatic
    fun setBalance(currentAccount: Int, amount: Long) {
        AiqiyiLocalDataConfig.localStarsBalance = amount.coerceAtLeast(0L)
        AiqiyiLocalDataConfig.localStarsInitialized = true
        try {
            NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.starBalanceUpdated)
        } catch (ignore: Throwable) {
            // 账号未初始化时静默失败
        }
    }

    /**
     * 重置为默认值
     */
    @JvmStatic
    fun reset(currentAccount: Int) {
        setBalance(currentAccount, DEFAULT_BALANCE)
    }
}