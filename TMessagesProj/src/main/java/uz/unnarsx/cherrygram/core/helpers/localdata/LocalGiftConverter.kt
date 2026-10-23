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
     * Aiqiyi: 保证 title 非空。
     *
     * 官方 UI 把 gift.title 直接丢给 Text(CharSequence,size) 和
     * SpannableStringBuilder(CharSequence)，传 null 会空指针闪退：
     *   GiftSheet$GiftCell.customDraw(GiftSheet:1390)  Text.setText(null)
     *   StarGiftSheet.set(...)                          SpannableStringBuilder(null)
     * 所以展示前必须补一个非空值。
     */
    @JvmStatic
    fun safeTitle(data: LocalGiftData): String {
        val t = data.title
        if (t != null && t.isNotBlank()) return t
        val s = data.slug
        if (s != null && s.isNotBlank()) return s
        return "#" + data.catalogGiftId
    }

    /** 按 slug 取可展示名，兼容老数据 title 为空 */
    @JvmStatic
    fun safeSlug(data: LocalGiftData): String {
        val s = data.slug
        if (s != null && s.isNotBlank()) return s
        val t = data.title
        if (t != null && t.isNotBlank()) return t
        return "gift" + data.catalogGiftId
    }
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
        status.title = safeTitle(data)
        status.slug = safeSlug(data)
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
     * 同名多条时优先取「能取到贴纸文档」的那条，避免老数据只剩色块。
     */
    @JvmStatic
    fun findCatalogGiftByTitle(title: String?): TL_stars.StarGift? {
        if (title.isNullOrEmpty()) return null
        return try {
            val catalog = StarsController.getInstance(UserConfig.selectedAccount).gifts
            if (catalog == null || catalog.isEmpty()) return null
            var firstMatch: TL_stars.StarGift? = null
            var firstWithDoc: TL_stars.StarGift? = null
            var i = 0
            while (i < catalog.size) {
                val g = catalog.get(i)
                if (g != null && title.equals(g.title, ignoreCase = true)) {
                    if (firstMatch == null) firstMatch = g
                    // Aiqiyi: 多个同名时不再直接放弃。旧逻辑「同名就 return null」会让
                    // 没有本地 documentId 的老数据彻底拿不到图案（只剩 backdrop 色块）。
                    // 这里改成优先挑「能取到贴纸文档」的那一条。
                    if (firstWithDoc == null) {
                        val d = runCatching { g.getDocument() }.getOrNull()
                        if (d != null && d.id != 0L) firstWithDoc = g
                    }
                }
                i++
            }
            firstWithDoc ?: firstMatch
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
        // Aiqiyi: title 不能为 null。官方 GiftSheet/GiftCell/StarGiftSheet 会直接把 title
        // 丢给 Text(string, size) 和 SpannableStringBuilder，传 null 就是空指针闪退。
        // 优先用 title，空则回退 slug，再空则用 #catalogGiftId。
        unique.title = safeTitle(data)
        unique.slug = safeSlug(data)
        // --- Aiqiyi: 编号，用户自定义优先 ---
        // customNum > 0 表示用户手动改过，覆盖原编号；为 0 时沿用 giftNum
        unique.num = when {
            data.customNum > 0 -> data.customNum
            data.giftNum <= 0 -> 1
            else -> data.giftNum
        }

        // --- 贴纸（图案本体）：本地 documentId 优先，退回目录 ---
        // Aiqiyi: sticker 取图优先级
        //   1. 本地 documentId（表情文档缓存命中）
        //   2. 礼物目录 catalog.getDocument()（按 catalogGiftId / title 查到的那条）
        //   3. 兜底：按 slug 前缀同族礼物借一张能用的文档（老数据 documentId 失效时）
        // 注意 findDocument() 只查 emojiDocumentsCache，文档没被拉取过就返回 null，
        // 所以 2/3 两层兜底必须有，否则卡片只剩 backdrop 渐变。
        // Aiqiyi C9: 外观的唯一权威来源是本地 documentId。
        // 以前 findDocument 命中就罢了，一旦它没命中就退到 catalog.getDocument()，
        // 而自建服务器的目录里只有基础款，于是不管买的是哪一款，画出来永远是基础款。
        // 现在顺序改成：本地 documentId -> 本地缓存文件 -> 同族借图 -> catalog 最后兜底。
        // Aiqiyi C13: 统一走 resolveStickerDocument。
        // 这里已经包含了：本地 documentId -> 本地缓存文件 -> 按 modelName 精确借图，
        // 并且一旦 modelName 存在就绝不回落目录基础款（否则同族全画成同一只）。
        var doc = resolveStickerDocument(data, catalog)
        if (doc == null && data.documentId != 0L) {
            doc = findDocumentByCachedFile(data.documentId)
        }
        unique.sticker = doc

        // --- 价格：目录优先，本地兜底 ---
        // Aiqiyi C9: 价格本地优先，否则基础款目录价会盖掉真实成交价
        unique.stars = if (data.valueAmount != 0L) data.valueAmount else (catalog?.stars ?: 0L)
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

        // Aiqiyi: 底图（pattern）。三层兜底，全失败就拿贴纸自己顶上，
        // 保证详情页背景永远有平铺图案，不会只剩一片渐变色。
        val patDoc = resolvePatternDocument(data) ?: unique.sticker
        if (patDoc != null) {
            val pattern = TL_stars.starGiftAttributePattern()
            pattern.name = if (!data.patternName.isNullOrEmpty()) data.patternName else safeTitle(data)
            pattern.document = patDoc
            pattern.rarity = TL_stars.TL_starGiftAttributeRarity()
            unique.attributes.add(pattern)
            // 有真 pattern 就顺手钉进本地，下次不用再找
            if (data.patternDocumentId == 0L && patDoc !== unique.sticker) {
                persistPatternDocument(data, patDoc)
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

    /**
     * Aiqiyi: 买礼物时，把贴纸和 pattern 文档「真的下到磁盘」。
     *
     * 为什么不能只靠 findDocument：它只查内存里的 emoji 文档缓存，
     * 重启就没了，而且很多目录礼物的文档压根没被 pull 过。
     * 这里直接走 FileLoader.loadFile，文件落进 MEDIA_DIR_CACHE，
     * 文件名天然是 "dc_id_id"（document.id 就是哈希），
     * 于是「按哈希到父目录找图」这件事，系统自己就替我们做了。
     *
     * 调用点在购买落库那一刻，属于用户主动操作，不额外偷跑流量。
     */
    @JvmStatic
    fun preloadGiftFiles(data: LocalGiftData) {
        try {
            val account = UserConfig.selectedAccount
            val loader = org.telegram.messenger.FileLoader.getInstance(account)
            // Aiqiyi C15: 这里只负责「把图拉下来」，绝不能改写 documentId。
            //
            // 旧实现会在 findDocument(data.documentId) 返回 null 时回退到
            // findCatalogGift(catalogGiftId) 拿目录图，然后 data.documentId = sticker.id
            // 直接覆盖原值 —— 而自建服务器目录里只有基础款，于是买回来的那一刻，
            // 那只狗就被永久替换成基础款，下次渲染读到的已经是基础款 id，再也回不去。
            //
            // 正确做法：按原 documentId 去拉文件，拉不到就让它空着（渲染层还有
            // findDocumentByCachedFile / 精确借图 两道路径），不碰记录本身。
            val sticker = findDocument(data.documentId)
                ?: (if (data.documentId != 0L) findDocumentByCachedFile(data.documentId) else null)
            if (sticker != null) {
                val f = loader.getPathToAttach(sticker, true)
                if (f == null || !f.exists() || f.length() <= 0) {
                    loader.loadFile(sticker, null, org.telegram.messenger.FileLoader.PRIORITY_NORMAL, 0)
                }
            } else if (data.documentId != 0L) {
                FileLog.e("Aiqiyi preloadGiftFiles: documentId=" + data.documentId
                    + " 暂不可解析，保持原值不回写（catalogGiftId=" + data.catalogGiftId + "）")
            }
            // 底图 pattern
            val patDoc = resolvePatternDocument(data)
            if (patDoc != null && patDoc.id != 0L) {
                persistPatternDocument(data, patDoc)
                val pf = loader.getPathToAttach(patDoc, true)
                if (pf == null || !pf.exists() || pf.length() <= 0) {
                    loader.loadFile(patDoc, null, org.telegram.messenger.FileLoader.PRIORITY_LOW, 0)
                }
            }
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter preloadGiftFiles failed", e)
        }
    }

    /**
     * Aiqiyi: 按哈希到父目录找图。找到就返回本地文件，找不到返回 null。
     *
     * 这条路径完全绕开 findDocument / 内存缓存：
     * 只要当初下载过，文件就一直在 MEDIA_DIR_CACHE 里，
     * 文件名是 dc_id_id，document.id 一查即得。
     */
    @JvmStatic
    fun findLocalCachedFile(doc: TLRPC.Document?): java.io.File? {
        if (doc == null || doc.id == 0L) return null
        return try {
            val f = org.telegram.messenger.FileLoader.getInstance(UserConfig.selectedAccount)
                .getPathToAttach(doc, true)
            if (f != null && f.exists() && f.length() > 0) f else null
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Aiqiyi: 按哈希（documentId）到父目录捞文件，捞到就造一个最小 Document。
     *
     * 这是「找不到 Document 对象但文件还在」时的最后一层。
     * FileLoader 的缓存目录按 dc_id_id 命名，只要知道 id 就能拼出路径；
     * 这里不猜 dc_id，而是让 FilePathDatabase / getAttachFileName 自己拼。
     *
     * 造出来的 Document 只带 id / dc_id / mime_type / 一个空 sizes，
     * 足够让 ImageReceiver 走「本地文件直读」分支把图画出来。
     */
    /**
     * Aiqiyi 性能(C18): 扫目录的负结果缓存。
     *
     * findDocumentByCachedFile 在磁盘没命中时要 listFiles() 遍历整个缓存目录。
     * 礼物列表页每帧 fillItems 会逐条调它，9 件礼物就是 9 次全目录扫描 ——
     * 这是点进资料页卡顿的主因。
     *
     * 这里把「扫过且没找到」的 id 记下来，下次直接返回 null，不再碰文件系统。
     * 唯一的代价是：若某文件在本进程内后来才下载完成，需要调用
     * clearCachedFileMiss() 清标记，否则要等重启才能识别。
     */
    private val aiqiyiCacheFileMiss: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet())

    /** Aiqiyi: 清除负结果标记（文件刚下载完 / 修复数据后调用） */
    @JvmStatic
    fun clearCachedFileMiss() {
        aiqiyiCacheFileMiss.clear()
    }

    @JvmStatic
    fun findDocumentByCachedFile(documentId: Long): TLRPC.Document? {
        if (documentId == 0L) return null
        if (aiqiyiCacheFileMiss.contains(documentId)) return null
        val missMarker = java.lang.Long.valueOf(documentId)
        val result = findDocumentByCachedFileInner(documentId)
        if (result == null) {
            aiqiyiCacheFileMiss.add(missMarker)
        } else {
            aiqiyiCacheFileMiss.remove(missMarker)
        }
        return result
    }

    private fun findDocumentByCachedFileInner(documentId: Long): TLRPC.Document? {
        return try {
            val account = UserConfig.selectedAccount
            // 先看数据库里有没有记录过这个 id 的落盘路径
            val db = org.telegram.messenger.FileLoader.getInstance(account).getFileDatabase()
            val cachedPath = db.getPath(documentId, 0, org.telegram.messenger.FileLoader.MEDIA_DIR_CACHE, false)
            if (cachedPath != null) {
                val f = java.io.File(cachedPath)
                if (f.exists() && f.length() > 0) {
                    return buildDocFromFile(documentId, f)
                }
            }
            // 数据库没记录，扫一遍缓存目录，按 "任意dc_ + id" 前缀后缀匹配
            val dir = org.telegram.messenger.FileLoader.getDirectory(
                org.telegram.messenger.FileLoader.MEDIA_DIR_CACHE
            )
            if (dir != null && dir.isDirectory) {
                val suffix = "_" + documentId
                val hit = dir.listFiles { f -> f.isFile && f.name.endsWith(suffix) && f.length() > 0 }
                if (hit != null && hit.isNotEmpty()) {
                    return buildDocFromFile(documentId, hit[0])
                }
            }
            null
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter findDocumentByCachedFile failed", e)
            null
        }
    }

    /** 用一个已存在的本地文件拼出最小可用的 Document */
    @JvmStatic
    fun buildDocFromFile(documentId: Long, file: java.io.File): TLRPC.Document {
        val d = TLRPC.TL_document()
        d.id = documentId
        // dc_id 从文件名前缀取，取不到就用 2（默认主 DC）
        try {
            val idx = file.name.lastIndexOf('_')
            if (idx > 0) {
                d.dc_id = file.name.substring(0, idx).toInt()
            }
        } catch (e: Throwable) {
        }
        d.mime_type = when {
            file.name.endsWith(".tgs") -> "application/x-tgsticker"
            file.name.endsWith(".webp") -> "image/webp"
            file.name.endsWith(".json") -> "application/x-tgsticker"
            file.name.endsWith(".png") -> "image/png"
            else -> "image/webp"
        }
        d.size = file.length()
        // 不填 sizes：ImageReceiver 有 localPath 就直接读本地文件，
        // 空 sizes 反而避免它去走网络拉缩略图。
        // 交给 ImageReceiver 直接读本地文件
        d.localPath = file.absolutePath
        return d
    }

    /**
     * Aiqiyi: 把 pattern 文档序列化成 base64 存进 LocalGiftData。
     *
     * 为什么需要：pattern 的 Document 只存在于服务端下发的那一刻。之后
     * AnimatedEmojiDrawable.findDocument() 只查内存缓存，重启就没了，
     * 于是详情页背景只剩渐变、没有平铺的小图案（"光秃秃"）。
     * 存一份 base64，等于把底图钉死在本机，永不丢失。
     */
    @JvmStatic
    fun persistPatternDocument(data: LocalGiftData, doc: TLRPC.Document?) {
        if (doc == null) return
        try {
            val sd = org.telegram.tgnet.SerializedData()
            doc.serializeToStream(sd)
            val bytes = sd.toByteArray()
            data.patternDocumentData = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            data.patternDocumentId = doc.id
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter persistPatternDocument failed", e)
        }
    }

    /**
     * Aiqiyi C17: 把礼物本体（sticker）文档序列化成 base64 钉进本地记录。
     *
     * 和 persistPatternDocument 同一套思路：Document 只活在内存里，重启就丢。
     * 序列化一存，之后无论缓存怎么清、服务器换没换，`买时那一只`都在。
     */
    @JvmStatic
    fun persistStickerDocument(data: LocalGiftData, doc: TLRPC.Document?) {
        if (doc == null || doc.id == 0L) return
        try {
            val sd = org.telegram.tgnet.SerializedData()
            doc.serializeToStream(sd)
            val bytes = sd.toByteArray()
            if (bytes.isEmpty()) return
            data.stickerDocumentData = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            if (data.documentId == 0L) data.documentId = doc.id
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter persistStickerDocument failed", e)
        }
    }

    /** 从持久化的 base64 还原 sticker 文档 */
    @JvmStatic
    fun getPersistedStickerDocument(data: LocalGiftData): TLRPC.Document? {
        val raw = data.stickerDocumentData
        if (raw.isNullOrEmpty()) return null
        return try {
            val bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
            val stream = org.telegram.tgnet.SerializedData(bytes)
            TLRPC.Document.TLdeserialize(stream, stream.readInt32(false), false)
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter getPersistedStickerDocument failed", e)
            null
        }
    }

    /**
     * Aiqiyi: 三层兜底取 pattern 文档（底图）。
     *
     *   1. patternDocumentId -> 内存缓存
     *   2. patternDocumentData -> 本地 base64 反序列化
     *   3. 礼物目录里同款礼物的 pattern 文档
     *
     * 全部失败返回 null，调用方可以再退一步拿贴纸顶上。
     */
    @JvmStatic
    fun resolvePatternDocument(data: LocalGiftData): TLRPC.Document? {
        // 1. id 命中内存缓存
        if (data.patternDocumentId != 0L) {
            val d = findDocument(data.patternDocumentId)
            if (d != null) return d
        }
        // 2. 本地 base64 还原
        val persisted = getPersistedPatternDocument(data)
        if (persisted != null) return persisted
        // 2.5 按哈希到父目录找文件（买的时候下过就一定有）
        if (data.patternDocumentId != 0L) {
            val fromDisk = findDocumentByCachedFile(data.patternDocumentId)
            if (fromDisk != null) return fromDisk
        }
        // 3. 目录里同款礼物的 pattern
        try {
            val catalog = findCatalogGift(data.catalogGiftId) ?: findCatalogGiftByTitle(data.title)
            if (catalog != null) {
                val pd = StarsController.findAttribute(
                    catalog.attributes,
                    TL_stars.starGiftAttributePattern::class.java
                )?.document
                if (pd != null && pd.id != 0L) return pd
            }
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter resolvePatternDocument catalog failed", e)
        }
        return null
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
     * Aiqiyi: 同族借图兜底。
     *
     * 老数据的 documentId 可能指向一个从没被拉取过的文档，findDocument 返回 null；
     * 而 catalogGiftId 也可能因为目录刷新对不上。这时按 slug 前缀（例如
     * "ResistanceDog-283" -> "ResistanceDog"）在目录里找任意一条同族礼物，
     * 只要能取到 document 就用它 —— 至少图案是对的，不会再是一片空白。
     */
    @JvmStatic
    fun borrowDocumentBySlug(slug: String?): TLRPC.Document? {
        return borrowDocumentBySlug(slug, null)
    }

    /**
     * Aiqiyi C11: 精确版借图。
     *
     * 旧实现只取 slug 的 `-` 前前缀（"ResistanceDog-283" -> "ResistanceDog"），
     * 然后在目录里找「第一条同族」就直接返回 —— 结果同族所有礼物都画成同一只。
     * 正确做法是带 modelName 做精确匹配，匹配不到就宁可返回 null 让上层走 catalog，
     * 也不要把整个家族强行画成一条狗。
     */
    @JvmStatic
    fun borrowDocumentBySlug(slug: String?, modelName: String?): TLRPC.Document? {
        if (slug.isNullOrEmpty()) return null
        val family = slug.substringBefore('-', slug)
        if (family.isEmpty()) return null
        return try {
            val catalog = StarsController.getInstance(UserConfig.selectedAccount).gifts
            if (catalog == null || catalog.isEmpty()) return null
            var i = 0
            while (i < catalog.size) {
                val g = catalog.get(i)
                if (g != null) {
                    val t = g.title
                    val s = runCatching { (g as? TL_stars.TL_starGiftUnique)?.slug }.getOrNull()
                    // Aiqiyi C11: 有 modelName 就必须精确命中，不许「随便找一条同族」
                    if (!modelName.isNullOrEmpty()) {
                        val attrName = runCatching {
                            StarsController.findAttribute(g.attributes, TL_stars.starGiftAttributeModel::class.java)?.name
                        }.getOrNull()
                        val exact = (!attrName.isNullOrEmpty() && attrName.equals(modelName, ignoreCase = true))
                                || (!s.isNullOrEmpty() && s.equals(slug, ignoreCase = true))
                        if (!exact) {
                            i++
                            continue
                        }
                        val dExact = runCatching { g.getDocument() }.getOrNull()
                        if (dExact != null && dExact.id != 0L) return dExact
                        i++
                        continue
                    }
                    val match = (!t.isNullOrEmpty() && t.equals(family, ignoreCase = true))
                            || (!s.isNullOrEmpty() && s.startsWith(family))
                    if (match) {
                        val d = runCatching { g.getDocument() }.getOrNull()
                        if (d != null && d.id != 0L) return d
                    }
                }
                i++
            }
            null
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter borrowDocumentBySlug failed", e)
            null
        }
    }

    /**
     * Aiqiyi C13: 渲染阶段的贴图决策。
     *
     * 只要能确定「这一只」的 modelName，就绝不允许退回目录里的基础款：
     * 目录基础款是别人的狗，画上去等于骗人。宁可留空（上层画渐变背景），
     * 也不能把六只不同的狗全画成同一只。
     */
    @JvmStatic
    fun resolveStickerDocument(data: LocalGiftData, catalog: TL_stars.StarGift?): TLRPC.Document? {
        var doc = findDocument(data.documentId)
        if (doc == null && data.documentId != 0L) doc = findDocumentByCachedFile(data.documentId)
        if (doc != null) return doc
        // Aiqiyi C17: 缓存和磁盘都空了，用购买时钉进来的 base64 还原 —— 这才是最稳的一层
        val persisted = getPersistedStickerDocument(data)
        if (persisted != null) return persisted
        val byModel = borrowDocumentBySlug(data.slug, data.modelName)
        if (byModel != null) return byModel
        if (!data.modelName.isNullOrEmpty()) {
            FileLog.e("Aiqiyi resolveStickerDocument: model=" + data.modelName + " 精确匹配失败，不使用目录基础款")
            return null
        }
        return if (catalog != null) runCatching { catalog.getDocument() }.getOrNull() else null
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

        // Aiqiyi: from_id 发送者。
        // 看自己主页（isMine）：送出/收到的礼物，赠送人是 originalSenderId，没有就退回 recipient。
        // 看别人主页（!isMine）：必须走 else 分支拿到 originalRecipientId，
        // 否则对方主页永远是「我送给自己」，礼物卡片出不来。
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
            // Aiqiyi C10: documentId 是外观的唯一依据，必须尽全力拿到。
            // 送别人礼物时传进来的 gift 可能只是目录条目（没有 attributes），
            // 这时如果再取不到 document，后面渲染就只能退回基础款。
            // Aiqiyi C11: model attribute 的 name 才是「这一只」的唯一标识。
            // 之前从头到尾没存过它，导致同族多款礼物共享同一个 slug 前缀，
            // 渲染时只能借到目录里第一条同族底图 -> 全部变成同一只。
            // Aiqiyi C16: 核心原则 —— 买时啥样，买后就啥样。
            // 购买界面画的就是 gift.getDocument()（内部优先 sticker，其次 model attribute），
            // 保存记录时必须用同一个来源，否则界面和记录两张皮。
            var doc = runCatching { gift.getDocument() }.getOrNull()
            if (doc == null) {
                doc = StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeModel::class.java)?.document
            }
            runCatching {
                val modelAttr = StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributeModel::class.java)
                if (modelAttr != null && !modelAttr.name.isNullOrEmpty()) {
                    data.modelName = modelAttr.name
                }
            }
            // Aiqiyi C16: 不再从目录回捞、不借同族图。
            // 目录里的图是别的款/基础款，借来就变成「买时一只，买后另一只」。
            // 这里只认 gift 本身带来的图；实在没有就存 0，渲染层再用缓存文件兜底。
            data.documentId = doc?.id ?: 0L
            // Aiqiyi C17: 买的那一刻就把图案钉进本地记录（base64 序列化）。
            // 这是「购买时保存数据做持久化」的落点：之后缓存被清、服务器换榜、
            // 甚至 documentId 变成孤儿，渲染层都能从这条记录里原样还原这一只。
            if (doc != null) {
                persistStickerDocument(data, doc)
            } else {
                FileLog.e("Aiqiyi fromStarGift: 该礼物没带 document，id=" + (gift.id) + " title=" + gift.title)
            }
            data.senderName = senderName
            // Aiqiyi: 编号。调用方手里其实是 TL_starGiftUnique（多态传参），num 字段一直都在，
            // 之前硬编码成 1 把真编号（如 #51,488）丢了，导致详情卡显示 1 或借来目录的编号。
            data.giftNum = runCatching {
                if (gift is TL_stars.TL_starGiftUnique) gift.num else 0
            }.getOrDefault(0).let { if (it <= 0) 1 else it }
            // Aiqiyi: 图案文档 id，从 attributes 的 pattern 里取，别写死 0
            runCatching {
                val patDoc = StarsController.findAttribute(gift.attributes, TL_stars.starGiftAttributePattern::class.java)?.document
                if (patDoc != null && patDoc.id != 0L) {
                    // 连同完整文档一起序列化存下，重启后也能还原底图
                    persistPatternDocument(data, patDoc)
                } else {
                    data.patternDocumentId = 0L
                }
            }
            data.valueAmount = gift.stars
            data.valueCurrency = "XTR"
            data.availabilityIssued = gift.availability_issued
            data.availabilityTotal = gift.availability_total
            data.originalDate = (System.currentTimeMillis() / 1000L).toInt()
            // Aiqiyi: 送礼人与收礼人不能都填 userId。
            // 都填同一个会让详情页走 ...Self 分支，显示成「某某 向您赠送了此礼物」，
            // 也就是收礼人自己送给自己，别人主页上的礼物卡片也因此出不来。
            // 送礼人 = 当前登录账号（自己买给自己时两值相等，仍然正确）。
            data.originalRecipientId = userId
            val aiqiyiSelfId = try {
                UserConfig.getInstance(UserConfig.selectedAccount).clientUserId
            } catch (t: Throwable) { 0L }
            data.originalSenderId = if (aiqiyiSelfId != 0L) aiqiyiSelfId else userId
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
            data
        } catch (e: Throwable) {
            FileLog.e("LocalGiftConverter fromStarGift failed", e)
            null
        }
    }
}
