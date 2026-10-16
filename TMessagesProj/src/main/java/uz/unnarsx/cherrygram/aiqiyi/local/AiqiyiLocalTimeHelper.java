/**
 * 爱琦艺客户端 - 本地消息时间显示覆盖
 *
 * 只改「气泡右下角时间」的渲染值，不修改 messageOwner.date，
 * 因此不会影响消息排序、阅读状态、查找历史、转发等内部逻辑。
 *
 * 支持两种模式：
 *   1) 固定偏移：所有消息的时间统一加上/减去一个秒数
 *   2) 固定时刻：所有消息显示同一个时间（如 12:00）
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;

public class AiqiyiLocalTimeHelper {

    /** 快速开关（内存镜像，避免渲染高频读 SP） */
    private static volatile boolean fastEnabled = false;
    /** 模式：0=关闭 1=偏移 2=固定时刻 */
    private static volatile int fastMode = 0;
    /** 偏移秒数（模式1） */
    private static volatile int fastOffset = 0;
    /** 固定时刻当天的秒数（模式2，0~86399） */
    private static volatile int fastFixedSeconds = 0;

    /**
     * 配置变更后调用，重建内存镜像。
     */
    public static void invalidate() {
        syncFast();
    }

    /**
     * 从配置刷新内存镜像。App 启动时也应调用一次。
     */
    public static void syncFast() {
        boolean on = AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOverride();
        fastEnabled = on;
        fastMode = on ? AiqiyiLocalDataConfig.INSTANCE.getLocalTimeMode() : 0;
        fastOffset = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOffset();
        fastFixedSeconds = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeFixedSeconds();
    }

    public static boolean isEnabled() {
        return fastEnabled && fastMode != 0;
    }

    /**
     * 计算用于「显示」的时间戳（秒）。
     *
     * @param originalDate 原始的 messageOwner.date
     * @return 显示用时间戳；未启用时原样返回
     */
    public static int displayDate(int originalDate) {
        if (!fastEnabled) {
            return originalDate;
        }
        switch (fastMode) {
            case 1: {
                long v = (long) originalDate + fastOffset;
                if (v < 0) v = 0;
                if (v > 0x7FFFFFFE) v = 0x7FFFFFFE;
                return (int) v;
            }
            case 2: {
                // 保留原日期，只替换当天的时分秒
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis((long) originalDate * 1000L);
                cal.set(java.util.Calendar.HOUR_OF_DAY, fastFixedSeconds / 3600);
                cal.set(java.util.Calendar.MINUTE, (fastFixedSeconds % 3600) / 60);
                cal.set(java.util.Calendar.SECOND, fastFixedSeconds % 60);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                return (int) (cal.getTimeInMillis() / 1000L);
            }
            default:
                return originalDate;
        }
    }

    /**
     * 计算用于「显示」的时间戳（秒）—— 优先单条伪装，其次全局。
     *
     * 单条伪装：长按某条消息设置的时间（当天秒数），只对该条生效。
     * 全局伪装：设置页里的固定偏移 / 固定时刻。
     *
     * @param chatId       消息所在会话 id（messageOwner.dialog_id）
     * @param messageId    消息 id（messageOwner.id）
     * @param originalDate 原始的 messageOwner.date
     */
    public static int displayDate(long chatId, int messageId, int originalDate) {
        // 1) 单条优先
        int single = AiqiyiMessageReplaceStore.getTime(chatId, messageId);
        if (single >= 0) {
            return replaceTimeOfDay(originalDate, single);
        }
        // 2) 全局兜底
        return displayDate(originalDate);
    }

    /**
     * 保留原日期，只把「当天的时分秒」替换成 secondsOfDay。
     */
    public static int replaceTimeOfDay(int originalDate, int secondsOfDay) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis((long) originalDate * 1000L);
        cal.set(java.util.Calendar.HOUR_OF_DAY, secondsOfDay / 3600);
        cal.set(java.util.Calendar.MINUTE, (secondsOfDay % 3600) / 60);
        cal.set(java.util.Calendar.SECOND, secondsOfDay % 60);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long v = cal.getTimeInMillis() / 1000L;
        if (v < 0) v = 0;
        if (v > 0x7FFFFFFE) v = 0x7FFFFFFE;
        return (int) v;
    }
}