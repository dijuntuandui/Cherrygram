package uz.unnarsx.cherrygram.core.huanghun;

import android.content.Context;
import android.graphics.Color;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

/**
 * 视频专区页面共用的透明根布局：透出当前聊天壁纸，无壁纸时回退主题灰底。
 * <p>
 * 这是黄昏 {@code HuanghunWallpaperLayout} 的精简版，去掉了动态视频壁纸播放器
 * （爱琦艺不带那套 DynamicVideoWallpaperHelper），只保留静态壁纸与主题回退，
 * 因此不依赖任何外部壁纸模块。
 */
public class HuanghunWallpaperLayout extends SizeNotifierFrameLayout {

    public HuanghunWallpaperLayout(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshWallpaper();
    }

    @Override
    protected boolean isActionBarVisible() {
        // 本页面自己负责 ActionBar 后方的绘制；无壁纸时由 refreshWallpaper() 提供主题底色。
        return false;
    }

    @Override
    protected boolean isStatusBarVisible() {
        return false;
    }

    /** 按当前壁纸刷新背景：有壁纸用透明，无壁纸回退主题灰底。 */
    public void refreshWallpaper() {
        try {
            setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            boolean hasWallpaper = Theme.getCachedWallpaper() != null;
            setBackgroundColor(hasWallpaper ? Color.TRANSPARENT : Theme.getColor(Theme.key_windowBackgroundGray));
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }
    }

    /** 壁纸变更后在 UI 线程刷新，供外部调用。 */
    public void refreshWallpaperAsync() {
        AndroidUtilities.runOnUIThread(this::refreshWallpaper);
    }
}