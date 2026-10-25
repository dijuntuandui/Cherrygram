/**
 * 爱琦艺客户端 - 回怼系统 核心引擎
 *
 * 行为说明：
 * 1. 监听本人在私聊、群聊、频道里发出的消息，命中触发词后开始循环发送词库内容。
 * 2. 遇到关闭词立即停止。
 * 3. 发送间隔按「发送速度」倍率缩放，越界有下限保护。
 *
 * 安全约束：触发判定只挂在 SendMessagesHelper.sendMessage（本机发送出口）上，
 * 别人发到群里的消息不会进入判定，因此群里有人刷触发词无法启动本引擎。
 * 引擎自身发出的消息带 aiqiyi_comeback_auto 标记，不会自触发。
 *
 * 注意：本功能会持续自动发消息，受 Telegram 服务端频率限制约束。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.comeback;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;

import java.util.ArrayList;
import java.util.Random;

public class AiqiyiComebackHelper {

    /** 发送模式：顺序 */
    public static final int MODE_SEQUENTIAL = 0;
    /** 发送模式：随机 */
    public static final int MODE_RANDOM = 1;

    /** 速度倍率下限保护，避免被压到 0 造成无限连发 */
    private static final int MIN_DELAY_MS = 120;
    /** 基础间隔（毫秒） */
    private static final int BASE_DELAY_MS = 1200;

    private static final Random RANDOM = new Random();

    private static volatile AiqiyiComebackHelper instance;

    private SharedPreferences prefs;

    /** 队列世代号，用于中断过期的发送任务 */
    private int queueGeneration = 0;

    /** 当前循环的目标 peer，0 表示未激活 */
    private long activePeer = 0;
    /** 当前循环所在账号 */
    private int activeAccount = 0;
    /** [Aiqiyi] 触发时所在 Topic 的根消息，非 Topic 会话为 null */
    private org.telegram.messenger.MessageObject activeTopicRoot = null;

    private AiqiyiComebackHelper() {
        prefs = ApplicationLoader.applicationContext
                .getSharedPreferences("aiqiyi_comeback", android.app.Activity.MODE_PRIVATE);
    }

    public static AiqiyiComebackHelper getInstance() {
        if (instance == null) {
            synchronized (AiqiyiComebackHelper.class) {
                if (instance == null) {
                    instance = new AiqiyiComebackHelper();
                }
            }
        }
        return instance;
    }

    // ==================== 语料模型 ====================

    public static final class Corpus {
        public final String id;
        public final String name;
        public final String text;
        public final boolean enabled;

        public Corpus(String id, String name, String text, boolean enabled) {
            this.id = id;
            this.name = name;
            this.text = text;
            this.enabled = enabled;
        }

        public ArrayList<String> lines() {
            ArrayList<String> out = new ArrayList<>();
            if (text == null) {
                return out;
            }
            for (String raw : text.split("\n")) {
                String line = raw.trim();
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
            return out;
        }
    }

    // ==================== 配置读写 ====================

    /**
     * 清洗输入：去除零宽字符、BOM、不间断空格等不可见字符，并统一全角空格。
     * 中文输入法联想、某些剪贴板来源会混入这些字符，肉眼不可见但会让 equals 失配。
     */
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF'
                    || c == '\u00A0' || c == '\u3000' || c == '\u0009') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    /** 归一化比较：清洗后逐字符比对，忽略不可见差异 */
    private static boolean sameText(String a, String b) {
        return sanitize(a).equals(sanitize(b));
    }

    public boolean isEnabled() {
        return prefs.getBoolean("enabled", false);
    }

    public void setEnabled(boolean value) {
        prefs.edit().putBoolean("enabled", value).apply();
        if (!value) {
            stopPendingMessages();
        }
    }

    public String getTrigger() {
        return prefs.getString("trigger", "");
    }

    public void setTrigger(String value) {
        prefs.edit().putString("trigger", sanitize(value)).apply();
    }

    public String getStopTrigger() {
        return prefs.getString("stop_trigger", "");
    }

    public void setStopTrigger(String value) {
        prefs.edit().putString("stop_trigger", sanitize(value)).apply();
    }

    public int getSendMode() {
        return prefs.getInt("send_mode", MODE_SEQUENTIAL);
    }

    public void setSendMode(int mode) {
        prefs.edit().putInt("send_mode", mode).apply();
    }

    /** 发送速度倍率，1 表示正常，数字越大越快 */
    public int getSendSpeed() {
        int speed = prefs.getInt("send_speed", 1);
        return speed < 1 ? 1 : speed;
    }

    public void setSendSpeed(int speed) {
        prefs.edit().putInt("send_speed", speed < 1 ? 1 : speed).apply();
    }

    // ==================== 词库存储 ====================

    public JSONArray readCorpora() {
        try {
            return new JSONArray(prefs.getString("corpora", "[]"));
        } catch (Throwable e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }

    private void writeCorpora(JSONArray array) {
        prefs.edit().putString("corpora", array.toString()).apply();
    }

    public ArrayList<Corpus> getCorpora() {
        ArrayList<Corpus> out = new ArrayList<>();
        JSONArray array = readCorpora();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject o = array.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                out.add(new Corpus(
                        o.optString("id"),
                        o.optString("name"),
                        o.optString("text"),
                        o.optBoolean("enabled", true)
                ));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return out;
    }

    public void addCorpus(String name, String text) {
        try {
            JSONArray array = readCorpora();
            JSONObject o = new JSONObject();
            o.put("id", String.valueOf(System.currentTimeMillis()));
            o.put("name", name == null ? "" : name.trim());
            o.put("text", text == null ? "" : text);
            o.put("enabled", true);
            array.put(o);
            writeCorpora(array);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void updateCorpus(String id, String name, String text) {
        try {
            JSONArray array = readCorpora();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null && id != null && id.equals(o.optString("id"))) {
                    o.put("name", name == null ? "" : name.trim());
                    o.put("text", text == null ? "" : text);
                    break;
                }
            }
            writeCorpora(array);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void setCorpusEnabled(String id, boolean enabled) {
        try {
            JSONArray array = readCorpora();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null && id != null && id.equals(o.optString("id"))) {
                    o.put("enabled", enabled);
                    break;
                }
            }
            writeCorpora(array);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void deleteCorpus(String id) {
        try {
            JSONArray array = readCorpora();
            JSONArray next = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) {
                    next.put(o);
                }
            }
            writeCorpora(next);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void deleteAllCorpora() {
        writeCorpora(new JSONArray());
    }

    public int getEnabledCorpusCount() {
        int count = 0;
        for (Corpus c : getCorpora()) {
            if (c.enabled && !c.lines().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** 汇总所有已启用词库的行 */
    public ArrayList<String> getEnabledCorpusLines() {
        ArrayList<String> out = new ArrayList<>();
        for (Corpus c : getCorpora()) {
            if (c.enabled) {
                out.addAll(c.lines());
            }
        }
        return out;
    }

    /** 随机取一条已启用词库里的内容 */
    public String getRandomEnabledLine() {
        ArrayList<Corpus> enabled = new ArrayList<>();
        for (Corpus c : getCorpora()) {
            if (c.enabled && !c.lines().isEmpty()) {
                enabled.add(c);
            }
        }
        if (enabled.isEmpty()) {
            return null;
        }
        Corpus pick = enabled.get(RANDOM.nextInt(enabled.size()));
        ArrayList<String> lines = pick.lines();
        if (lines.isEmpty()) {
            return null;
        }
        return lines.get(RANDOM.nextInt(lines.size()));
    }

    // ==================== 触发判定 ====================

    /**
     * 在发送前拦截。返回 true 表示该消息命中了触发逻辑，已被处理。
     *
     * @param peer     目标会话 id
     * @param message  文本内容
     * @param account  账号
     * @param autoTag  是否为引擎自身发出的消息（防止自触发）
     */
    public boolean onBeforeSend(long peer, String message, int account, boolean autoTag) {
        return onBeforeSend(peer, message, account, autoTag, null);
    }

    /**
     * [Aiqiyi] 带 Topic 上下文的触发判定。群聊里的 Forum/Topic 会话由
     * replyToTopMsg 标识，触发时记下来，循环里每条都发回同一个 Topic，
     * 避免消息全部掉进 General。
     */
    public boolean onBeforeSend(long peer, String message, int account, boolean autoTag,
                                org.telegram.messenger.MessageObject replyToTopMsg) {
        if (autoTag) {
            return false;
        }
        if (!isEnabled()) {
            return false;
        }
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        final String trimmed = message.trim();

        // 关闭词优先判定，命中即停（归一化比较，容忍不可见字符差异）
        // 注意：关闭词判定不受会话类型限制，任何会话都能停
        String stop = getStopTrigger();
        if (!TextUtils.isEmpty(stop) && sameText(trimmed, stop)) {
            stopPendingMessages();
            return true;
        }
        // [Aiqiyi] 群聊/频道也支持：这里不再按 peer 正负拦截。
        // 触发判定只发生在本机发送路径上，别人发到群里的消息不会走到这里，
        // 所以放开负数 peer 不会让别人刷触发词就能启动引擎。
        if (peer == 0) {
            return false;
        }
        String trigger = getTrigger();
        if (TextUtils.isEmpty(trigger)) {
            return false;
        }
        if (!sameText(trimmed, trigger)) {
            return false;
        }
        if (getEnabledCorpusCount() <= 0) {
            return false;
        }

        startLoop(peer, account, replyToTopMsg);
        return true;
    }

    // ==================== 发送循环 ====================
    private synchronized void startLoop(long peer, int account) {
        startLoop(peer, account, null);
    }

    private synchronized void startLoop(long peer, int account, org.telegram.messenger.MessageObject topicRoot) {
        stopPendingMessages();
        activePeer = peer;
        activeAccount = account;
        activeTopicRoot = topicRoot;
        final int generation = ++queueGeneration;
        sendNext(peer, account, generation, 0);
    }

    public synchronized void stopPendingMessages() {
        queueGeneration++;
        activePeer = 0;
        activeAccount = 0;
        activeTopicRoot = null;
    }

    public boolean isRunning() {
        return activePeer != 0;
    }

    private void sendNext(final long peer, final int account, final int generation, final int index) {
        if (!isEnabled() || activePeer == 0) {
            return;
        }

        ArrayList<String> lines = getEnabledCorpusLines();
        if (lines.isEmpty()) {
            stopPendingMessages();
            return;
        }

        // 顺序模式到达末尾则重新开始，形成循环
        final int idx = index % lines.size();
        final String content = getSendMode() == MODE_RANDOM
                ? getRandomEnabledLine()
                : lines.get(idx);
        if (TextUtils.isEmpty(content)) {
            stopPendingMessages();
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            if (generation != queueGeneration || !isEnabled() || activePeer == 0) {
                return;
            }
            try {
                SendMessagesHelper.SendMessageParams params = new SendMessagesHelper.SendMessageParams();
                params.peer = peer;
                params.message = content;
                // [Aiqiyi] 群聊里的 Forum/Topic 会话：沿着触发时所在的 Topic 发送，
                // 否则消息会掉进 General，看起来像发错了地方。
                if (activeTopicRoot != null) {
                    params.replyToTopMsg = activeTopicRoot;
                }
                if (params.params == null) {
                    params.params = new java.util.HashMap<>();
                }
                // 打标记，避免引擎自己的消息再次触发判定
                params.params.put("aiqiyi_comeback_auto", "1");
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) {
                FileLog.e(e);
                stopPendingMessages();
                return;
            }
            sendNext(peer, account, generation, index + 1);
        }, nextDelay());
    }

    /** 计算下一次发送的间隔 */
    private long nextDelay() {
        int speed = getSendSpeed();
        long delay = BASE_DELAY_MS / speed + RANDOM.nextInt(300);
        return Math.max(MIN_DELAY_MS, delay);
    }
}