package uz.unnarsx.cherrygram.aiqiyi.verified;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * [Aiqiyi] 二代认证角标
 *
 * 外观：浅蓝八角星外框 + 中心四角星「镂空」
 *
 * 镂空实现要点：
 * 不使用 PorterDuff.CLEAR（在普通 View canvas 上容易把整块清空成黑色），
 * 改用 Path.FillType.EVEN_ODD 奇偶填充规则：
 * 把八角星外轮廓与中心四角星轮廓放进同一个 Path，
 * 按奇偶规则，被覆盖两次的区域不填充，
 * 得到真正的透明镂空，且在任何渲染环境下行为一致。
 *
 * 几何比例来自官方素材实测（原图 1637x1637）：
 *   - 中心四角星 bbox 占整图 25%~75%，即整体 50%
 *   - 凹口半径约为尖端的 0.49
 */
public class AiqiyiVerifiedBadgeDrawable extends Drawable {

    /** 外框浅蓝，取自官方素材 #7E96F0 */
    private static final int DEFAULT_BLUE = 0xFF7E96F0;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path outerPath = new Path();
    private final Path holePath = new Path();
    private final RectF tmpRect = new RectF();

    private int badgeColor = DEFAULT_BLUE;
    /** 中心镂空区域占整体边长的比例，实测 0.5 */
    private float holeRatio = 0.50f;

    public AiqiyiVerifiedBadgeDrawable() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(badgeColor);
        path.setFillType(Path.FillType.EVEN_ODD);
    }

    public void setBadgeColor(int color) {
        if (badgeColor != color) {
            badgeColor = color;
            paint.setColor(color);
            invalidateSelf();
        }
    }

    public void setHoleRatio(float ratio) {
        ratio = Math.max(0.2f, Math.min(0.8f, ratio));
        if (holeRatio != ratio) {
            holeRatio = ratio;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        final RectF b = tmpRect;
        b.set(getBounds());
        if (b.isEmpty()) {
            return;
        }
        final float cx = b.centerX();
        final float cy = b.centerY();
        // 用短边做基准，保证非正方形 bounds 时也不变形
        final float size = Math.min(b.width(), b.height());
        final float half = size / 2f;
        // 留 4% 内边距，避免抗锯齿被切边
        final float radius = half * 0.96f;

        buildEightPointStar(outerPath, cx, cy, radius);
        buildFourPointStar(holePath, cx, cy, radius * holeRatio);

        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addPath(outerPath);
        path.addPath(holePath);

        canvas.drawPath(path, paint);
    }

    /**
     * 八角星：8 个顶点交错分布
     * 四个主尖在正方向，四个次尖在对角方向。
     * 相邻顶点之间用轻微内收的二次曲线，让轮廓带圆润感。
     */
    private void buildEightPointStar(Path out, float cx, float cy, float r) {
        out.reset();
        final float mainR = r;
        final float diagR = r * 0.78f;
        final float[] xs = new float[8];
        final float[] ys = new float[8];
        for (int i = 0; i < 8; i++) {
            double ang = Math.toRadians(i * 45);
            float rr = (i % 2 == 0) ? mainR : diagR;
            xs[i] = (float) (cx + Math.cos(ang) * rr);
            ys[i] = (float) (cy + Math.sin(ang) * rr);
        }
        final float pull = 0.86f;
        out.moveTo(xs[0], ys[0]);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            float mx = (xs[i] + xs[j]) / 2f;
            float my = (ys[i] + ys[j]) / 2f;
            float ccx = cx + (mx - cx) * pull;
            float ccy = cy + (my - cy) * pull;
            out.quadTo(ccx, ccy, xs[j], ys[j]);
        }
        out.close();
    }

    /**
     * 中心四角星（尖角朝上下左右）
     * 实测：尖端半径 = half，凹口半径 = half * 0.49
     */
    private void buildFourPointStar(Path out, float cx, float cy, float half) {
        out.reset();
        final float tip = half;
        final float notch = half * 0.49f;

        // 四个尖端：上、右、下、左
        final float[] tx = new float[]{cx, cx + tip, cx, cx - tip};
        final float[] ty = new float[]{cy - tip, cy, cy + tip, cy};

        // 四个凹口：右上、右下、左下、左上
        final float d = notch * 0.7071f;
        final float[] nx = new float[]{cx + d, cx + d, cx - d, cx - d};
        final float[] ny = new float[]{cy - d, cy + d, cy + d, cy - d};

        out.moveTo(tx[0], ty[0]);
        for (int i = 0; i < 4; i++) {
            int ni = i;
            float c1x = tx[i] + (nx[ni] - tx[i]) * 0.25f;
            float c1y = ty[i] + (ny[ni] - ty[i]) * 0.25f;
            out.quadTo(c1x, c1y, nx[ni], ny[ni]);

            int ti = (i + 1) % 4;
            float c2x = nx[ni] + (tx[ti] - nx[ni]) * 0.75f;
            float c2y = ny[ni] + (ty[ti] - ny[ni]) * 0.75f;
            out.quadTo(c2x, c2y, tx[ti], ty[ti]);
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
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }
}