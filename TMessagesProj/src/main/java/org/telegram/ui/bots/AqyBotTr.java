package org.telegram.ui.bots;

import java.util.HashMap;

/**
 * [Aiqiyi] Bot 命令描述本地化
 * 服务器下发的 botCommand.description 是英文，这里按原文精确映射成中文，
 * 渲染格式：英文 · 中文
 */
public class AqyBotTr {

    private static final HashMap<String, String> MAP = new HashMap<>();

    static {
        // —— BotFather /mybots 系列 ——
        MAP.put("create a new bot", "创建新机器人");
        MAP.put("edit your bots", "编辑你的机器人");
        MAP.put("edit your web apps", "编辑网页应用");
        MAP.put("edit your games", "编辑你的游戏");
        MAP.put("get authorization token", "获取授权令牌");
        MAP.put("revoke bot access token", "吊销机器人令牌");
        MAP.put("change a bot's name", "修改机器人名");
        MAP.put("change bot description", "修改机器人描述");
        MAP.put("change bot about info", "修改机器人简介");
        MAP.put("change bot profile photo", "修改机器人头像");
        MAP.put("change inline settings", "修改内联设置");
        MAP.put("toggle inline location requests", "内联位置请求");
        MAP.put("change inline feedback", "修改内联反馈");
        MAP.put("change bot commands", "修改机器人命令");
        MAP.put("can your bot be added to groups and channels?", "可否被拉进群");
        MAP.put("can your bot be added", "可否被拉进群");
        MAP.put("link website's domain to your bot", "网站域名关联");
        MAP.put("link website's domain to", "网站域名关联");
        MAP.put("what messages does your bot receive?", "谁可给机器人发消息");
        MAP.put("what messages does yo", "谁可给机器人发消息");
        MAP.put("toggle manager mode", "管理员模式");
        MAP.put("toggle manager", "管理员模式");
        MAP.put("toggle threaded mode", "话题模式");
        MAP.put("toggle Bot 2 Bot communication", "Bot 互联");
        MAP.put("toggle Bot 2 Bot comm", "Bot 互联");
        MAP.put("delete a bot", "删除机器人");
        MAP.put("create a new web app", "创建网页应用");
        MAP.put("get a list of your web apps", "我的网页应用");
        MAP.put("edit a web app", "编辑网页应用");
        MAP.put("delete an existing web app", "删除网页应用");
        MAP.put("delete an existing web a", "删除网页应用");
        MAP.put("create a new game", "创建新游戏");
        MAP.put("get a list of your games", "我的游戏");
        MAP.put("edit a game", "编辑游戏");
        MAP.put("delete an existing game", "删除游戏");
        MAP.put("cancel the current operation", "取消当前操作");
    }

    /**
     * 精确匹配翻译。
     * 命中 -> 只显示中文
     * 未命中 -> 原文原样返回
     */
    public static String tr(String desc) {
        if (desc == null) return null;
        String zh = MAP.get(desc);
        if (zh == null) return desc;
        return zh;
    }
}