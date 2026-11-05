package com.Huanghun.protocol;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话产物读取器：把各种容器读回统一的 {@link SessionWriter.Account}。
 *
 * <p>与 SessionWriter 互为反函数。读入的文件格式由扩展名与内容双重判断，
 * 不依赖用户手选格式，降低选错导致误报的概率。</p>
 */
public final class SessionReader {

    private SessionReader() {
    }

    /** 自动识别格式并读出所有账号。 */
    public static List<SessionWriter.Account> read(File file) throws Exception {
        if (file == null || !file.isFile()) {
            throw new IOException("文件不存在：" + (file == null ? "null" : file.getAbsolutePath()));
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".json")) {
            List<SessionWriter.Account> result = new ArrayList<>();
            result.add(readJson(file));
            return result;
        }
        if (name.endsWith(".txt")) {
            return readStringFile(file);
        }
        if (name.endsWith(".session")) {
            List<SessionWriter.Account> result = new ArrayList<>();
            result.add(readSessionDb(file));
            return result;
        }
        throw new IOException("不支持的文件类型：" + file.getName());
    }

    // ---------------------------------------------------------- .session

    /** 打开 SQLite，根据表结构判定是 Telethon 还是 Pyrogram。 */
    public static SessionWriter.Account readSessionDb(File file) throws Exception {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            Set<String> sessionColumns = readColumns(database, "sessions");
            if (sessionColumns.isEmpty()) {
                throw new IOException(".session 里没有 sessions 表");
            }
            SessionWriter.Account account = sessionColumns.contains("api_id")
                    ? readPyrogramRow(database, sessionColumns)
                    : readTelethonRow(database, sessionColumns);
            validateAccount(account);
            return account;
        } finally {
            database.close();
        }
    }

    private static SessionWriter.Account readTelethonRow(SQLiteDatabase database, Set<String> columns)
            throws Exception {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns.contains("dc_id") ? "dc_id" : "0 AS dc_id").append(", ");
        sql.append(columns.contains("auth_key") ? "auth_key" : "NULL AS auth_key").append(", ");
        sql.append(columns.contains("server_address") ? "server_address" : "NULL AS server_address").append(", ");
        sql.append(columns.contains("port") ? "port" : "0 AS port");
        sql.append(" FROM sessions LIMIT 1");
        SessionWriter.Account account = new SessionWriter.Account();
        try (Cursor cursor = database.rawQuery(sql.toString(), null)) {
            if (!cursor.moveToFirst()) {
                throw new IOException(".session 的 sessions 表是空的");
            }
            account.dcId = cursor.getInt(0);
            account.authKey = cursor.isNull(1) ? null : cursor.getBlob(1);
        }
        account.userId = readUserIdFromTables(database);
        account.apiId = SessionWriter.DESKTOP_API_ID;
        account.apiHash = SessionWriter.DESKTOP_API_HASH;
        account.source = "telethon";
        return account;
    }

    private static SessionWriter.Account readPyrogramRow(SQLiteDatabase database, Set<String> columns)
            throws Exception {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns.contains("dc_id") ? "dc_id" : "0 AS dc_id").append(", ");
        sql.append(columns.contains("auth_key") ? "auth_key" : "NULL AS auth_key").append(", ");
        sql.append(columns.contains("api_id") ? "api_id" : "0 AS api_id").append(", ");
        sql.append(columns.contains("user_id") ? "user_id" : "0 AS user_id").append(", ");
        sql.append(columns.contains("is_bot") ? "is_bot" : "0 AS is_bot");
        sql.append(" FROM sessions LIMIT 1");
        SessionWriter.Account account = new SessionWriter.Account();
        try (Cursor cursor = database.rawQuery(sql.toString(), null)) {
            if (!cursor.moveToFirst()) {
                throw new IOException(".session 的 sessions 表是空的");
            }
            account.dcId = cursor.getInt(0);
            account.authKey = cursor.isNull(1) ? null : cursor.getBlob(1);
            account.apiId = cursor.getInt(2);
            account.userId = cursor.isNull(3) ? 0L : cursor.getLong(3);
            account.isBot = !cursor.isNull(4) && cursor.getInt(4) != 0;
        }
        account.apiHash = SessionWriter.DESKTOP_API_HASH;
        account.source = "pyrogram";
        return account;
    }

    // ---------------------------------------------------------- session string

    /** 解码 Pyrogram session string（271 字节定长结构）。 */
    public static SessionWriter.Account decodeString(String value) throws Exception {
        if (value == null) {
            throw new IOException("session string 为空");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("session string 为空");
        }
        byte[] raw;
        try {
            raw = Base64.decode(trimmed, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IOException("session string 不是合法的 base64：" + e.getMessage(), e);
        }
        if (raw.length != 271) {
            throw new IOException("session string 长度异常：" + raw.length + "（应为 271）");
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        SessionWriter.Account account = new SessionWriter.Account();
        account.dcId = buffer.get() & 0xff;
        account.apiId = buffer.getInt();
        int testMode = buffer.get() & 0xff;
        if (testMode != 0 && testMode != 1) {
            throw new IOException("session string 的 test_mode 字节异常：" + testMode);
        }
        account.authKey = new byte[256];
        buffer.get(account.authKey);
        account.userId = buffer.getLong();
        account.isBot = buffer.get() != 0;
        account.apiHash = SessionWriter.DESKTOP_API_HASH;
        account.source = "string";
        validateAccount(account);
        return account;
    }

    /** 读 txt：一行一条 session string，空行与 # 注释跳过。 */
    public static List<SessionWriter.Account> readStringFile(File file) throws Exception {
        List<SessionWriter.Account> result = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                index++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                try {
                    result.add(decodeString(trimmed));
                } catch (Exception e) {
                    errors.add("第 " + index + " 行：" + e.getMessage());
                }
            }
        }
        if (result.isEmpty()) {
            String detail = errors.isEmpty() ? "文件中没有有效内容" : errors.get(0);
            throw new IOException("txt 里没有可用 session：" + detail);
        }
        return result;
    }

    // ---------------------------------------------------------- JSON

    /** 读 JSON 归档，字段名与 SessionWriter.writeJson 对称。 */
    public static SessionWriter.Account readJson(File file) throws Exception {
        byte[] raw;
        try (FileInputStream stream = new FileInputStream(file)) {
            raw = readAll(stream, 4 * 1024 * 1024);
        }
        JSONObject payload = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        SessionWriter.Account account = new SessionWriter.Account();
        String hexKey = payload.optString("auth_key", "");
        if (hexKey.isEmpty()) {
            throw new IOException("JSON 缺少 auth_key 字段");
        }
        account.authKey = fromHex(hexKey);
        account.dcId = payload.optInt("dc_id", 2);
        account.userId = payload.optLong("user_id", 0L);
        account.apiId = payload.optInt("api_id", SessionWriter.DESKTOP_API_ID);
        String apiHash = payload.optString("api_hash", "");
        account.apiHash = apiHash.isEmpty() ? SessionWriter.DESKTOP_API_HASH : apiHash;
        account.isBot = payload.optBoolean("is_bot", false);
        account.source = "json";
        validateAccount(account);
        return account;
    }

    // ---------------------------------------------------------- 内部工具

    private static void validateAccount(SessionWriter.Account account) throws IOException {
        if (account.authKey == null || account.authKey.length != 256) {
            throw new IOException("auth_key 必须是 256 字节，当前："
                    + (account.authKey == null ? "null" : String.valueOf(account.authKey.length)));
        }
        if (account.dcId < 1 || account.dcId > 5) {
            throw new IOException("DC 编号非法：" + account.dcId + "（应为 1~5）");
        }
    }

    private static Set<String> readColumns(SQLiteDatabase database, String table) {
        Set<String> result = new HashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) {
                    result.add(cursor.getString(nameIndex));
                }
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    private static long readUserIdFromTables(SQLiteDatabase database) {
        String[] tables = new String[] {"entities", "users", "user"};
        for (String table : tables) {
            Set<String> columns = readColumns(database, table);
            if (!columns.contains("id")) {
                continue;
            }
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM " + table + " WHERE id > 0 LIMIT 1", null)) {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            } catch (Exception ignore) {
            }
        }
        return 0L;
    }

    private static byte[] readAll(FileInputStream stream, int limit) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > limit) {
                throw new IOException("文件过大，拒绝读取：" + limit + " 字节上限");
            }
        }
        return buffer.toByteArray();
    }

    public static byte[] fromHex(String hex) throws IOException {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        String value = hex.trim();
        if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        if ((value.length() & 1) != 0) {
            throw new IOException("十六进制字符串长度必须是偶数");
        }
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(value.charAt(i * 2), 16);
            int lo = Character.digit(value.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IOException("十六进制字符串含非法字符：位置 " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
