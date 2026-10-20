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
     * Aiqiyi: 按礼物标题在目录里反查（老数据缺 catalogGiftId 时的兜底）。
     * 只匹配唯一命中，避免同名误配。
     */
    @JvmStatic
    fun findCatalogGiftByTitle(title: String?): TL_stars.StarGift? {
        if (title.isNullOrEmpty()) return null
        return try {
            val catalog = StarsController.getInstance(UserConfig.selectedAccount).gifts
            if (catalog == null || catalog.isEmpty()) return null
            var found: TL_stars.StarGift? = null
            var i = 0
            while (i < catalog.size) {
                val g = catalog.get(i)
                if (g != null && title.equals(g.title, ignoreCase = true)) {
                    if (found != null) return null   // 多个同名，不猜
                    found = g
                }
                i++
            }
            found
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter findCatalogGiftByTitle failed", e)
            null
        }
    }

    /**
     * Aiqiyi: 构造 TL_starGiftUnique（不是基础款 TL_starGift）。
     *
     * 为什么必须是 unique：GiftSheet.GiftCell 全流程用
     * `gift instanceof TL_starGiftUnique` 判断渲染路径 —— 只有 unique 才会
     * 画彩色背景/图案、显示标题、走完整的礼物卡片布局。
     * 用基础款会出现：背景错位、标题回落成 slug、发送者位置画成头像。
     *
     * 字段来源优先取礼物目录（catalogGiftId），目录缺失时用本地记录兜底。
     */
    @JvmStatic
    fun resolveStarGift(data: LocalGiftData): TL_stars.StarGift {
        return buildUniqueGift(data)
    }

    /** 构造完整的 TL_starGiftUnique */
    @JvmStatic
    fun buildUniqueGift(data: LocalGiftData): TL_stars.TL_starGiftUnique {
        val unique = TL_stars.TL_starGiftUnique()
        val catalog = findCatalogGift(data.catalogGiftId)
            ?: findCatalogGiftByTitle(data.title)

        // --- 基础标识 ---
        unique.id = if (data.collectibleId != 0L) data.collectibleId else -kotlin.math.abs(data.catalogGiftId)
        unique.gift_id = data.catalogGiftId
        // --- Aiqiyi: 所有者必须是当前登录用户 ---
        // 黄昏的礼物来自服务端回传（owner_id 自带），爱琦艺是纯本地构造，不补这一项
        // 详情页会走购买者视角（显示 Gram 购买按钮），而不是「转让 / 佩戴 / 出售」。
        runCatching {
            val owner = TLRPC.TL_peerUser()
            owner.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
            unique.owner_id = owner
        }
        unique.title = data.title
        unique.slug = data.slug
        // --- Aiqiyi: 编号，用户自定义优先 ---
        // customNum > 0 表示用户手动改过，覆盖原编号；为 0 时沿用 giftNum
        unique.num = when {
            data.customNum > 0 -> data.customNum
            data.giftNum <= 0 -> 1
            else -> data.giftNum
        }

        // --- 贴纸（图案本体）：本地 documentId 优先，退回目录 ---
        // Aiqiyi: sticker 取图优先级：本地 documentId -> 目录（catalogGiftId 命中 / title 兜底）
        var doc = findDocument(data.documentId)
        if (doc == null && catalog != null) {
            doc = catalog.getDocument()
        }
        unique.sticker = doc

        // --- 价格：目录优先，本地兜底 ---
        unique.stars = if (catalog != null && catalog.stars != 0L) catalog.stars else data.valueAmount
        unique.value_amount = if (data.valueAmount != 0L) data.valueAmount else (catalog?.value_amount ?: 0L)
        unique.value_currency = if (!data.valueCurrency.isNullOrEmpty()) data.valueCurrency else catalog?.value_currency
        unique.value_usd_amount = if (data.valueUsdAmount != 0L) data.valueUsdAmount else (catalog?.value_usd_amount ?: 0L)

        // --- 限量信息 ---
        unique.availability_issued = if (data.availabilityIssued != 0) data.availabilityIssued else (catalog?.availability_issued ?: 0)
        unique.availability_total = if (data.availabilityTotal != 0) data.availabilityTotal else (catalog?.availability_total ?: 0)
        if (catalog != null) {
            unique.limited = catalog.limited
            unique.sold_out = catalog.sold_out
            if (unique.background == null) {
                unique.background = catalog.background
            }
        }

        // --- Aiqiyi: 关键！value_* 三字段的存在位必须置，否则读出来是 0 ---
        if (unique.value_amount != 0L && !unique.value_currency.isNullOrEmpty()) {
            unique.flags = unique.flags or 0x100   // FLAG_8 = value_amount 组
        }

        // --- attributes: backdrop + model + pattern ---
        val backdrop = TL_stars.starGiftAttributeBackdrop()
        backdrop.name = if (!data.backdropName.isNullOrEmpty()) data.backdropName else data.title
        backdrop.center_color = data.centerColor
        backdrop.edge_color = data.edgeColor
        backdrop.pattern_color = data.patternColor
        backdrop.text_color = data.textColor
        backdrop.rarity = TL_stars.TL_starGiftAttributeRarity()
        unique.attributes.add(backdrop)

        if (unique.sticker != null) {
            val model = TL_stars.starGiftAttributeModel()
            model.name = if (!data.modelName.isNullOrEmpty()) data.modelName else data.title
            model.document = unique.sticker
            model.rarity = TL_stars.TL_starGiftAttributeRarity()
            unique.attributes.add(model)
        }

        if (data.patternDocumentId != 0L) {
            var patDoc = findDocument(data.patternDocumentId)
            if (patDoc == null) {
                patDoc = getPersistedPatternDocument(data)
            }
            if (patDoc != null) {
                val pattern = TL_stars.starGiftAttributePattern()
                pattern.name = if (!data.patternName.isNullOrEmpty()) data.patternName else data.title
                pattern.document = patDoc
                pattern.rarity = TL_stars.TL_starGiftAttributeRarity()
                unique.attributes.add(pattern)
            }
        }

        // --- 清理无效 attribute（document 为空的不留） ---
        val it = unique.attributes.iterator()
        while (it.hasNext()) {
            val attr = it.next()
            if (attr is TL_stars.starGiftAttributeModel && attr.document == null) { it.remove(); continue }
            if (attr is TL_stars.starGiftAttributePattern && attr.document == null) { it.remove() }
        }

        return unique
    }

    /** 从持久化的 base64 里还原 pattern 文档 */
    @JvmStatic
    fun getPersistedPatternDocument(data: LocalGiftData): TLRPC.Document? {
        val raw = data.patternDocumentData
        if (raw.isNullOrEmpty()) return null
        return try {
            val bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
            val stream = org.telegram.tgnet.SerializedData(bytes)
            TLRPC.Document.TLdeserialize(stream, stream.readInt32(false), false)
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter getPersistedPatternDocument failed", e)
            null
        }
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
        saved.pinned_to_top = data.pinned
        saved.unsaved = false
        saved.name_hidden = false
        saved.refunded = false
        saved.can_upgrade = false

        // Aiqiyi: from_id 发送者。本地礼物默认填自己，避免显示「隐藏用户」
        var peerId = if (isMine && data.originalSenderId != 0L) data.originalSenderId else data.originalRecipientId
        if (peerId == 0L) {
            peerId = try { UserConfig.getInstance(UserConfig.selectedAccount).clientUserId } catch (e: Throwable) { 0L }
        }
        if (peerId != 0L) {
            saved.from_id = TLRPC.TL_peerUser().also { it.user_id = peerId }
        }

        val msg = data.originalMessage
        if (!msg.isNullOrEmpty()) {
            saved.message = TLRPC.TL_textWithEntities().also { it.text = msg }
        }

        // Aiqiyi: TL_savedStarGift 每个可选字段都有 presence flag，不置就等价于没赋值。
        // 0x2    = from_id 存在（不置 -> 显示「隐藏用户」）
        // 0x800  = saved_id 存在
        // 0x40000 = gift_num 存在
        // 0x1000 = pinned_to_top
        var flags = saved.flags
        if (saved.from_id != null) flags = flags or 0x2
        flags = flags or 0x800
        if (saved.gift_num > 0) flags = flags or 0x40000
        if (saved.pinned_to_top) flags = flags or 0x1000
        saved.flags = flags

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
    /**
     * Aiqiyi C4: 从礼物页选中的 StarGift 构造一条本地礼物记录。
     * 用于「送给自己」时跳过真实付费，直接落一条本地数据。
     * 关键：catalogGiftId 必须写入，否则主页渲染时 getStarGift(id) 拿不到对象画不出来。
     */
    @JvmStatic
    fun fromStarGift(gift: TL_stars.StarGift?, userId: Long, senderName: String?): LocalGiftData? {
        if (gift == null) return null
        return try {
            val data = LocalGiftData()
            data.title = gift.title
            data.slug = gift.slug
            data.catalogGiftId = gift.id          // 必须写，渲染靠它找回 StarGift
            val doc = gift.document
            data.documentId = doc?.id ?: 0L
            data.senderName = senderName
            data.giftNum = 1
            data.valueAmount = gift.stars
            data.valueCurrency = "XTR"
            data.availabilityIssued = gift.availability_issued
            data.availabilityTotal = gift.availability_total
            data.originalDate = (System.currentTimeMillis() / 1000L).toInt()
            data.originalSenderId = userId
            data.originalRecipientId = userId
            val backdrop = StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop::class.java)
            if (backdrop != null) {
                data.centerColor = backdrop.center_color
                data.edgeColor = backdrop.edge_color
                data.patternColor = backdrop.pattern_color
                data.textColor = backdrop.text_color
                data.backdropName = backdrop.name
                data.localStyle = true
            } else {
                data.localStyle = false
            }
            data.patternDocumentId = 0L
            data
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter fromStarGift failed", e)
            null
        }
    }
}
