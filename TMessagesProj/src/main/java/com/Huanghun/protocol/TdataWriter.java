package com.Huanghun.protocol;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Telegram Desktop tdata 写出器。与 TdataParser 严格互逆。
 *
 * <p>产物与 opentele 一致：只写 key_datas 与账号数据文件，不写 map。
 * map 只是 TDesktop 的索引缓存，缺失时会自行重建；opentele 生成的 tdata
 * 在本工程已实测可正常往返读取，所以这里保持同样策略。</p>
 *
 * <p>注意：写出的 tdata 不带本地密码（passcode）。带密码的版本需要
 * 用户输入的 passcode，再由 PBKDF2 派生，当前先不做。</p>
 */
public final class TdataWriter {

    private static final byte[] TDF_MAGIC = "TDF$".getBytes(StandardCharsets.US_ASCII);
    private static final int TD_VERSION = 3004000;
    private static final int MTP_AUTHORIZATION_BLOCK = 0x4b;
    private static final long WIDE_IDS_TAG = -1L;
    private static final String DEFAULT_DATA_NAME = "data";
    /** lskType.lskUserSettings */
    private static final int LSK_USER_SETTINGS = 0x09;
    /** opentele 写 map 时唯一一个非零 FileKey（UserSettings）。 */
    private static final long SETTINGS_FILE_KEY = 1851671142505648812L;

    private TdataWriter() {
    }

    /** 一个账号的授权信息。 */
    public static final class Entry {
        public long userId;
        public int dcId = 2;
        public byte[] authKey;

        public Entry() {
        }

        public Entry(byte[] authKey, int dcId, long userId) {
            this.authKey = authKey;
            this.dcId = dcId;
            this.userId = userId;
        }
    }

    // ---------------------------------------------------------- 对外入口

