package uz.unnarsx.cherrygram.aiqiyi.verified;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * [Aiqiyi] 认证角标（官方样式矢量复刻）
 *
 * 浅蓝圆润八角星外框 + 中心四角星透明镂空
 *
 * ============================================================
 * 几何数据来自官方素材逐像素扫描 + 傅里叶级数最小二乘拟合
 * ============================================================
 *
 * 外框八角星，沿角度扫描蓝色边界，r(θ) 拟合为：
 *   r = 0.9012 + 0.0899*cos(8θ) + 0.0066*cos(16θ)
 * 拟合最大误差 0.0062（相对半径），16 个采样点全部贴合。
 *   尖峰 0/45/90/... 度 -> 0.998
 *   凹谷 22.5/67.5/... 度 -> 0.818
 *
 * 中心四角星，同样扫描拟合：
 *   r = 0.3284 + 0.1119*cos(4θ) + 0.0412*cos(8θ)
 *           + 0.0163*cos(12θ) + 0.0065*cos(16θ)
 * 拟合最大误差 0.0057。
 *   尖端 0/90/180/270 度 -> 0.504
 *   凹口 45/135/... 度   -> 0.248
 *
 * 为什么用极坐标而不是贝塞尔：
 * 之前用「尖-谷」两段二次贝塞尔拼接，控制点系数靠猜，
 * 结果尖角发硬、凹谷不够深，整体看着不平整。
 * 极坐标傅里叶级数是解析式，天然 C-infinity 光滑，
 * 只要采样够密就能 1:1 还原官方轮廓。
 *
 * 镂空用 Path.FillType.EVEN_ODD 奇偶填充实现真正的透明，
 * 不用 PorterDuff.CLEAR（在普通 View canvas 上会清成黑色）。
 */
public class AiqiyiVerifiedBadgeDrawable extends Drawable {

    /** 官方素材取色 #7E96F0 */
    private static final int DEFAULT_BLUE = 0xFF7E96F0;

    // 外框八角星傅里叶系数
    private static final float STAR_A0 = 0.9012f;
    private static final float STAR_A1 = 0.0899f;
    private static final float STAR_A2 = 0.0066f;

    // 中心四角星傅里叶系数
    private static final float HOLE_A0 = 0.3284f;
    private static final float HOLE_A1 = 0.1119f;
    private static final float HOLE_A2 = 0.0412f;
    private static final float HOLE_A3 = 0.0163f;
    private static final float HOLE_A4 = 0.0065f;

    /** 采样段数：八角星一圈 256 段，曲线足够顺滑 */
    private static final int SAMPLES = 256;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path outerPath = new Path();
    private final Path holePath = new Path();
    private final RectF bounds = new RectF();

    private int badgeColor = DEFAULT_BLUE;
    private int intrinsicSize = 0;

    public AiqiyiVerifiedBadgeDrawable() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(badgeColor);
    }

    /** 指定图标边长（px）。SimpleTextView 等容器按此值决定布局 */
    public AiqiyiVerifiedBadgeDrawable setIntrinsicSize(int px) {
        intrinsicSize = Math.max(0, px);
        invalidateSelf();
        return this;
    }

    public void setBadgeColor(int color) {
        if (badgeColor != color) {
            badgeColor = color;
            paint.setColor(color);
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        bounds.set(getBounds());
        if (bounds.isEmpty()) {
            return;
        }
        final float cx = bounds.centerX();
        final float cy = bounds.centerY();
        final float half = Math.min(bounds.width(), bounds.height()) / 2f;
        // 用满可用半径。官方素材本身四周就留了 ~1% 透明边，
        // 再额外缩边会让图标视觉上比官方小一圈
        final float r = half;

        buildEightStar(outerPath, cx, cy, r);
        buildFourStar(holePath, cx, cy, r);

        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addPath(outerPath);
        path.addPath(holePath);
        canvas.drawPath(path, paint);
    }

    /**
     * 圆润八角星：r = A0 + A1*cos(8θ) + A2*cos(16θ)
     *
     * 从 12 点方向（-90 度）开始顺时针采样。
     * 极坐标天然闭合，最后 lineTo 回起点即可。
     */
    private void buildEightStar(Path out, float cx, float cy, float r) {
        out.reset();
        for (int i = 0; i <= SAMPLES; i++) {
            // 归一化角度 0..1
            final float u = (float) i / SAMPLES;
            // 展开成 0..2π，从正上方开始
            final double theta = u * 2.0 * Math.PI - Math.PI / 2.0;
            // 傅里叶级数按 8θ 展开
            final double phase = theta * 8.0;
            final double radius = STAR_A0
                    + STAR_A1 * Math.cos(phase)
                    + STAR_A2 * Math.cos(2.0 * phase);
            final float x = (float) (cx + Math.cos(theta) * radius * r);
            final float y = (float) (cy + Math.sin(theta) * radius * r);
            if (i == 0) {
                out.moveTo(x, y);
            } else {
                out.lineTo(x, y);
            }
        }
        out.close();
    }

    /**
     * 中心圆润四角星：r = A0 + A1*cos(4θ) + A2*cos(8θ)
     *                     + A3*cos(12θ) + A4*cos(16θ)
     */
    private void buildFourStar(Path out, float cx, float cy, float r) {
        out.reset();
        for (int i = 0; i <= SAMPLES; i++) {
            final float u = (float) i / SAMPLES;
            final double theta = u * 2.0 * Math.PI - Math.PI / 2.0;
            final double phase = theta * 4.0;
            final double radius = HOLE_A0
                    + HOLE_A1 * Math.cos(phase)
                    + HOLE_A2 * Math.cos(2.0 * phase)
                    + HOLE_A3 * Math.cos(3.0 * phase)
                    + HOLE_A4 * Math.cos(4.0 * phase);
            final float x = (float) (cx + Math.cos(theta) * radius * r);
            final float y = (float) (cy + Math.sin(theta) * radius * r);
            if (i == 0) {
                out.moveTo(x, y);
            } else {
                out.lineTo(x, y);
            }
        }
        out.close();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicSize;
    }
}