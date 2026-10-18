/**
 * 爱琦艺客户端 - 本地礼物转换器
 *
 * 职责：把 LocalGiftData 转成 Telegram 内部可直接消费的类型，
 * 让资料页礼物区 / 礼物列表无需任何改动就能渲染本地礼物。
 *
 * 设计要点：
 * - 走 TL_emojiStatusCollectible 路线（字段少、构造轻）
 * - 不改动任何真实数据，只读取
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.core.helpers.localdata

import org.telegram.tgnet.TLRPC
import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig

object LocalGiftConverter {

    /**
     * LocalGiftData -> TLRPC.TL_emojiStatusCollectible
     *
     * 这个类型是 ProfileGiftsView.Gift 和 StarsController 都能直接消费的形态，
     * 也对应真实「个人资料挂载礼物」的数据结构。
     */
    @JvmStatic
    fun toStatus(data: LocalGiftData): TLRPC.EmojiStatus {
        val status = TLRPC.TL_emojiStatusCollectible()
        status.collectible_id = data.collectibleId
        status.document_id = data.documentId
        status.title = data.title
        status.slug = data.slug
        status.pattern_document_id = data.patternDocumentId
        status.center_color = data.centerColor
        status.edge_color = data.edgeColor
        status.pattern_color = data.patternColor
        status.text_color = data.textColor
        // until = 0 表示永不过期
        status.until = 0
        return status
    }

    /**
     * 批量转换
     */
    @JvmStatic
    fun toStatusList(list: List<LocalGiftData>): ArrayList<TLRPC.EmojiStatus> {
        val result = ArrayList<TLRPC.EmojiStatus>(list.size)
        for (data in list) {
            result.add(toStatus(data))
        }
        return result
    }

    /**
     * 取某个用户「当前挂载」的本地礼物（列表第一条）。
     * 语义对应真实的 emoji_status，用于资料页头像旁的小图标。
     */
    @JvmStatic
    fun getMountedGift(userId: Long): TLRPC.EmojiStatus? {
        if (!AiqiyiLocalDataConfig.localGiftEnabled) {
            return null
        }
        val list = LocalGiftStore.getDataForUser(userId)
        if (list.isEmpty()) {
            return null
        }
        return toStatus(list[0])
    }

    /**
     * 判断某个 collectibleId 是否属于本地礼物
     */
    @JvmStatic
    fun isLocalGiftId(collectibleId: Long): Boolean {
        // 本地 id 统一用负数区间
        return collectibleId < 0
    }
}