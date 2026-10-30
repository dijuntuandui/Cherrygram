/*
 * 爱琦艺客户端 - 本地坐标伪造 · 存储层
 *
 * 只改本机发出的位置消息的经纬度，
 * 不修改 GPS 硬件、不影响其它应用、不接管系统定位。
 */
package uz.unnarsx.cherrygram.aiqiyi.location;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public class AiqiyiLocationStore {

    private static final String KEY_ENABLED = "aqy_loc_enabled";
    private static final String KEY_LAT = "aqy_loc_lat";
    private static final String KEY_LON = "aqy_loc_lon";
    private static final String KEY_LABEL = "aqy_loc_label";
    private static final String KEY_LIVE = "aqy_loc_live";
    private static final String KEY_MAP = "aqy_loc_map";
    private static final String PRESET_PREFIX = "aqy_loc_preset_";

    /** 预设点：名称 → "lat,lon" */
    private static final String[][] BUILTIN_PRESETS = new String[][]{
            {"北京 天安门", "39.908722,116.397499"},
            {"上海 外滩", "31.239666,121.490997"},
            {"广州 塔", "23.106241,113.324631"},
            {"深圳 市民中心", "22.544578,114.057847"},
            {"成都 天府广场", "30.657484,104.065758"},
            {"香港 中环", "22.281944,114.158056"},
            {"台北 101", "25.033611,121.565000"},
            {"东京 塔", "35.658611,139.745556"},
            {"首尔 光化门", "37.575833,126.976944"},
            {"新加坡 鱼尾狮", "1.286667,103.854444"},
            {"伦敦 大本钟", "51.500556,-0.124444"},
            {"巴黎 铁塔", "48.858333,2.294444"},
            {"纽约 时代广场", "40.757500,-73.985556"},
            {"洛杉矶 好莱坞", "34.134167,-118.321389"}
    };

    private static volatile SharedPreferences sp;

    private static SharedPreferences sp() {
        SharedPreferences cur = sp;
        if (cur != null) {
            return cur;
        }
        synchronized (AiqiyiLocationStore.class) {
            if (sp == null) {
                // [Aiqiyi] 修复：applicationContext 在冷启动早期可能为 null，
                // 原实现会在此处 NPE，被上层 catch 吞掉后直接返回真实定位，
                // 表现为「伪坐标先生效、随后跳回真实位置」。
                // 这里改成先取上下文，拿不到就安全降级返回默认值。
                android.content.Context ctx = ApplicationLoader.applicationContext;
                if (ctx == null) {
                    // 上下文还没就绪（冷启动早期）：返回 null，
                    // 由 getXxxSafe 用默认值安全降级，绝不抛异常。
                    // 不再尝试别的上下文，避免依赖不存在的 API。
                    return null;
                }
                sp = ctx.getSharedPreferences("aqy_fake_location", 0);
            }
            return sp;
        }
    }

    /** 安全读取：sp 可能为 null，绝不抛异常 */
    private static boolean getBoolSafe(String key, boolean def) {
        try {
            SharedPreferences p = sp();
            if (p == null) return def;
            return p.getBoolean(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private static float getFloatSafe(String key, float def) {
        try {
            SharedPreferences p = sp();
            if (p == null) return def;
            return p.getFloat(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private static String getStringSafe(String key, String def) {
        try {
            SharedPreferences p = sp();
            if (p == null) return def;
            String v = p.getString(key, def);
            return v == null ? def : v;
        } catch (Throwable e) {
            return def;
        }
    }

    // ==================== 开关 ====================

    public static boolean isEnabled() {
        try {
            return getBoolSafe(KEY_ENABLED, false);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            sp().edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    /** 是否连实时位置一起伪造 */
    public static boolean isLiveEnabled() {
        try {
            return getBoolSafe(KEY_LIVE, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setLiveEnabled(boolean enabled) {
        try {
            sp().edit().putBoolean(KEY_LIVE, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    /**
     * 地图同步伪造开关。
     * 打开后 LocationController 拿到的定位本身就被换成伪坐标，
     * 地图预览、精度、发送出去的位置全部一致；
     * 关闭则只伪造发出去的位置，地图仍显示真实定位。
     */
    public static boolean isMapEnabled() {
        try {
            return getBoolSafe(KEY_MAP, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setMapEnabled(boolean enabled) {
        try {
            sp().edit().putBoolean(KEY_MAP, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    /** 地图是否需要伪造：总开关 + 坐标有效 + 地图开关 */
    public static boolean shouldOverrideMap() {
        return shouldOverride() && isMapEnabled();
    }

    // ==================== 坐标 ====================

    public static double getLat() {
        try {
            return getFloatSafe(KEY_LAT, 0f);
        } catch (Throwable e) {
            return 0d;
        }
    }

    public static double getLon() {
        try {
            return getFloatSafe(KEY_LON, 0f);
        } catch (Throwable e) {
            return 0d;
        }
    }

    public static String getLabel() {
        try {
            return sp().getString(KEY_LABEL, "");
        } catch (Throwable e) {
            return "";
        }
    }

    public static void setPoint(double lat, double lon, String label) {
        try {
            sp().edit()
                    .putFloat(KEY_LAT, (float) lat)
                    .putFloat(KEY_LON, (float) lon)
                    .putString(KEY_LABEL, label == null ? "" : label)
                    .apply();
        } catch (Throwable ignore) {
        }
    }

    /** 坐标是否有效（未设置时是 0,0，属于大西洋，应视为无效） */
    public static boolean hasValidPoint() {
        double lat = getLat();
        double lon = getLon();
        if (lat == 0d && lon == 0d) {
            return false;
        }
        return lat >= -90d && lat <= 90d && lon >= -180d && lon <= 180d;
    }

    /** 生效判断：开关打开且坐标有效 */
    public static boolean shouldOverride() {
        return isEnabled() && hasValidPoint();
    }

    /**
     * 解析用户输入，支持三种：
     *   1) 39.908722,116.397499
     *   2) 39.908722 116.397499
     *   3) https://maps.google.com/?q=39.908722,116.397499
     *
     * @return double[]{lat, lon}；解析失败返回 null
     */
    public static double[] parseInput(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String s = raw.trim();
        // 去掉 URL 参数前缀
        int q = s.indexOf('q');
        if (s.startsWith("http") && q > 0) {
            int eq = s.indexOf('=', q);
            if (eq > 0) {
                s = s.substring(eq + 1);
            }
        }
        // 统一分隔符
        s = s.replace('，', ',').replace('；', ',').replace(';', ',');
        s = s.replaceAll("\\s+", ",");
        String[] parts = s.split(",");
        java.util.List<Double> nums = new java.util.ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                nums.add(Double.parseDouble(t));
            } catch (Throwable ignore) {
            }
        }
        if (nums.size() < 2) {
            return null;
        }
        double lat = nums.get(0);
        double lon = nums.get(1);
        if (lat < -90d || lat > 90d || lon < -180d || lon > 180d) {
            return null;
        }
        return new double[]{lat, lon};
    }

    /** 格式化展示 */
    public static String formatPoint() {
        if (!hasValidPoint()) {
            return "未设置";
        }
        String label = getLabel();
        String coords = String.format(java.util.Locale.US, "%.6f, %.6f", getLat(), getLon());
        if (TextUtils.isEmpty(label)) {
            return coords;
        }
        return label + "\n" + coords;
    }

    // ==================== 预设 ====================

    public static String[][] getPresets() {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        for (String[] p : BUILTIN_PRESETS) {
            list.add(p);
        }
        // 用户自定义的，排在最前
        try {
            java.util.Map<String, ?> all = sp().getAll();
            java.util.List<String[]> custom = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                if (e.getKey().startsWith(PRESET_PREFIX)) {
                    String name = e.getKey().substring(PRESET_PREFIX.length());
                    String val = String.valueOf(e.getValue());
                    custom.add(new String[]{name, val});
                }
            }
            if (!custom.isEmpty()) {
                list.addAll(0, custom);
            }
        } catch (Throwable ignore) {
        }
        return list.toArray(new String[0][]);
    }

    /**
     * [Aiqiyi] 改成返回 boolean。
     * 原实现 catch (Throwable ignore) 全吞，sp() 为 null 时 NPE 被吃掉，
     * 表现就是“点了保存什么也没发生，列表里也没有”。
     * 现在失败必须让上层知道，才能给用户提示。
     */
    public static boolean addPreset(String name, double lat, double lon) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        try {
            SharedPreferences p = sp();
            if (p == null) {
                return false;
            }
            return p.edit()
                    .putString(PRESET_PREFIX + name.trim(),
                            String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon))
                    .commit();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 版本区分：内置预设写在 BUILTIN_PRESETS 常量里，读取时每次拼进去，
     * 删不掉。所以长按菜单只对“用户自已建的”开放删/改名，
     * 否则会出现“删了重启又回来”的鬼故事。
     */
    public static boolean isBuiltinPreset(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        String n = name.trim();
        for (String[] p : BUILTIN_PRESETS) {
            if (p[0].equals(n)) {
                return true;
            }
        }
        return false;
    }

    /** 删除一条自建预设；内置的不许删 */
    public static boolean removePreset(String name) {
        if (TextUtils.isEmpty(name) || isBuiltinPreset(name)) {
            return false;
        }
        try {
            sp().edit().remove(PRESET_PREFIX + name.trim()).apply();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 重命名自建预设，保留原坐标；内置不许改名 */
    public static boolean renamePreset(String oldName, String newName) {
        if (TextUtils.isEmpty(oldName) || TextUtils.isEmpty(newName)) {
            return false;
        }
        if (isBuiltinPreset(oldName) || isBuiltinPreset(newName)) {
            return false;
        }
        String from = oldName.trim();
        String to = newName.trim();
        if (from.equals(to)) {
            return true;
        }
        try {
            SharedPreferences p = sp();
            String val = p.getString(PRESET_PREFIX + from, null);
            if (val == null) {
                return false;
            }
            // 改名不改坐标：直接搬过去，不重新解析字符串，避免精度折损。
            p.edit()
                    .remove(PRESET_PREFIX + from)
                    .putString(PRESET_PREFIX + to, val)
                    .apply();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 当前登录账号 id，日志/调试用 */
    public static int account() {
        try {
            return UserConfig.selectedAccount;
        } catch (Throwable e) {
            return 0;
        }
    }
}