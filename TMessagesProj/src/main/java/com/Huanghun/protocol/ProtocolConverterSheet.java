package com.Huanghun.protocol;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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
import java.util.zip.ZipOutputStream;

/**
 * 协议转换面板：入口行、SAF 落地、后台执行、结果弹窗。
 *
 * <p>流程：点入口 → {@link ProtocolConvertPage} 二级页 → 选取功能 →
 * 选输入文件（或压缩包）→ 产物落固定目录 → 弹结果。</p>
 *
 * <p>转换逻辑在 {@link ProtocolConverter}，这里只做 SAF 适配与界面。</p>
 */
public final class ProtocolConverterSheet {

    public static final int REQ_INPUT = 5100;

    private static final int MAX_ENTRIES = 512;
    private static final long MAX_UNCOMPRESSED = 64L * 1024L * 1024L;

    private ProtocolConverterSheet() {
    }

    private static int dp(float value) {
        return (int) (value * AndroidUtilities.density + 0.5f);
    }

    // ---------------------------------------------------------- 入口行

    /** 把一行入口加进现有容器，风格与协议登录 sheet 一致。 */
    public static void addEntry(Context context, LinearLayout container, final Runnable onPicked) {
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

    // ---------------------------------------------------------- 执行

    /**
     * 按功能执行。产物落在 {@link ConvertReport#OUTPUT_ROOT} 下的时间戳目录。
     *
     * @param feature  {@link ProtocolConvertPage.FlowHost} 里的常量
     * @param inputs   已经落地的输入，自检传 null
     */
    public static void runFeature(final Activity activity, final int feature, final List<File> inputs,
            final Callback callback) {
        new Thread(() -> {
            final ConvertReport report;
            try {
                report = execute(activity, feature, inputs);
            } catch (Throwable e) {
                FileLog.e(e);
                final ConvertReport failed = ConvertReport.failure(
                        e.getMessage() == null ? e.toString() : e.getMessage());
                AndroidUtilities.runOnUIThread(() -> callback.onDone(failed));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.onDone(report));
        }, "protocol-convert").start();
    }

    /** 真正干活的地方，全部在后台线程。 */
    private static ConvertReport execute(Activity activity, int feature, List<File> inputs) throws Exception {
        ConvertReport report = new ConvertReport();

        // 自检不需要输入
        if (feature == ProtocolConvertPage.FlowHost.F_SELF_TEST) {
            File work = new File(activity.getCacheDir(), "protocol_selftest");
            deleteTree(work);
            String text = ProtocolConverter.selfTest(work);
            boolean allOk = text.contains("全部一致");
            report.title = allOk ? "自检通过" : "自检未通过";
            for (String line : text.split("\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (line.contains("MISMATCH") || line.contains("不一致")) {
                    report.addFail(line);
                } else {
                    report.addNote(line);
                }
            }
            if (allOk) {
                report.okCount = 1;
                report.outputCount = 1;
            }
            return report;
        }

        if (inputs == null || inputs.isEmpty()) {
            return ConvertReport.failure("没有可用的输入文件");
        }

        File runDir = ConvertReport.createRunDir();
        report.runDir = runDir;

        // 按功能决定要产出哪种格式
        // 每个功能只产出它该产出的东西，避免一次吐五种格式
        // 1 tdata->session  : telethon + pyrogram + string
        // 2 session->tdata  : tdata
        // 4 tdata->JSON     : json
        // 5 JSON->session   : telethon + pyrogram + string
        boolean wantTelethon = false;
        boolean wantPyrogram = false;
        boolean wantString = false;
        boolean wantJson = false;
        boolean wantTdata = false;
        if (feature == ProtocolConvertPage.FlowHost.F_TDATA_TO_SESSION
                || feature == ProtocolConvertPage.FlowHost.F_JSON_TO_SESSION) {
            wantTelethon = true;
            wantPyrogram = true;
            wantString = true;
        } else if (feature == ProtocolConvertPage.FlowHost.F_SESSION_TO_TDATA) {
            wantTdata = true;
        } else if (feature == ProtocolConvertPage.FlowHost.F_TDATA_TO_JSON) {
            wantJson = true;
        } else {
            // 未单独定义的功能：保持旧行为，全格式产出
            wantTelethon = true;
            wantPyrogram = true;
            wantString = true;
            wantJson = true;
            wantTdata = true;
            report.addNote("未定义功能 " + feature + "，已按全格式转换");
        }

        List<File> produced = new ArrayList<>();
        for (File input : inputs) {
            try {
                List<SessionWriter.Account> accounts = ProtocolConverter.load(input);
                if (accounts.isEmpty()) {
                    report.addSkip(input.getName() + "：没有账号");
                    continue;
                }
                for (SessionWriter.Account account : accounts) {
                    // 查看信息只列不写
                    if (feature == ProtocolConvertPage.FlowHost.F_INSPECT) {
                        report.addOk(account.label());
                        continue;
                    }
                    try {
                        ProtocolConverter.Result result = ProtocolConverter.convert(
                                account, runDir, true,
                                wantTelethon, wantPyrogram, wantString, wantJson, wantTdata);
                        report.addOk(result.summary());
                        collectFiles(result, produced, report);
                    } catch (Exception e) {
                        report.addFail(account.label() + "：" + describe(e));
                    }
                }
            } catch (Exception e) {
                report.addSkip(input.getName() + "：" + describe(e));
            }
        }

        // 「查看信息」没有产物，直接返回
        if (feature == ProtocolConvertPage.FlowHost.F_INSPECT) {
            return report;
        }

        // 统一打包成 zip，用户拿一个文件就够
        if (!produced.isEmpty()) {
            File zip = new File(runDir, "转换结果.zip");
            try {
                zipDirectory(runDir, zip);
                report.addNote("已打包：" + zip.getName());
                report.outputCount++;
            } catch (Exception e) {
                report.addNote("打包失败：" + describe(e));
            }
        }

        report.outputCount += produced.size();
        return report;
    }

    private static void collectFiles(ProtocolConverter.Result result, List<File> out, ConvertReport report) {
        if (result.telethon != null) {
            out.add(result.telethon);
        }
        if (result.pyrogram != null) {
            out.add(result.pyrogram);
        }
        if (result.string != null) {
            out.add(result.string);
        }
        if (result.json != null) {
            out.add(result.json);
        }
        if (result.tdataDir != null) {
            out.add(result.tdataDir);
        }
        for (String warning : result.warnings) {
            report.addNote("注意 " + warning);
        }
    }

    private static String describe(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** 把目录（不含 zip 自身）打成压缩包。 */
    private static void zipDirectory(File dir, File zipFile) throws Exception {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : files) {
                if (file.equals(zipFile)) {
                    continue;
                }
                addToZip(zip, file, file.getName());
            }
        }
    }

