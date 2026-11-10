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
public class AiqiyiWallpaperLayer extends android.widget.FrameLayout {

    /** 未拿到壁纸时的重试上限，约 60 帧 ≈ 1 秒；之后改由外部事件触发刷新 */
    private static final int MAX_PENDING_RETRY = 60;

    /**
     * [Aiqiyi] 动态壁纸：复用黄昏的 DynamicVideoWallpaperHelper.Player。
     * 它已经处理了三件本类原实现没做的事：
     *   1. onVideoSizeChanged 里 setDefaultBufferSize(视频宽高)
     *      —— 否则 Surface 层先 cover 缩放，再被 Matrix 缩放一次，只剩视频局部
     *   2. onLayoutChange 里 applyFitCenter + setTransform(new Matrix())
     *   3. alpha 0 -> 0.96 淡入，未就绪时不会露黑帧
     */
    private tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper.Player videoPlayer;
    private String videoPath;
    private int videoAccount = Integer.MIN_VALUE;
    private boolean videoReady;

    private Drawable appliedWallpaper;
    private boolean motion;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int pendingRetry = 0;

    public AiqiyiWallpaperLayer(Context context) {
        super(context);
        setWillNotDraw(false);
        // 视频子层由 DynamicVideoWallpaperHelper.attach() 在 setVideoPath 时加入，
        // 这里不自建 TextureView。
    }

    /**
     * [Aiqiyi] 设置全局动态壁纸视频。传 null 表示关掉动态壁纸，回到静态壁纸。
     * 视频层是本类唯一的子 View，铺满整层；静态壁纸由 onDraw 画，
     * 两者不会同时出现：视频就绪后 onDraw 直接返回不再画静态壁纸。
     */
    public void setVideoPath(String path) {
        setVideo(path, -1);
    }

    /**
     * [Aiqiyi] 设置动态壁纸。
     *
     * @param path    视频路径，null 表示关闭
     * @param account 取路径时用的账号；attach() 内部会用同样的 account 再取一次，
     *                所以这里必须传与 getVideoPath 一致的值，否则 attach 会返回 null。
     */
    public void setVideo(String path, int account) {
        final boolean samePath = android.text.TextUtils.equals(path, videoPath)
                && account == videoAccount;
        // 只有「路径相同且播放器还活着」才跳过，
        // 避免路径已写入但视频没起来后每次调用都被短路
        if (samePath && (path == null || (videoPlayer != null && !videoPlayer.isReleased()))) {
            return;
        }
        videoPath = path;
        videoAccount = account;
        releaseVideo();
        if (android.text.TextUtils.isEmpty(path) || account < 0) {
            invalidate();
            return;
        }
        try {
            // attach 是 public 的；它自己 new 一个 FrameLayout 加进本层并返回 Player
            videoPlayer = tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper
                    .attach(this, getContext(), account, 0L);
            if (videoPlayer != null) {
                videoReady = true;
            } else {
                org.telegram.messenger.FileLog.e(
                        "AiqiyiWallpaperLayer: attach returned null, path=" + path
                                + " account=" + account);
            }
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            releaseVideo();
        }
        invalidate();
        requestLayout();
    }

    private void releaseVideo() {
        videoReady = false;
        try {
            if (videoPlayer != null) {
                videoPlayer.release();
            }
        } catch (Throwable ignored) {
        }
        videoPlayer = null;
    }

    /** 外部主动刷新（开关切换、切页、onResume 时调用） */
    public void updateWallpaper() {
        appliedWallpaper = null;      // 强制 onDraw 重新比对
        pendingRetry = MAX_PENDING_RETRY;
        lastWidth = -1;
        invalidate();
    }

    public boolean hasWallpaper() {
        return videoPlayer != null || Theme.getCachedWallpaperNonBlocking() != null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 有视频时不再画静态壁纸，否则会盖在 TextureView 子层上面
        if (videoPath != null) {
            return;
        }
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
        // 不能用 LAYER_TYPE_HARDWARE：TextureView 需要 SurfaceFlinger 单独合成，
        // 一旦被塞进 RenderNode 就会渲染成空白，vivo/iQOO 上尤其明显。
        setLayerType(LAYER_TYPE_NONE, null);
        updateWallpaper();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseVideo();
        super.onDetachedFromWindow();
    }

    @Override
    public void setVisibility(int visibility) {
        final int old = getVisibility();
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            updateWallpaper();
            if (old != VISIBLE && videoPath != null) {
                // 从 GONE 转 VISIBLE：子 View 之前没有测量信息，重新走一遍布局
                requestLayout();
            }
        }
    }

}