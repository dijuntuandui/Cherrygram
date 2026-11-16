package uz.unnarsx.cherrygram.aiqiyi.bookmark;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig;

/**
 * [Aiqiyi] 消息书签 — 存储层
 *
 * 设计参考 Nekogram 的 BookmarksHelper，但在 Cherrygram 侧重写为纯 Java，
 * 不依赖 Kotlin / NaConfig，避免跨项目耦合。
 *
 * 存储模型：
 *   SharedPreferences "mainconfig"（与 Cherrygram 其余配置一致）
 *   key   = "apy_bookmarks_v1_<ownerId>_<dialogId>"
 *   value = 逗号分隔的 messageId，例 "101,205,307"
 *
 * ownerId 取当前账号 clientUserId，未登录时退化为 accountId，
 * 保证多账号之间书签互不干扰。
 */
public final class AiqiyiBookmarksHelper {

    private AiqiyiBookmarksHelper() {}

    private static final String KEY_PREFIX = "apy_bookmarks_v1_";

    private static final ConcurrentHashMap<String, int[]> CACHE = new ConcurrentHashMap<>();

    private static final String SNAPSHOT_PREFIX = "apy_bookmark_snap_v1_";
    private static final int SNAPSHOT_MAX_LEN = 200;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("mainconfig", android.app.Activity.MODE_PRIVATE);
    }

    private static long ownerId(int account) {
        try {
            UserConfig cfg = UserConfig.getInstance(account);
            if (cfg != null) {
                long uid = cfg.getClientUserId();
                if (uid != 0L) {
                    return uid;
                }
            }
        } catch (Throwable ignore) {
        }
        return account;
    }

    private static String key(int account, long dialogId) {
        return KEY_PREFIX + ownerId(account) + "_" + dialogId;
    }

    private static boolean featureOn() {
        return CherrygramAppearanceConfig.INSTANCE.getAiqiyiBookmarks();
    }

    public static int[] getBookmarkedIds(int account, long dialogId) {
        if (!featureOn()) {
            return new int[0];
        }
        final String k = key(account, dialogId);
        int[] cached = CACHE.get(k);
        if (cached != null) {
            return cached;
        }
        int[] parsed = parse(prefs().getString(k, null));
        CACHE.put(k, parsed);
        return parsed;
    }

    public static boolean isBookmarked(int account, long dialogId, int messageId) {
        if (!featureOn() || messageId <= 0) {
            return false;
        }
        for (int id : getBookmarkedIds(account, dialogId)) {
            if (id == messageId) {
                return true;
            }
        }
        return false;
    }

    public static boolean areAllBookmarked(int account, long dialogId, int[] messageIds) {
        if (!featureOn() || messageIds == null || messageIds.length == 0) {
            return false;
        }
        Set<Integer> cur = toSet(getBookmarkedIds(account, dialogId));
        for (int id : messageIds) {
            if (!cur.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public static boolean addBookmark(int account, long dialogId, int messageId) {
        if (!featureOn() || messageId <= 0) {
            return false;
        }
        return toggle(account, dialogId, new int[]{messageId}, true);
    }

    public static boolean toggleBookmark(int account, long dialogId, int messageId) {
        if (!featureOn() || messageId <= 0) {
            return false;
        }
        boolean nowOn = !isBookmarked(account, dialogId, messageId);
        toggle(account, dialogId, new int[]{messageId}, nowOn);
        return nowOn;
    }

    public static boolean toggleBookmarks(int account, long dialogId, int[] messageIds) {
        if (!featureOn() || messageIds == null || messageIds.length == 0) {
            return false;
        }
        boolean wasAll = areAllBookmarked(account, dialogId, messageIds);
        toggle(account, dialogId, messageIds, !wasAll);
        return !wasAll;
    }

    public static boolean removeBookmark(int account, long dialogId, int messageId) {
        if (!featureOn() || messageId <= 0) {
            return false;
        }
        return toggle(account, dialogId, new int[]{messageId}, false);
    }

    public static boolean removeBookmarks(int account, long dialogId, int[] messageIds) {
        if (!featureOn() || messageIds == null || messageIds.length == 0) {
            return false;
        }
        return toggle(account, dialogId, messageIds, false);
    }

    public static void clearAll(int account) {
        SharedPreferences p = prefs();
        String prefix = KEY_PREFIX + ownerId(account) + "_";
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (k != null && k.startsWith(prefix)) {
                toRemove.add(k);
            }
        }
        SharedPreferences.Editor ed = p.edit();
        for (String k : toRemove) {
            ed.remove(k);
            CACHE.remove(k);
        }
        ed.apply();
    }

    public static Map<Long, Integer> getBookmarkCounts(int account) {
        Map<Long, Integer> out = new ConcurrentHashMap<>();
        if (!featureOn()) {
            return out;
        }
        SharedPreferences p = prefs();
        String prefix = KEY_PREFIX + ownerId(account) + "_";
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            if (k == null || !k.startsWith(prefix)) {
                continue;
            }
            long dialogId;
            try {
                dialogId = Long.parseLong(k.substring(prefix.length()));
            } catch (NumberFormatException ex) {
                continue;
            }
            Object v = e.getValue();
            if (!(v instanceof String)) {
                continue;
            }
            int[] ids = parse((String) v);
            if (ids.length > 0) {
                out.put(dialogId, ids.length);
            }
        }
        return out;
    }

    public static final class Snapshot {
        public final int messageId;
        public long date;
        public String text = "";
        public String sender = "";

        Snapshot(int messageId) {
            this.messageId = messageId;
        }
    }

    private static String snapKey(int account, long dialogId, int messageId) {
        return SNAPSHOT_PREFIX + ownerId(account) + "_" + dialogId + "_" + messageId;
    }

    /** 从消息里解析发送者显示名；解析不到返回空串。 */
    public static String resolveSenderName(int account, org.telegram.messenger.MessageObject mo) {
        try {
            if (mo == null || mo.messageOwner == null) {
                return "";
            }
            long fromId = 0;
            org.telegram.tgnet.TLRPC.Peer from = mo.messageOwner.from_id;
            if (from != null) {
                if (from.user_id != 0) {
                    fromId = from.user_id;
                } else if (from.channel_id != 0) {
                    fromId = -from.channel_id;
                } else if (from.chat_id != 0) {
                    fromId = -from.chat_id;
                }
            }
            if (fromId == 0) {
                return "";
            }
            org.telegram.messenger.MessagesController mc =
                    org.telegram.messenger.MessagesController.getInstance(account);
            if (fromId > 0) {
                org.telegram.tgnet.TLRPC.User u = mc.getUser(fromId);
                if (u != null) {
                    String name = org.telegram.messenger.UserObject.getUserName(u);
                    return name == null ? "" : name;
                }
            } else {
                org.telegram.tgnet.TLRPC.Chat c = mc.getChat(-fromId);
                if (c != null && c.title != null) {
                    return c.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    /** 非文本消息的占位描述，用于列表页预览。 */
    public static CharSequence describeNonText(org.telegram.messenger.MessageObject mo) {
        if (mo == null || mo.messageOwner == null) {
            return "[\u6d88\u606f]";
        }
        try {
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_STICKER) {
                return "[\u8d34\u7eb8]";
            }
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_VIDEO
                    || mo.type == org.telegram.messenger.MessageObject.TYPE_ROUND_VIDEO) {
                return "[\u89c6\u9891]";
            }
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_PHOTO) {
                return "[\u56fe\u7247]";
            }
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_VOICE) {
                return "[\u8bed\u97f3]";
            }
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_GIF) {
                return "[GIF]";
            }
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_ANIMATED_STICKER) {
                return "[\u8d34\u7eb8]";
            }
            if (mo.type == org.telegram.messenger.MessageObject.TYPE_MUSIC) {
                return "[\u97f3\u9891]";
            }
            org.telegram.tgnet.TLRPC.MessageMedia media = mo.messageOwner.media;
            if (media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaDocument) {
                return "[\u6587\u4ef6]";
            }
            if (media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaGeo
                    || media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaVenue) {
                return "[\u4f4d\u7f6e]";
            }
            if (media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaPoll) {
                return "[\u6295\u7968]";
            }
            if (media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaContact) {
                return "[\u8054\u7cfb\u4eba]";
            }
        } catch (Throwable ignore) {
        }
        return "[\u6d88\u606f]";
    }

    public static void saveSnapshot(int account, long dialogId, int messageId,
                                    long dateSeconds, CharSequence text, CharSequence sender) {
        if (messageId <= 0 || dialogId == 0) {
            return;
        }
        String t = text == null ? "" : text.toString();
        t = t.replace('\u0001', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > SNAPSHOT_MAX_LEN) {
            t = t.substring(0, SNAPSHOT_MAX_LEN) + "\u2026";
        }
        String sn = sender == null ? "" : sender.toString().replace('\u0001', ' ').trim();
        prefs().edit()
                .putString(snapKey(account, dialogId, messageId),
                        dateSeconds + "\u0001" + t + "\u0001" + sn)
                .apply();
    }

    public static void removeSnapshot(int account, long dialogId, int messageId) {
        prefs().edit().remove(snapKey(account, dialogId, messageId)).apply();
    }

    public static List<Snapshot> getSnapshots(int account, long dialogId) {
        List<Snapshot> out = new ArrayList<>();
        int[] ids = getBookmarkedIds(account, dialogId);
        if (ids.length == 0) {
            return out;
        }
        SharedPreferences p = prefs();
        for (int id : ids) {
            Snapshot sn = new Snapshot(id);
            String raw = p.getString(snapKey(account, dialogId, id), null);
            if (raw != null) {
                String[] parts = raw.split("\u0001", -1);
                if (parts.length >= 1) {
                    try {
                        sn.date = Long.parseLong(parts[0].trim());
                    } catch (NumberFormatException ignore) {
                        sn.date = 0;
                    }
                }
                if (parts.length >= 2) {
                    sn.text = parts[1];
                }
                if (parts.length >= 3) {
                    sn.sender = parts[2];
                }
            }
            out.add(sn);
        }
        return out;
    }

    public static void invalidateCache() {
        CACHE.clear();
    }

    private static boolean toggle(int account, long dialogId, int[] ids, boolean on) {
        final String k = key(account, dialogId);
        int[] cur = getBookmarkedIds(account, dialogId);
        Set<Integer> set = toSet(cur);

        boolean changed = false;
        for (int id : ids) {
            if (id <= 0) {
                continue;
            }
            if (on) {
                if (set.add(id)) {
                    changed = true;
                }
            } else {
                if (set.remove(id)) {
                    changed = true;
                }
            }
        }
        if (!changed) {
            return false;
        }

        int[] next = toSortedArray(set);
        CACHE.put(k, next);
        SharedPreferences.Editor ed = prefs().edit();
        if (next.length == 0) {
            ed.remove(k);
        } else {
            ed.putString(k, join(next));
        }
        ed.apply();
        return true;
    }

    private static int[] parse(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return new int[0];
        }
        Set<Integer> set = new HashSet<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int v = Integer.parseInt(t);
                if (v > 0) {
                    set.add(v);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return toSortedArray(set);
    }

    private static String join(int[] ids) {
        StringBuilder sb = new StringBuilder(ids.length * 8);
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids[i]);
        }
        return sb.toString();
    }

    private static Set<Integer> toSet(int[] ids) {
        Set<Integer> set = new HashSet<>();
        if (ids != null) {
            for (int id : ids) {
                set.add(id);
            }
        }
        return set;
    }

    private static int[] toSortedArray(Set<Integer> set) {
        List<Integer> list = new ArrayList<>(set);
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        Arrays.sort(arr);
        return arr;
    }
}