    /**
     * 把一个或多个账号写成 tdata 目录。
     *
     * @param entries  账号列表，至少一个
     * @param root     目标 tdata 目录（不存在会自动建）
     * @param force    已存在同名数据文件时是否覆盖
     */
    public static File write(List<Entry> entries, File root, boolean force) throws Exception {
        if (entries == null || entries.isEmpty()) {
            throw new IOException("没有可写出的账号");
        }
        for (Entry entry : entries) {
            if (entry.authKey == null || entry.authKey.length != 256) {
                throw new IOException("auth_key 必须是 256 字节");
            }
            if (entry.dcId < 1 || entry.dcId > 5) {
                throw new IOException("DC 编号非法：" + entry.dcId);
            }
        }
        if (!root.exists() && !root.mkdirs() && !root.exists()) {
            throw new IOException("建目录失败：" + root.getAbsolutePath());
        }

        byte[] salt = new byte[32];
        new Random().nextBytes(salt);
        byte[] localKey = createLocalKey(salt);

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            indices.add(i);
            writeAccountData(entries.get(i), i, localKey, root, force);
        }
        writeKeyDatas(salt, localKey, indices, root, force);
        writeMaps(root, localKey, force);
        return root;
    }

    /** 便捷单账号入口。 */
    public static File write(SessionWriter.Account account, File root, boolean force) throws Exception {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(account.authKey, account.dcId, account.userId));
        return write(entries, root, force);
    }

    // ---------------------------------------------------------- 账号数据

    private static void writeAccountData(Entry entry, int index, byte[] localKey, File root, boolean force)
            throws Exception {
        String fileName = toFilePart(computeDataNameKey(DEFAULT_DATA_NAME));
        File target = new File(root, fileName + "s");
        if (target.exists() && !force) {
            throw new IOException("tdata 中已有账号数据：" + target.getName());
        }

        byte[] serialized = serializeMtpAuthorization(entry);
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        // 与 opentele 一致：blockId 之后是一个带 uint32 长度前缀的 QByteArray。
        writeInt32(block, MTP_AUTHORIZATION_BLOCK);
        writeByteArray(block, serialized);

        byte[] plain = block.toByteArray();
        ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
        writeByteArray(wrapped, prepareEncrypted(plain, localKey));
        writeFile(target, wrapped.toByteArray());
    }

    /** MtpAuthorization 块的序列化，与 opentele serializeMtpAuthorization 一致。 */
    static byte[] serializeMtpAuthorization(Entry entry) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt64(out, WIDE_IDS_TAG);
        writeInt64(out, entry.userId);
        writeInt32(out, entry.dcId);
        writeInt32(out, 1);
        writeInt32(out, entry.dcId);
        out.write(entry.authKey);
        writeInt32(out, 0);
        return out.toByteArray();
    }

    // ---------------------------------------------------------- maps

    /**
     * 写账号目录下的 maps。
     *
     * <p>opentele 装载时先 readMapWith()，map 读不出来就直接放弃整个账号，
     * 根本不会去看 MtpAuthorization。所以 maps 不是缓存而是必需文件。</p>
     *
     * <p>内容最少可以是「两个空段 + 一个只含 UserSettings 键的段」，
     * 与其他实现写出的 68 字节版本结构一致。</p>
     */
    private static void writeMaps(File root, byte[] localKey, boolean force) throws Exception {
        File dir = new File(root, toFilePart(computeDataNameKey(DEFAULT_DATA_NAME)));
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("建目录失败：" + dir.getAbsolutePath());
        }
        File target = new File(dir, "maps");
        if (target.exists() && !force) {
            throw new IOException("tdata 中已有 maps");
        }

        ByteArrayOutputStream mapBlock = new ByteArrayOutputStream();
        writeInt32(mapBlock, LSK_USER_SETTINGS);
        writeInt64(mapBlock, SETTINGS_FILE_KEY);

        ByteArrayOutputStream safe = new ByteArrayOutputStream();
        writeByteArray(safe, new byte[0]);
        writeByteArray(safe, new byte[0]);
        writeByteArray(safe, prepareEncrypted(mapBlock.toByteArray(), localKey));

        writeFile(target, safe.toByteArray());
    }

    // ---------------------------------------------------------- key_datas

    private static void writeKeyDatas(byte[] salt, byte[] localKey, List<Integer> indices,
            File root, boolean force) throws Exception {
        File target = new File(root, "key_datas");
        if (target.exists() && !force) {
            throw new IOException("tdata 中已有 key_datas");
        }

        ByteArrayOutputStream indexBlock = new ByteArrayOutputStream();
        writeInt32(indexBlock, indices.size());
        for (Integer index : indices) {
            writeInt32(indexBlock, index);
        }
        writeInt32(indexBlock, indices.isEmpty() ? 0 : indices.get(0));

        // 三段都按 QDataStream 写 QByteArray 的规矩：uint32 大端长度 + 数据。
        // 第二段是用 passcodeKey 加密的 localKey 本体，不是加密 salt；
        // 第三段才是用 localKey 加密的账号索引块。
        byte[] passcodeKey = createLocalKey(salt);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeByteArray(out, salt);
        writeByteArray(out, prepareEncrypted(localKey, passcodeKey));
        writeByteArray(out, prepareEncrypted(indexBlock.toByteArray(), localKey));

        writeFile(target, out.toByteArray());
    }

    // ---------------------------------------------------------- 加密

    /** localKey 派生，与 opentele CreateLocalKey 一致：PBKDF2-HMAC-SHA512(SHA512(salt+salt), salt, 1, 256)。 */
    static byte[] createLocalKey(byte[] salt) throws Exception {
        byte[] passcodeHash = digest("SHA-512", concat(salt, salt));
        return pbkdf2Sha512(passcodeHash, salt, 1, 256);
    }

    /** 明文加密：补零对齐 → 长度头 → SHA1 前 16 字节作 messageKey → AES-IGE。 */
    static byte[] prepareEncrypted(byte[] plain, byte[] localKey) throws Exception {
        // EncryptedDescriptor 会先 seek(4) 预留长度位，真实数据从第 4 字节开始。
        // 所以明文布局是 [uint32 长度][数据]，整体再补零到 16 字节对齐。
        int size = plain.length;
        int body = 4 + size;
        int fullSize = body;
        if ((fullSize & 0x0F) != 0) {
            fullSize += 0x10 - (fullSize & 0x0F);
        }
        byte[] buffer = new byte[fullSize];
        // 长度字段记录的是「含 4 字节头」的总长度，与 opentele 的
        // EncryptedDescriptor(kSize) 先 resize(4+kSize) 再 seek(4) 行为一致。
        writeInt32At(buffer, 0, body);
        System.arraycopy(plain, 0, buffer, 4, size);

        byte[] messageKey = new byte[16];
        System.arraycopy(digest("SHA-1", buffer), 0, messageKey, 0, 16);
        byte[][] keyAndIv = deriveIgeKey(localKey, messageKey);
        byte[] cipherText = aesIgeEncrypt(buffer, keyAndIv[0], keyAndIv[1]);

        byte[] result = new byte[16 + cipherText.length];
        System.arraycopy(messageKey, 0, result, 0, 16);
        System.arraycopy(cipherText, 0, result, 16, cipherText.length);
        return result;
    }

    static byte[][] deriveIgeKey(byte[] authKey, byte[] messageKey) throws Exception {
        if (authKey == null || authKey.length < 136 || messageKey == null || messageKey.length != 16) {
            throw new IOException("密钥材料无效");
        }
        int x = 8;
        byte[] sha1a = digest("SHA-1", concat(messageKey, slice(authKey, x, 32)));
        byte[] sha1b = digest("SHA-1", concat(slice(authKey, 32 + x, 16), messageKey, slice(authKey, 48 + x, 16)));
        byte[] sha1c = digest("SHA-1", concat(slice(authKey, 64 + x, 32), messageKey));
        byte[] sha1d = digest("SHA-1", concat(messageKey, slice(authKey, 96 + x, 32)));
        byte[] aesKey = concat(slice(sha1a, 0, 8), slice(sha1b, 8, 12), slice(sha1c, 4, 12));
        byte[] aesIv = concat(slice(sha1a, 8, 12), slice(sha1b, 0, 8), slice(sha1c, 16, 4), slice(sha1d, 0, 8));
        return new byte[][] {aesKey, aesIv};
    }

    static byte[] aesIgeEncrypt(byte[] plain, byte[] key, byte[] iv) throws Exception {
        if (plain.length == 0 || plain.length % 16 != 0 || iv.length != 32) {
            throw new IOException("AES-IGE 参数无效");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] previousCipher = slice(iv, 0, 16);
        byte[] previousPlain = slice(iv, 16, 16);
        byte[] output = new byte[plain.length];
        for (int position = 0; position < plain.length; position += 16) {
            byte[] block = slice(plain, position, 16);
            byte[] mixed = xor(block, previousCipher);
            byte[] encrypted = cipher.doFinal(mixed);
            byte[] cipherBlock = xor(encrypted, previousPlain);
            System.arraycopy(cipherBlock, 0, output, position, 16);
            previousCipher = cipherBlock;
            previousPlain = block;
        }
        return output;
    }

    // ---------------------------------------------------------- TDF 封装

    /** TDF 文件：TDF$ + version(4LE) + safeData + md5(16)。 */
    static void writeFile(File target, byte[] safeData) throws Exception {
        byte[] version = new byte[4];
        writeInt32LeAt(version, 0, TD_VERSION);
        byte[] tail = new byte[] {
                (byte) safeData.length, (byte) (safeData.length >>> 8),
                (byte) (safeData.length >>> 16), (byte) (safeData.length >>> 24)
        };
        byte[] md5 = digest("MD5", concat(safeData, tail, version, TDF_MAGIC));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TDF_MAGIC);
        out.write(version);
        out.write(safeData);
        out.write(md5);

        try (FileOutputStream stream = new FileOutputStream(target, false)) {
            stream.write(out.toByteArray());
        }
    }

    // ---------------------------------------------------------- 名称哈希

    /** md5(dataName) 按小端读成 128 位整数。 */
    static byte[] computeDataNameKey(String dataName) throws Exception {
        return digest("MD5", dataName.getBytes(StandardCharsets.UTF_8));
    }

    /** 把 16 字节按小端 nibble 顺序转成 16 个十六进制字符。 */
    static String toFilePart(byte[] key) {
        if (key == null || key.length < 16) {
            throw new IllegalArgumentException("dataNameKey 需要 16 字节");
        }
        // 对齐 opentele ToFilePart：md5 按小端视为 128 位整数，从最低位起
        // 每 4 bit 取一个十六进制字符，共 16 个。等价于按顺序取前 8 字节的
        // 低 nibble、高 nibble，因为小端下最低位就在第 0 字节的低半字节。
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            int b = key[i] & 0xff;
            builder.append(Character.toUpperCase(Character.forDigit(b & 0x0F, 16)));
            builder.append(Character.toUpperCase(Character.forDigit((b >>> 4) & 0x0F, 16)));
        }
        return builder.toString();
    }

    // ---------------------------------------------------------- 工具

    private static byte[] digest(String algorithm, byte[] data) throws Exception {
        return MessageDigest.getInstance(algorithm).digest(data);
    }

    private static byte[] pbkdf2Sha512(byte[] password, byte[] salt, int iterations, int outputBytes)
            throws Exception {
        if (iterations <= 0 || outputBytes <= 0) {
            throw new IOException("PBKDF2 参数无效");
        }
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(password, "HmacSHA512"));
        int hashSize = mac.getMacLength();
        int blocks = (outputBytes + hashSize - 1) / hashSize;
        byte[] output = new byte[outputBytes];
        int outputOffset = 0;
        for (int blockIndex = 1; blockIndex <= blocks; blockIndex++) {
            mac.update(salt);
            mac.update((byte) (blockIndex >>> 24));
            mac.update((byte) (blockIndex >>> 16));
            mac.update((byte) (blockIndex >>> 8));
            mac.update((byte) blockIndex);
            byte[] u = mac.doFinal();
            byte[] t = java.util.Arrays.copyOf(u, u.length);
            for (int round = 1; round < iterations; round++) {
                u = mac.doFinal(u);
                for (int i = 0; i < t.length; i++) {
                    t[i] ^= u[i];
                }
            }
            int copyLength = Math.min(t.length, output.length - outputOffset);
            System.arraycopy(t, 0, output, outputOffset, copyLength);
            outputOffset += copyLength;
        }
        return output;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static byte[] slice(byte[] data, int from, int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, from, result, 0, length);
        return result;
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /** QDataStream 写 QByteArray：uint32 大端长度 + 数据（null 时长度为 0xFFFFFFFF）。 */
    private static void writeByteArray(ByteArrayOutputStream out, byte[] data) {
        if (data == null) {
            out.write(0xff);
            out.write(0xff);
            out.write(0xff);
            out.write(0xff);
            return;
        }
        int size = data.length;
        out.write((size >>> 24) & 0xff);
        out.write((size >>> 16) & 0xff);
        out.write((size >>> 8) & 0xff);
        out.write(size & 0xff);
        out.write(data, 0, size);
    }

    /** QDataStream 默认大端，所有走 stream 的整数都按这个写。 */
    private static void writeInt32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private static void writeInt64(ByteArrayOutputStream out, long value) {
        writeInt32(out, (int) ((value >>> 32) & 0xFFFFFFFFL));
        writeInt32(out, (int) (value & 0xFFFFFFFFL));
    }

    /**
     * 加密体的长度头是唯一的小端字段：DecryptLocal 用 int.from_bytes(..., "little") 读。
     * 它和 QDataStream 的大端整数不是一回事，不要混用。
     */
    private static void writeInt32At(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xff);
        target[offset + 1] = (byte) ((value >>> 8) & 0xff);
        target[offset + 2] = (byte) ((value >>> 16) & 0xff);
        target[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    /** TDF 文件尾的长度与版本字段是单独的小端写法，不走 QDataStream。 */
    private static void writeInt32LeAt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xff);
        target[offset + 1] = (byte) ((value >>> 8) & 0xff);
        target[offset + 2] = (byte) ((value >>> 16) & 0xff);
        target[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }
}
