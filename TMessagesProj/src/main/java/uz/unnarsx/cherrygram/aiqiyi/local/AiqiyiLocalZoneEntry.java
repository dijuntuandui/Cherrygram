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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import com.aiqiyi.local.AiqiyiLocalGramHelper;
import com.aiqiyi.local.AiqiyiLocalStarsHelper;
import uz.unnarsx.cherrygram.core.configs.AiqiyiLocalDataConfig;
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
    private final int localTimeValueRow = 333;

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
        items.add(SettingsHelper.asTextDetail(localGiftCountRow, 0,
                        getString(R.string.AQY_LocalGift_Count), getGiftCountText())
                .slug("aqy_local_gift_count")
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
                        getString(R.string.AQY_LocalMessage_Rules), getMessageRulesText())
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
                        getString(R.string.AQY_LocalTime_Mode), getTimeModeText())
                .slug("aqy_local_time_mode")
        );
        items.add(SettingsHelper.asTextDetail(localTimeValueRow, 0,
                        getString(R.string.AQY_LocalTime_Value), getTimeValueText())
                .slug("aqy_local_time_value")
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
            AiqiyiLocalDataConfig.INSTANCE.clearLocalGifts();
            listView.adapter.update(true);
        } else if (item.id == localMessageOverrideRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalMessageOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalMessageOverride());
            AiqiyiLocalMessageHelper.invalidate();
            listView.adapter.update(true);
        } else if (item.id == localMessageRulesRow) {
            showMessageRulesInput();
        } else if (item.id == localTimeOverrideRow) {
            AiqiyiLocalDataConfig.INSTANCE.setLocalTimeOverride(!AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOverride());
            AiqiyiLocalTimeHelper.invalidate();
            listView.adapter.update(true);
        } else if (item.id == localTimeModeRow) {
            showTimeModeSelect();
        } else if (item.id == localTimeValueRow) {
            showTimeValueInput();
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
        return String.valueOf(AiqiyiLocalDataConfig.INSTANCE.getLocalGiftCount());
    }

    private CharSequence getMessageRulesText() {
        int count = AiqiyiLocalMessageHelper.getRuleCount();
        if (count <= 0) {
            return getString(R.string.AQY_LocalMessage_RulesNone);
        }
        return String.format(getString(R.string.AQY_LocalMessage_RulesCount), count);
    }

    private void showMessageRulesInput() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AQY_LocalMessage_Rules));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(AiqiyiLocalDataConfig.INSTANCE.getLocalMessageRules());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(false);
        editText.setMinLines(5);
        editText.setMaxLines(12);
        editText.setHint(getString(R.string.AQY_LocalMessage_RulesHint));

        builder.setView(editText);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            AiqiyiLocalDataConfig.INSTANCE.setLocalMessageRules(editText.getText().toString());
            AiqiyiLocalMessageHelper.invalidate();
            listView.adapter.update(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    // ===== 本地时间 =====

    private CharSequence getTimeModeText() {
        int mode = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeMode();
        if (mode == 2) {
            return getString(R.string.AQY_LocalTime_ModeFixed);
        }
        return getString(R.string.AQY_LocalTime_ModeOffset);
    }

    private CharSequence getTimeValueText() {
        int mode = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeMode();
        if (mode == 2) {
            int s = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeFixedSeconds();
            return String.format("%02d:%02d", (s / 3600) % 24, (s % 3600) / 60);
        }
        int offset = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOffset();
        if (offset == 0) {
            return getString(R.string.AQY_LocalTime_ValueNone);
        }
        String sign = offset > 0 ? "+" : "-";
        int abs = Math.abs(offset);
        int h = abs / 3600;
        int m = (abs % 3600) / 60;
        if (h > 0 && m > 0) {
            return String.format(getString(R.string.AQY_LocalTime_ValueHM), sign, h, m);
        } else if (h > 0) {
            return String.format(getString(R.string.AQY_LocalTime_ValueH), sign, h);
        } else {
            return String.format(getString(R.string.AQY_LocalTime_ValueM), sign, m);
        }
    }

    private void showTimeModeSelect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AQY_LocalTime_Mode));
        CharSequence[] modes = new CharSequence[]{
                getString(R.string.AQY_LocalTime_ModeOffset),
                getString(R.string.AQY_LocalTime_ModeFixed)
        };
        builder.setItems(modes, (dialog, which) -> {
            AiqiyiLocalDataConfig.INSTANCE.setLocalTimeMode(which == 1 ? 2 : 1);
            AiqiyiLocalTimeHelper.invalidate();
            listView.adapter.update(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showTimeValueInput() {
        int mode = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeMode();
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AQY_LocalTime_Value));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setSingleLine(true);
        if (mode == 2) {
            int s = AiqiyiLocalDataConfig.INSTANCE.getLocalTimeFixedSeconds();
            editText.setText(String.format("%02d:%02d", (s / 3600) % 24, (s % 3600) / 60));
            editText.setHint(getString(R.string.AQY_LocalTime_ValueHintFixed));
        } else {
            editText.setText(String.valueOf(AiqiyiLocalDataConfig.INSTANCE.getLocalTimeOffset()));
            editText.setHint(getString(R.string.AQY_LocalTime_ValueHintOffset));
        }

        builder.setView(editText);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String raw = editText.getText().toString().trim();
            if (mode == 2) {
                int sec = parseHhMm(raw);
                if (sec >= 0) {
                    AiqiyiLocalDataConfig.INSTANCE.setLocalTimeFixedSeconds(sec);
                }
            } else {
                try {
                    AiqiyiLocalDataConfig.INSTANCE.setLocalTimeOffset(Integer.parseInt(raw));
                } catch (Throwable ignore) {
                }
            }
            AiqiyiLocalTimeHelper.invalidate();
            listView.adapter.update(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    /** 解析 HH:mm 为当天秒数；失败返回 -1 */
    private static int parseHhMm(String s) {
        if (s == null) {
            return -1;
        }
        s = s.trim().replace('：', ':');
        int idx = s.indexOf(':');
        if (idx <= 0) {
            return -1;
        }
        try {
            int h = Integer.parseInt(s.substring(0, idx).trim());
            int m = Integer.parseInt(s.substring(idx + 1).trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return -1;
            }
            return h * 3600 + m * 60;
        } catch (Throwable e) {
            return -1;
        }
    }
}