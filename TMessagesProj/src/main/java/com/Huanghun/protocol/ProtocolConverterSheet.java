package com.Huanghun.protocol;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 协议转换面板：从登录页的「协议登录」弹窗进来，一行入口。
 *
 * <p>流程：选输入（文件或 zip）→ 落到私有缓存 → 选输出目录 → 后台转换 → 显示结果。</p>
 *
 * <p>实际转换逻辑在 {@link ProtocolConverter}，这里只做 SAF 适配与界面。</p>
 */
public final class ProtocolConverterSheet {

    public static final int REQ_INPUT = 5100;
    public static final int REQ_OUTPUT = 5101;

    private static final int MAX_ENTRIES = 512;
    private static final long MAX_UNCOMPRESSED = 64L * 1024L * 1024L;

    private ProtocolConverterSheet() {
    }

    private static int dp(float value) {
        return (int) (value * AndroidUtilities.density + 0.5f);
    }

    // ---------------------------------------------------------- 入口行

    /** 把一行入口加进现有容器，风格与协议登录 sheet 一致。 */
    public static void addEntry(Context context, LinearLayout container, Runnable onPicked) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(22), dp(12), dp(22), dp(12));
        item.setBackground(Theme.getSelectorDrawable(false));
        item.setOnClickListener(v -> onPicked.run());

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setText(R.string.AQY_Conv_Entry);
        item.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setText(R.string.AQY_Conv_Entry_Desc);
        item.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

        container.addView(item, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /** 打开输入选择器：文件或 zip。 */
    public static void openInputPicker(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            activity.startActivityForResult(intent, REQ_INPUT);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** 打开输出目录选择器。 */
    public static void openOutputPicker(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            activity.startActivityForResult(intent, REQ_OUTPUT);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ---------------------------------------------------------- 输入落地

    /** 把用户选的 URI 落到私有缓存，解压 zip，返回可用的本地根目录。 */
    public static File stageInput(Activity activity, Uri uri) throws Exception {
        File root = new File(activity.getCacheDir(), "protocol_convert_in");
        deleteTree(root);
        if (!root.exists() && !root.mkdirs() && !root.exists()) {
            throw new IOException("无法创建临时目录");
        }
        String name = queryDisplayName(activity, uri);
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            extractZip(activity, uri, root);
        } else {
            File target = new File(root, name == null || name.isEmpty() ? "input.bin" : name);
            copyTo(activity, uri, target);
        }
        return root;
    }

    private static String queryDisplayName(Activity activity, Uri uri) {
        try (android.database.Cursor cursor = activity.getContentResolver()
                .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static void copyTo(Activity activity, Uri uri, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录");
        }
        try (InputStream source = activity.getContentResolver().openInputStream(uri)) {
            if (source == null) {
                throw new IOException("无法读取所选文件");
            }
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = source.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED) {
                        throw new IOException("文件超过 64 MB 限制");
                    }
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static void extractZip(Activity activity, Uri uri, File root) throws Exception {
        int entries = 0;
        long totalBytes = 0;
        try (InputStream source = activity.getContentResolver().openInputStream(uri)) {
            if (source == null) {
                throw new IOException("无法读取所选压缩包");
            }
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) {
                        throw new IOException("压缩包包含过多文件");
                    }
                    File output = resolveInside(root, entry.getName());
                    if (entry.isDirectory()) {
                        if (!output.mkdirs() && !output.isDirectory()) {
                            throw new IOException("无法创建解压目录");
                        }
                    } else {
                        File parent = output.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("无法创建解压目录");
                        }
                        try (OutputStream out = new FileOutputStream(output)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                totalBytes += read;
                                if (totalBytes > MAX_UNCOMPRESSED) {
                                    throw new IOException("压缩包解压后超过 64 MB 限制");
                                }
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    /** 防 zip slip：解析后的路径必须仍在根目录内。 */
    private static File resolveInside(File root, String entryName) throws IOException {
        File target = new File(root, entryName);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("压缩包路径非法：" + entryName);
        }
        return target;
    }

    /** 在解包结果里找所有可用的输入根（tdata 目录或单文件）。 */
    public static List<File> findInputs(File root) {
        List<File> result = new ArrayList<>();
        collectInputs(root, result);
        return result;
    }

    private static void collectInputs(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (new File(file, "key_datas").isFile()) {
                    result.add(file);
                } else {
                    collectInputs(file, result);
                }
            } else {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".session") || name.endsWith(".json") || name.endsWith(".txt")) {
                    result.add(file);
                }
            }
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    // ---------------------------------------------------------- 输出与执行

    /** 把输出目录树 URI 映射成真实可写路径，失败返回 null。 */
    public static File resolveOutputDir(Activity activity, Uri treeUri) {
        try {
            String[] parts = treeUri.getPath().split(":");
            if (parts.length > 1 && parts[1] != null && !parts[1].isEmpty()) {
                File base = android.os.Environment.getExternalStorageDirectory();
                return new File(base, parts[1]);
            }
            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** 后台执行转换，回调一定在主线程。 */
    public static void runConvert(Activity activity, List<File> inputs, File outputDir,
            final Callback callback) {
        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            boolean ok = false;
            try {
                List<SessionWriter.Account> accounts = new ArrayList<>();
                for (File input : inputs) {
                    try {
                        accounts.addAll(ProtocolConverter.load(input));
                    } catch (Exception e) {
                        report.append("跳过 ").append(input.getName()).append("：")
                                .append(e.getMessage()).append("\n");
                    }
                }
                if (accounts.isEmpty()) {
                    throw new IOException("没有可用的账号");
                }
                List<String> errors = new ArrayList<>();
                List<ProtocolConverter.Result> results =
                        ProtocolConverter.convertAll(accounts, outputDir, true, errors);
                report.append("共 ").append(results.size()).append(" 个账号\n\n");
                for (ProtocolConverter.Result result : results) {
                    report.append(result.summary()).append("\n\n");
                }
                for (String error : errors) {
                    report.append(error).append("\n");
                }
                ok = true;
            } catch (Exception e) {
                FileLog.e(e);
                report.append(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            final boolean success = ok;
            final String text = report.toString().trim();
            AndroidUtilities.runOnUIThread(() -> callback.onDone(success, text));
        }, "protocol-convert").start();
    }

    /** 后台跑自检。 */
    public static void runSelfTest(Activity activity, final Callback callback) {
        new Thread(() -> {
            String result;
            try {
                result = ProtocolConverter.selfTest(new File(activity.getCacheDir(), "protocol_selftest"));
            } catch (Exception e) {
                FileLog.e(e);
                result = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String text = result;
            final boolean success = text.contains("全部一致");
            AndroidUtilities.runOnUIThread(() -> callback.onDone(success, text));
        }, "protocol-selftest").start();
    }

    /** 结果回调。 */
    public interface Callback {
        void onDone(boolean success, String text);
    }

    /** 弹一个可滚动的结果框。 */
    public static void showResult(Activity activity, boolean success, String text) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(success ? R.string.AQY_Conv_Done : R.string.AQY_Conv_Failed);
        container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 16, 22, 8));

        TextView body = new TextView(activity);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        body.setTextIsSelectable(true);
        body.setText(text);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        container.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(320), 22, 0, 22, 16));

        builder.setCustomView(container);
        builder.show();
    }
}
