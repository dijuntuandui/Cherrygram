package org.telegram.ui.Components;

import static android.graphics.Canvas.ALL_SAVE_FLAG;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

public class BlurBehindDrawable {

    DispatchQueue queue;

    private final int type;
    public static final int TAG_DRAWING_AS_BACKGROUND = (1 << 26) + 3;

    public static final int STATIC_CONTENT = 0;
    public static final int ADJUST_PAN_TRANSLATION_CONTENT = 1;

    private View behindView;
    private View parentView;

    private Bitmap[] blurredBitmapTmp;
    private Bitmap[] backgroundBitmap;
    private Bitmap[] renderingBitmap;
    private Canvas[] renderingBitmapCanvas;
    private Canvas[] backgroundBitmapCanvas;
    private Canvas[] blurCanvas;

    private boolean processingNextFrame;
    private boolean invalidate = true;

    private float blurAlpha;
    private boolean show;
    private boolean error;
    private boolean animateAlpha = true;

    private final float DOWN_SCALE = 15f;
    private int lastH;
    private int lastW;
    private int toolbarH;

    private boolean wasDraw;
    private boolean skipDraw;

    private float panTranslationY;

    BlurBackgroundTask blurBackgroundTask = new BlurBackgroundTask();

    Paint emptyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    Paint errorBlackoutPaint = new Paint();
    private final Theme.ResourcesProvider resourcesProvider;

    public BlurBehindDrawable(View behindView, View parentView, int type, Theme.ResourcesProvider resourcesProvider) {
        this.type = type;
        this.behindView = behindView;
        this.parentView = parentView;
        this.resourcesProvider = resourcesProvider;
        errorBlackoutPaint.setColor(Color.BLACK);
    }

