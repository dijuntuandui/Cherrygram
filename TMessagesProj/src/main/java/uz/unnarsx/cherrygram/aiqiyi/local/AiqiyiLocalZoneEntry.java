/**
 * 爱琦艺客户端 - 本地功能专区 设置页
 *
 * 本页仅修改本机显示与本地测试数据，不会修改 Telegram 服务器内容。
 * 虚拟星星 / Gram 不能真实购买；本地礼物也不会产生真实订单或扣款。
 *
 * @author Aiqiyi
 */

package uz.unnarsx.cherrygram.aiqiyi.local;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import com.aiqiyi.local.AiqiyiLocalGramHelper;
import com.aiqiyi.local.AiqiyiLocalStarsHelper;
import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;
import uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftData;
import uz.unnarsx.cherrygram.core.helpers.localdata.LocalGiftStore;
import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiLocalZoneEntry extends BaseCGPreferencesEntry {

    private final int localZoneEnabledRow = 320;
    private final int localStarsOverrideRow = 321;
    private final int localStarsBalanceRow = 322;
    private final int localGramOverrideRow = 323;
    private final int localGramBalanceRow = 324;
    private final int localPremiumRow = 325;
    private final int localBusinessRow = 326;
    private final int localGiftCountRow = 327;
    private final int clearLocalGiftsRow = 328;
    private final int localMessageOverrideRow = 329;
    private final int localMessageRulesRow = 330;
    private final int localTimeOverrideRow = 331;
    private final int localTimeModeRow = 332;
    // Aiqiyi: 本地礼物相关
    private final int localGiftEnabledRow = 333;
    private final int localGiftShowOnProfileRow = 334;
    private final int localGiftAddRow = 335;
    private final int localGiftListRow = 336;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_LocalZone);
    }

    @Override
    protected String getKey() {
        return "aqy_local_zone";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // ===== 总开关 =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalZone_Header)));
        items.add(SettingsHelper.asSwitchCG(localZoneEnabledRow, getString(R.string.AQY_LocalZone_Enabled),
                        getString(R.string.AQY_LocalZone_Enabled_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled())
                .slug("aqy_local_zone_enabled")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalZone_Note)));

        // ===== 虚拟星星 =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalStars_Header)));
        items.add(SettingsHelper.asSwitchCG(localStarsOverrideRow, getString(R.string.AQY_LocalStars_Override),
                        getString(R.string.AQY_LocalStars_Override_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalStarsOverride())
                .slug("aqy_local_stars_override")
        );
        items.add(SettingsHelper.asTextDetail(localStarsBalanceRow, 0,
                        getString(R.string.AQY_LocalStars_Balance), getStarsBalanceText())
                .slug("aqy_local_stars_balance")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalStars_Note)));

        // ===== 虚拟 Gram =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalGram_Header)));
        items.add(SettingsHelper.asSwitchCG(localGramOverrideRow, getString(R.string.AQY_LocalGram_Override),
                        getString(R.string.AQY_LocalGram_Override_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalGramOverride())
                .slug("aqy_local_gram_override")
        );
        items.add(SettingsHelper.asTextDetail(localGramBalanceRow, 0,
                        getString(R.string.AQY_LocalGram_Balance), getGramBalanceText())
                .slug("aqy_local_gram_balance")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalGram_Note)));

        // ===== 本地高级版 =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalPremium_Header)));
        items.add(SettingsHelper.asSwitchCG(localPremiumRow, getString(R.string.AQY_LocalPremium),
                        getString(R.string.AQY_LocalPremium_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalPremiumOverride())
                .slug("aqy_local_premium")
        );
        items.add(SettingsHelper.asSwitchCG(localBusinessRow, getString(R.string.AQY_LocalBusiness),
                        getString(R.string.AQY_LocalBusiness_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalBusinessOverride())
                .slug("aqy_local_business")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalPremium_Note)));

        // ===== 本地虚拟礼物 =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalGift_Header)));
        items.add(SettingsHelper.asSwitchCG(localGiftEnabledRow, getString(R.string.AQY_LocalGift_Enabled),
                        getString(R.string.AQY_LocalGift_Enabled_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalGiftEnabled())
                .slug("aqy_local_gift_enabled")
        );
        items.add(SettingsHelper.asSwitchCG(localGiftShowOnProfileRow, getString(R.string.AQY_LocalGift_ShowOnProfile),
                        getString(R.string.AQY_LocalGift_ShowOnProfile_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalGiftShowOnProfile())
                .slug("aqy_local_gift_show_profile")
        );
        items.add(SettingsHelper.asTextDetail(localGiftCountRow, 0,
                        getString(R.string.AQY_LocalGift_Count), getGiftCountText())
                .slug("aqy_local_gift_count")
        );
        items.add(SettingsHelper.asTextDetail(localGiftAddRow, 0,
                        getString(R.string.AQY_LocalGift_Add), getString(R.string.AQY_LocalGift_Add_Desc))
                .slug("aqy_local_gift_add")
        );
        items.add(UItem.asButton(clearLocalGiftsRow, getString(R.string.AQY_LocalGift_Clear), getString(R.string.AQY_LocalGift_Clear_Desc))
                .slug("aqy_local_gift_clear")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalGift_Note)));

        // ===== 本地消息 =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalMessage_Header)));
        items.add(SettingsHelper.asSwitchCG(localMessageOverrideRow, getString(R.string.AQY_LocalMessage_Override),
                        getString(R.string.AQY_LocalMessage_Override_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalMessageOverride())
                .slug("aqy_local_message_override")
        );
        items.add(SettingsHelper.asTextDetail(localMessageRulesRow, 0,
                        getString(R.string.AQY_LocalMessage_Replaced), getMessageRulesText())
                .slug("aqy_local_message_rules")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalMessage_Note)));

        // ===== 本地时间 =====
        items.add(UItem.asHeader(getString(R.string.AQY_LocalTime_Header)));
        items.add(SettingsHelper.asSwitchCG(localTimeOverrideRow, getString(R.string.AQY_LocalTime_Override),
                        getString(R.string.AQY_LocalTime_Override_Desc))
                .setChecked(AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOverride())
                .slug("aqy_local_time_override")
        );
        items.add(SettingsHelper.asTextDetail(localTimeModeRow, 0,
                        getString(R.string.AQY_LocalTime_Faked), getTimeCountText())
                .slug("aqy_local_time_count")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_LocalTime_Note)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int account = UserConfig.selectedAccount;

        if (item.id == localZoneEnabledRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalZoneEnabled(!AiqiyiLocalDataConfig.INSTANCE.getLocalZoneEnabled());
            listView.adapter.update(true);
        } else if (item.id == localStarsOverrideRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalStarsOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalStarsOverride());
            listView.adapter.update(true);
        } else if (item.id == localGramOverrideRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalGramOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalGramOverride());
            listView.adapter.update(true);
        } else if (item.id == localPremiumRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalPremiumOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalPremiumOverride());
            listView.adapter.update(true);
        } else if (item.id == localBusinessRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalBusinessOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalBusinessOverride());
            listView.adapter.update(true);
        } else if (item.id == localStarsBalanceRow) {
            showBalanceInput(account, true);
        } else if (item.id == localGramBalanceRow) {
            showBalanceInput(account, false);
        } else if (item.id == clearLocalGiftsRow) {
            // Aiqiyi: 清空当前账号的真实本地礼物列表
            final long clearUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            LocalGiftStore.clear(clearUserId);
            AiqiyiLocalDataConfig.INSTANCE.clearLocalGifts();
            listView.adapter.update(true);
        } else if (item.id == localGiftEnabledRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalGiftEnabled(!AiqiyiLocalDataConfig.INSTANCE.getLocalGiftEnabled());
            listView.adapter.update(true);
        } else if (item.id == localGiftShowOnProfileRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalGiftShowOnProfile(!AiqiyiLocalDataConfig.INSTANCE.getLocalGiftShowOnProfile());
            listView.adapter.update(true);
        } else if (item.id == localGiftAddRow) {
            showAddLocalGiftDialog();
        } else if (item.id == localMessageOverrideRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalMessageOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalMessageOverride());
            AiqiyiLocalMessageHelper.invalidate();
            listView.adapter.update(true);
        } else if (item.id == localMessageRulesRow) {
            showReplaceListDialog();
        } else if (item.id == localTimeOverrideRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalTimeOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOverride());
            AiqiyiLocalTimeHelper.invalidate();
            listView.adapter.update(true);
        } else if (item.id == localTimeModeRow) {
            showFakeTimeListDialog();
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    private void showBalanceInput(int account, boolean stars) {
        long current = stars ? AiqiyiLocalStarsHelper.getBalance() : AiqiyiLocalGramHelper.getBalance();

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(stars ? getString(R.string.AQY_LocalStars_Balance) : getString(R.string.AQY_LocalGram_Balance));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(String.valueOf(current));
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);

        builder.setView(editText);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            long value;
            try {
                value = Long.parseLong(editText.getText().toString().trim());
            } catch (Throwable e) {
                value = 0;
            }
            if (stars) {
                AiqiyiLocalStarsHelper.setBalance(account, value);
            } else {
                AiqiyiLocalGramHelper.setBalance(account, value);
            }
            listView.adapter.update(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private CharSequence getStarsBalanceText() {
        if (!AiqiyiLocalStarsHelper.isEnabled()) {
            return getString(R.string.AQY_LocalZone_Disabled);
        }
        return String.format("%,d", AiqiyiLocalStarsHelper.getBalance());
    }

    private CharSequence getGramBalanceText() {
        if (!AiqiyiLocalGramHelper.isEnabled()) {
            return getString(R.string.AQY_LocalZone_Disabled);
        }
        return String.format("%,d", AiqiyiLocalGramHelper.getBalance());
    }

    private CharSequence getGiftCountText() {
        // Aiqiyi: 改为统计当前账号的本地礼物列表实际条数
        final int count = LocalGiftStore.getDataForUser(
                UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()
        ).size();
        if (count <= 0) {
            return getString(R.string.AQY_LocalGift_CountNone);
        }
        return String.format(getString(R.string.AQY_LocalGift_CountFormat), count);
    }

    /**
     * Aiqiyi: 打开礼物目录，选一个作为本地礼物的外观模板
     */
    private void showAddLocalGiftDialog() {
        final long userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == 0) {
            return;
        }

        final StarsController controller = StarsController.getInstance(UserConfig.selectedAccount);
        controller.loadStarGifts();

        final ArrayList<TL_stars.StarGift> catalog = controller.gifts;
        if (catalog == null || catalog.isEmpty()) {
            // 目录还没加载好，注册一次性回调
            final NotificationCenter.NotificationCenterDelegate[] observer = new NotificationCenter.NotificationCenterDelegate[1];
            final int account = UserConfig.selectedAccount;
            observer[0] = (id, acc, args) -> {
                if (id == NotificationCenter.starGiftsLoaded) {
                    NotificationCenter.getInstance(account).removeObserver(observer[0], NotificationCenter.starGiftsLoaded);
                    showGiftCatalogPicker(userId);
                }
            };
            NotificationCenter.getInstance(account).addObserver(observer[0], NotificationCenter.starGiftsLoaded);
            return;
        }
        showGiftCatalogPicker(userId);
    }

    /**
     * Aiqiyi: 礼物目录选择器
     */
    private void showGiftCatalogPicker(long userId) {
        final StarsController controller = StarsController.getInstance(UserConfig.selectedAccount);
        final ArrayList<TL_stars.StarGift> catalog = controller.gifts;
        if (catalog == null || catalog.isEmpty()) {
            return;
        }

        final java.util.List<String> names = new java.util.ArrayList<>();
        final java.util.List<TL_stars.StarGift> refs = new java.util.ArrayList<>();
        // 只收限量款（unique 礼物有图案可展示），最多 40 个
        for (int i = 0; i < catalog.size() && refs.size() < 40; i++) {
            final TL_stars.StarGift g = catalog.get(i);
            if (g.sticker == null) {
                continue;
            }
            final String label = (g.title == null || g.title.isEmpty()) ? ("#" + g.id) : g.title;
            names.add(label);
            refs.add(g);
        }
        if (refs.isEmpty()) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.AQY_LocalGift_PickTemplate));
        builder.setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
            final TL_stars.StarGift g = refs.get(which);
            try {
                final LocalGiftData data = new LocalGiftData();
                data.setTitle(g.title);
                data.setSlug(g.slug);
                data.setCatalogGiftId(g.id);
                data.setDocumentId(g.sticker == null ? 0 : g.sticker.id);
                data.setSenderName(getString(R.string.AQY_LocalGift_SenderSelf));
                data.setGiftNum(1);
                data.setValueAmount(g.stars);
                data.setValueCurrency("XTR");
                data.setAvailabilityIssued(g.availability_issued);
                data.setAvailabilityTotal(g.availability_total);
                data.setOriginalDate((int) (System.currentTimeMillis() / 1000));
                data.setOriginalSenderId(userId);
                data.setOriginalRecipientId(userId);
                // 从 attributes 里取背景色
                final TL_stars.starGiftAttributeBackdrop backdrop = StarsController.findAttribute(g.attributes, TL_stars.starGiftAttributeBackdrop.class);
                if (backdrop != null) {
                    data.setCenterColor(backdrop.center_color);
                    data.setEdgeColor(backdrop.edge_color);
                    data.setPatternColor(backdrop.pattern_color);
                    data.setTextColor(backdrop.text_color);
                    data.setBackdropName(backdrop.name);
                    data.setLocalStyle(true);
                } else {
                    data.setLocalStyle(false);
                }
                data.setPatternDocumentId(0);
                // 爱琦艺：违规礼物佩戴入口 —— 写入即佩戴，直接挂到资料页名字下方
                data.setPinned(true);
                LocalGiftStore.addLocalGift(userId, data);
                listView.adapter.update(true);
            } catch (Exception ignored) {
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private CharSequence getMessageRulesText() {
        int count = AiqiyiMessageReplaceStore.count();
        if (count <= 0) {
            return getString(R.string.AQY_LocalMessage_RulesNone);
        }
        return String.format(getString(R.string.AQY_LocalMessage_RulesCount), count);
    }

    private CharSequence getTimeCountText() {
        int count = AiqiyiMessageReplaceStore.timeCount();
        if (count <= 0) {
            return getString(R.string.AQY_LocalMessage_RulesNone);
        }
        return String.format(getString(R.string.AQY_LocalMessage_RulesCount), count);
    }

    /**
     * 「已替换 N 条」——点进去查看单条替换，可直接清除。
     */
    private void showReplaceListDialog() {
        final java.util.ArrayList<Long> keys = AiqiyiMessageReplaceStore.listChatIds();
        final java.util.List<String> lines = new java.util.ArrayList<>();
        final java.util.List<long[]> refs = new java.util.ArrayList<>();

        for (Long cidObj : keys) {
            long cid = cidObj;
            for (int[] pair : AiqiyiMessageReplaceStore.listMessageIds(cid)) {
                int mid = pair[0];
                String text = AiqiyiMessageReplaceStore.get(cid, mid);
                if (text == null) continue;
                lines.add("chat " + cid + " / msg " + mid + " → " + text);
                refs.add(new long[]{cid, mid});
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AQY_LocalMessage_Replaced));
        if (lines.isEmpty()) {
            builder.setMessage(getString(R.string.AQY_LocalMessage_ReplacedNone));
            builder.setNegativeButton(getString(R.string.Close), null);
        } else {
            builder.setItems(lines.toArray(new CharSequence[0]), (dialog, which) -> {
                long[] ref = refs.get(which);
                AiqiyiMessageReplaceStore.remove(ref[0], (int) ref[1]);
                listView.adapter.update(true);
            });
            builder.setNeutralButton(getString(R.string.AQY_LocalMessage_ClearAll), (dialog, which) -> {
                AiqiyiMessageReplaceStore.clearAll();
                listView.adapter.update(true);
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
        }
        builder.show();
    }

    /**
     * 「已伪装 N 条」——点进去查看单条时间伪装，可直接清除。
     */
    private void showFakeTimeListDialog() {
        final java.util.ArrayList<Long> keys = AiqiyiMessageReplaceStore.listChatIds();
        final java.util.List<String> lines = new java.util.ArrayList<>();
        final java.util.List<long[]> refs = new java.util.ArrayList<>();

        for (Long cidObj : keys) {
            long cid = cidObj;
            for (int[] pair : AiqiyiMessageReplaceStore.listMessageIds(cid)) {
                int mid = pair[0];
                int sec = AiqiyiMessageReplaceStore.getTime(cid, mid);
                if (sec < 0) continue;
                lines.add("chat " + cid + " / msg " + mid + " → "
                        + String.format("%02d:%02d", (sec / 3600) % 24, (sec % 3600) / 60));
                refs.add(new long[]{cid, mid});
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AQY_LocalTime_Faked));
        if (lines.isEmpty()) {
            builder.setMessage(getString(R.string.AQY_LocalTime_FakedNone));
            builder.setNegativeButton(getString(R.string.Close), null);
        } else {
            builder.setItems(lines.toArray(new CharSequence[0]), (dialog, which) -> {
                long[] ref = refs.get(which);
                AiqiyiMessageReplaceStore.removeTime(ref[0], (int) ref[1]);
                listView.adapter.update(true);
            });
            builder.setNeutralButton(getString(R.string.AQY_LocalMessage_ClearAll), (dialog, which) -> {
                AiqiyiMessageReplaceStore.clearAllTime();
                listView.adapter.update(true);
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
        }
        builder.show();
    }
}
