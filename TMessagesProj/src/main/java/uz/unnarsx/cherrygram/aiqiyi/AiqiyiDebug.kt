package uz.unnarsx.cherrygram.aiqiyi

/**
 * [Aiqiyi] 统一调试日志开关。
 *
 * 背景：toWornStatus / injectLocalGifts 等热路径原先散落大量 Log.d，
 * 状态徽章每次绘制都会走到，导致 UI 线程被日志 IO 拖到 ANR。
 *
 * 用法：if (AiqiyiDebug.LOG) { ... }
 * 需要抓日志时把 LOG 改成 true 重新编译即可。
 */
object AiqiyiDebug {

    /** 热路径日志总开关。默认关闭，切勿在生产包里打开。 */
    const val LOG = false
}