    public void draw(Canvas canvas) {
        // [Aiqiyi FIX-BLUR] 原写法 `a == 0 && b == 0` 受运算符优先级影响，
        // 只在宽高同时为 0 时才拦截。宽度为 0、高度非 0 时会漏过去，
        // 到后面 canvas.scale(measuredW / bitmapW, ...) 算成 0，画面被压成零宽而"消失"。
        // 改为单边为 0 即跳过绘制。
        if (parentView == null
                || parentView.getMeasuredHeight() <= 0
                || parentView.getMeasuredWidth() <= 0) {
            return;
        }
        if (type == 1 && !wasDraw && !animateAlpha) {
            generateBlurredBitmaps();
            invalidate = false;
        }
        final Bitmap[] bitmap = renderingBitmap;
        if ((bitmap != null || error) && animateAlpha) {
            if (show && blurAlpha != 1f) {
                blurAlpha += 0.09f;
                if (blurAlpha > 1f) {
                    blurAlpha = 1f;
                }
                parentView.invalidate();
            } else if (!show && blurAlpha != 0) {
                blurAlpha -= 0.09f;
                if (blurAlpha < 0) {
                    blurAlpha = 0f;
                }
                parentView.invalidate();
            }
        }

        float alpha = animateAlpha ? blurAlpha : 1f;
        if (bitmap == null && error) {
            errorBlackoutPaint.setAlpha((int) (50 * alpha));
            canvas.drawPaint(errorBlackoutPaint);
            return;
        }

        if (alpha == 1f) {
            canvas.save();
        } else {
            canvas.saveLayerAlpha(0, 0, parentView.getMeasuredWidth(), parentView.getMeasuredHeight(), (int) (alpha * 255), ALL_SAVE_FLAG);
        }
        if (bitmap != null) {
            emptyPaint.setAlpha((int) (255 * alpha));
            if (type == 1) {
                canvas.translate(0, panTranslationY);
            }
            // [Aiqiyi FIX-BLUR] 双缓冲交换后 bitmap 的尺寸是异步任务提交那刻的快照，
            // 可能与视图当前尺寸不一致；若任一宽度为 0，canvas.scale 会得到 0/Infinity，
            // 表现就是毛玻璃横向拉丝或整体消失。先做尺寸合法性检查再画。
            final int aiqiyiViewW = parentView.getMeasuredWidth();
            final int aiqiyiViewH = parentView.getMeasuredHeight();
            if (bitmap[1] != null && bitmap[1].getWidth() > 0 && bitmap[1].getHeight() > 0
                    && aiqiyiViewW > 0 && aiqiyiViewH > 0) {
                canvas.save();
                canvas.scale(aiqiyiViewW / (float) bitmap[1].getWidth(), aiqiyiViewH / (float) bitmap[1].getHeight());
                canvas.drawBitmap(bitmap[1], 0, 0, emptyPaint);
                canvas.restore();
            }
            if (bitmap[0] != null && bitmap[0].getWidth() > 0 && bitmap[0].getHeight() > 0
                    && aiqiyiViewW > 0 && toolbarH > 0) {
                canvas.save();
                if (type == 0) {
                    canvas.translate(0, panTranslationY);
                }
                canvas.scale(aiqiyiViewW / (float) bitmap[0].getWidth(), toolbarH / (float) bitmap[0].getHeight());
                canvas.drawBitmap(bitmap[0], 0, 0, emptyPaint);
                canvas.restore();
            }
            wasDraw = true;

            canvas.drawColor(0x1a000000);
        }
        canvas.restore();

        if (show && !processingNextFrame && (renderingBitmap == null || invalidate)) {
            processingNextFrame = true;
            invalidate = false;
            if (blurredBitmapTmp == null) {
                blurredBitmapTmp = new Bitmap[2];
                blurCanvas = new Canvas[2];
            }
            for (int i = 0; i < 2; i++) {
                if (blurredBitmapTmp[i] == null || parentView.getMeasuredWidth() != lastW || parentView.getMeasuredHeight() != lastH) {
                    int lastH = parentView.getMeasuredHeight();
                    int lastW = parentView.getMeasuredWidth();
                    toolbarH = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(200);
                    try {
                        int h = i == 0 ? toolbarH : lastH;
                        blurredBitmapTmp[i] = Bitmap.createBitmap((int) (lastW / DOWN_SCALE), (int) (h / DOWN_SCALE), Bitmap.Config.ARGB_8888);
                        blurCanvas[i] = new Canvas(blurredBitmapTmp[i]);
                    } catch (Exception e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> {
                            error = true;
                            parentView.invalidate();
                        });
                        return;
                    }
                } else {
                    blurredBitmapTmp[i].eraseColor(Color.TRANSPARENT);
                }
                if (i == 1) {
                    blurredBitmapTmp[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                blurCanvas[i].save();
                blurCanvas[i].scale(1f / DOWN_SCALE, 1f / DOWN_SCALE, 0, 0);
                Drawable backDrawable = behindView.getBackground();
                if (backDrawable == null) {
                    backDrawable = getBackgroundDrawable();
                }
                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, i);
                if (i == STATIC_CONTENT) {
                    blurCanvas[i].translate(0, -panTranslationY);
                    behindView.draw(blurCanvas[i]);
                }

                if (backDrawable != null && i == ADJUST_PAN_TRANSLATION_CONTENT) {
                    Rect oldBounds = backDrawable.getBounds();
                    backDrawable.setBounds(0, 0, behindView.getMeasuredWidth(), behindView.getMeasuredHeight());
                    backDrawable.draw(blurCanvas[i]);
                    backDrawable.setBounds(oldBounds);
                    behindView.draw(blurCanvas[i]);
                }

                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, null);
                blurCanvas[i].restore();
            }

            lastH = parentView.getMeasuredHeight();
            lastW = parentView.getMeasuredWidth();

            blurBackgroundTask.width = parentView.getMeasuredWidth();
            blurBackgroundTask.height = parentView.getMeasuredHeight();
            if (blurBackgroundTask.width == 0 || blurBackgroundTask.height == 0) {
                processingNextFrame = false;
                return;
            }
            if (queue == null) {
                queue = new DispatchQueue("blur_thread_" + this);
            }
            queue.postRunnable(blurBackgroundTask);
        }
    }

    private int getBlurRadius() {
        return 15; // Math.max(7, Math.max(lastH, lastW) / 180);
    }

    public void clear() {
        invalidate = true;
        wasDraw = false;
        error = false;
        blurAlpha = 0;
        lastW = 0;
        lastH = 0;
        if (queue != null) {
            queue.cleanupQueue();
            queue.postRunnable(() -> {
                if (renderingBitmap != null) {
                    if (renderingBitmap[0] != null) {
                        renderingBitmap[0].recycle();
                    }
                    if (renderingBitmap[1] != null) {
                        renderingBitmap[1].recycle();
                    }
                    renderingBitmap = null;
                }
                if (backgroundBitmap != null) {
                    if (backgroundBitmap[0] != null) {
                        backgroundBitmap[0].recycle();
                    }
                    if (backgroundBitmap[1] != null) {
                        backgroundBitmap[1].recycle();
                    }
                    backgroundBitmap = null;
                }
                renderingBitmapCanvas = null;
                skipDraw = false;
                AndroidUtilities.runOnUIThread(() -> {
                    if (queue != null) {
                        queue.recycle();
                        queue = null;
                    }
                });
            });
        }
    }

    public void invalidate() {
        invalidate = true;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public boolean isFullyDrawing() {
        return !skipDraw && wasDraw && (blurAlpha == 1f || !animateAlpha) && show && parentView.getAlpha() == 1f;
    }

    public void checkSizes() {
        final Bitmap[] bitmap = renderingBitmap;
        if (bitmap == null || parentView.getMeasuredHeight() == 0 || parentView.getMeasuredWidth() == 0) {
            return;
        }
        generateBlurredBitmaps();

        lastH = parentView.getMeasuredHeight();
        lastW = parentView.getMeasuredWidth();
    }

    private void generateBlurredBitmaps() {
        Bitmap[] bitmap = renderingBitmap;
        if (bitmap == null) {
            bitmap = renderingBitmap = new Bitmap[2];
            renderingBitmapCanvas = new Canvas[2];
        }
        if (blurredBitmapTmp == null) {
            blurredBitmapTmp = new Bitmap[2];
            blurCanvas = new Canvas[2];
        }
        blurBackgroundTask.canceled = true;
        blurBackgroundTask = new BlurBackgroundTask();

        for (int i = 0; i < 2; i++) {
            int lastH = parentView.getMeasuredHeight();
            int lastW = parentView.getMeasuredWidth();
            toolbarH = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(200);
            int h = i == 0 ? toolbarH : lastH;

            // [Aiqiyi FIX-BLUR] 原判断只看 renderingBitmap(bitmap[i]) 的尺寸，
            // 但本块重建的是 blurredBitmapTmp[i] 以及 renderingBitmap[i]。
            // 若 renderingBitmap 尺寸刚好匹配、而 blurredBitmapTmp 是旧尺寸，
            // 该 if 会被跳过，导致 tmp 与渲染位图尺寸不一致，
            // 后续异步 scale 计算错配 -> 毛玻璃横向拉丝/错位。
            final int aiqiyiTargetW = (int) (lastW / DOWN_SCALE);
            final int aiqiyiTargetH = (int) (h / DOWN_SCALE);
            final Bitmap aiqiyiTmpCur = blurredBitmapTmp[i];
            final boolean aiqiyiTmpBad = aiqiyiTmpCur == null
                    || aiqiyiTmpCur.getWidth() != aiqiyiTargetW
                    || aiqiyiTmpCur.getHeight() != aiqiyiTargetH;
            if (bitmap[i] == null || bitmap[i].getHeight() != h
                    || bitmap[i].getWidth() != parentView.getMeasuredWidth()
                    || aiqiyiTmpBad) {
                if (queue != null) {
                    queue.cleanupQueue();
                }

                blurredBitmapTmp[i] = Bitmap.createBitmap((int) (lastW / DOWN_SCALE), (int) (h / DOWN_SCALE), Bitmap.Config.ARGB_8888);
                if (i == 1) {
                    blurredBitmapTmp[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                blurCanvas[i] = new Canvas(blurredBitmapTmp[i]);

                int bitmapH = (int) ((i == 0 ? toolbarH : lastH) / DOWN_SCALE);
                int bitmapW = (int) (lastW / DOWN_SCALE);
                renderingBitmap[i] = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
                renderingBitmapCanvas[i] = new Canvas(renderingBitmap[i]);
                renderingBitmapCanvas[i].scale((float) renderingBitmap[i].getWidth() / (float) blurredBitmapTmp[i].getWidth(), (float) renderingBitmap[i].getHeight() / (float) blurredBitmapTmp[i].getHeight());

                blurCanvas[i].save();
                blurCanvas[i].scale(1f / DOWN_SCALE, 1f / DOWN_SCALE, 0, 0);
                Drawable backDrawable = behindView.getBackground();
                if (backDrawable == null) {
                    backDrawable = getBackgroundDrawable();
                }
                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, i);
                if (i == STATIC_CONTENT) {
                    blurCanvas[i].translate(0, -panTranslationY);
                    behindView.draw(blurCanvas[i]);
                }

                if (i == ADJUST_PAN_TRANSLATION_CONTENT) {
                    Rect oldBounds = backDrawable.getBounds();
                    backDrawable.setBounds(0, 0, behindView.getMeasuredWidth(), behindView.getMeasuredHeight());
                    backDrawable.draw(blurCanvas[i]);
                    backDrawable.setBounds(oldBounds);
                    behindView.draw(blurCanvas[i]);
                }

                behindView.setTag(TAG_DRAWING_AS_BACKGROUND, null);
                blurCanvas[i].restore();

                Utilities.stackBlurBitmap(blurredBitmapTmp[i], getBlurRadius());
                emptyPaint.setAlpha(255);
                if (i == 1) {
                    renderingBitmap[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                }
                renderingBitmapCanvas[i].drawBitmap(blurredBitmapTmp[i], 0, 0, emptyPaint);
            }
        }
    }

    public void show(boolean show) {
        this.show = show;
    }

    public void setAnimateAlpha(boolean animateAlpha) {
        this.animateAlpha = animateAlpha;
    }

    public void onPanTranslationUpdate(float y) {
        panTranslationY = y;
        parentView.invalidate();
    }

    public class BlurBackgroundTask implements Runnable {

        boolean canceled;
        int width;
        int height;

        @Override
        public void run() {
            if (backgroundBitmap == null) {
                backgroundBitmap = new Bitmap[2];
                backgroundBitmapCanvas = new Canvas[2];
            }
            int bitmapWidth = (int) (width / DOWN_SCALE);
            for (int i = 0; i < 2; i++) {
                int h = (int) ((i == 0 ? toolbarH : height) / DOWN_SCALE);

                if (backgroundBitmap[i] != null && (backgroundBitmap[i].getHeight() != h || backgroundBitmap[i].getWidth() != bitmapWidth)) {
                    if (backgroundBitmap[i] != null) {
                        backgroundBitmap[i].recycle();
                        backgroundBitmap[i] = null;
                    }
                }
                long t = System.currentTimeMillis();
                if (backgroundBitmap[i] == null) {
                    int w = bitmapWidth;
                    try {
                        backgroundBitmap[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        backgroundBitmapCanvas[i] = new Canvas(backgroundBitmap[i]);
                        // [Aiqiyi FIX-BLUR] 原代码直接用 bitmapWidth/width 与 blurredBitmapTmp 比，
                        // 二者可能来自不同时刻（width 是 run() 执行时读的字段，tmp 是提交时建的），
                        // 且 blurredBitmapTmp[i] 可能为 null 或宽度为 0，除零得到 Infinity
                        // 会直接变成“拉丝/错位”。这里统一改用 tmp 的真实尺寸反推，
                        // 保证 scale 恒等于“把 tmp 铺满目标位图”。
                        final Bitmap aiqiyiTmp = blurredBitmapTmp[i];
                        final float aiqiyiTmpW = (aiqiyiTmp != null) ? aiqiyiTmp.getWidth() : 0;
                        final float aiqiyiTmpH = (aiqiyiTmp != null) ? aiqiyiTmp.getHeight() : 0;
                        if (aiqiyiTmpW > 0 && aiqiyiTmpH > 0 && w > 0 && h > 0) {
                            backgroundBitmapCanvas[i].scale(w / aiqiyiTmpW, h / aiqiyiTmpH);
                        } else {
                            // 尺寸不可用，直接跳过本次绘制，避免 Infinity / 0 缩放
                            backgroundBitmapCanvas[i] = null;
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                        backgroundBitmapCanvas[i] = null;
                    }
                }
                // [Aiqiyi FIX-BLUR] canvas 因尺寸非法被置 null 时，位图内容不可用，
                // 直接回收并置空，让下一帧重新进入创建分支，避免长期输出空白玻璃。
                if (backgroundBitmapCanvas[i] == null) {
                    if (backgroundBitmap[i] != null && !backgroundBitmap[i].isRecycled()) {
                        backgroundBitmap[i].recycle();
                    }
                    backgroundBitmap[i] = null;
                    if (canceled) {
                        return;
                    }
                    continue;
                }
                if (i == 1) {
                    backgroundBitmap[i].eraseColor(getThemedColor(Theme.key_windowBackgroundWhite));
                } else {
                    backgroundBitmap[i].eraseColor(Color.TRANSPARENT);
                }
                emptyPaint.setAlpha(255);
                Utilities.stackBlurBitmap(blurredBitmapTmp[i], getBlurRadius());

                if (backgroundBitmapCanvas[i] != null) {
                    backgroundBitmapCanvas[i].drawBitmap(blurredBitmapTmp[i], 0, 0, emptyPaint);
                }

                if (canceled) {
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (canceled) {
                    return;
                }
                Bitmap[] bitmap = renderingBitmap;
                Canvas[] canvas = renderingBitmapCanvas;

                renderingBitmap = backgroundBitmap;
                renderingBitmapCanvas = backgroundBitmapCanvas;

                backgroundBitmap = bitmap;
                backgroundBitmapCanvas = canvas;

                processingNextFrame = false;
                if (parentView != null) {
                    parentView.invalidate();
                }
            });
        }
    }

    private Drawable getBackgroundDrawable() {
        return (resourcesProvider instanceof ChatActivity.ThemeDelegate)
                ? ((ChatActivity.ThemeDelegate) resourcesProvider).getWallpaperDrawable()
                : Theme.getCachedWallpaperNonBlocking();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
