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
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import androidx.collection.LongSparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import uz.unnarsx.cherrygram.core.configs.CherrygramMessagesConfig;

public class AiqiyiSaveDeleted {

    public static final String TABLE = "saved_deleted_cherry";
    /** [Aiqiyi] key/value side table used for one-shot migration markers. */
    public static final String META_TABLE = "aiqiyi_meta";
    public static final String CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE +
            "(dialog_id INTEGER, mid INTEGER, date INTEGER, data BLOB, path TEXT, " +
            "PRIMARY KEY(dialog_id, mid))";

    /**
     * [Aiqiyi] Add the `path` column to databases created by earlier builds.
     * SQLite has no "add column if not exists", so probe table_info first.
     * Never throws.
     */
    public static void ensurePathColumn(SQLiteDatabase database) {
        if (database == null) {
            return;
        }
        try {
            boolean has = false;
            SQLiteCursor c = null;
            try {
                c = database.queryFinalized("PRAGMA table_info(" + TABLE + ")");
                while (c.next()) {
                    String name = c.stringValue(1);
                    if ("path".equals(name)) {
                        has = true;
                        break;
                    }
                }
            } finally {
                if (c != null) {
                    c.dispose();
                }
            }
            if (!has) {
                database.executeFast("ALTER TABLE " + TABLE + " ADD COLUMN path TEXT").stepThis().dispose();
                FileLog.d("AiqiyiSaveDeleted: added path column");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Whether revoked messages should be retained locally. */
    public static boolean isEnabled() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * [Aiqiyi] Force the anti-revoke switch on for installs that predate the
     * default flip. Earlier builds shipped with the preference defaulting to
     * false, so devices that already ran one of them have an explicit `false`
     * persisted and would ignore the new default - the feature would look
     * completely broken (revoked messages vanish after a restart).
     *
     * Runs once per installation, tracked in aiqiyi_meta so a user who really
     * wants it off is not overridden again afterwards.
     *
     * Must be called once the storage database is open. Never throws.
     */
    public static void forceEnableOnce(SQLiteDatabase database) {
        if (database == null) {
            return;
        }
        try {
            database.executeFast("CREATE TABLE IF NOT EXISTS " + META_TABLE +
                    "(k TEXT, v INTEGER, PRIMARY KEY(k))").stepThis().dispose();
            SQLiteCursor c = null;
            try {
                c = database.queryFinalized("SELECT v FROM " + META_TABLE + " WHERE k = 'enable_v1'");
                if (c.next()) {
                    return;
                }
            } finally {
                if (c != null) {
                    c.dispose();
                }
            }
            if (!CherrygramMessagesConfig.INSTANCE.getAiqiyiSaveDeletedMessages()) {
                CherrygramMessagesConfig.INSTANCE.setAiqiyiSaveDeletedMessages(true);
                FileLog.d("AiqiyiSaveDeleted: forced anti-revoke switch on for legacy install");
            }
            database.executeFast("INSERT OR REPLACE INTO " + META_TABLE + " (k, v) VALUES ('enable_v1', 1)")
                    .stepThis().dispose();
        } catch (Throwable e) {
            FileLog.e(e);
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
     * [Aiqiyi] One-shot purge of rows written by builds that saved on *every*
     * delete path instead of only on server-pushed revokes. Those rows are
     * indistinguishable from real ones by inspection, so the only safe move is
     * to drop the whole table once and let it rebuild from genuine revokes.
     *
     * Guarded by a marker row so it runs exactly once per installation.
     * Never throws.
     */
    public static void purgeLegacyRowsOnce(SQLiteDatabase database) {
        if (database == null) {
            return;
        }
        try {
            database.executeFast("CREATE TABLE IF NOT EXISTS " + META_TABLE +
                    "(k TEXT, v INTEGER, PRIMARY KEY(k))").stepThis().dispose();
            SQLiteCursor c = null;
            try {
                c = database.queryFinalized("SELECT v FROM " + META_TABLE + " WHERE k = 'purge_v3'");
                if (c.next()) {
                    return;
                }
            } finally {
                if (c != null) {
                    c.dispose();
                }
            }
            database.executeFast("DELETE FROM " + TABLE).stepThis().dispose();
            // [Aiqiyi] the in-memory sets describe rows that no longer exist
            REVOKED_IDS.clear();
            MEMBERSHIP_CACHE.clear();
            MEMBERSHIP_CACHE_STAMP.clear();
            database.executeFast("INSERT OR REPLACE INTO " + META_TABLE + " (k, v) VALUES ('purge_v3', 1)")
                    .stepThis().dispose();
            FileLog.d("AiqiyiSaveDeleted: purged legacy rows written by over-eager save path");
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Persist one deleted message body. Runs on the storage queue (caller side).
     * Never throws: a failure here must not break the normal delete flow.
     */
    public static void saveDeleted(SQLiteDatabase database, long dialogId, int mid, int date, TLRPC.Message message) {
        saveDeleted(database, dialogId, mid, date, message, null);
    }

    /**
     * [Aiqiyi] Same as {@link #saveDeleted(SQLiteDatabase, long, int, int, TLRPC.Message)}
     * but also records a locally cached file path for the attachment, if one
     * already exists. Nothing is ever downloaded here - this only points at files
     * that are already sitting in the app cache, so revoked photos can still be
     * rendered after the message row is wiped.
     */
    public static void saveDeleted(SQLiteDatabase database, long dialogId, int mid, int date, TLRPC.Message message, String attachPath) {
        if (database == null || message == null || mid == 0) {
            return;
        }
        SQLitePreparedStatement state = null;
        try {
            state = database.executeFast(
                    "INSERT OR REPLACE INTO " + TABLE + " (dialog_id, mid, date, data, path) VALUES (?, ?, ?, ?, ?)");
            state.requery();
            state.bindLong(1, dialogId);
            state.bindInteger(2, mid);
            state.bindInteger(3, date);
            state.bindTlObject(4, message);
            if (attachPath != null && !attachPath.isEmpty()) {
                state.bindString(5, attachPath);
            } else {
                state.bindNull(5);
            }
            state.step();
            // [Aiqiyi] A freshly revoked message must be flagged immediately, and
            // the session-wide set is the only thing isDeletedMessageCached
            // trusts on the UI thread.
            try {
                java.util.HashSet<Integer> all = REVOKED_IDS.get(dialogId);
                if (all == null) {
                    all = new java.util.HashSet<>();
                    REVOKED_IDS.put(dialogId, all);
                }
                all.add(mid);
            } catch (Throwable ignored) {
            }
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
     * [Aiqiyi] Resolve the already-cached local file for a media attachment.
     *
     * Strictly read-only: it asks FileLoader where the file would be and only
     * returns a path when that exact file exists on disk. It never triggers a
     * download and never copies anything into the user's storage, so a revoked
     * photo whose cache entry was already evicted simply yields null and falls
     * back to the normal "file unavailable" placeholder.
     */
    public static String resolveCachedPath(int account, TLRPC.Message message) {
        if (message == null || !(message.media instanceof TLRPC.TL_messageMediaPhoto)) {
            return null;
        }
        try {
            TLRPC.Photo photo = message.media.photo;
            if (photo == null || photo instanceof TLRPC.TL_photoEmpty) {
                return null;
            }
            // The size actually used for display is the largest available one;
            // FileLoader keys cache files by photo id + dc, so querying either the
            // photo object or its largest size lands on the same cache file.
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
            if (size == null || size instanceof TLRPC.TL_photoSizeEmpty) {
                return null;
            }
            java.io.File f = FileLoader.getInstance(account).getPathToAttach(size, null, true);
            if (f != null && f.exists() && f.length() > 0) {
                return f.getAbsolutePath();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
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
            // [Aiqiyi] keep the in-memory sets in sync with the table
            try {
                java.util.HashSet<Integer> all = REVOKED_IDS.get(dialogId);
                if (all != null) {
                    all.remove(mid);
                }
                java.util.HashSet<Integer> window = MEMBERSHIP_CACHE.get(dialogId);
                if (window != null) {
                    window.remove(mid);
                }
            } catch (Throwable ignored) {
            }
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
                    "SELECT data, mid, date, path FROM " + TABLE + " WHERE dialog_id = %d", dialogId));
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                int colMid = cursor.intValue(1);
                int colDate = cursor.intValue(2);
                String colPath = cursor.stringValue(3);
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
                        // [Aiqiyi] Re-point the attachment at the cache file that was
                        // already on disk when the message got revoked. ImageLoader
                        // prefers attachPath, so the photo keeps rendering without any
                        // network access. If the cache entry is gone by now we simply
                        // leave it unset and the normal placeholder shows up.
                        if (colPath != null && !colPath.isEmpty()) {
                            try {
                                if (new java.io.File(colPath).exists()) {
                                    message.attachPath = colPath;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(message);
                    }
                }
            }
                } catch (Throwable e) {
            android.util.Log.d("AQYDBG", "preSave EX " + e);
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
        android.util.Log.d("AQYDBG", "merge ENTER dialog=" + dialogId + " win=[" + minMid + "," + maxMid + "] db=" + (database == null ? "null" : "ok") + " target=" + (target == null ? "null" : target.messages.size()));
        if (database == null || target == null) {
            return;
        }
        if (!isEnabled()) {
            return;
        }
        SQLiteCursor cursor = null;
        int diagMerged = 0;
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
            // [Aiqiyi] ids that really came from saved_deleted_cherry. Kept apart
            // from `merged` because that list also gets the whole live page
            // appended for sorting purposes.
            java.util.HashSet<Integer> revokedIds = new java.util.HashSet<>();
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
                    // [Aiqiyi] remember the real revoked id for the membership cache
                    if (mid != 0) {
                        revokedIds.add(mid);
                    } else if (message.id != 0) {
                        revokedIds.add(message.id);
                    }
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

            diagMerged = merged == null ? 0 : merged.size();
            android.util.Log.d("AQYDBG", "merge dialog=" + dialogId + " win=[" + minMid + "," + maxMid
                    + "] live=" + target.messages.size() + " hit=" + diagMerged);
            if (merged != null && !merged.isEmpty()) {
                // [Aiqiyi] The page assembled by getMessagesInternal is NOT a single
                // monotonic list: the hole logic UNIONs a "date DESC" half with a
                // "date ASC" half. Inserting into that shape by binary search puts
                // messages in the wrong slots and flips the chat. Rebuild the whole
                // page in one consistent order instead (newest first, matching
                // messages.v2 ordering), keeping the merged entries in place.
                merged.addAll(target.messages);
                // [Aiqiyi] Only messages that actually came out of
                // saved_deleted_cherry are revoked. `merged` at this point also
                // contains every live message of the page, so the cache must be
                // built from `revokedIds` alone - seeding it with the whole page
                // made healthy messages render as deleted.
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
                // Warm the membership cache with the revoked ids only, so
                // processLoadedMessages (UI thread) can flag the MessageObjects
                // without touching SQLite.
                try {
                    MEMBERSHIP_CACHE.put(dialogId, revokedIds);
                    MEMBERSHIP_CACHE_STAMP.put(dialogId, android.os.SystemClock.elapsedRealtime());
                    // [Aiqiyi] REVOKED_IDS itself is rebuilt in full by
                    // refreshRevokedIds() at the end of this method - doing an
                    // addAll here would only ever see the windowed subset.
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
        // [Aiqiyi] The page window above exists only to keep very old revoked
        // messages from being dragged into the first screen. The *membership*
        // cache must not inherit that window: a freshly revoked id is always
        // outside the range built from the page's live ids, so the flag would
        // never be set and the bubble would render as a normal message.
        refreshRevokedIds(database, dialogId);
    }

    /**
     * [Aiqiyi] Rebuild REVOKED_IDS for one dialog from saved_deleted_cherry,
     * unconditionally (no id window). Storage thread only. Never throws.
     */
    private static void refreshRevokedIds(SQLiteDatabase database, long dialogId) {
        android.util.Log.d("AQYDBG", "refresh ENTER dialog=" + dialogId);
        if (database == null) {
            return;
        }
        SQLiteCursor c = null;
        try {
            java.util.HashSet<Integer> all = new java.util.HashSet<>();
            c = database.queryFinalized("SELECT mid FROM " + TABLE + " WHERE dialog_id = " + dialogId);
            while (c.next()) {
                try {
                    all.add(c.intValue(0));
                } catch (Throwable ignored) {
                }
            }
            if (!all.isEmpty()) {
                REVOKED_IDS.put(dialogId, all);
            }
            android.util.Log.d("AQYDBG", "refresh dialog=" + dialogId + " revokedTotal=" + all.size());
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (c != null) {
                try { c.dispose(); } catch (Throwable ignored) {}
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
    /**
     * [Aiqiyi] The authoritative set. `mergeIntoResult` puts the revoked ids of a
     * dialog here and never removes them within a session, so the flag does not
     * depend on how fast processLoadedMessages happens to run after the merge.
     * Rebuilt from SQLite the first time a dialog is merged.
     */
    private static final LongSparseArray<java.util.HashSet<Integer>> REVOKED_IDS = new LongSparseArray<>();
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
            java.util.HashSet<Integer> probe = REVOKED_IDS.get(dialogId);
            android.util.Log.d("AQYDBG", "isCached dialog=" + dialogId + " mid=" + mid
                    + " revokedSet=" + (probe == null ? "null" : probe.size())
                    + " hit=" + (probe != null && probe.contains(mid)));
        } catch (Throwable ignored) {
        }
        try {
            // [Aiqiyi] Prefer the session-wide set: it is only ever filled from
            // saved_deleted_cherry and never seeded with live page ids, so it
            // cannot flag a message that was never revoked.
            java.util.HashSet<Integer> all = REVOKED_IDS.get(dialogId);
            if (all != null && all.contains(mid)) {
                return true;
            }
            // Fall back to the freshly merged window for dialogs that have not
            // been merged yet in this process.
            java.util.HashSet<Integer> ids = MEMBERSHIP_CACHE.get(dialogId);
            if (ids == null) {
                return false;
            }
            Long stamp = MEMBERSHIP_CACHE_STAMP.get(dialogId);
            if (stamp == null || android.os.SystemClock.elapsedRealtime() - stamp > MEMBERSHIP_TTL_MS) {
                MEMBERSHIP_CACHE.remove(dialogId);
                MEMBERSHIP_CACHE_STAMP.remove(dialogId);
                return false;
            }
            return ids.contains(mid);
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
        android.util.Log.d("AQYDBG", "preSave ENTER db=" + (database == null ? "null" : "ok")
                + " ids=" + (ids == null ? "null" : ids.size()) + " dialog=" + dialogId + " me=" + currentUser);
        if (database == null || ids == null || ids.isEmpty()) {
            android.util.Log.d("AQYDBG", "preSave EARLY-out null/empty");
            return;
        }
        if (!isEnabled()) {
            android.util.Log.d("AQYDBG", "preSave EARLY-out disabled");
            return;
        }
        if (dialogId == 0) {
            // [Aiqiyi] TL_updateDeleteMessages carries no peer, so the controller
            // stacks it under dialogId 0 and resolves the real dialog later from
            // each message id. Querying "WHERE mid IN(...) AND uid = 0" therefore
            // matches nothing and every revoked body was dropped. Resolve the row
            // by mid alone and keep whatever uid the row already has.
            android.util.Log.d("AQYDBG", "preSave dialog=0 -> resolve by mid only");
        } else if (dialogId == currentUser) {
            android.util.Log.d("AQYDBG", "preSave EARLY-out self-dialog");
            return;
        }
        SQLiteCursor cursor = null;
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(ids.get(i));
            }
            if (dialogId == 0) {
                android.util.Log.d("AQYDBG", "preSave SQL mid IN(" + sb + ") no-uid");
                cursor = database.queryFinalized(String.format(Locale.US,
                        "SELECT uid, data, mid, date FROM messages_v2 WHERE mid IN(%s)",
                        sb.toString()));
            } else {
                android.util.Log.d("AQYDBG", "preSave SQL mid IN(" + sb + ") uid=" + dialogId);
                cursor = database.queryFinalized(String.format(Locale.US,
                        "SELECT uid, data, mid, date FROM messages_v2 WHERE mid IN(%s) AND uid = %d",
                        sb.toString(), dialogId));
            }
            int saved = 0;
            int scanned = 0;
            while (cursor.next()) {
                scanned++;
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
                        // [Aiqiyi] messages_v2.mid can be NULL/0 for rows that were
                        // never backfilled (and the `data` blob then carries the
                        // only real id). Both directions must be covered: fill
                        // message.id from mid, and fill mid from message.id.
                        // Missing the second direction used to feed mid == 0 into
                        // saveDeleted, which silently drops the row - that is why
                        // revoked messages were never persisted at all.
                        if (mid != 0 && message.id == 0) {
                            message.id = mid;
                        }
                        if (mid == 0 && message.id != 0) {
                            mid = message.id;
                        }
                        if (message.date == 0 && date != 0) {
                            message.date = date;
                        }
                        if (date == 0 && message.date != 0) {
                            date = message.date;
                        }
                        if (mid == 0) {
                            android.util.Log.d("AQYDBG", "preSave SKIP id-less row dialog=" + dialogId);
                            continue;
                        }
                        // [Aiqiyi] B: keep revoked photos visible by pointing at the
                        // copy that is already in the app cache. resolveCachedPath is
                        // read-only - it never downloads and never writes to external
                        // storage, so this cannot leak anything the user did not
                        // already have on device.
                        String cached = null;
                        try {
                            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                                cached = resolveCachedPath(UserConfig.selectedAccount, message);
                            }
                        } catch (Throwable ignored) {
                        }
                        saveDeleted(database, uid, mid, date, message, cached);
                        saved++;
                    }
                } catch (Throwable ignore) {
                } finally {
                    data.reuse();
                }
            }
            android.util.Log.d("AQYDBG", "preSave QUERY done dialog=" + dialogId
                    + " ids=" + ids.size() + " scanned=" + scanned + " saved=" + saved);
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
            REVOKED_IDS.clear();
            MEMBERSHIP_CACHE.clear();
            MEMBERSHIP_CACHE_STAMP.clear();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
    private AiqiyiSaveDeleted() {
    }
}