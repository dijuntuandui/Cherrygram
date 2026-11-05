package com.Huanghun.protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 协议转换核心：把四种容器（tdata 目录 / .session / session string / JSON）互相转换。
 *
 * <p>不涉及任何 UI 与权限：输入输出都是已经落到本地的 {@link File}。
 * SAF 的 content:// URI 由调用方先落到临时目录再交给这里，
 * 这样核心逻辑可以在纯 JVM 下测试，不必背 Android 依赖。</p>
 */
public final class ProtocolConverter {

    /** 支持的输入类型。 */
    public enum InputKind {
        TDATA, SESSION, STRING, JSON
    }

    /** 单个账号的转换结果。 */
    public static final class Result {
        public SessionWriter.Account account;
        public File telethon;
        public File pyrogram;
        public File string;
        public File json;
        public File tdataDir;
        public final List<String> warnings = new ArrayList<>();

        public String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append(account == null ? "?" : account.label());
            if (telethon != null) builder.append("\n  telethon: ").append(telethon.getName());
            if (pyrogram != null) builder.append("\n  pyrogram: ").append(pyrogram.getName());
            if (string != null) builder.append("\n  string:   ").append(string.getName());
            if (json != null) builder.append("\n  json:     ").append(json.getName());
            if (tdataDir != null) builder.append("\n  tdata:    ").append(tdataDir.getName()).append("/");
            for (String warning : warnings) {
                builder.append("\n  注意: ").append(warning);
            }
            return builder.toString();
        }
    }

    private ProtocolConverter() {
    }

    // ---------------------------------------------------------- 读入

    /** 判断一个 File 属于哪种输入。 */
    public static InputKind detect(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("路径不存在：" + (file == null ? "null" : file.getAbsolutePath()));
        }
        if (file.isDirectory()) {
            if (new File(file, "key_datas").isFile()) {
                return InputKind.TDATA;
            }
            throw new IOException("目录里没有 key_datas，不像是 tdata：" + file.getName());
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".session")) return InputKind.SESSION;
        if (name.endsWith(".json")) return InputKind.JSON;
        if (name.endsWith(".txt")) return InputKind.STRING;
        throw new IOException("不支持的文件类型：" + file.getName());
    }

    /** 把一个输入读成账号列表。tdata 目录可能有多个账号。 */
    public static List<SessionWriter.Account> load(File file) throws Exception {
        InputKind kind = detect(file);
        switch (kind) {
            case TDATA:
                return loadTdata(file);
            case SESSION:
                return Collections.singletonList(SessionReader.readSessionDb(file));
            case JSON:
                return Collections.singletonList(SessionReader.readJson(file));
            case STRING:
                return SessionReader.readStringFile(file);
            default:
                throw new IOException("未知输入类型");
        }
    }

    private static List<SessionWriter.Account> loadTdata(File dir) throws Exception {
        ArrayList<ProtocolParser.SessionData> parsed = TdataParser.parse(dir);
        ArrayList<SessionWriter.Account> result = new ArrayList<>();
        for (ProtocolParser.SessionData data : parsed) {
            result.add(SessionWriter.Account.fromSessionData(data));
        }
        if (result.isEmpty()) {
            throw new IOException("tdata 中没有可用的账号");
        }
        return result;
    }

    // ---------------------------------------------------------- 写出

    /**
     * 把一个账号转换成全部产物，输出到 outDir/<base>/ 目录里。
     *
     * <p>产物命名与 PC 版工具一致：<base>.session / <base>.pyro.session /
     * <base>.string.txt / <base>.json / tdata/。</p>
     */
    public static Result convert(SessionWriter.Account account, File outDir, boolean force)
            throws Exception {
        return convert(account, outDir, force, true, true, true, true, true);
    }

    public static Result convert(SessionWriter.Account account, File outDir, boolean force,
            boolean wantTelethon, boolean wantPyrogram, boolean wantString,
            boolean wantJson, boolean wantTdata) throws Exception {
        if (account == null || account.authKey == null) {
            throw new IOException("账号数据为空");
        }
        String base = baseName(account);
        File accountDir = new File(outDir, base);
        if (!accountDir.exists() && !accountDir.mkdirs() && !accountDir.exists()) {
            throw new IOException("建目录失败：" + accountDir.getAbsolutePath());
        }

        Result result = new Result();
        result.account = account;

        if (wantTelethon) {
            result.telethon = SessionWriter.writeTelethon(account, new File(accountDir, base), force);
        }
        if (wantPyrogram) {
            result.pyrogram = SessionWriter.writePyrogram(account, new File(accountDir, base + ".pyro"), force);
        }
        if (wantString) {
            result.string = SessionWriter.writeString(account, new File(accountDir, base + ".string"), force);
        }
        if (wantJson) {
            result.json = SessionWriter.writeJson(account, new File(accountDir, base), force);
        }
        if (wantTdata) {
            result.tdataDir = TdataWriter.write(account, new File(accountDir, "tdata"), force);
        }
        return result;
    }

    /** 产物基础名：有 uid 用 uid，没有就退回 key 指纹。 */
    private static String baseName(SessionWriter.Account account) {
        if (account.userId > 0) {
            return String.valueOf(account.userId);
        }
        return "acct_" + account.fingerprint();
    }

    // ---------------------------------------------------------- 批量与自检

    /** 批量转换，单个失败不影响其余。 */
    public static List<Result> convertAll(List<SessionWriter.Account> accounts, File outDir,
            boolean force, List<String> errors) throws Exception {
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i++) {
            try {
                results.add(convert(accounts.get(i), outDir, force));
            } catch (Exception e) {
                String message = "第 " + (i + 1) + " 个账号失败：" + e.getMessage();
                if (errors != null) {
                    errors.add(message);
                }
            }
        }
        if (results.isEmpty()) {
            throw new IOException("全部账号转换失败" + (errors != null && !errors.isEmpty()
                    ? "（" + errors.get(0) + "）" : ""));
        }
        return results;
    }

    /**
     * 自检：造一个随机 key 走完整往返，不碰真实账号。
     *
     * <p>验证链路：key → tdata → 读回 → session → 读回 → string → 解码，
     * 每一环的 auth_key 指纹都必须一致。</p>
     */
    public static String selfTest(File workDir) throws Exception {
        if (!workDir.exists() && !workDir.mkdirs() && !workDir.exists()) {
            throw new IOException("建目录失败：" + workDir.getAbsolutePath());
        }
        byte[] key = new byte[256];
        new java.security.SecureRandom().nextBytes(key);
        SessionWriter.Account origin = new SessionWriter.Account(key, 2, 777000L);
        String expected = origin.fingerprint();
        StringBuilder log = new StringBuilder();
        log.append("origin =").append(expected).append("\n");

        // tdata 往返
        File tdata = TdataWriter.write(origin, new File(workDir, "st_tdata"), true);
        List<SessionWriter.Account> back = loadTdata(tdata);
        String tdataFp = back.get(0).fingerprint();
        log.append("tdata  =").append(tdataFp).append(tdataFp.equals(expected) ? "  OK" : "  MISMATCH").append("\n");

        // session 往返
        File session = SessionWriter.writeTelethon(origin, new File(workDir, "st"), true);
        String sessionFp = SessionReader.readSessionDb(session).fingerprint();
        log.append("sess   =").append(sessionFp).append(sessionFp.equals(expected) ? "  OK" : "  MISMATCH").append("\n");

        // string 往返
        String encoded = SessionWriter.exportString(origin);
        String stringFp = SessionReader.decodeString(encoded).fingerprint();
        log.append("string =").append(stringFp).append(stringFp.equals(expected) ? "  OK" : "  MISMATCH").append("\n");

        // JSON 往返
        File json = SessionWriter.writeJson(origin, new File(workDir, "st"), true);
        String jsonFp = SessionReader.readJson(json).fingerprint();
        log.append("json   =").append(jsonFp).append(jsonFp.equals(expected) ? "  OK" : "  MISMATCH").append("\n");

        boolean allOk = tdataFp.equals(expected) && sessionFp.equals(expected)
                && stringFp.equals(expected) && jsonFp.equals(expected);
        log.append(allOk ? "结果：全部一致" : "结果：存在不一致");
        return log.toString();
    }
}
