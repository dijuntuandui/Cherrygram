/**
 * 爱琦艺客户端 - 本地消息文本替换引擎
 *
 * 仅在「本机渲染」时替换消息正文，不修改 messageOwner.message，
 * 因此不会把改动写回服务器，也不会影响对方看到的内容。
 *
 * 规则格式（每行一条）：关键词=替换值
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.local;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;

public class AiqiyiLocalMessageHelper {

    private static volatile List<String[]> cachedRules = null;
    private static volatile String cachedRaw = null;

    /**
     * 快速开关（内存镜像，避免 MessageObject 高频构造时反复读 SharedPreferences）。
     * 由 invalidate() 与开关变更处同步。
     */
    private static volatile boolean fastEnabled = false;

    /**
     * 规则变更或开关变更后调用，重建缓存。
     */
    public static void invalidate() {
        cachedRules = null;
        cachedRaw = null;
        syncFastEnabled();
    }

    /**
     * 从配置刷新内存镜像。App 启动时也应调用一次。
     */
    public static void syncFastEnabled() {
        boolean on = AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled()
                && AiqiyiLocalDataConfig.INSTANCE.getLocalMessageOverride();
        fastEnabled = on && getRulesInternal() != null && !getRulesInternal().isEmpty();
    }

    private static List<String[]> getRulesInternal() {
        String raw = AiqiyiLocalDataConfig.INSTANCE.getLocalMessageRules();
        if (raw == null) {
            raw = "";
        }
        if (raw.equals(cachedRaw)) {
            List<String[]> c = cachedRules;
            return c != null ? c : Collections.<String[]>emptyList();
        }
        ArrayList<String[]> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int idx = t.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = t.substring(0, idx);
            String value = t.substring(idx + 1);
            if (key.isEmpty()) {
                continue;
            }
            list.add(new String[]{key, value});
        }
        // 长关键词优先，避免短词先把长词打断
        Collections.sort(list, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return b[0].length() - a[0].length();
            }
        });
        cachedRaw = raw;
        cachedRules = list;
        return list;
    }

    /**
     * 当前是否应当执行替换。
     */
    public static boolean isEnabled() {
        return fastEnabled;
    }

    /**
     * 对消息正文做本地替换。
     *
     * @param text 原始正文
     * @return 替换后的正文；未启用或无命中时原样返回
     */
    public static CharSequence apply(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        if (!isEnabled()) {
            return text;
        }
        List<String[]> rules = getRulesInternal();
        if (rules.isEmpty()) {
            return text;
        }
        String out = text.toString();
        boolean changed = false;
        for (String[] rule : rules) {
            String key = rule[0];
            if (key.isEmpty()) {
                continue;
            }
            if (out.contains(key)) {
                out = out.replace(key, rule[1]);
                changed = true;
            }
        }
        return changed ? out : text;
    }

    /**
     * 已配置的规则条数（用于设置页摘要显示）。
     */
    public static int getRuleCount() {
        return getRulesInternal().size();
    }
}