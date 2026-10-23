/**
 * 爱琦艺客户端 - 本地虚拟礼物存储层
 *
 * 职责仅限：按 userId 读写本地礼物列表。
 * 不碰 UI、不碰网络、不改动真实礼物数据。
 *
 * 存储：SharedPreferences("aiqiyi_local_gifts")，key = localProfileGift_<userId>
 * 值：Gson 序列化的 List<LocalGiftData> JSON
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.core.helpers.localdata

import android.app.Activity
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig

object LocalGiftStore {

    /** SharedPreferences key 前缀 */
    private const val KEY_PREFIX = "localProfileGift_"

    /** 内存缓存：userId -> 礼物列表 */
    private val dataMap: MutableMap<Long, ArrayList<LocalGiftData>> = HashMap()

    /** 已从磁盘加载过的 userId 集合，避免重复读盘 */
    private val loadedUsers: MutableSet<Long> = HashSet()

    private val gson: Gson by lazy { Gson() }

    private val listType = object : TypeToken<ArrayList<LocalGiftData>>() {}.type

    private val prefs: SharedPreferences
        get() = ApplicationLoader.applicationContext
            .getSharedPreferences("aiqiyi_local_gifts", Activity.MODE_PRIVATE)

    // ===================== 读 =====================

    /**
     * 取某个用户的本地礼物列表（可能为空列表，不会返回 null）。
     * 首次访问会从磁盘加载并缓存。
     */
    @JvmStatic
    fun getDataForUser(userId: Long): ArrayList<LocalGiftData> {
        if (!loadedUsers.contains(userId)) {
            loadForUser(userId)
        }
        return dataMap.getOrPut(userId) { ArrayList() }
    }

    /**
     * Aiqiyi 性能：只查询该 userId 是否已在内存里加载过，不触发加载。
     *
     * getDataForUser() 首次调用会同步 prefs.getString + gson.fromJson，
     * 在 UI 线程上遇到大 JSON 就是整页卡死。调用方应当先用本方法判断，
     * 未加载时把 getDataForUser() 丢到后台线程。
     */
    @JvmStatic
    fun isLoadedForUser(userId: Long): Boolean {
        return loadedUsers.contains(userId)
    }

    /**
     * Aiqiyi 性能(C18): 只读内存缓存，绝不触发 IO。
     *
     * 未加载时返回 null，调用方应当把这个 userId 丢到后台线程去 getDataForUser()。
     * 这是礼物列表页 fillItems 唯一的合法取数方式 —— 那方法是 UI 线程跑的。
     */
    @JvmStatic
    fun peekForUser(userId: Long): ArrayList<LocalGiftData>? {
        if (!loadedUsers.contains(userId)) return null
        return dataMap[userId]
    }

    /**
     * 是否有本地礼物
     */
    @JvmStatic
    fun hasLocalGifts(userId: Long): Boolean {
        return getDataForUser(userId).isNotEmpty()
    }

    /**
     * Aiqiyi: 预热本地礼物贴纸。
     *
     * 本地礼物的 documentId 是从礼物数据里抓来的，未必走过表情文档拉取流程，
     * 所以第一次进资料页时 AnimatedEmojiDrawable 要现下载，肉眼可见的空白。
     * 这里在拿到数据后立刻把文档丢给 FileLoader 后台下载，优先低优先级，
     * 不抢正常消息的带宽。
     */
    @JvmStatic
    /** 已预取过的 documentId，避免每次进页面重复入队 */
    private val prefetchedDocs: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet())

    fun prefetchStickers(userId: Long) {
        try {
            val list = getDataForUser(userId)
            if (list.isEmpty()) return
            val account = UserConfig.selectedAccount
            org.telegram.messenger.AndroidUtilities.runOnUIThread {
                try {
                    val loader = org.telegram.messenger.FileLoader.getInstance(account)
                    val imageLoader = org.telegram.messenger.ImageLoader.getInstance()
                    for (d in list) {
                        // Aiqiyi C17: 预取的文档来源必须与渲染层一致，逐层往下取。
                        // 注意这里不再用「只传 slug」的旧借图 —— 那版按家族前缀匹配，
                        // 会把同族六只狗全当成第一只，是「全都变一只」的元凶。
                        val doc = LocalGiftConverter.findDocument(d.documentId)
                            ?: LocalGiftConverter.findDocumentByCachedFile(d.documentId)
                            ?: LocalGiftConverter.getPersistedStickerDocument(d)
                            ?: LocalGiftConverter.borrowDocumentBySlug(d.slug, d.modelName)
                            ?: continue
                        val docId = doc.id
                        if (!prefetchedDocs.add(docId)) continue
                        try {
                            // Aiqiyi: 优先级 LOW -> NORMAL。
                            // LOW 会被任何前台下载压到队尾，是「进页面半天不出图」的主因
                            loader.loadFile(doc, null, org.telegram.messenger.FileLoader.PRIORITY_NORMAL, 0)
                        } catch (ignore: Throwable) {
                        }
                        // Aiqiyi: 关键一步——入队后立刻解码进 ImageLoader 内存缓存。
                        // 只下载不解码的话绘制时会现解码，首帧空白即「闪一下」
                        try {
                            preloadIntoLruCache(imageLoader, doc, docId)
                        } catch (e: Throwable) {
                            org.telegram.messenger.FileLog.e("LocalGiftStore prefetch decode failed", e)
                        }
                    }
                } catch (e: Throwable) {
                    org.telegram.messenger.FileLog.e("LocalGiftStore prefetchStickers failed", e)
                }
            }
        } catch (e: Throwable) {
            org.telegram.messenger.FileLog.e("LocalGiftStore prefetchStickers outer failed", e)
        }
    }

    /**
     * 把贴纸位图解码进 ImageLoader 的 LRU 缓存，
     * 与贴纸体系共用同一份内存缓存，绘制时直接命中，不二次解码。
     */
    private fun preloadIntoLruCache(
        imageLoader: org.telegram.messenger.ImageLoader?,
        doc: org.telegram.tgnet.TLRPC.Document,
        docId: Long
    ) {
        try {
            val f = org.telegram.messenger.FileLoader.getInstance(UserConfig.selectedAccount)
                .getPathToAttach(doc, true)
            if (f == null || !f.exists() || f.length() <= 0L) {
                scheduleDecodeAfterDownload(doc, docId)
                return
            }
            decodeFileIntoCache(imageLoader, doc, docId, f.absolutePath)
        } catch (e: Throwable) {
            org.telegram.messenger.FileLog.e("LocalGiftStore preloadIntoLruCache failed", e)
        }
    }

    /** 文件未落盘时，轮询几次等就绪再解码（不阻塞 UI） */
    private fun scheduleDecodeAfterDownload(doc: org.telegram.tgnet.TLRPC.Document, docId: Long) {
        val account = UserConfig.selectedAccount
        repeat(6) { attempt ->
            org.telegram.messenger.AndroidUtilities.runOnUIThread({
                try {
                    val f = org.telegram.messenger.FileLoader.getInstance(account)
                        .getPathToAttach(doc, true)
                    if (f != null && f.exists() && f.length() > 0L) {
                        decodeFileIntoCache(
                            org.telegram.messenger.ImageLoader.getInstance(),
                            doc, docId, f.absolutePath
                        )
                    }
                } catch (ignore: Throwable) {
                }
            }, ((attempt + 1) * 400).toLong())
        }
    }

    private fun decodeFileIntoCache(
        imageLoader: org.telegram.messenger.ImageLoader?,
        doc: org.telegram.tgnet.TLRPC.Document,
        docId: Long,
        absPath: String
    ) {
        if (imageLoader == null) return
        try {
            val opts = android.graphics.BitmapFactory.Options()
            opts.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            val bmp = android.graphics.BitmapFactory.decodeFile(absPath, opts) ?: return
            val key = "" + doc.dc_id + "_" + docId
            imageLoader.putImageToCache(android.graphics.drawable.BitmapDrawable(org.telegram.messenger.ApplicationLoader.applicationContext.getResources(), bmp), key, false)
            if (org.telegram.messenger.BuildVars.LOGS_ENABLED) {
                org.telegram.messenger.FileLog.d("AQY prefetch cached doc=" + docId + " key=" + key)
            }
        } catch (e: Throwable) {
            org.telegram.messenger.FileLog.e("LocalGiftStore decodeFileIntoCache failed", e)
        }
    }

    /** 用户增删礼物后清空预取标记，确保下次重新预取 */
    @JvmStatic
    fun resetPrefetchMarks() {
        prefetchedDocs.clear()
    }

    /**
     * 从磁盘加载
     */
    private fun loadForUser(userId: Long) {
        val key = KEY_PREFIX + userId
        val list = try {
            val json = prefs.getString(key, null)
            if (json.isNullOrEmpty()) {
                null
            } else {
                gson.fromJson<ArrayList<LocalGiftData>>(json, listType)
            }
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore load failed, userId=$userId", e)
            null
        }

        if (list != null) {
            dataMap[userId] = list
        }
        loadedUsers.add(userId)
    }

    // ===================== 写 =====================

    /**
     * 保存某个用户的礼物列表。
     * 列表为空时直接删除对应 key，避免留空 JSON。
     */
    private fun saveData(userId: Long, data: List<LocalGiftData>) {
        // Aiqiyi 性能(C18): 内存态同步更新，磁盘写丢到后台。
        //
        // 以前这里是同步 gson.toJson + prefs.edit().putString()。
        // 礼物记录多了、每条又带 base64 图案之后，一次购买要卡几百毫秒，
        // 这就是「点购买卡一下」的直接来源。而调用方（购买/佩戴/修复）
        // 真正要的是内存里立刻可见，落盘晚几十毫秒完全不影响。
        dataMap[userId] = ArrayList(data)
        loadedUsers.add(userId)
        scheduleSave(KEY_PREFIX + userId, ArrayList(data))
    }

    // Aiqiyi 性能(C18): 合并写盘。同一 key 短时间内多次保存只落最后一次。
    private val pendingSave: MutableMap<String, List<LocalGiftData>> =
        java.util.Collections.synchronizedMap(HashMap())
    private val aiqiyiSaveScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun scheduleSave(key: String, snapshot: List<LocalGiftData>) {
        pendingSave[key] = snapshot
        if (!aiqiyiSaveScheduled.compareAndSet(false, true)) return
        try {
            org.telegram.messenger.Utilities.globalQueue.postRunnable {
                aiqiyiSaveScheduled.set(false)
                val batch: Map<String, List<LocalGiftData>>
                synchronized(pendingSave) {
                    batch = HashMap(pendingSave)
                    pendingSave.clear()
                }
                for ((k, v) in batch) {
                    try {
                        if (v.isEmpty()) {
                            prefs.edit { remove(k) }
                        } else {
                            val json = gson.toJson(v, listType)
                            prefs.edit { putString(k, json) }
                        }
                    } catch (e: Exception) {
                        FileLog.e("LocalGiftStore save failed, key=" + k, e)
                    }
                }
            }
        } catch (e: Throwable) {
            aiqiyiSaveScheduled.set(false)
            FileLog.e("LocalGiftStore scheduleSave failed", e)
        }
    }

    /**
     * 新增一条本地礼物
     */
    @JvmStatic
    fun addLocalGift(userId: Long, gift: LocalGiftData) {
        val list = getDataForUser(userId)
        // 分配唯一 id
        if (gift.collectibleId == 0L) {
            gift.collectibleId = nextLocalGiftId(list)
        }
        list.add(gift)
        saveData(userId, list)
        notifyChanged(userId)
    }

    /**
     * 爱琦艺：按 collectibleId 查一条本地礼物数据
     */
    @JvmStatic
    fun findLocalGift(userId: Long, collectibleId: Long): LocalGiftData? {
        if (collectibleId == 0L) return null
        return try {
            getDataForUser(userId).firstOrNull { it.collectibleId == collectibleId }
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore findLocalGift failed", e)
            null
        }
    }

    /**
     * 爱琦艺：设置某条本地礼物是否佩戴（显示在资料页名字下方）
     *
     * @return 是否找到并修改成功
     */
    @JvmStatic
    fun setPinned(userId: Long, collectibleId: Long, pinned: Boolean): Boolean {
        return try {
            val list = getDataForUser(userId)
            val target = list.firstOrNull { it.collectibleId == collectibleId } ?: return false
            if (target.pinned == pinned) return true
            target.pinned = pinned
            saveData(userId, list)
            notifyChanged(userId)
            true
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore setPinned failed", e)
            false
        }
    }

    /**
     * 爱琦艺：取某个用户所有已佩戴的本地礼物（按添加顺序）
     */
    /**
     * 爱琦艺：设置自定义典藏编号。
     *
     * @param customNum 0 = 恢复原编号；>0 = 指定编号
     * @return 是否找到并修改成功
     */
    @JvmStatic
    fun setCustomNum(userId: Long, collectibleId: Long, customNum: Int): Boolean {
        return try {
            val list = getDataForUser(userId)
            val target = list.firstOrNull { it.collectibleId == collectibleId } ?: return false
            val v = if (customNum < 0) 0 else customNum
            if (target.customNum == v) return true
            target.customNum = v
            saveData(userId, list)
            notifyChanged(userId)
            true
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore setCustomNum failed", e)
            false
        }
    }

    /**
     * 爱琦艺：取某个用户所有已佩戴的本地礼物（按添加顺序）
     */
    @JvmStatic
    fun getPinnedForUser(userId: Long): ArrayList<LocalGiftData> {
        return try {
            ArrayList(getDataForUser(userId).filter { it.pinned })
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore getPinnedForUser failed", e)
            ArrayList()
        }
    }

    /**
     * 按 collectibleId 移除
     */
    @JvmStatic
    fun removeLocalGift(userId: Long, collectibleId: Long): Boolean {
        val list = getDataForUser(userId)
        val removed = list.removeAll { it.collectibleId == collectibleId }
        if (removed) {
            saveData(userId, list)
            notifyChanged(userId)
        }
        return removed
    }

    /**
     * 清空某个用户的本地礼物
     */
    @JvmStatic
    fun clear(userId: Long) {
        saveData(userId, ArrayList())
        notifyChanged(userId)
    }

    /**
     * 新增一条本地礼物（静默版，不推通知，用于内部迁移避免重入）
     */
    @JvmStatic
    fun addLocalGiftSilent(userId: Long, gift: LocalGiftData) {
        val list = getDataForUser(userId)
        if (gift.collectibleId == 0L) {
            gift.collectibleId = nextLocalGiftId(list)
        }
        list.add(gift)
        saveData(userId, list)
    }

    /**
     * 清空某个用户的本地礼物（静默版，不推通知）
     */
    @JvmStatic
    fun clearSilent(userId: Long) {
        saveData(userId, ArrayList())
    }

    /**
     * 通知 UI 刷新（资料页礼物区 / 礼物列表）
     */
    @JvmStatic
    fun notifyChanged(userId: Long) {
        try {
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(
                NotificationCenter.aiqiyiLocalGiftUpdated, userId
            )
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore notifyChanged failed", e)
        }
    }

    /**
     * Aiqiyi: 修复旧数据——把 collectibleId 为正数的本地礼物重新分配为负数 id。
     *
     * 为什么要改：早期版本直接拿服务端正数 id 当 collectibleId，
     * 导致 aiqiyiIsLocalGiftId() 的 id<0 快路径失效，点开落到商店购买页。
     *
     * 安全措施：修改前先把原始 JSON 全量写盘备份，写在 app 外部文件目录下。
     *
     * @return 修复条数；0 表示无需修复
     */
    @JvmStatic
    fun repairPositiveCollectibleIds(): Int {
        var fixed = 0
        return try {
            val all = prefs.all
            val userIds = ArrayList<Long>()
            for (key in all.keys) {
                if (!key.startsWith(KEY_PREFIX)) continue
                val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: continue
                userIds.add(id)
            }
            if (userIds.isEmpty()) return 0

            // --- 备份原始数据 ---
            try {
                val dir = ApplicationLoader.applicationContext.getExternalFilesDir("aiqiyi_gift_backup")
                if (dir != null && !dir.exists()) dir.mkdirs()
                if (dir != null) {
                    val sb = StringBuilder()
                    for (uid in userIds) {
                        val raw = prefs.getString(KEY_PREFIX + uid, null) ?: continue
                        sb.append("/// user ").append(uid).append("\n").append(raw).append("\n")
                    }
                    val f = java.io.File(dir, "local_gifts_backup_" + System.currentTimeMillis() + ".json")
                    f.writeText(sb.toString())
                    FileLog.d("Aiqiyi gift repair backup -> " + f.absolutePath)
                }
            } catch (e: Exception) {
                FileLog.e("Aiqiyi gift repair backup failed", e)
            }

            // --- 逐个用户修正 ---
            for (uid in userIds) {
                val list = getDataForUser(uid)
                if (list.isEmpty()) continue
                var changed = false
                var nextId = nextLocalGiftId(list)
                for (item in list) {
                    if (item.collectibleId > 0L) {
                        val old = item.collectibleId
                        item.collectibleId = nextId
                        nextId -= 1L
                        FileLog.d("Aiqiyi gift repair: uid=" + uid + " " + old + " -> " + item.collectibleId)
                        fixed++
                        changed = true
                    }
                    // Aiqiyi C17: 顺手把老数据的图案补钉一遍。
                    // 只要这一只现在还能从缓存/磁盘/目录里拿到 document，
                    // 就序列化存进记录，以后再也不靠运气。
                    if (item.stickerDocumentData.isNullOrEmpty()) {
                        try {
                            val d = LocalGiftConverter.resolveStickerDocument(
                                item,
                                if (item.catalogGiftId != 0L) LocalGiftConverter.findCatalogGift(item.catalogGiftId) else null
                            )
                            if (d != null) {
                                LocalGiftConverter.persistStickerDocument(item, d)
                                FileLog.d("Aiqiyi gift repair: 补钉图案 uid=" + uid + " id=" + item.collectibleId + " doc=" + d.id)
                                fixed++
                                changed = true
                            } else {
                                FileLog.e("Aiqiyi gift repair: 无法补钉图案 id=" + item.collectibleId + " docId=" + item.documentId + " model=" + item.modelName)
                            }
                        } catch (e: Throwable) {
                            FileLog.e("Aiqiyi gift repair sticker failed id=" + item.collectibleId, e)
                        }
                    }
                }
                if (changed) {
                    // 直接落盘（saveData 可能会被其他路径拦截，这里显式写）
                    saveData(uid, list)
                    notifyChanged(uid)
                }
            }
            fixed
        } catch (e: Exception) {
            FileLog.e("Aiqiyi gift repair failed", e)
            0
        }
    }

    /**
     * Aiqiyi: 修复任务的一次性完成标记。
     *
     * 背景：repairGiftNumsFromSlug / repairPatternDocuments 每次进资料页都在主线程
     * 全库遍历一遍，条目越多越慢，而且数据早就修好后仍然每次都跑，纯浪费。
     * 这里给每个修复任务加一个 PREF 标记，跑过一次就跳过，不再重复。
     *
     * 注意：删除/新增本地礼物时不需要重置标记——这两个修复只针对「历史遗留数据」，
     * 新写入的数据本身字段就是完整的，不需要再补。
     */
    private const val KEY_REPAIR_FLAG_PREFIX = "repair_done_"

    private fun isRepairDone(task: String): Boolean {
        return try {
            prefs.getBoolean(KEY_REPAIR_FLAG_PREFIX + task, false)
        } catch (e: Exception) {
            false
        }
    }

    private fun markRepairDone(task: String) {
        try {
            prefs.edit { putBoolean(KEY_REPAIR_FLAG_PREFIX + task, true) }
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore markRepairDone failed, task=$task", e)
        }
    }

    /**
     * Aiqiyi: 修复旧数据——把 giftNum 从写死的 1 回填成真实编号。
     *
     * 为什么：早期 fromStarGift 里 data.giftNum = 1 是硬编码，
     * 导致所有购买后的礼物都显示 #1。真实编号其实一直都在 slug 后缀里
     * （形如 ResistanceDog-283 -> 283），无需联网即可还原。
     *
     * 只在 giftNum <= 1 且 slug 带数字后缀时才改，自定义编号(customNum)不受影响。
     *
     * @return 修复条数；0 表示无需修复
     */
    @JvmStatic
    fun repairGiftNumsFromSlug(): Int {
        var fixed = 0
        if (isRepairDone("gift_nums")) return 0
        return try {
            val all = prefs.all
            val userIds = ArrayList<Long>()
            for (key in all.keys) {
                if (!key.startsWith(KEY_PREFIX)) continue
                val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: continue
                userIds.add(id)
            }
            if (userIds.isEmpty()) return 0
            for (uid in userIds) {
                val list = getDataForUser(uid)
                if (list.isEmpty()) continue
                var changed = false
                for (item in list) {
                    if (item.giftNum > 1) continue
                    val slug = item.slug ?: continue
                    val idx = slug.lastIndexOf('-')
                    if (idx <= 0 || idx >= slug.length - 1) continue
                    val num = slug.substring(idx + 1).toIntOrNull() ?: continue
                    if (num <= 1) continue
                    item.giftNum = num
                    FileLog.d("Aiqiyi giftNum backfill: uid=" + uid + " slug=" + slug + " -> " + num)
                    fixed++
                    changed = true
                }
                if (changed) {
                    saveData(uid, list)
                    notifyChanged(uid)
                }
            }
            markRepairDone("gift_nums")
            fixed
        } catch (e: Exception) {
            FileLog.e("Aiqiyi giftNum backfill failed", e)
            0
        }
    }
    /**
     * Aiqiyi: 删除一条本地礼物（直接删除，不再弹确认）。
     *
     * 函数名保留 showDeleteLocalGiftDialog 是为了三个调用方零改动：
     *   - ProfileGiftsContainer 长按列表项
     *   - StarGiftSheet 详情页右上角菜单
     *   - LocalGiftStore 内部
     * 只删本机记录，不碰服务端任何真实礼物，所以确认这一步是多余的。
     */
    @JvmStatic
    fun showDeleteLocalGiftDialog(
        ctx: android.content.Context,
        dialogId: Long,
        collectibleId: Long,
        giftName: String?,
        onDeleted: Runnable?
    ) {
        if (collectibleId == 0L) return
        try {
            removeLocalGift(dialogId, collectibleId)
            resetPrefetchMarks()
            FileLog.d("Aiqiyi delete local gift id=" + collectibleId + " did=" + dialogId)
        } catch (t: Throwable) {
            FileLog.e("Aiqiyi delete local gift failed", t)
        }
        try { onDeleted?.run() } catch (t: Throwable) {}
    }

    /**
     * Aiqiyi: 回填底图（pattern 文档）。
     *
     * 老数据的 patternDocumentId 是 0、patternDocumentData 也是空，
     * 详情页背景就只剩一片渐变（"光秃秃"）。这里遍历全库，
     * 凡是缺底图的，都让 LocalGiftConverter.resolvePatternDocument()
     * 去目录里捞一次，捞到就序列化成 base64 钉进本地。
     *
     * 只补 pattern，不碰编号 / 颜色 / 佩戴状态。
     *
     * @return 修复条数；0 表示无需修复
     */
    @JvmStatic
    fun repairPatternDocuments(): Int {
        var fixed = 0
        if (isRepairDone("pattern_docs")) return 0
        return try {
            val userIds = ArrayList<Long>()
            for (key in prefs.all.keys) {
                if (!key.startsWith(KEY_PREFIX)) continue
                val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: continue
                userIds.add(id)
            }
            if (userIds.isEmpty()) return 0
            for (uid in userIds) {
                val list = getDataForUser(uid)
                if (list.isEmpty()) continue
                var changed = false
                for (item in list) {
                    // 已经有底图就跳过
                    if (item.patternDocumentId != 0L) continue
                    if (!item.patternDocumentData.isNullOrEmpty()) continue
                    val doc = LocalGiftConverter.resolvePatternDocument(item)
                    if (doc != null && doc.id != 0L) {
                        LocalGiftConverter.persistPatternDocument(item, doc)
                        FileLog.d("Aiqiyi pattern backfill: uid=" + uid
                                + " id=" + item.collectibleId + " doc=" + doc.id)
                        fixed++
                        changed = true
                    }
                }
                if (changed) {
                    saveData(uid, list)
                    notifyChanged(uid)
                }
            }
            markRepairDone("pattern_docs")
            fixed
        } catch (e: Exception) {
            FileLog.e("Aiqiyi pattern backfill failed", e)
            0
        }
    }
    /**
     * Aiqiyi: 某个 collectibleId 是否存在于任意用户名下。
     * 用于旧数据兜底：早期版本可能把正数 id 存成 collectibleId，
     * 且归属的 userId 与当前登录账号不一致。
     */
    @JvmStatic
    fun existsAnyUser(collectibleId: Long): Boolean {
        if (collectibleId == 0L) return false
        return try {
            val all = prefs.all
            for ((key, value) in all) {
                if (!key.startsWith(KEY_PREFIX)) continue
                val raw = value as? String ?: continue
                if (raw.contains("\"collectibleId\":" + collectibleId)) return true
            }
            false
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore existsAnyUser failed", e)
            false
        }
    }

    /**
     * 清空全部用户的本地礼物
     */
    @JvmStatic
    fun clearAll() {
        dataMap.clear()
        loadedUsers.clear()
        try {
            val all = prefs.all
            val editor = prefs.edit()
            for (key in all.keys) {
                if (key.startsWith(KEY_PREFIX)) {
                    editor.remove(key)
                }
            }
            editor.apply()
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore clearAll failed", e)
        }
    }

    /**
     * 生成下一个本地 collectibleId。
     * 用负数区间，天然避开真实礼物 id（真实 id 为正）。
     */
    private fun nextLocalGiftId(current: List<LocalGiftData>): Long {
        var min = 0L
        for (item in current) {
            if (item.collectibleId < min) {
                min = item.collectibleId
            }
        }
        return if (min == 0L) -1L else min - 1L
    }
}