package com.Huanghun.protocol;

import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 会话产物写出器：一个账号（auth_key + dc + uid）写成各平台可用容器。
 * 与 TdataParser 方向相反。auth_key 全程原样搬运，转换前后用 fingerprint 核对。
 */
public final class SessionWriter {

    public static final int DESKTOP_API_ID = 2040;
    public static final String DESKTOP_API_HASH = "b18441a1ff607e10a989891a5462e627";
    private static final int SESSION_STRING_SIZE = 271;

    private SessionWriter() {
    }

    public static final class Account {
        public byte[] authKey;
        public int dcId = 2;
        public long userId = 0L;
        public int apiId = DESKTOP_API_ID;
        public String apiHash = DESKTOP_API_HASH;
        public boolean isBot = false;
        public String source = "";

        public Account() {
        }

        public Account(byte[] authKey, int dcId, long userId) {
            this.authKey = authKey;
            this.dcId = dcId;
            this.userId = userId;
        }

        public static Account fromSessionData(ProtocolParser.SessionData data) {
            Account account = new Account();
            account.authKey = data.authKey;
            account.dcId = data.dcId;
            account.userId = data.userId;
            account.apiId = DESKTOP_API_ID;
            account.apiHash = DESKTOP_API_HASH;
            return account;
        }

        public String fingerprint() {
            return SessionWriter.fingerprint(authKey);
        }

        public String label() {
            return "uid=" + (userId == 0 ? "?" : String.valueOf(userId))
                    + " dc=" + dcId
                    + " fp=" + fingerprint();
        }

        void validate() throws IOException {
            if (authKey == null || authKey.length != 256) {
                throw new IOException("auth_key 必须是 256 字节，当前："
                        + (authKey == null ? "null" : String.valueOf(authKey.length)));
            }
            if (dcId < 1 || dcId > 5) {
                throw new IOException("DC 编号非法：" + dcId + "（应为 1~5）");
            }
        }
    }

    public static String fingerprint(byte[] authKey) {
        if (authKey == null) {
            return "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(authKey);
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ---------------------------------------------------------- Telethon

    public static File writeTelethon(Account account, File target, boolean force) throws IOException {
        account.validate();
        File out = ensureSessionExt(target);
        prepareTarget(out, force);
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openOrCreateDatabase(out, null);
            database.execSQL("CREATE TABLE version (version integer primary key)");
            database.execSQL("INSERT INTO version VALUES (8)");
            database.execSQL("CREATE TABLE sessions ("
                    + "dc_id integer primary key,"
                    + "server_address text,"
                    + "port integer,"
                    + "auth_key blob,"
                    + "takeout_id integer,"
                    + "tmp_auth_key blob)");
            database.execSQL("CREATE TABLE entities ("
                    + "id integer primary key,"
                    + "hash integer not null,"
                    + "username text,"
                    + "phone integer,"
                    + "name text,"
                    + "date integer)");
            database.execSQL("CREATE TABLE update_state ("
                    + "id integer primary key,"
                    + "pts integer,"
                    + "qts integer,"
                    + "date integer,"
                    + "seq integer)");
            String address = ProtocolParser.addressForDc(account.dcId);
            database.execSQL(
                    "INSERT INTO sessions (dc_id, server_address, port, auth_key, takeout_id, tmp_auth_key) VALUES (?,?,?,?,?,?)",
                    new Object[] {account.dcId, address, 443, account.authKey, null, null});
        } finally {
            if (database != null) {
                database.close();
            }
        }
        hardenPermissions(out);
        return out;
    }

    // ---------------------------------------------------------- Pyrogram

    public static File writePyrogram(Account account, File target, boolean force) throws IOException {
        account.validate();
        File out = ensureSessionExt(target);
        prepareTarget(out, force);
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openOrCreateDatabase(out, null);
            database.execSQL("CREATE TABLE sessions ("
                    + "dc_id INTEGER PRIMARY KEY,"
                    + "api_id INTEGER,"
                    + "test_mode INTEGER,"
                    + "auth_key BLOB,"
                    + "date INTEGER NOT NULL,"
                    + "user_id INTEGER,"
                    + "is_bot INTEGER)");
            database.execSQL("CREATE TABLE peers ("
                    + "id INTEGER PRIMARY KEY,"
                    + "access_hash INTEGER,"
                    + "type INTEGER NOT NULL,"
                    + "username TEXT,"
                    + "phone_number TEXT,"
                    + "last_update_on INTEGER NOT NULL DEFAULT (CAST(STRFTIME('%s', 'now') AS INTEGER)))");
            database.execSQL("CREATE TABLE version (number INTEGER PRIMARY KEY)");
            database.execSQL("CREATE INDEX idx_peers_id ON peers (id)");
            database.execSQL("CREATE INDEX idx_peers_username ON peers (username)");
            database.execSQL("CREATE INDEX idx_peers_phone_number ON peers (phone_number)");
            database.execSQL("CREATE TRIGGER trg_peers_last_update_on AFTER UPDATE ON peers BEGIN"
                    + " UPDATE peers SET last_update_on = CAST(STRFTIME('%s', 'now') AS INTEGER) WHERE id = NEW.id; END");
            database.execSQL("INSERT INTO version VALUES (3)");
            database.execSQL(
                    "INSERT INTO sessions (dc_id, api_id, test_mode, auth_key, date, user_id, is_bot) VALUES (?,?,?,?,?,?,?)",
                    new Object[] {
                            account.dcId,
                            account.apiId,
                            0,
                            account.authKey,
                            System.currentTimeMillis() / 1000L,
                            account.userId,
                            account.isBot ? 1 : 0
                    });
        } finally {
            if (database != null) {
                database.close();
            }
        }
        hardenPermissions(out);
        return out;
    }

