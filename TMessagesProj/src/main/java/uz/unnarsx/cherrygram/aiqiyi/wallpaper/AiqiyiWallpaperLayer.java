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
 * 壁纸从下往上透出来，形成全屏效果。
 *
 * ===== 为什么不用 onAttachedToWindow 里取一次壁纸 =====
 * Theme.loadWallpaper(true) 是异步的：先 post 到 themeQueue 上解码，
 * 解码完成才把 themedWallpaper 赋值。LaunchActivity 里调用它的下一行
 * 就去取壁纸，此时必然还是 null，取一次 = 永远空白。
 *
 * 所以改成：
 *   1. 每次 onDraw 都重新比对当前壁纸引用，有变化立刻换
 *   2. 还没拿到壁纸时，用 postInvalidateOnAnimation 持续重试（限次数防死循环）
 *   3. 拿到动态壁纸后每帧重绘驱动动画
 */
public class AiqiyiWallpaperLayer extends View {

    /** 未拿到壁纸时的重试上限，约 60 帧 ≈ 1 秒；之后改由外部事件触发刷新 */
    private static final int MAX_PENDING_RETRY = 60;

    private Drawable appliedWallpaper;
    private boolean motion;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int pendingRetry = 0;

    public AiqiyiWallpaperLayer(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    /** 外部主动刷新（开关切换、切页、onResume 时调用） */
    public void updateWallpaper() {
        appliedWallpaper = null;      // 强制 onDraw 重新比对
        pendingRetry = MAX_PENDING_RETRY;
        lastWidth = -1;
        invalidate();
    }

    public boolean hasWallpaper() {
        return Theme.getCachedWallpaperNonBlocking() != null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable d = Theme.getCachedWallpaperNonBlocking();

        if (d == null) {
            // 壁纸还在异步加载，继续等一下
            if (pendingRetry > 0) {
                pendingRetry--;
                postInvalidateOnAnimation();
            }
            return;
        }

        pendingRetry = MAX_PENDING_RETRY;
        motion = Theme.isWallpaperMotion();

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // 壁纸换了 / 尺寸变了，重设 bounds
        if (d != appliedWallpaper || w != lastWidth || h != lastHeight) {
            appliedWallpaper = d;
            lastWidth = w;
            lastHeight = h;
            d.setBounds(0, 0, w, h);
        }

        d.draw(canvas);

        if (motion) {
            // 动态壁纸需要持续重绘才能跑动画
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setLayerType(LAYER_TYPE_HARDWARE, null);
        updateWallpaper();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            updateWallpaper();
        }
    }
}