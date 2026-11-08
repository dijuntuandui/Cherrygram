/**
 * 爱琦艺客户端 - 视频专区 设置页
 * 仅本地生效：设置与视频库均保存在本机，不会上传服务器。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.videozone;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.core.configs.CherrygramCameraConfig;
import uz.unnarsx.cherrygram.core.huanghun.HuanghunVideoLibraryActivity;
import uz.unnarsx.cherrygram.core.huanghun.HuanghunVideoLibraryHelper;
import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

public class AiqiyiVideoZoneEntry extends BaseCGPreferencesEntry {

    private final int enabledRow = 520;
    private final int pickVideosRow = 521;
    private final int manageVideosRow = 522;
    private final int videoSoundRow = 523;
    private final int roundVideoRow = 524;
    private final int squareVideoRow = 525;
    private final int videoToGifRow = 526;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AQY_VideoZone);
    }

    @Override
    protected String getKey() {
        return "aqy_video_zone";
    }

    private CherrygramCameraConfig cfg() {
        return CherrygramCameraConfig.INSTANCE;
    }

    private int videoCount() {
        try {
            return HuanghunVideoLibraryHelper.getVideoCount(ApplicationLoader.applicationContext, currentAccount);
        } catch (Throwable e) {
            return 0;
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean on = cfg().getHuanghunBuiltinCameraEnabled();

        items.add(UItem.asHeader(getString(R.string.AQY_VideoZone_Header)));
        items.add(SettingsHelper.asSwitchCG(enabledRow,
                        getString(R.string.AQY_VideoZone_Enabled),
                        getString(R.string.AQY_VideoZone_Enabled_Desc))
                .setChecked(on)
                .slug("aqy_video_zone_enabled")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_VideoZone_Note)));

        if (!on) {
            return;
        }

        // 视频库
        items.add(UItem.asHeader(getString(R.string.AQY_VideoZone_Library_Header)));
        items.add(SettingsHelper.asTextDetail(pickVideosRow, 0,
                        getString(R.string.AQY_VideoZone_Pick),
                        getString(R.string.AQY_VideoZone_Pick_Desc))
                .slug("aqy_video_zone_pick")
        );
        items.add(SettingsHelper.asTextDetail(manageVideosRow, 0,
                        getString(R.string.AQY_VideoZone_Manage),
                        org.telegram.messenger.LocaleController.formatString(R.string.AQY_VideoZone_Manage_Desc, videoCount()))
                .slug("aqy_video_zone_manage")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_VideoZone_Library_Note)));

        // 输出格式
        items.add(UItem.asHeader(getString(R.string.AQY_VideoZone_Output_Header)));
        items.add(SettingsHelper.asSwitchCG(roundVideoRow,
                        getString(R.string.AQY_VideoZone_Round),
                        getString(R.string.AQY_VideoZone_Round_Desc))
                .setChecked(cfg().getHuanghunBuiltinRoundVideo())
                .slug("aqy_video_zone_round")
        );
        items.add(SettingsHelper.asSwitchCG(squareVideoRow,
                        getString(R.string.AQY_VideoZone_Square),
                        getString(R.string.AQY_VideoZone_Square_Desc))
                .setChecked(cfg().getHuanghunBuiltinSquareVideo())
                .slug("aqy_video_zone_square")
        );
        items.add(UItem.asShadow(getString(R.string.AQY_VideoZone_Output_Note)));

        // 声音与格式
        items.add(UItem.asHeader(getString(R.string.AQY_VideoZone_Sound_Header)));
        items.add(SettingsHelper.asSwitchCG(videoSoundRow,
                        getString(R.string.AQY_VideoZone_Sound),
                        getString(R.string.AQY_VideoZone_Sound_Desc))
                .setChecked(cfg().getHuanghunBuiltinVideoSound())
                .slug("aqy_video_zone_sound")
        );
        items.add(SettingsHelper.asSwitchCG(videoToGifRow,
                        getString(R.string.AQY_VideoZone_ToGif),
                        getString(R.string.AQY_VideoZone_ToGif_Desc))
                .setChecked(cfg().getHuanghunVideoToGif())
                .slug("aqy_video_zone_to_gif")
        );
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == enabledRow) {
            cfg().setHuanghunBuiltinCameraEnabled(!cfg().getHuanghunBuiltinCameraEnabled());
            SettingsHelper.updateCheckState(view, cfg().getHuanghunBuiltinCameraEnabled());
            listView.adapter.update(true);
        } else if (item.id == pickVideosRow) {
            presentFragment(new HuanghunVideoLibraryActivity(false, false));
        } else if (item.id == manageVideosRow) {
            presentFragment(new HuanghunVideoLibraryActivity(true, false));
        } else if (item.id == videoSoundRow) {
            cfg().setHuanghunBuiltinVideoSound(!cfg().getHuanghunBuiltinVideoSound());
            SettingsHelper.updateCheckState(view, cfg().getHuanghunBuiltinVideoSound());
        } else if (item.id == roundVideoRow) {
            cfg().setHuanghunBuiltinRoundVideo(!cfg().getHuanghunBuiltinRoundVideo());
            SettingsHelper.updateCheckState(view, cfg().getHuanghunBuiltinRoundVideo());
        } else if (item.id == squareVideoRow) {
            cfg().setHuanghunBuiltinSquareVideo(!cfg().getHuanghunBuiltinSquareVideo());
            SettingsHelper.updateCheckState(view, cfg().getHuanghunBuiltinSquareVideo());
        } else if (item.id == videoToGifRow) {
            cfg().setHuanghunVideoToGif(!cfg().getHuanghunVideoToGif());
            SettingsHelper.updateCheckState(view, cfg().getHuanghunVideoToGif());
        } else {
            super.onClick(item, view, position, x, y);
        }
    }
}