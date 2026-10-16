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
}