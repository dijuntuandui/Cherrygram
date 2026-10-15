package org.telegram.ui.bots;

import java.util.HashMap;

/**
 * [Aiqiyi] 服务器下发文本本地化（Bot 命令描述 / 举报理由）
 *
 * 两级匹配：
 *   1) 精确匹配（原文整串，小写）
 *   2) 前缀匹配（小写、去尾部标点后，按前缀比对）
 * 命中 -> 只显示中文；未命中 -> 原文原样返回。
 */
public class AqyBotTr {

    /** 精确表：短词专用，避免前缀误伤 */
    private static final HashMap<String, String> MAP = new HashMap<>();

    /** 前缀表：长句专用，容忍句尾截断/标点/变体 */
    private static final String[][] PREFIX = {

        // ============ BotFather /mybots 主菜单 ============
        {"create a new bot", "创建新机器人"},
        {"edit your bots", "编辑你的机器人"},
        {"edit your web apps", "编辑网页应用"},
        {"edit your games", "编辑你的游戏"},
        {"get authorization token", "获取授权令牌"},
        {"revoke bot access token", "吊销机器人令牌"},
        {"cancel the current operation", "取消当前操作"},

        // ============ BotFather 子菜单 ============
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
        {"can your bot be added", "可否被拉进群"},
        {"link website's domain to", "网站域名关联"},
        {"what messages does your bot receive", "谁可给机器人发消息"},
        {"what messages does yo", "谁可给机器人发消息"},
        {"toggle manager", "管理员模式"},
        {"toggle threaded", "话题模式"},
        {"toggle bot 2 bot", "Bot 互联"},
        {"create a new web app", "创建网页应用"},
        {"get a list of your web apps", "我的网页应用"},
        {"edit a web app", "编辑网页应用"},
        {"create a new game", "创建新游戏"},
        {"get a list of your games", "我的游戏"},
        {"edit a game", "编辑游戏"},

        // ============ 举报：主菜单 ============
        {"i don't like it", "我不喜欢"},
        {"child abuse", "虐童内容"},
        {"violence", "暴力内容"},
        {"illegal goods and services", "非法商品与服务"},
        {"illegal adult content", "非法成人内容"},
        {"personal data", "个人数据"},
        {"scam or fraud", "诈骗或欺诈"},
        {"copyright", "侵犯版权"},
        {"it's not illegal, but must be taken", "不违法，但应下架"},

        // ============ 举报：Violence 子菜单 ============
        {"insults or false information", "侮辱或虚假信息"},
        {"graphic or disturbing content", "血腥或令人不适"},
        {"extreme violence, dismemberment", "极端暴力、肢解"},
        {"hate speech or symbols", "仇恨言论或符号"},
        {"calling for violence", "煽动暴力"},
        {"organized crime", "有组织犯罪"},
        {"terrorism", "恐怖主义"},
        {"animal abuse", "虐待动物"},

        // ============ 举报：Spam 子菜单 ============
        {"promoting illegal content", "推广非法内容"},
        {"promoting other content", "推广其他内容"},

        // ============ 举报：Illegal goods 子菜单 ============
        {"weapons", "武器"},
        {"drugs", "毒品"},
        {"fake documents", "伪造证件"},
        {"counterfeit money", "假币"},
        {"hacking tools and malware", "黑客工具与恶意软件"},
        {"counterfeit merchandise", "假冒商品"},
        {"other goods and services", "其他商品与服务"},

        // ============ 举报：Child abuse 子菜单 ============
        {"child sexual abuse", "儿童性虐待"},
        {"child physical abuse", "儿童身体虐待"},

        // ============ 举报：Scam or fraud 子菜单 ============
        {"impersonation", "冒充他人"},
        {"deceptive or unrealistic financial", "欺骗性金融内容"},
        {"malware, phishing", "恶意软件、钓鱼"},
        {"fraudulent seller, product or ser", "欺诈卖家或商品"},

        // ============ 举报：Illegal adult content 子菜单 ============
        {"illegal sexual services", "非法性服务"},
        {"non-consensual sexual imagery", "未经同意的性影像"},
        {"pornography", "色情内容"},
        {"other illegal sexual content", "其他非法性内容"},

        // ============ 举报：Other 子菜单 ============
        {"false information or defamation", "虚假信息或诽谤"},
        {"something else", "其他情况"},

        // ============ 举报：Personal data 子菜单 ============
        {"private images", "私密图片"},
        {"phone number", "电话号码"},
        {"address", "住址"},
        {"stolen data or credentials", "被盗数据或凭证"},
        {"other personal information", "其他个人信息"},
    };

    static {
        MAP.put("cancel", "取消");
        MAP.put("done", "完成");
        MAP.put("back", "返回");
        MAP.put("no, thanks", "不用了");
        MAP.put("other", "其他");
        MAP.put("spam", "垃圾信息");
    }

    /** 归一化：转小写、去首尾空白、去尾部标点与省略号 */
    private static String norm(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase();
        while (s.length() > 0) {
            char c = s.charAt(s.length() - 1);
            if (c == '.' || c == '?' || c == '!' || c == ' ' || c == '\u2026') {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        return s;
    }

    public static String tr(String desc) {
        if (desc == null) return null;

        String low = desc.trim().toLowerCase();

        // 1) 精确匹配
        String exact = MAP.get(low);
        if (exact != null) return exact;

        // 2) 前缀匹配
        String n = norm(low);
        if (n == null || n.isEmpty()) return desc;

        String best = null;
        int bestLen = -1;
        for (String[] pair : PREFIX) {
            String key = pair[0];
            if (!n.startsWith(key)) continue;
            // 边界校验：
            //  - key 足够长（>=12）时，允许截断在词中间（如 "can your bot be add" 命中 "...added"）
            //  - key 较短时，要求前缀后是非字母数字，避免误伤
            if (n.length() > key.length()
                    && key.length() < 12
                    && Character.isLetterOrDigit(n.charAt(key.length()))) {
                continue;
            }
            // 取最长匹配，避免短 key 抢匹配
            if (key.length() > bestLen) { bestLen = key.length(); best = pair[1]; }
        }
        return best != null ? best : desc;
    }
}