package uz.unnarsx.cherrygram.misc

import android.util.Log
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ConnectionsManager
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC

/**
 * [Aiqiyi] 启动时自动加入内置频道。
 *
 * 使用 addUserToChat 走正常入群流程（不伪造 dialog），加入后频道会出现在对话列表中。
 * 已加入（!left && !kicked）的直接跳过，不会重复请求。
 *
 * 加入成功后可选地置顶：调用 pinDialog 走服务器 pin，需要该频道允许 pin，
 * 频道默认允许自己 pin 自己的对话，所以通常能成功。
 */
object CGForceJoinChannels {

    private const val TAG = "CGForceJoinChannels"

    /** 需要自动加入的两个频道（正数 chat id，不带 -100 前缀）。 */
    private val CHANNEL_IDS = longArrayOf(
        3947415395L, // Aiqiyi Channel
        4416165726L  // 第二个频道
    )

    /** 每账号只跑一次，避免切号/重进重复请求。 */
    private fun flagKey(account: Int) = "aiqiyi_force_join_done_$account"

    fun ensureJoined(account: Int = UserConfig.selectedAccount) {
        if (!UserConfig.getInstance(account).isClientActivated) return
        val settings = MessagesController.getMainSettings(account)
        // 按版本号做标记，改频道列表时改 VERSION 即可重新触发
        if (settings.getInt(flagKey(account), 0) >= VERSION) return

        val mc = MessagesController.getInstance(account)
        val cm = ConnectionsManager.getInstance(account)

        AndroidUtilities.runOnUIThread {
            var pending = 0
            var anyRequest = false

            for (chatId in CHANNEL_IDS) {
                val cached = mc.getChat(chatId)
                if (cached != null && !cached.left && !cached.kicked) {
                    continue // 已在群里
                }
                if (cached != null && cached.kicked) {
                    continue // 被封禁，无法再加入
                }

                anyRequest = true
                pending++

                val req = TLRPC.TL_channels_getChannels()
                val input = TLRPC.TL_inputChannel()
                input.channel_id = chatId
                req.id.add(input)

                cm.sendRequest(req) { response, error ->
                    AndroidUtilities.runOnUIThread {
                        try {
                            if (error == null && response is TLRPC.TL_messages_chats) {
                                val res = response as TLRPC.TL_messages_chats
                                if (res.chats.isNotEmpty()) {
                                    mc.putChats(res.chats, false)
                                    MessagesStorage.getInstance(account)
                                        .putUsersAndChats(null, res.chats, false, true)
                                    val chat = res.chats[0]
                                    if (chat != null && chat.left && !chat.kicked) {
                                        val user = UserConfig.getInstance(account).currentUser
                                        if (user != null) {
                                            mc.addUserToChat(chat.id, user, 0, null, null, null)
                                            Log.d(TAG, "join requested: ${chat.id}")
                                        }
                                    }
                                }
                            } else if (error != null) {
                                Log.d(TAG, "getChannels failed for $chatId: ${error.text}")
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "join exception: ${t.message}")
                        } finally {
                            pending--
                            if (pending == 0) {
                                settings.edit().putInt(flagKey(account), VERSION).apply()
                            }
                        }
                    }
                }
            }

            if (!anyRequest) {
                settings.edit().putInt(flagKey(account), VERSION).apply()
            }
        }
    }

    /** 列表变更时递增，触发已有用户重新检查。 */
    private const val VERSION = 1
}