    private static void addToZip(ZipOutputStream zip, File file, String entryName) throws Exception {
        if (file.isDirectory()) {
            zip.putNextEntry(new ZipEntry(entryName + "/"));
            zip.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(zip, child, entryName + "/" + child.getName());
                }
            }
        } else {
            zip.putNextEntry(new ZipEntry(entryName));
            try (InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zip.write(buffer, 0, read);
                }
            }
            zip.closeEntry();
        }
    }

    // ---------------------------------------------------------- 结果弹窗

    /** 结果回调。 */
    public interface Callback {
        void onDone(ConvertReport report);
    }

    /**
     * 结果弹窗，对齐「协议登录完成」那种四色块布局。
     *
     * <p>标题 → 四色块统计 → 明细可滚动 → 路径 + 确定按钮。</p>
     */
    public static void showResult(Activity activity, final ConvertReport report) {
        if (activity == null || activity.isFinishing() || report == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(activity);
        final BottomSheet[] holder = new BottomSheet[1];

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(16), dp(22), dp(12));

        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(report.title);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        TextView subtitle = new TextView(activity);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setText(report.runDir == null ? "产物已保存到固定目录" : "产物已保存到固定目录");
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 3, 0, 14));

        // ==== 四个数字块 ====
        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(buildStatBox(activity, report.okCount, "成功", 0xff34a853),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 4, 0));
        stats.addView(buildStatBox(activity, report.failCount, "失败", 0xffea4335),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 4, 0));
        stats.addView(buildStatBox(activity, report.skipCount, "跳过", 0xfffbbc04),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 4, 0));
        stats.addView(buildStatBox(activity, report.outputCount, "产物", 0xff4285f4),
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 0, 0));
        root.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        // ==== 明细 ====
        TextView detail = new TextView(activity);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        detail.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        detail.setTextIsSelectable(true);
        detail.setText(report.detailText());

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(detail, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(180),
                0, 14, 0, 10));

        // ==== 底部统计 ====
        TextView footer = new TextView(activity);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        footer.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        footer.setTextIsSelectable(true);
        footer.setText(report.footer());
        root.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        // ==== 确定 ====
        TextView confirm = new TextView(activity);
        confirm.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        confirm.setTypeface(Typeface.DEFAULT_BOLD);
        confirm.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        confirm.setGravity(Gravity.CENTER);
        confirm.setPadding(0, dp(14), 0, dp(4));
        confirm.setText("确定");
        confirm.setOnClickListener(v -> {
            if (holder[0] != null) {
                holder[0].dismiss();
            }
        });
        root.addView(confirm, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        builder.setCustomView(root);
        holder[0] = builder.show();
    }

    /** 单个数字块：大数字 + 小标签。 */
    private static LinearLayout buildStatBox(Context context, int value, String label, int accent) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(10), 0, dp(10));
        box.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_windowBackgroundGray)));

        TextView number = new TextView(context);
        number.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        number.setTextColor(accent);
        number.setText(String.valueOf(value));
        box.addView(number, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT));

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        name.setText(label);
        box.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        return box;
    }
}