    // ---------------------------------------------------------- session string

    public static String exportString(Account account) throws IOException {
        account.validate();
        ByteBuffer buffer = ByteBuffer.allocate(SESSION_STRING_SIZE);
        buffer.put((byte) account.dcId);
        buffer.putInt(account.apiId);
        buffer.put((byte) 0);
        buffer.put(account.authKey);
        buffer.putLong(account.userId);
        buffer.put((byte) (account.isBot ? 1 : 0));
        String encoded = Base64.encodeToString(buffer.array(), Base64.URL_SAFE | Base64.NO_WRAP);
        int end = encoded.length();
        while (end > 0 && encoded.charAt(end - 1) == '=') {
            end--;
        }
        return encoded.substring(0, end);
    }

    public static File writeString(Account account, File target, boolean force) throws IOException {
        File out = target.getName().endsWith(".txt")
                ? target
                : new File(target.getParentFile(), target.getName() + ".txt");
        ensureParent(out);
        if (out.exists() && !force) {
            throw new IOException("目标已存在：" + out.getAbsolutePath());
        }
        try (FileOutputStream stream = new FileOutputStream(out, false)) {
            stream.write((exportString(account) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        hardenPermissions(out);
        return out;
    }

    // ---------------------------------------------------------- JSON

    public static File writeJson(Account account, File target, boolean force) throws IOException {
        account.validate();
        File out = target.getName().endsWith(".json")
                ? target
                : new File(target.getParentFile(), target.getName() + ".json");
        ensureParent(out);
        if (out.exists() && !force) {
            throw new IOException("目标已存在：" + out.getAbsolutePath());
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("auth_key", toHex(account.authKey));
            payload.put("dc_id", account.dcId);
            payload.put("user_id", account.userId == 0 ? JSONObject.NULL : account.userId);
            payload.put("api_id", account.apiId);
            payload.put("api_hash", account.apiHash);
            payload.put("is_bot", account.isBot);
            payload.put("fingerprint", account.fingerprint());
            payload.put("exported_at", System.currentTimeMillis() / 1000L);
            try (FileOutputStream stream = new FileOutputStream(out, false)) {
                stream.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("写 JSON 失败：" + e.getMessage(), e);
        }
        hardenPermissions(out);
        return out;
    }

    // ---------------------------------------------------------- 内部工具

    private static File ensureSessionExt(File target) {
        if (target.getName().endsWith(".session")) {
            return target;
        }
        return new File(target.getParentFile(), target.getName() + ".session");
    }

    private static void prepareTarget(File out, boolean force) throws IOException {
        ensureParent(out);
        String base = out.getAbsolutePath();
        if (out.exists()) {
            if (!force) {
                throw new IOException("目标已存在：" + base);
            }
            out.delete();
        }
        for (String suffix : new String[] {"-wal", "-shm", "-journal"}) {
            File side = new File(base + suffix);
            if (side.exists()) {
                side.delete();
            }
        }
    }

    private static void ensureParent(File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("建目录失败：" + parent.getAbsolutePath());
        }
    }

    private static void hardenPermissions(File file) {
        try {
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
            file.setExecutable(false, false);
        } catch (Exception ignore) {
        }
    }

    public static String toHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
