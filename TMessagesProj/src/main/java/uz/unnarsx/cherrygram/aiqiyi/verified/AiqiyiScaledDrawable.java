package uz.unnarsx.cherrygram.aiqiyi.verified;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * [Aiqiyi] 尺寸约束包装器
 *
 * 用途：把任意 Drawable（尤其是固定像素的 PNG）约束到指定边长，
 * 解决 SimpleTextView 严格按 getIntrinsicWidth 设 bounds 导致
 * 「图标比名字还大」的问题。
 *
 * 为什么不直接用 ScaleDrawable：
 * ScaleDrawable 的 intrinsic 尺寸是 -1，SimpleTextView 拿到 -1
 * 会让布局计算出错。这里自己做，intrinsic 明确返回目标尺寸。
 */
public class AiqiyiScaledDrawable extends Drawable {

    private final Drawable inner;
    private final int size;
    private final Rect rect = new Rect();

    public AiqiyiScaledDrawable(Drawable inner, int sizePx) {
        this.inner = inner;
        this.size = Math.max(1, sizePx);
    }

    @Override
    public void draw(Canvas canvas) {
        rect.set(getBounds());
        if (rect.isEmpty()) {
            // 兜底：没给 bounds 时用 intrinsic 从原点画
            rect.set(0, 0, size, size);
        }
        inner.setBounds(rect);
        inner.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        inner.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        inner.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}