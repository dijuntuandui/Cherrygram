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

import org.telegram.messenger.FileLog
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_stars
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Stars.StarsController
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

    // ===================== 阶段三：礼物列表条目 =====================

    /** 从真实礼物目录里按 gift_id 找原始 StarGift（只读，不改动原对象），找不到返回 null */
    @JvmStatic
    fun findCatalogGift(giftId: Long): TL_stars.StarGift? {
        if (giftId == 0L) return null
        try {
            val catalog = StarsController.getInstance(UserConfig.selectedAccount).gifts
            if (catalog == null || catalog.isEmpty()) return null
            var i = 0
            while (i < catalog.size) {
                val g = catalog.get(i)
                if (g != null && g.id == giftId) return g
                i++
            }
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter findCatalogGift failed", e)
        }
        return null
    }

    /**
     * 取可渲染的 StarGift：优先用礼物目录里的原对象（文档/属性齐全）
     * 注意：返回的对象不允许被修改，调用方只读
     */
    @JvmStatic
    fun resolveStarGift(data: LocalGiftData): TL_stars.StarGift {
        val fromCatalog = findCatalogGift(data.catalogGiftId)
        if (fromCatalog != null && fromCatalog.getDocument() != null) {
            return fromCatalog
        }
        val gift = TL_stars.TL_starGift()
        gift.id = if (data.catalogGiftId != 0L) data.catalogGiftId else data.collectibleId
        gift.title = data.title
        gift.slug = data.slug
        gift.stars = data.valueAmount
        gift.sticker = findDocument(data.documentId)
        gift.availability_issued = data.availabilityIssued
        gift.availability_total = data.availabilityTotal
        if (data.localStyle) {
            val backdrop = TL_stars.starGiftAttributeBackdrop()
            backdrop.name = data.backdropName
            backdrop.center_color = data.centerColor
            backdrop.edge_color = data.edgeColor
            backdrop.pattern_color = data.patternColor
            backdrop.text_color = data.textColor
            gift.attributes.add(backdrop)
        }
        return gift
    }

    /** 从缓存里按 id 捞 Document 对象 */
    @JvmStatic
    fun findDocument(documentId: Long): TLRPC.Document? {
        if (documentId == 0L) return null
        try {
            return AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, documentId)
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter findDocument failed", e)
        }
        return null
    }

    /**
     * LocalGiftData -> SavedStarGift，可直接喂给 GiftSheet.GiftCell.Factory.asStarGift
     * saved_id 用负数（= collectibleId）作为本地礼物身份标记，长按删除靠它精确回收
     */
    @JvmStatic
    fun toSavedGift(data: LocalGiftData, isMine: Boolean): TL_stars.SavedStarGift {
        val saved = TL_stars.TL_savedStarGift()
        saved.gift = resolveStarGift(data)
        saved.saved_id = data.collectibleId
        saved.date = if (data.originalDate != 0) data.originalDate else (System.currentTimeMillis() / 1000).toInt()
        saved.gift_num = if (data.giftNum <= 0) 1 else data.giftNum
        saved.pinned_to_top = false
        saved.unsaved = false
        saved.name_hidden = false
        saved.refunded = false
        saved.can_upgrade = false
        val peerId = if (isMine && data.originalSenderId != 0L) data.originalSenderId else data.originalRecipientId
        saved.from_id = TLRPC.TL_peerUser().also { it.user_id = peerId }
        val msg = data.originalMessage
        if (!msg.isNullOrEmpty()) {
            saved.message = TLRPC.TL_textWithEntities().also { it.text = msg }
        }
        return saved
    }

    /** 批量转成 SavedStarGift 列表 */
    @JvmStatic
    fun toSavedGiftList(list: List<LocalGiftData>, isMine: Boolean): ArrayList<TL_stars.SavedStarGift> {
        val result = ArrayList<TL_stars.SavedStarGift>(list.size)
        for (data in list) {
            try {
                result.add(toSavedGift(data, isMine))
            } catch (e: Throwable) {
                FileLog.e("LocalGiftConverter toSavedGift failed, id=" + data.collectibleId, e)
            }
        }
        return result
    }

    /** 从 SavedStarGift 反查是否为本地礼物（saved_id 为负） */
    @JvmStatic
    fun isLocalSavedGift(saved: TL_stars.SavedStarGift?): Boolean {
        return saved != null && saved.saved_id < 0
    }
}