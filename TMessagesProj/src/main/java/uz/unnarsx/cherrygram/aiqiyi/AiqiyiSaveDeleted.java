/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Saved Deleted Messages (anti-revoke).
 *
 * Storage layer: a dedicated SQLite table `saved_deleted_cherry` living inside
 * the same MessagesStorage database. When a message is about to be removed we
 * copy its serialized body here first, so the bubble can still be rendered with
 * a "deleted" mark instead of vanishing.
 *
 * Table schema:
 *   saved_deleted_cherry(dialog_id INTEGER, mid INTEGER, date INTEGER, data BLOB,
 *                        PRIMARY KEY(dialog_id, mid))
 */
package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import androidx.collection.LongSparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import uz.unnarsx.cherrygram.core.configs.CherrygramMessagesConfig;

public class AiqiyiSaveDeleted {

    public static final String TABLE = "saved_deleted_cherry";
    public static final String CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE +
            "(dialog_id INTEGER, mid INTEGER, date INTEGER, data BLOB, " +
            "PRIMARY KEY(dialog_id, mid))";

    /** Whether revoked messages should be retained locally. */
    public static boolean isEnabled() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether retained deleted messages should be rendered translucent. */
    public static boolean isTranslucent() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiTranslucentDeleted();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Whether the default "deleted" text mark should be replaced by an icon. */
    public static boolean useIcon() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiUseDeletedIcon();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The text mark to render for a deleted message. */
    public static String getDeletedMark() {
        try {
            String custom = CherrygramMessagesConfig.INSTANCE.getAiqiyiCustomDeletedMark();
            if (custom != null && !custom.trim().isEmpty()) {
                return custom;
            }
        } catch (Throwable ignored) {
        }
        return "已删除";
    }

