package uz.unnarsx.cherrygram.aiqiyi.cleanup;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * [Aiqiyi] 扩展清理 - 安全档
 *
 * 只做本地缓存与只读统计，不碰服务器数据。
 * 不删聊天记录、不退群、不改资料、不删联系人。
 *
 * 清理走的是 Telegram native 通道 Utilities.clearDir，
 * 和原生设置页清缓存用的是同一条路，不会破坏本地数据库。
 */
public final class AiqiyiCleanup {

    public static final int CAT_CACHE = 0;
    public static final int CAT_PHOTO = 1;
    public static final int CAT_VIDEO = 2;
    public static final int CAT_DOCUMENT = 3;
    public static final int CAT_STICKER = 4;
    public static final int CAT_COUNT = 5;

    private AiqiyiCleanup() {}

    /** 每个类别的展示名 key，交给设置页取字符串 */
    public static String[] categoryKeys() {
        return new String[]{"cache", "photo", "video", "document", "sticker"};
    }

    /** 取得某个类别对应的目录列表。返回 null 或空表示该类不适用。 */
    private static List<File> dirsFor(int category) {
        List<File> out = new ArrayList<>();
        try {
            switch (category) {
                case CAT_CACHE: {
                    File d = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
                    if (d != null) out.add(d);
                    break;
                }
                case CAT_PHOTO: {
                    File d = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE);
                    if (d != null) out.add(d);
                    File p = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC);
                    if (p != null) out.add(p);
                    break;
                }
                case CAT_VIDEO: {
                    File d = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO);
                    if (d != null) out.add(d);
                    break;
                }
                case CAT_DOCUMENT: {
                    File d = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT);
                    if (d != null) out.add(d);
                    File f = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES);
                    if (f != null) out.add(f);
                    break;
                }
                case CAT_STICKER: {
                    File base = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
                    if (base != null) {
                        out.add(new File(base, "acache"));
                    }
                    break;
                }
                default:
                    break;
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    /** 统计某类别占用的字节数。走 native getDirSize，比 Java 遍历快且准确。 */
    public static long sizeOf(int category) {
        long total = 0;
        for (File f : dirsFor(category)) {
            try {
                if (!f.exists()) {
                    continue;
                }
                // docType: 0=普通 1=文档  (沿用原生 CacheControlActivity 的取值)
                int docType = category == CAT_DOCUMENT ? 1 : 0;
                total += Utilities.getDirSize(f.getAbsolutePath(), docType, category == CAT_CACHE);
            } catch (Throwable ignore) {
            }
        }
        return total;
    }

    /** 全部类别合计 */
    public static long totalSize() {
        long total = 0;
        for (int i = 0; i < CAT_COUNT; i++) {
            total += sizeOf(i);
        }
        return total;
    }

    public interface Progress {
        void onFinish(long freedBytes);
    }

    /**
     * 清理某个类别。
     *
     * 关键点：传给 native 的时间戳用「未来 1 小时」，
     * 这样所有已有文件都满足「早于该时间」，等于全清；
     * 而清理期间新下载的文件时间戳更晚，不会被误删。
     */
    public static void clear(final int category, final Progress callback) {
        Utilities.cacheClearQueue.postRunnable(() -> {
            long before = sizeOf(category);
            long cutoff = System.currentTimeMillis() / 1000 + 3600;
            for (File f : dirsFor(category)) {
                try {
                    if (!f.exists() || !f.isDirectory()) {
                        continue;
                    }
                    int docType = category == CAT_DOCUMENT ? 1 : 0;
                    boolean subdirs = category == CAT_CACHE;
                    Utilities.clearDir(f.getAbsolutePath(), docType, cutoff, subdirs);
                } catch (Throwable ignore) {
                }
            }
            long after = sizeOf(category);
            final long freed = Math.max(0, before - after);
            if (callback != null) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(
                        () -> callback.onFinish(freed));
            }
        });
    }

    /** 全部清理 */
    public static void clearAll(final Progress callback) {
        Utilities.cacheClearQueue.postRunnable(() -> {
            long before = totalSize();
            long cutoff = System.currentTimeMillis() / 1000 + 3600;
            for (int cat = 0; cat < CAT_COUNT; cat++) {
                for (File f : dirsFor(cat)) {
                    try {
                        if (!f.exists() || !f.isDirectory()) {
                            continue;
                        }
                        int docType = cat == CAT_DOCUMENT ? 1 : 0;
                        boolean subdirs = cat == CAT_CACHE;
                        Utilities.clearDir(f.getAbsolutePath(), docType, cutoff, subdirs);
                    } catch (Throwable ignore) {
                    }
                }
            }
            long after = totalSize();
            final long freed = Math.max(0, before - after);
            if (callback != null) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(
                        () -> callback.onFinish(freed));
            }
        });
    }
}
