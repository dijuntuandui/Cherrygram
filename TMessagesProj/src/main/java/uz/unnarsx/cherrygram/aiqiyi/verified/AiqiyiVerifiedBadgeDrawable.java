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
 * 外观：浅蓝圆润八角星外框 + 中心四角星「透明镂空」
 *
 * ===== 几何数据来自官方素材逐像素实测（1643x1637）=====
 *
 * 外框八角星（沿角度扫描蓝色外边界，单位=半宽）：
 *   8 个尖峰位于 0/45/90/135/180/225/270/315 度，半径 0.996
 *   8 个凹谷位于 15/30/60/75 度等，半径 0.857
 *   注意：8 个尖是等长的，不是「4 长 4 短」的交替星
 *
 * 中心四角星（沿角度扫描黑色区域）：
 *   4 个尖端位于 0/90/180/270 度，半径 0.502
 *   4 个凹口位于 45/135/225/315 度，半径 0.245
 *   0.245 / 0.502 ≈ 0.49，过渡为平滑外凸弧
 *
 * ===== 镂空实现 =====
 * 用 Path.FillType.EVEN_ODD 奇偶填充：
 * 外轮廓与中心星放同一个 Path，重叠区域不填充，得到真正透明。
 * 不用 PorterDuff.CLEAR —— 在普通 View canvas 上容易把整块清空成黑色。
 */
public class AiqiyiVerifiedBadgeDrawable extends Drawable {

    /** 官方素材取色 #7E96F0 */
    private static final int DEFAULT_BLUE = 0xFF7E96F0;

    /** 外框八角尖峰半径（相对半径 r） */
    private static final float STAR_TIP = 0.996f;
    /** 外框八个凹谷半径 */
    private static final float STAR_VALLEY = 0.857f;

    /** 中心星尖端半径 */
    private static final float HOLE_TIP = 0.502f;
    /** 中心星凹口半径 */
    private static final float HOLE_NOTCH = 0.245f;

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
        path.setFillType(Path.FillType.EVEN_ODD);
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
        // 留 2% 边距防抗锯齿切边
        final float r = half * 0.98f;

        buildEightStar(outerPath, cx, cy, r);
        buildFourStar(holePath, cx, cy, r);

        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addPath(outerPath);
        path.addPath(holePath);
        canvas.drawPath(path, paint);
    }

    /**
     * 圆润八角星
     *
     * 16 个控制点交替：8 个尖（0,45,90...度，半径 tip）
     * 与 8 个谷（22.5,67.5,112.5...度，半径 valley）。
     *
     * 注意谷点在「尖的正中间」：实测谷出现在 15/30/60 度附近，
     * 说明不是尖峰之间的中点，而是每个尖是一小段圆弧收口，
     * 两条边在距离尖端较近的位置就向内拗进去。
     *
     * 这里用二次贝塞尔的简化模型逼近：
     * 每 45 度一个尖，尖之间用控制点收向圆心，形成圆润凹谷。
     */
    private void buildEightStar(Path out, float cx, float cy, float r) {
        out.reset();
        final float tipR = r * STAR_TIP;
        final float valleyR = r * STAR_VALLEY;

        // 8 个尖，间隔 45 度
        final float[] tipX = new float[8];
        final float[] tipY = new float[8];
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 - 90); // 从正上方开始
            tipX[i] = (float) (cx + Math.cos(a) * tipR);
            tipY[i] = (float) (cy + Math.sin(a) * tipR);
        }

        out.moveTo(tipX[0], tipY[0]);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            // 两尖之间的谷点，位于两者角平分线上
            double midA = Math.toRadians((i * 45 - 90) + 22.5);
            float valleyX = (float) (cx + Math.cos(midA) * valleyR);
            float valleyY = (float) (cy + Math.sin(midA) * valleyR);

            // 尖 -> 谷：控制点贴近尖端一侧，让尖角保持锐利但带圆角
            float c1x = tipX[i] + (valleyX - tipX[i]) * 0.45f;
            float c1y = tipY[i] + (valleyY - tipY[i]) * 0.45f;
            out.quadTo(c1x, c1y, valleyX, valleyY);

            // 谷 -> 下一尖：控制点贴近谷一侧，让凹谷平滑
            float c2x = valleyX + (tipX[j] - valleyX) * 0.55f;
            float c2y = valleyY + (tipY[j] - valleyY) * 0.55f;
            out.quadTo(c2x, c2y, tipX[j], tipY[j]);
        }
        out.close();
    }

    /**
     * 中心圆润四角星
     *
     * 尖端 4 个（正上下左右），半径 0.502r
     * 凹口 4 个（对角线方向），半径 0.245r
     * 尖端到凹口用平滑外凸弧，形成「软糖状」星形
     */
    private void buildFourStar(Path out, float cx, float cy, float r) {
        out.reset();
        final float tipR = r * HOLE_TIP;
        final float notchR = r * HOLE_NOTCH;

        // 4 个尖端：上、右、下、左
        final float[] tipX = new float[4];
        final float[] tipY = new float[4];
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 - 90);
            tipX[i] = (float) (cx + Math.cos(a) * tipR);
            tipY[i] = (float) (cy + Math.sin(a) * tipR);
        }

        // 4 个凹口：右上、右下、左下、左上
        final float[] notchX = new float[4];
        final float[] notchY = new float[4];
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 - 45);
            notchX[i] = (float) (cx + Math.cos(a) * notchR);
            notchY[i] = (float) (cy + Math.sin(a) * notchR);
        }

        out.moveTo(tipX[0], tipY[0]);
        for (int i = 0; i < 4; i++) {
            int ni = i;                       // 尖端 i 之后是凹口 i
            int tj = (i + 1) % 4;             // 再之后是尖端 i+1

            // 尖端 -> 凹口：控制点偏尖端，产生细长收腰
            float c1x = tipX[i] + (notchX[ni] - tipX[i]) * 0.35f;
            float c1y = tipY[i] + (notchY[ni] - tipY[i]) * 0.35f;
            out.quadTo(c1x, c1y, notchX[ni], notchY[ni]);

            // 凹口 -> 下一尖端：控制点偏凹口，衔接平顺
            float c2x = notchX[ni] + (tipX[tj] - notchX[ni]) * 0.65f;
            float c2y = notchY[ni] + (tipY[tj] - notchY[ni]) * 0.65f;
            out.quadTo(c2x, c2y, tipX[tj], tipY[tj]);
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