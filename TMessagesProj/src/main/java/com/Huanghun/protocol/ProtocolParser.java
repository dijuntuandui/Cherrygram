package com.Huanghun.protocol;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class ProtocolParser {

    private ProtocolParser() {
    }

    public static final class SessionData {
        public int dcId = 2;
        public byte[] authKey;
        public long userId = 0;
        public String address = "";
        public int port = 443;
        /** [Aiqiyi] 从 JSON 产物里带回来的会话续期凭证；旧文件没有这个字段时为 null */
        public byte[] futureAuthToken;
    }

    public static String addressForDc(int dcId) {
        switch (dcId) {
            case 1: return "149.154.175.50";
            case 2: return "149.154.167.51";
            case 3: return "149.154.175.100";
            case 4: return "149.154.167.91";
            case 5: return "91.108.56.130";
            default: return "";
        }
    }

    private static boolean isValidDc(int dcId) {
        return dcId >= 1 && dcId <= 5;
    }

    public static SessionData parseTelethonSession(File sessionFile) throws Exception {
        if (sessionFile == null || !sessionFile.isFile()) {
            throw new Exception("session file not found");
        }
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                sessionFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            SessionData data = new SessionData();
            Set<String> columns = readColumns(database, "sessions");
            if (columns.isEmpty()) {
                throw new Exception("no sessions table in .session");
            }
            StringBuilder projection = new StringBuilder();
            projection.append(columns.contains("dc_id") ? "dc_id" : "0 AS dc_id");
            projection.append(columns.contains("auth_key") ? ", auth_key" : ", NULL AS auth_key");
            projection.append(columns.contains("server_address") ? ", server_address" : ", NULL AS server_address");
            projection.append(columns.contains("port") ? ", port" : ", NULL AS port");
            try (Cursor cursor = database.rawQuery(
                    "SELECT " + projection + " FROM sessions LIMIT 1", null)) {
                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) data.dcId = cursor.getInt(0);
                    if (!cursor.isNull(1)) data.authKey = cursor.getBlob(1);
                    if (!cursor.isNull(2)) data.address = cursor.getString(2);
                    if (!cursor.isNull(3)) data.port = cursor.getInt(3);
                }
            }
            data.userId = readUserId(database);
            if (data.authKey == null || data.authKey.length != 256) {
                throw new Exception("no valid 256-byte MTProto auth key in .session");
            }
            if (!isValidDc(data.dcId)) {
                throw new Exception("invalid datacenter id in .session");
            }
            if (data.address == null || data.address.trim().isEmpty()) {
                data.address = addressForDc(data.dcId);
            }
            if (data.port <= 0 || data.port > 65535) {
                data.port = 443;
            }
            return data;
        } finally {
            database.close();
        }
    }

    private static Set<String> readColumns(SQLiteDatabase database, String table) {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) result.add(cursor.getString(nameIndex));
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    private static long readUserId(SQLiteDatabase database) {
        String[] tables = new String[] {"entities", "users", "user"};
        for (String table : tables) {
            Set<String> columns = readColumns(database, table);
            if (!columns.contains("id")) continue;
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM " + table + " WHERE id > 0 LIMIT 1", null)) {
                if (cursor.moveToFirst()) return cursor.getLong(0);
            } catch (Exception ignore) {
            }
        }
        return 0;
    }
}
