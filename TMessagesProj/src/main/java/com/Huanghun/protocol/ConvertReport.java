package com.Huanghun.protocol;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 一次转换的汇总，喂给结果弹窗。
 *
 * <p>四色块对应：成功 / 失败 / 跳过 / 产物，数字全在这里统计，
 * 弹窗只负责画。</p>
 */
public final class ConvertReport {

    /** 固定产物根目录：/sdcard/Download/爱琦艺/协议转换/ */
    public static final String OUTPUT_ROOT = "/sdcard/Download/爱琦艺/协议转换";

    public int okCount;
    public int failCount;
    public int skipCount;
    public int outputCount;
    public final List<String> lines = new ArrayList<>();
    public final List<File> outputs = new ArrayList<>();
    public String title = "转换完成";

    /** 本次运行的产物目录，形如 .../协议转换/20261106_014530/ */
    public File runDir;

    public static ConvertReport failure(String message) {
        ConvertReport report = new ConvertReport();
        report.title = "转换失败";
        report.failCount = 1;
        report.lines.add(message);
        return report;
    }

    /** 为本次运行建立带时间戳的子目录。 */
    public static File createRunDir() throws Exception {
        File root = new File(OUTPUT_ROOT);
        if (!root.exists() && !root.mkdirs() && !root.exists()) {
            throw new java.io.IOException("无法创建输出目录：" + OUTPUT_ROOT);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(root, stamp);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new java.io.IOException("无法创建本次目录：" + dir.getAbsolutePath());
        }
        return dir;
    }

    /** 加一行明细，同时按类别计数。 */
    public void addOk(String line) {
        okCount++;
        lines.add(line);
    }

    public void addFail(String line) {
        failCount++;
        lines.add(line);
    }

    public void addSkip(String line) {
        skipCount++;
        lines.add(line);
    }

    public void addNote(String line) {
        lines.add(line);
    }

    /** 末尾那行统计。 */
    public String footer() {
        StringBuilder builder = new StringBuilder();
        builder.append("成功 ").append(okCount);
        if (failCount > 0) builder.append(" · 失败 ").append(failCount);
        if (skipCount > 0) builder.append(" · 跳过 ").append(skipCount);
        builder.append(" · 产物 ").append(outputCount).append(" 个");
        if (runDir != null) {
            builder.append("\n").append(runDir.getAbsolutePath());
        }
        return builder.toString();
    }

    public String detailText() {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append("\n");
        }
        return builder.toString().trim();
    }
}