package org.telegram.ui.bots;

import java.util.HashMap;
import java.util.Map;

/**
 * [Aiqiyi] Bot 命令描述本地化
 *
 * 两级匹配：
 *   1) 精确匹配（原文整串）
 *   2) 前缀匹配（忽略大小写、忽略尾部标点/空格）
 * 命中 -> 只显示中文；未命中 -> 原文原样返回。
 */
public class AqyBotTr {

    private static final HashMap<String, String> MAP = new HashMap<>();

    /** 前缀表：只要原文以此开头就命中（小写、去尾标点后比较） */
    private static final String[][] PREFIX = {
        // ===== BotFather /mybots 主菜单 =====
        {"create a new bot", "创建新机器人"},
        {"edit your bots", "编辑你的机器人"},
        {"edit your web apps", "编辑网页应用"},
        {"edit your games", "编辑你的游戏"},
        {"get authorization token", "获取授权令牌"},
        {"revoke bot access token", "吊销机器人令牌"},
        {"cancel the current operation", "取消当前操作"},

        // ===== 子菜单 =====
        {"change a bot's name", "修改机器人名"},
        {"change bot description", "修改机器人描述"},
        {"change bot about info", "修改机器人简介"},
        {"change bot profile photo", "修改机器人头像"},
        {"change inline settings", "修改内联设置"},
        {"toggle inline location requests", "内联位置请求"},
        {"change inline feedback", "修改内联反馈"},
        {"change bot commands", "修改机器人命令"},
        {"change bot settings", "修改机器人设置"},
        {"delete a bot", "删除机器人"},
        {"delete an existing web app", "删除网页应用"},
        {"delete an existing game", "删除游戏"},
        {"transfer ownership", "转移所有权"},

        // ===== /setjoingroups =====
        {"can your bot be added", "可否被拉进群"},

        // ===== /setdomain =====
        {"link website's domain to", "网站域名关联"},

        // ===== /setprivacy =====
        {"what messages does your bot receive", "谁可给机器人发消息"},
        {"what messages does yo", "谁可给机器人发消息"},

        // ===== Bot 互联 / 管理 / 话题 =====
        {"toggle manager", "管理员模式"},
        {"toggle threaded", "话题模式"},
        {"toggle bot 2 bot", "Bot 互联"},

        // ===== /myapps =====
        {"create a new web app", "创建网页应用"},
        {"get a list of your web apps", "我的网页应用"},
        {"edit a web app", "编辑网页应用"},

        // ===== /mygames =====
        {"create a new game", "创建新游戏"},
        {"get a list of your games", "我的游戏"},
        {"edit a game", "编辑游戏"},

        // ===== 通用 =====
        {"no, thanks", "不用了"},

        // ===== 举报理由（ReportBottomSheet，服务器下发）=====
        {"i don't like it", "我不喜欢"},
        {"child abuse", "虐童内容"},
        {"violence", "暴力内容"},
        {"illegal goods and services", "非法商品与服务"},
        {"illegal adult content", "非法成人内容"},
        {"personal data", "个人数据泄露"},
        {"scam or fraud", "诈骗或欺诈"},
        {"copyright", "侵犯版权"},
        {"it's not illegal, but must be taken down", "不违法，但应下架"},
        {"fake account", "虚假账号"},
        {"impersonation", "冒充他人"},
        {"i don't like this user", "我不喜欢这个用户"},
        {"unauthorized use of my content", "未经授权使用我的内容"},
        {"geographical irrelevance", "地理无关"},
        {"false information", "虚假信息"},
        {"hate speech", "仇恨言论"},
    };

    static {
        // 精确表（保留，用于短词避免误伤）
        MAP.put("cancel", "取消");
        MAP.put("done", "完成");
        MAP.put("back", "返回");
        MAP.put("no, thanks", "不用了");
        MAP.put("other", "其他");
        MAP.put("spam", "垃圾信息");
    }

    /** 归一化：小写、去首尾空白、去尾部标点 */
    private static String norm(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase();
        while (s.length() > 0) {
            char c = s.charAt(s.length() - 1);
            if (c == '.' || c == '?' || c == '!' || c == ' ' || c == '\u2026') {
                s = s.substring(0, s.length() - 1);
            } else break;
        }
        return s;
    }

    public static String tr(String desc) {
        if (desc == null) return null;

        // 1) 精确匹配
        String exact = MAP.get(desc.trim().toLowerCase());
        if (exact != null) return exact;

        // 2) 前缀匹配
        String n = norm(desc);
        if (n == null || n.isEmpty()) return desc;
        for (String[] pair : PREFIX) {
            String key = pair[0];
            if (n.startsWith(key)) {
                // 边界校验：前缀后必须是结尾或非字母数字，避免 "edit a game" 误中 "edit a games"
                if (n.length() == key.length()) return pair[1];
                char next = n.charAt(key.length());
                if (!Character.isLetterOrDigit(next)) return pair[1];
            }
        }
        return desc;
    }
}