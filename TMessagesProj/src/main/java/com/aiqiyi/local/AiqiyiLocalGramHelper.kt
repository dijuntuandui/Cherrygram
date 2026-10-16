/**
 * 爱琦艺客户端 - 本地 Gram 余额 Helper
 *
 * 仅修改本机显示与本地测试数据，不会修改 Telegram 服务器内容。
 * Gram 不能真实购买、充值或扣款。
 *
 * @author Aiqiyi
 */
package com.aiqiyi.local

import org.telegram.messenger.NotificationCenter
import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig

object AiqiyiLocalGramHelper {

    private const val DEFAULT_BALANCE = 9999999L

    /**
     * 获取本地 Gram 余额
     *
     * 首次访问时若余额 <= 0，自动填充默认值（9999999）
     */
    @JvmStatic
    fun getBalance(): Long {
        if (AiqiyiLocalDataConfig.localGramBalance <= 0L) {
            AiqiyiLocalDataConfig.localGramBalance = DEFAULT_BALANCE
        }
        return AiqiyiLocalDataConfig.localGramBalance.coerceAtLeast(0L)
    }

    /**
     * 本地 Gram 覆盖是否启用
     */
    @JvmStatic
    fun isEnabled(): Boolean {
        return AiqiyiLocalDataConfig.localZoneEnabled && AiqiyiLocalDataConfig.localGramOverride
    }

    /**
     * 是否买得起（仅本地判定）
     */
    @JvmStatic
    fun canAfford(amount: Long): Boolean {
        return amount >= 0L && getBalance() >= amount
    }

    /**
     * 本地扣减 Gram（仅本地，不产生真实扣款）
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
     * 设置本地 Gram 余额，并通知 UI 刷新
     */
    @JvmStatic
    fun setBalance(currentAccount: Int, amount: Long) {
        AiqiyiLocalDataConfig.localGramBalance = amount.coerceAtLeast(0L)
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