    /**
     * Persist one deleted message body. Runs on the storage queue (caller side).
     * Never throws: a failure here must not break the normal delete flow.
     */
    public static void saveDeleted(SQLiteDatabase database, long dialogId, int mid, int date, TLRPC.Message message) {
        if (database == null || message == null || mid == 0) {
            return;
        }
        SQLitePreparedStatement state = null;
        try {
            state = database.executeFast(
                    "INSERT OR REPLACE INTO " + TABLE + " (dialog_id, mid, date, data) VALUES (?, ?, ?, ?)");
            state.requery();
            state.bindLong(1, dialogId);
            state.bindInteger(2, mid);
            state.bindInteger(3, date);
            state.bindTlObject(4, message);
            state.step();
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (state != null) {
                try {
                    state.dispose();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Drop a saved copy (e.g. when the user clears the chat or deletes it on purpose).
     */
    public static void removeDeleted(SQLiteDatabase database, long dialogId, int mid) {
        if (database == null || mid == 0) {
            return;
        }
        try {
            database.executeFast(String.format(Locale.US,
                    "DELETE FROM " + TABLE + " WHERE dialog_id = %d AND mid = %d", dialogId, mid))
                    .stepThis().dispose();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Load the serialized bodies of saved deleted messages for one dialog.
     * Returns null when nothing was saved.
     */
    public static ArrayList<TLRPC.Message> loadDeleted(SQLiteDatabase database, long dialogId) {
        if (database == null) {
            return null;
        }
        SQLiteCursor cursor = null;
        ArrayList<TLRPC.Message> result = null;
        try {
            // [Aiqiyi] Purge rows that were persisted before the id fix. Older
            // builds stored message.id straight out of the `data` blob, which can
            // be 0 there - such rows collapse onto PRIMARY KEY (dialog_id, 0) and
            // make every merged message jump to one spot. They are unrecoverable,
            // so drop them once here.
            try {
                database.executeFast(String.format(Locale.US,
                        "DELETE FROM " + TABLE + " WHERE mid = 0 OR date = 0"))
                        .stepThis().dispose();
            } catch (Throwable ignored) {
            }
            cursor = database.queryFinalized(String.format(Locale.US,
                    "SELECT data, mid, date FROM " + TABLE + " WHERE dialog_id = %d", dialogId));
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                int colMid = cursor.intValue(1);
                int colDate = cursor.intValue(2);
                if (data != null) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    message.readAttachPath(data, 0);
                    data.reuse();
                    if (message != null) {
                        // Column values win - same rule Telegram uses in
                        // getMessagesInternal (mid overwrites message.id).
                        if (colMid != 0) {
                            message.id = colMid;
                        }
                        if (colDate != 0) {
                            message.date = colDate;
                        }
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(message);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.dispose();
                } catch (Throwable ignored) {
                }
            }
        }
        return result;
    }

    /**
     * [Aiqiyi] Reload hook. Merges locally retained deleted messages back into a
     * page of freshly loaded history, so that a revoked message stays visible
     * after leaving and re-entering the chat (or after an app restart).
     *
     * Only rows inside [minMid, maxMid] are considered, so the merge stays
     * scoped to the page that is actually being displayed. Rows already present
     * in target are skipped (dedup), and every merged message is flagged
     * deleted=true so the UI renders it with the deleted mark instead of
     * treating it as a live message.
     *
     * Never throws.
     */
    public static void mergeIntoResult(SQLiteDatabase database, long dialogId, int minMid, int maxMid, TLRPC.TL_messages_messages target) {
        if (database == null || target == null) {
            return;
        }
        if (!isEnabled()) {
            return;
        }
        SQLiteCursor cursor = null;
        try {
            LongSparseArray<Boolean> present = null;
            for (int i = 0; i < target.messages.size(); i++) {
                TLRPC.Message m = target.messages.get(i);
                if (m == null) {
                    continue;
                }
                if (present == null) {
                    present = new LongSparseArray<>();
                }
                present.put(m.id, Boolean.TRUE);
            }

            String where = "dialog_id = " + dialogId;
            if (maxMid != Integer.MAX_VALUE) {
                where += " AND mid <= " + maxMid;
            }
            if (minMid > 0) {
                where += " AND mid >= " + minMid;
            }
            cursor = database.queryFinalized("SELECT data, mid FROM " + TABLE + " WHERE " + where + " ORDER BY mid ASC");

            ArrayList<TLRPC.Message> merged = null;
            while (cursor.next()) {
                int mid = 0;
                try {
                    mid = cursor.intValue(1);
                } catch (Throwable ignored) {
                }
                if (present != null && present.get(mid) != null) {
                    continue;
                }
                NativeByteBuffer data = null;
                try {
                    data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message == null) {
                        continue;
                    }
                    message.readAttachPath(data, 0);
                    message.dialog_id = dialogId;
                    if (message.id == 0 && mid != 0) {
                        message.id = mid;
                    }
                    if (merged == null) {
                        merged = new ArrayList<>();
                    }
                    merged.add(message);
                } catch (Throwable ignored) {
                } finally {
                    if (data != null) {
                        try {
                            data.reuse();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            if (merged != null && !merged.isEmpty()) {
                // [Aiqiyi] The page assembled by getMessagesInternal is NOT a single
                // monotonic list: the hole logic UNIONs a "date DESC" half with a
                // "date ASC" half. Inserting into that shape by binary search puts
                // messages in the wrong slots and flips the chat. Rebuild the whole
                // page in one consistent order instead (newest first, matching
                // messages.v2 ordering), keeping the merged entries in place.
                merged.addAll(target.messages);
                Collections.sort(merged, (a, b) -> {
                    if (a == null || b == null) {
                        return 0;
                    }
                    if (a.date != b.date) {
                        return Integer.compare(b.date, a.date);
                    }
                    return Integer.compare(b.id, a.id);
                });
                target.messages.clear();
                target.messages.addAll(merged);
                // Warm the membership cache with everything we just learned, so
                // processLoadedMessages (UI thread) can flag the MessageObjects
                // without touching SQLite.
                try {
                    java.util.HashSet<Integer> warm = new java.util.HashSet<>();
                    for (int i = 0; i < merged.size(); i++) {
                        warm.add(merged.get(i).id);
                    }
                    MEMBERSHIP_CACHE.put(dialogId, warm);
                    MEMBERSHIP_CACHE_STAMP.put(dialogId, android.os.SystemClock.elapsedRealtime());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.dispose();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * [Aiqiyi] Cheap membership test used by the loader when it builds
     * MessageObjects: a message that exists in saved_deleted_cherry was
     * revoked from the server, so its MessageObject must be flagged
     * deleted = true for the UI to draw the deleted mark.
     *
     * The full id set is cached per dialog for a short while so the loader
     * does not hit SQLite once per message when a page is assembled.
     *
     * Never throws.
     */
    private static final LongSparseArray<Long> MEMBERSHIP_CACHE_STAMP = new LongSparseArray<>();
    private static final LongSparseArray<java.util.HashSet<Integer>> MEMBERSHIP_CACHE = new LongSparseArray<>();
    private static final long MEMBERSHIP_TTL_MS = 3000L;

    /**
     * [Aiqiyi] UI-thread safe lookup. processLoadedMessages builds MessageObjects
     * on the UI thread, where touching SQLite directly would be a bad idea, so
     * this only reads the warm cache that the storage queue filled through
     * isDeletedMessage(). Returns false on a cold cache - the message simply
     * renders as normal for that frame, and the next storage-side pass fills it.
     */
    public static boolean isDeletedMessageCached(long dialogId, int mid) {
        if (mid == 0 || !isEnabled()) {
            return false;
        }
        try {
            java.util.HashSet<Integer> ids = MEMBERSHIP_CACHE.get(dialogId);
            return ids != null && ids.contains(mid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Storage-thread variant: refreshes the cache from SQLite if it is stale. */
    public static boolean isDeletedMessage(SQLiteDatabase database, long dialogId, int mid) {
        if (database == null || mid == 0 || !isEnabled()) {
            return false;
        }
        try {
            long now = android.os.SystemClock.elapsedRealtime();
            Long stamp = MEMBERSHIP_CACHE_STAMP.get(dialogId);
            java.util.HashSet<Integer> ids = MEMBERSHIP_CACHE.get(dialogId);
            if (stamp == null || ids == null || now - stamp > MEMBERSHIP_TTL_MS) {
                ids = new java.util.HashSet<>();
                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized("SELECT mid FROM " + TABLE + " WHERE dialog_id = " + dialogId);
                    while (cursor.next()) {
                        ids.add(cursor.intValue(0));
                    }
                } finally {
                    if (cursor != null) {
                        try {
                            cursor.dispose();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                MEMBERSHIP_CACHE.put(dialogId, ids);
                MEMBERSHIP_CACHE_STAMP.put(dialogId, now);
            }
            return ids.contains(mid);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** How many deleted messages are stored for a dialog. */
    public static int getDeletedCount(SQLiteDatabase database, long dialogId) {
        if (database == null) {
            return 0;
        }
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US,
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE dialog_id = %d", dialogId));
            if (cursor.next()) {
                return cursor.intValue(0);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.dispose();
                } catch (Throwable ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * [Aiqiyi] Pre-save hook. Called at the very entry of
     * MessagesStorage.markMessagesAsDeletedInternal, before any of the official
     * branch logic runs. It reads the message bodies straight out of
     * messages_v2 by id, so the copy is guaranteed regardless of which branch
     * (revoke / push-delete / user-delete) later executes.
     *
     * Never throws.
     */
    public static void preSaveBeforeDelete(SQLiteDatabase database, long currentUser, long dialogId, ArrayList<Integer> ids) {
        if (database == null || ids == null || ids.isEmpty()) {
            return;
        }
        if (!isEnabled()) {
            return;
        }
        if (dialogId == currentUser) {
            return;
        }
        SQLiteCursor cursor = null;
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(ids.get(i));
            }
            cursor = database.queryFinalized(String.format(Locale.US,
                    "SELECT uid, data, mid, date FROM messages_v2 WHERE mid IN(%s) AND uid = %d",
                    sb.toString(), dialogId));
            int saved = 0;
            while (cursor.next()) {
                long uid = cursor.longValue(0);
                NativeByteBuffer data = cursor.byteBufferValue(1);
                int mid = cursor.intValue(2);
                int date = cursor.intValue(3);
                if (data == null) {
                    continue;
                }
                try {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, 0);
                        // [Aiqiyi] message.id inside `data` is not authoritative -
                        // Telegram overwrites it with the mid column on read. Force
                        // the real id into the snapshot before it is persisted,
                        // otherwise the merged copy comes back with id 0 and the
                        // page ordering collapses.
                        if (mid != 0 && message.id == 0) {
                            message.id = mid;
                        }
                        if (message.date == 0 && date != 0) {
                            message.date = date;
                        }
                        saveDeleted(database, uid, mid, date, message);
                        saved++;
                    }
                } catch (Throwable ignore) {
                } finally {
                    data.reuse();
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                try { cursor.dispose(); } catch (Throwable ignored) {}
            }
        }
    }
    /** Wipe every saved deleted message. */
    public static void clearAll(SQLiteDatabase database) {
        if (database == null) {
            return;
        }
        try {
            database.executeFast("DELETE FROM " + TABLE).stepThis().dispose();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private AiqiyiSaveDeleted() {
    }
}