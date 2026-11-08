/*
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 */

package uz.unnarsx.cherrygram.aiqiyi.callzone;

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
import org.telegram.messenger.voip.HuanghunVirtualCameraCapturer;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import uz.unnarsx.cherrygram.core.configs.CherrygramCameraConfig;
import uz.unnarsx.cherrygram.core.huanghun.HuanghunCallVideoLibraryActivity;
import uz.unnarsx.cherrygram.core.huanghun.HuanghunCallVideoLibraryHelper;
import uz.unnarsx.cherrygram.preferences.BaseCGPreferencesEntry;
import uz.unnarsx.cherrygram.preferences.helpers.SettingsHelper;

/**
 * 爱琦艺客户端 - 通话专区 设置页。
 *
 * 通话虚拟摄像头的视频文件使用独立的账号隔离库，绝不会读取或修改视频录制专区的视频列表。
 */
public class AiqiyiCallZoneEntry extends BaseCGPreferencesEntry {

    private final int defaultCameraRow = 620;
    private final int virtualCameraRow = 621;
    private final int pickCallVideosRow = 622;
    private final int callVideoSoundRow = 623;
    private final int viewCallVideosRow = 624;
    // [Aiqiyi] 删除入口：进入 deleteMode=true 的视频库页面，带勾选与删除按钮。
    private final int deleteCallVideosRow = 625;

    private static final int REQUEST_AIQIYI_CALL_VIDEOS = 0x5A72;

    @Override
    protected CharSequence getTitle() {
        return "通话专区";
    }

    @Override
    protected String getKey() {
        return "aqy_call_zone";
    }

    private CherrygramCameraConfig cfg() {
        return CherrygramCameraConfig.INSTANCE;
    }

    private int callVideoCount() {
        try {
            return HuanghunCallVideoLibraryHelper.getVideoCount(ApplicationLoader.applicationContext, currentAccount);
        } catch (Throwable e) {
            return 0;
        }
    }

    private String defaultCameraName() {
        switch (cfg().getHuanghunCallDefaultCamera()) {
            case CherrygramCameraConfig.HuanghunCallCamFront:
                return "前置摄像头";
            case CherrygramCameraConfig.HuanghunCallCamScreen:
                return "手机屏幕";
            case CherrygramCameraConfig.HuanghunCallCamRear:
            default:
                return "后置摄像头";
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("通话设置"));
        items.add(SettingsHelper.asTextDetail(defaultCameraRow, 0,
                        "通话默认摄像头",
                        defaultCameraName())
                .slug("aqy_call_default_camera")
        );
        items.add(UItem.asShadow("默认摄像头用于关闭虚拟摄像头时的新视频通话。选择“手机屏幕”时，开启视频会进入 Telegram 官方屏幕共享授权流程。"));

        boolean on = cfg().getHuanghunCallVirtualCameraEnabled();
        items.add(UItem.asHeader("虚拟摄像头配置"));
        items.add(SettingsHelper.asSwitchCG(virtualCameraRow,
                        "启动内置虚拟摄像头",
                        "开启后，视频通话中打开摄像头将播放本地视频。")
                .setChecked(on)
                .slug("aqy_call_virtual_camera")
        );
        if (on) {
            items.add(SettingsHelper.asTextDetail(pickCallVideosRow, 0,
                            "选取内置视频",
                            callVideoCount() == 0 ? "未选取" : "已选取 " + callVideoCount() + " 个")
                    .slug("aqy_call_pick_videos")
            );
            items.add(SettingsHelper.asSwitchCG(callVideoSoundRow,
                            "视频声音",
                            "关闭后不会向通话上行混入内置视频原声。")
                    .setChecked(cfg().getHuanghunCallVirtualVideoSound())
                    .slug("aqy_call_video_sound")
            );
            items.add(SettingsHelper.asTextDetail(viewCallVideosRow, 0,
                            "查看内置视频",
                            "管理已导入的通话内置视频")
                    .slug("aqy_call_view_videos")
            );
            items.add(SettingsHelper.asTextDetail(deleteCallVideosRow, 0,
                            "删除内置视频",
                            "勾选并删除已导入的视频")
                    .slug("aqy_call_delete_videos")
            );
        }
        items.add(UItem.asShadow("通话内置视频仅保存在当前设备和当前账号中，与视频录制专区完全隔离。开启虚拟摄像头并在视频通话中开启摄像头后，将循环播放所选视频；通话界面会显示上一个、下一个、暂停和播放控制按钮。视频会保持原始宽高比例，自适应通话画面。"));
    }

