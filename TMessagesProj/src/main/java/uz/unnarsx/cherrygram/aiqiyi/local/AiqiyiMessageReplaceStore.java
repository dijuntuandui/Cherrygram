/**
 * 爱琦艺客户端 - 单条消息替换存储
 *
 * 按 chatId + messageId 保存「这条消息在本机显示成什么」，
 * 只影响本机渲染，不修改任何消息内容，不发送任何数据。
 *
 * 存储格式（SharedPreferences, String）：
 *   key   = "AQY_MR_<chatId>_<messageId>"
 *   value = 被替换后的文字（原文本身不作改动）
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Map;

public class AiqiyiMessageReplaceStore {

    private static final String PREFIX = "AQY_MR_";
    private static SharedPreferences sp;

    private static SharedPreferences sp() {
        if (sp == null) {
            synchronized (AiqiyiMessageReplaceStore.class) {
                if (sp == null) {
                    sp = ApplicationLoader.applicationContext.getSharedPreferences("aqy_local_replace", 0);
                }
            }
        }
        return sp;
    }

    private static String key(long chatId, int messageId) {
        return PREFIX + chatId + "_" + messageId;
    }

    /**
     * 存一条替换。
     */
    public static void put(long chatId, int messageId, String text) {
        if (text == null || text.isEmpty()) {
            remove(chatId, messageId);
            return;
        }
        sp().edit().putString(key(chatId, messageId), text).apply();
    }

    /**
     * 取一条替换；没有则返回 null。
     */
    public static String get(long chatId, int messageId) {
        try {
            return sp().getString(key(chatId, messageId), null);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 这条消息是否被替换过。
     */
    public static boolean has(long chatId, int messageId) {
        return get(chatId, messageId) != null;
    }

    /**
     * 移除一条替换（恢复原文）。
     */
    public static void remove(long chatId, int messageId) {
        sp().edit().remove(key(chatId, messageId)).apply();
    }

    /**
     * 当前一共有多少条替换（设置页显示用）。
     */
    public static int count() {
        int n = 0;
        try {
            for (Map.Entry<String, ?> e : sp().getAll().entrySet()) {
                if (e.getKey().startsWith(PREFIX) && e.getValue() instanceof String) {
                    n++;
                }
            }
        } catch (Throwable ignore) {
        }
        return n;
    }

    /**
     * 清空所有替换。
     */
    public static void clearAll() {
        try {
            SharedPreferences.Editor ed = sp().edit();
            for (String k : sp().getAll().keySet()) {
                if (k.startsWith(PREFIX)) {
                    ed.remove(k);
                }
            }
            ed.apply();
        } catch (Throwable ignore) {
        }
    }

    /**
     * 取这条消息「当前应该显示的文字」（考虑替换后的结果）。
     * 用于通知时直接刷 messageText 缓存。
     */
    public static CharSequence resolve(long chatId, int messageId, CharSequence original) {
        String replaced = get(chatId, messageId);
        if (replaced != null) {
            return replaced;
        }
        return original;
    }

    /**
     * 替换数据变更后由界面调用：
     * 遍历可见消息，把 messageText 缓存按最新替换表刷一遍。
     *
     * MessageObject.messageText 在构造时算好并缓存，
     * 不清掉的话界面上看不到变化。
     *
     * @param messages 当前聊天窗口里的消息列表
     */
    public static void applyTo(java.util.List<org.telegram.messenger.MessageObject> messages) {
        if (messages == null) {
            return;
        }
        for (org.telegram.messenger.MessageObject mo : messages) {
            if (mo == null || mo.messageOwner == null) {
                continue;
            }
            long chatId = mo.messageOwner.dialog_id;
            int messageId = mo.messageOwner.id;
            String replaced = get(chatId, messageId);
            if (replaced != null) {
                mo.messageText = replaced;
            } else if (mo.messageOwner.message != null) {
                // 恢复成规则式替换后的原文
                mo.messageText = uz.unnarsx.cherrygram.aiqiyi.local.AiqiyiLocalMessageHelper
                        .apply(mo.messageOwner.message);
            }
        }
    }
}