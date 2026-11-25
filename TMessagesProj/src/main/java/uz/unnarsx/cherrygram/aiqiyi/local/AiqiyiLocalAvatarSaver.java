/**
 * 爱琦艺客户端 - 本地头像选图落盘工具
 *
 * 对齐黄昏 Huanghun-TG ProfileActivity.saveLocalAvatar(Uri) 的行为：
 *   decodeStream -> 超过 1024 等比缩 -> JPEG 质量 90 -> 写入私有目录 -> 记路径。
 *
 * 为什么必须拷贝落盘而不能直接存相册 Uri：
 *   相册原图被用户删除 / 移动到别的目录 / 清缓存后，content:// 或 file:// 都会失效，
 *   本地头像就变成空白。拷到应用私有目录后，生命周期由我们自己的清理逻辑管理。
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class AiqiyiLocalAvatarSaver {

    /** 最大边长，与黄昏一致 */
    private static final int MAX_SIDE = 1024;
    /** JPEG 压缩质量，与黄昏一致（0x5a = 90） */
    private static final int JPEG_QUALITY = 90;

    private AiqiyiLocalAvatarSaver() {
    }

    /**
     * 把 Uri 指向的图片拷贝、缩放、压缩后保存为本机私有文件，并写入存储。
     *
     * @return 保存后的绝对路径；失败返回 null
     */
    public static String saveFromUri(int account, long userId, Uri uri) {
        if (uri == null || userId <= 0) {
            return null;
        }
        ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();
        InputStream in = null;
        FileOutputStream out = null;
        Bitmap decoded = null;
        Bitmap scaled = null;
        File target = null;
        try {
            in = cr.openInputStream(uri);
            if (in == null) {
                return null;
            }
            decoded = BitmapFactory.decodeStream(in);
            if (decoded == null) {
                return null;
            }

            final int w = decoded.getWidth();
            final int h = decoded.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }

            // 只有超限才缩，小图原样保留，避免放大糊掉
            if (w > MAX_SIDE || h > MAX_SIDE) {
                float ratio = Math.min((float) MAX_SIDE / w, (float) MAX_SIDE / h);
                int nw = Math.max(1, Math.round(w * ratio));
                int nh = Math.max(1, Math.round(h * ratio));
                scaled = Bitmap.createScaledBitmap(decoded, nw, nh, true);
            } else {
                scaled = decoded;
            }

            target = AiqiyiLocalAvatar.newFile(account, userId);
            out = new FileOutputStream(target);
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            out.flush();

            final String path = target.getAbsolutePath();
            // setPath 内部会删掉旧文件
            AiqiyiLocalAvatar.setPath(account, userId, path);
            return path;
        } catch (Throwable t) {
            // 失败时把半成品删掉，别留垃圾
            if (target != null && target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            return null;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (scaled != null && scaled != decoded) {
                scaled.recycle();
            }
            if (decoded != null && !decoded.isRecycled()) {
                decoded.recycle();
            }
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignore) {
            }
        }
    }
}