    private void showDefaultCameraChooser() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] cameras = new String[]{"前置摄像头", "后置摄像头", "手机屏幕"};
        int selected = cfg().getHuanghunCallDefaultCamera();
        if (selected < 0 || selected >= cameras.length) {
            selected = CherrygramCameraConfig.HuanghunCallCamRear;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle("通话默认摄像头");
        builder.setItems(cameras, (dialog, which) -> {
            if (which >= 0 && which < cameras.length) {
                cfg().setHuanghunCallDefaultCamera(which);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }
        });
        showDialog(builder.create());
    }

    private void chooseCallVideos() {
        if (getParentActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_AIQIYI_CALL_VIDEOS);
        } catch (Throwable e) {
            FileLog.e(e);
            showCallInfo("无法选择视频", "无法打开系统视频选择器，请稍后重试。");
        }
    }

    private void importCallVideos(ArrayList<Uri> sources) {
        if (sources == null || sources.isEmpty() || getParentActivity() == null) {
            showCallInfo("导入失败", "未读取到所选视频，请重新选择。");
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage("正在保存并验证通话内置视频，请稍候。");
        progress.setCancelable(false);
        showDialog(progress);
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            HuanghunCallVideoLibraryHelper.ImportResult result;
            try {
                result = HuanghunCallVideoLibraryHelper.importVideos(ApplicationLoader.applicationContext, account, sources);
            } catch (Throwable e) {
                FileLog.e(e);
                ArrayList<String> errors = new ArrayList<>();
                String message = e.getMessage();
                errors.add(message == null || message.length() == 0 ? "视频导入失败，请稍后重试。" : message);
                result = new HuanghunCallVideoLibraryHelper.ImportResult(0, errors);
            }
            final HuanghunCallVideoLibraryHelper.ImportResult importResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (progress.isShowing()) {
                    progress.dismiss();
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (importResult.importedCount > 0) {
                    String message = "已导入 " + importResult.importedCount + " 个通话内置视频。";
                    if (importResult.hasErrors()) {
                        message += "部分文件未能导入。";
                    }
                    BulletinFactory.of(AiqiyiCallZoneEntry.this).createSimpleBulletin(R.raw.done, message).show();
                } else {
                    String message = importResult.errors.isEmpty() ? "所选视频无法导入，请更换视频后重试。" : importResult.errors.get(0);
                    showCallInfo("导入失败", message);
                }
            });
        });
    }

    private void showCallInfo(String title, String message) {
        if (getParentActivity() == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(ApplicationLoader.applicationContext.getString(R.string.OK), null)
                .create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_AIQIYI_CALL_VIDEOS && resultCode == Activity.RESULT_OK) {
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
            importCallVideos(sources);
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == defaultCameraRow) {
            showDefaultCameraChooser();
        } else if (item.id == virtualCameraRow) {
            boolean enabled = !cfg().getHuanghunCallVirtualCameraEnabled();
            cfg().setHuanghunCallVirtualCameraEnabled(enabled);
            SettingsHelper.updateCheckState(view, enabled);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (enabled && callVideoCount() == 0) {
                showCallInfo("请先选取视频", "已开启内置虚拟摄像头。请先点击“选取内置视频”导入至少一个视频；下次开启视频通话摄像头时才会播放本地视频。\n\n关闭此开关后，通话会完全恢复 Telegram 官方前置、后置摄像头或手机屏幕共享流程。");
            }
        } else if (item.id == pickCallVideosRow) {
            chooseCallVideos();
        } else if (item.id == callVideoSoundRow) {
            boolean enabled = !cfg().getHuanghunCallVirtualVideoSound();
            cfg().setHuanghunCallVirtualVideoSound(enabled);
            HuanghunVirtualCameraCapturer.refreshSoundState();
            SettingsHelper.updateCheckState(view, enabled);
        } else if (item.id == viewCallVideosRow) {
            presentFragment(new HuanghunCallVideoLibraryActivity(false));
        } else if (item.id == deleteCallVideosRow) {
            presentFragment(new HuanghunCallVideoLibraryActivity(true));
        } else {
            super.onClick(item, view, position, x, y);
        }
    }
}