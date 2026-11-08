/**
 * 爱琦艺客户端 - 视频专区 设置页
 * 仅本地生效：设置与视频库均保存在本机，不会上传服务器。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.aiqiyi.videozone;

import static org.telegram.messenger.LocaleController.getString;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
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

    private static final int REQUEST_AIQIYI_BUILTIN_VIDEOS = 0x5A71;

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

    /**
     * 打开系统文件选择器，只允许选择视频，支持多选。
     * 参考黄昏实现：ACTION_OPEN_DOCUMENT + CATEGORY_OPENABLE + video/*。
     */
    private void chooseBuiltinVideos() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_AIQIYI_BUILTIN_VIDEOS);
        } catch (Throwable e) {
            FileLog.e(e);
            showVideoInfo("无法选择视频", "无法打开系统视频选择器，请稍后重试。");
        }
    }

    /**
     * 将选中的视频复制到应用私有视频库。
     * 复制、时长校验都在后台线程完成，单个文件失败不影响同批次其它视频。
     */
    private void importBuiltinVideos(ArrayList<Uri> sources) {
        if (sources == null || sources.isEmpty() || getParentActivity() == null) {
            showVideoInfo("导入失败", "未读取到所选视频，请重新选择。");
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage("正在保存并验证所选视频，请稍候。");
        progress.setCancelable(false);
        showDialog(progress);
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            HuanghunVideoLibraryHelper.ImportResult result;
            try {
                result = HuanghunVideoLibraryHelper.importVideos(ApplicationLoader.applicationContext, account, sources);
            } catch (Throwable e) {
                FileLog.e(e);
                ArrayList<String> errors = new ArrayList<>();
                String message = e.getMessage();
                errors.add(message == null || message.length() == 0 ? "视频导入失败，请稍后重试。" : message);
                result = new HuanghunVideoLibraryHelper.ImportResult(0, errors);
            }
            final HuanghunVideoLibraryHelper.ImportResult importResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (progress.isShowing()) {
                    progress.dismiss();
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (importResult.importedCount > 0) {
                    String message = "已导入 " + importResult.importedCount + " 个内置视频。";
                    if (importResult.hasErrors()) {
                        message += "部分文件未能导入。";
                    }
                    BulletinFactory.of(AiqiyiVideoZoneEntry.this).createSimpleBulletin(R.raw.done, message).show();
                } else {
                    String message = importResult.errors.isEmpty() ? "所选视频无法导入，请更换视频后重试。" : importResult.errors.get(0);
                    showVideoInfo("导入失败", message);
                }
            });
        });
    }

    private void showVideoInfo(String title, String message) {
        if (getParentActivity() == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.OK), null)
                .create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_AIQIYI_BUILTIN_VIDEOS && resultCode == Activity.RESULT_OK) {
            ArrayList<Uri> sources = new ArrayList<>();
            if (data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null && !sources.contains(uri)) {
                            sources.add(uri);
                        }
                    }
                }
                Uri singleUri = data.getData();
                if (singleUri != null && !sources.contains(singleUri)) {
                    sources.add(singleUri);
                }
            }
            importBuiltinVideos(sources);
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == enabledRow) {
            cfg().setHuanghunBuiltinCameraEnabled(!cfg().getHuanghunBuiltinCameraEnabled());
            SettingsHelper.updateCheckState(view, cfg().getHuanghunBuiltinCameraEnabled());
            listView.adapter.update(true);
        } else if (item.id == pickVideosRow) {
            chooseBuiltinVideos();
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