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
     * 是否有本地礼物
     */
    @JvmStatic
    fun hasLocalGifts(userId: Long): Boolean {
        return getDataForUser(userId).isNotEmpty()
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
        dataMap[userId] = ArrayList(data)
        loadedUsers.add(userId)

        val key = KEY_PREFIX + userId
        try {
            if (data.isEmpty()) {
                prefs.edit { remove(key) }
            } else {
                val json = gson.toJson(data, listType)
                prefs.edit { putString(key, json) }
            }
        } catch (e: Exception) {
            FileLog.e("LocalGiftStore save failed, userId=$userId", e)
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