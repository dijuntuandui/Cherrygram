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

    /** [Aiqiyi] 动态壁纸状态 */
    private android.view.TextureView videoView;
    private android.media.MediaPlayer videoPlayer;
    private android.view.Surface videoSurface;
    private String videoPath;
    private boolean videoReady;

    private Drawable appliedWallpaper;
    private boolean motion;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int pendingRetry = 0;

    public AiqiyiWallpaperLayer(Context context) {
        super(context);
        setWillNotDraw(false);

        // [Aiqiyi] 动态壁纸子层：铺满整个壁纸层，位于静态壁纸之下绘制顺序之外，
        // 视频就绪后由 onDraw 直接跳过静态壁纸，由 TextureView 显示画面。
        videoView = new android.view.TextureView(context);
        videoView.setOpaque(false);
        videoView.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) {
                if (w <= 0 || h <= 0) {
                    return;
                }
                startVideo(st);
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) {
                if (w > 0 && h > 0 && videoPlayer == null && videoPath != null) {
                    startVideo(st);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                releaseVideo();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {
            }
        });
        addView(videoView, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * [Aiqiyi] 设置全局动态壁纸视频。传 null 表示关掉动态壁纸，回到静态壁纸。
     * 视频层是本类唯一的子 View，铺满整层；静态壁纸由 onDraw 画，
     * 两者不会同时出现：视频就绪后 onDraw 直接返回不再画静态壁纸。
     */
    public void setVideoPath(String path) {
        final boolean samePath = android.text.TextUtils.equals(path, videoPath);
        if (samePath && (path == null || videoPlayer != null)) {
            return;
        }
        videoPath = path;
        releaseVideo();
        if (path == null) {
            videoReady = false;
            videoView.setVisibility(GONE);
            invalidate();
            return;
        }
        android.graphics.SurfaceTexture st = videoView.getSurfaceTexture();
        if (st != null && videoView.getWidth() > 0 && videoView.getHeight() > 0) {
            startVideo(st);
        } else {
            videoView.requestLayout();
        }
    }

    private void startVideo(android.graphics.SurfaceTexture st) {
        final String path = videoPath;
        if (path == null) {
            return;
        }
        try {
            videoSurface = new android.view.Surface(st);
            videoPlayer = new android.media.MediaPlayer();
            videoPlayer.setDataSource(path);
            videoPlayer.setSurface(videoSurface);
            videoPlayer.setVideoScalingMode(
                    android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            videoPlayer.setLooping(true);
            videoPlayer.setVolume(0f, 0f);
            videoPlayer.setOnPreparedListener(mp -> {
                videoReady = true;
                videoView.setVisibility(VISIBLE);
                invalidate();
                try {
                    mp.start();
                } catch (Throwable ignored) {
                }
            });
            videoPlayer.prepareAsync();
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            releaseVideo();
        }
    }

    private void releaseVideo() {
        videoReady = false;
        try {
            if (videoPlayer != null) {
                videoPlayer.stop();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (videoPlayer != null) {
                videoPlayer.release();
            }
        } catch (Throwable ignored) {
        }
        videoPlayer = null;
        try {
            if (videoSurface != null) {
                videoSurface.release();
            }
        } catch (Throwable ignored) {
        }
        videoSurface = null;
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
        // [Aiqiyi] 动态壁纸已就绪时由 TextureView 子层显示画面，
        // 这里必须直接返回，否则后画的静态壁纸会盖住视频。
        if (videoReady) {
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
        setLayerType(LAYER_TYPE_HARDWARE, null);
        updateWallpaper();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (videoPath == null || videoPlayer != null) {
            return;
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        android.graphics.SurfaceTexture st = videoView.getSurfaceTexture();
        if (st != null) {
            startVideo(st);
        }
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
            if (old != VISIBLE) {
                requestLayout();
                post(this::tryStartVideoIfReady);
            }
        }
    }

    private void tryStartVideoIfReady() {
        if (videoPath == null || videoPlayer != null) {
            return;
        }
        if (videoView.getWidth() <= 0 || videoView.getHeight() <= 0) {
            return;
        }
        android.graphics.SurfaceTexture st = videoView.getSurfaceTexture();
        if (st != null) {
            startVideo(st);
        }
    }
}