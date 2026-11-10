package uz.unnarsx.cherrygram.aiqiyi.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

/**
 * [Aiqiyi] 全屏壁纸层
 *
 * 铺在 LaunchActivity 根布局最底部（index 0），作为整个 App 的底图。
 * 上层各页面的 fragmentView 在「全屏壁纸」开启时被设成透明，
 * 于是壁纸从下往上透出来，形成全屏效果。
 *
 * 支持：
 *   静态壁纸 —— Theme 缓存里的 BitmapDrawable / 渐变 / 纯色
 *   动态壁纸 —— MotionBackgroundDrawable，需持续 invalidate 驱动动画
 *
 * 个人资料页不加壁纸：ProfileActivity 是 hasOwnBackground = true，
 * 不会进入 ActionBarLayout 的透明化分支，它自己会铺底色盖住壁纸。
 */
public class AiqiyiWallpaperLayer extends View {

    private Drawable wallpaper;
    private boolean motion;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public AiqiyiWallpaperLayer(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    /** 刷新壁纸引用。换壁纸 / 切主题后需要重新调用 */
    public void updateWallpaper() {
        Drawable d = Theme.getCachedWallpaperNonBlocking();
        if (d != this.wallpaper) {
            this.wallpaper = d;
        }
        this.motion = Theme.isWallpaperMotion();
        lastWidth = -1; // 强制重新设置 bounds
        invalidate();
    }

    public boolean hasWallpaper() {
        return wallpaper != null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable d = wallpaper;
        if (d == null) {
            return;
        }
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w != lastWidth || h != lastHeight) {
            lastWidth = w;
            lastHeight = h;
            // 壁纸按视差通常比屏幕高，这里铺满整个可视区域
            d.setBounds(0, 0, w, h);
        }
        d.draw(canvas);

        if (motion) {
            // 动态壁纸需要持续重绘驱动动画
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateWallpaper();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            updateWallpaper();
        }
    }
}