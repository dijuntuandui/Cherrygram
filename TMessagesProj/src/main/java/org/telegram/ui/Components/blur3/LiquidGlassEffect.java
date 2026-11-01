package org.telegram.ui.Components.blur3;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

@RequiresApi(api = 33)
public class LiquidGlassEffect {

    private final RenderNode node;
    private final RuntimeShader shader;
    private RenderEffect effect;

    public LiquidGlassEffect(RenderNode node) {
        this.node = node;
        final String code = AndroidUtilities.readRes(R.raw.liquid_glass_shader);
        shader = new RuntimeShader(code);
        node.setRenderEffect(effect = RenderEffect.createRuntimeShaderEffect(shader, "img"));
    }

    private float resolutionX, resolutionY;
    private float centerX, centerY;
    private float sizeX, sizeY;
    private float radiusLeftTop;
    private float radiusRightTop;
    private float radiusRightBottom;
    private float radiusLeftBottom;
    private float thickness;
    private float intensity;
    private float index;
    private int foregroundColor;

    public void update(
        float left, float top, float right, float bottom,
        float radiusLeftTop, float radiusRightTop, float radiusRightBottom, float radiusLeftBottom,

        float thickness,
        float intensity,
        float index,
        int foregroundColor
    ) {
        float resolutionX = node.getWidth();
        float resolutionY = node.getHeight();
        // [Aiqiyi FIX-GLASS] RenderNode 尚未录制时 getWidth/getHeight 返回 0，
        // 若此时把 resolution=(0,0) 灌进 shader，后面的归一化换算会得到 NaN/Inf，
        // 采样坐标漂移，表现为毛玻璃背景被横向拉丝、文字扭曲成斜线。
        // 尺寸无效时直接跳过，等真正布局完成后再更新。
        if (resolutionX <= 0 || resolutionY <= 0) {
            return;
        }
        float centerX = (left + right) / 2;
        float centerY = (top + bottom) / 2;
        float width = right - left, height = bottom - top;
        float sizeX = width / 2;
        float sizeY = height / 2;

        if (radiusLeftTop + radiusLeftBottom > height) {
            float a = radiusLeftTop / (radiusLeftTop + radiusLeftBottom);
            radiusLeftTop = height * a;
            radiusLeftBottom = height * (1.0f - a);
        }
        if (radiusRightTop + radiusRightBottom > height) {
            float a = radiusRightTop / (radiusRightTop + radiusRightBottom);
            radiusRightTop = height * a;
            radiusRightBottom = height * (1.0f - a);
        }

        // [Aiqiyi FIX-GLASS] 首次（字段均为 0）必须强制更新，否则 shader 会长期停留在
        // 构造时那套全 0 默认 uniform 上，模糊一直异常。
        final boolean aiqiyiFirstInit = this.resolutionX == 0f || this.resolutionY == 0f;
        if (
            aiqiyiFirstInit ||
            Math.abs(this.resolutionX - resolutionX) > 0.1f ||
            Math.abs(this.resolutionY - resolutionY) > 0.1f ||
            Math.abs(this.centerX - centerX) > 0.1f ||
            Math.abs(this.centerY - centerY) > 0.1f ||
            Math.abs(this.sizeX - sizeX) > 0.1f ||
            Math.abs(this.sizeY - sizeY) > 0.1f ||
            Math.abs(this.radiusLeftTop - radiusLeftTop) > 0.1f ||
            Math.abs(this.radiusRightTop - radiusRightTop) > 0.1f ||
            Math.abs(this.radiusRightBottom - radiusRightBottom) > 0.1f ||
            Math.abs(this.radiusLeftBottom - radiusLeftBottom) > 0.1f ||
            Math.abs(this.thickness - thickness) > 0.1f ||
            Math.abs(this.intensity - intensity) > 0.1f ||
            Math.abs(this.index - index) > 0.1f ||
            this.foregroundColor != foregroundColor
        ) {
            this.foregroundColor = foregroundColor;

            final float a = Color.alpha(foregroundColor) / 255f;
            final float r = Color.red(foregroundColor) / 255f * a;
            final float g = Color.green(foregroundColor) / 255f * a;
            final float b = Color.blue(foregroundColor) / 255f * a;

            shader.setFloatUniform("resolution", this.resolutionX = resolutionX, this.resolutionY = resolutionY);
            shader.setFloatUniform("center", this.centerX = centerX, this.centerY = centerY);
            shader.setFloatUniform("size", this.sizeX = sizeX, this.sizeY = sizeY);
            shader.setFloatUniform("radius", this.radiusRightBottom = radiusRightBottom, this.radiusRightTop = radiusRightTop, this.radiusLeftBottom = radiusLeftBottom, this.radiusLeftTop = radiusLeftTop);
            shader.setFloatUniform("thickness", this.thickness = thickness);
            shader.setFloatUniform("refract_intensity", this.intensity = intensity);
            shader.setFloatUniform("refract_index", this.index = index);
            shader.setFloatUniform("foreground_color_premultiplied", r, g, b, a);
            node.setRenderEffect(effect = RenderEffect.createRuntimeShaderEffect(shader, "img"));
        }
    }

}
