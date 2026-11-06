package com.Huanghun.protocol;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * 协议转换二级页。卡片分组结构，对齐「本地功能专区」的视觉。
 *
 * <p>九个功能按用途分三组。未实现的项标「下一版」并置灰，点上去只提示不做事，
 * 避免用户点了没反应。</p>
 */
public final class ProtocolConvertPage {

    private ProtocolConvertPage() {
    }

    private static int dp(float value) {
        return (int) (value * AndroidUtilities.density + 0.5f);
    }

    // ---------------------------------------------------------- 打开

    /**
     * 弹出二级页。点击任一条目会先关面板再执行，避免两层叠在一起。
     */
    public static void show(Activity activity, final FlowHost host) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final BottomSheet.Builder builder = new BottomSheet.Builder(activity);
        // sheet 在 create() 之后才有，条目点击时通过这个持有者关闭
        final BottomSheet[] sheetRef = new BottomSheet[1];

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(8));

        // 标题
        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(R.string.AQY_Conv_Title);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 12, 22, 2));

        TextView sub = new TextView(activity);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setText("tdata / session / json / 字符串互转，产物落在固定目录。tdata 请压缩成 zip 后再选。");
        root.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 22, 0, 22, 12));

        // ==== 组一：转换 ====
        addSectionHeader(activity, root, "转换");
        addCard(activity, root, sheetRef, new Item[] {
                new Item("tdata 转 session", "选 tdata 压缩包，输出 telethon + pyrogram 两份 session", true,
                        () -> host.onFeature(FlowHost.F_TDATA_TO_SESSION)),
                new Item("session 转 tdata", "读 session / json / 字符串，输出标准 tdata 目录", true,
                        () -> host.onFeature(FlowHost.F_SESSION_TO_TDATA)),
                new Item("tdata 转 JSON", "选 tdata 压缩包，输出带 auth_key 的 json 归档", true,
                        () -> host.onFeature(FlowHost.F_TDATA_TO_JSON)),
                new Item("JSON 转 session", "读 json 归档，还原成 session 与字符串", true,
                        () -> host.onFeature(FlowHost.F_JSON_TO_SESSION)),
        });

        // ==== 组二：工具 ====
        addSectionHeader(activity, root, "工具");
        addCard(activity, root, sheetRef, new Item[] {
                new Item("查看信息", "不写出任何文件，只列出 dc / uid / 密钥指纹", true,
                        () -> host.onFeature(FlowHost.F_INSPECT)),
                new Item("自检", "随机 key 走完整往返，验证转换链路是否闭合", true,
                        () -> host.onFeature(FlowHost.F_SELF_TEST)),
                new Item("测账号状态", "联网探测账号是否仍有效，只读不写", true,
                        () -> host.onFeature(FlowHost.F_CHECK_STATUS)),
        });

        // ==== 组三：生成 ====
        addSectionHeader(activity, root, "生成");
        addCard(activity, root, sheetRef, new Item[] {
                new Item("生成新会话", "自备私钥与账号 id，或直接选已有 session / zip，产出 session 与 tdata", true,
                        () -> host.onFeature(FlowHost.F_NEW_LOGIN)),
                new Item("凭私钥生成", "粘贴 512 位私钥与 dc_id 直接生成，也可以选 .session / zip 自动读取", true,
                        () -> host.onFeature(FlowHost.F_FROM_KEY)),
        });

        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        sheetRef[0] = sheet;
        host.attachSheet(sheet);
    }

    // ---------------------------------------------------------- 构件

    private static void addSectionHeader(Context context, LinearLayout parent, String text) {
        TextView header = new TextView(context);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        header.setText(text);
        parent.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 26, 8, 22, 4));
    }

    /** 一张卡里放多条，条目之间加分隔线。 */
    private static void addCard(Context context, LinearLayout parent, final BottomSheet[] sheetRef,
            Item[] items) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(14),
                Theme.getColor(Theme.key_windowBackgroundGray)));

        for (int i = 0; i < items.length; i++) {
            card.addView(buildRow(context, items[i], sheetRef), LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (i < items.length - 1) {
                View divider = new View(context);
                divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                card.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1,
                        20, 0, 20, 0));
            }
        }

        parent.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 16, 0, 16, 10));
    }

    private static LinearLayout buildRow(Context context, final Item item,
            final BottomSheet[] sheetRef) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(Theme.getSelectorDrawable(false));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTextColor(Theme.getColor(item.enabled
                ? Theme.key_windowBackgroundWhiteBlackText
                : Theme.key_windowBackgroundWhiteGrayText3));
        name.setText(item.title);
        texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setText(item.enabled ? item.desc : item.desc + "（下一版）");
        texts.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        arrow.setTextColor(Theme.getColor(item.enabled
                ? Theme.key_windowBackgroundWhiteGrayText
                : Theme.key_windowBackgroundWhiteGrayText3));
        row.addView(arrow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

        if (!item.enabled) {
            row.setAlpha(0.45f);
        }
        row.setOnClickListener(v -> {
            // 先关面板，再执行，避免两层叠在一起
            try {
                if (sheetRef[0] != null) {
                    sheetRef[0].dismiss();
                }
            } catch (Exception ignore) {
            }
            item.action.run();
        });
        return row;
    }

    /** 一个条目。 */
    private static final class Item {
        final String title;
        final String desc;
        final boolean enabled;
        final Runnable action;

        Item(String title, String desc, boolean enabled, Runnable action) {
            this.title = title;
            this.desc = desc;
            this.enabled = enabled;
            this.action = action;
        }
    }

    /** 页面把用户的选择回抛给宿主。 */
    public interface FlowHost {
        int F_TDATA_TO_SESSION = 1;
        int F_SESSION_TO_TDATA = 2;
        int F_TDATA_TO_JSON = 3;
        int F_JSON_TO_SESSION = 4;
        int F_INSPECT = 5;
        int F_SELF_TEST = 6;
        int F_CHECK_STATUS = 7;
        int F_NEW_LOGIN = 8;
        int F_FROM_KEY = 9;

        void onFeature(int feature);

        void attachSheet(BottomSheet sheet);
    }
}