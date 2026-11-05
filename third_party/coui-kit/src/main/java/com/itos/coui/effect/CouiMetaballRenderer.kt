package com.itos.coui.effect

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb

/** 横向融合主体; 中心、尺寸和圆角均为绘制节点本地像素。零尺寸表示收缩后的主体。 */
data class CouiMetaballBody(
    val center: Offset,
    val size: Size,
    val cornerRadius: Float,
) {
    init {
        require(center.x.isFinite() && center.y.isFinite()) { "融合主体中心必须是有限值" }
        require(size.width.isFinite() && size.height.isFinite() && size.width >= 0f && size.height >= 0f) {
            "融合主体尺寸必须是有限的非负值"
        }
        require(cornerRadius.isFinite() && cornerRadius >= 0f) { "融合圆角必须是有限的非负值" }
    }
}

/**
 * 同色双主体的横向 CONIC 融合场, 不采样背景或处理输入。
 * 每个绘制节点独立持有 renderer; Color.White 配合 DstIn 可使用同一算法绘制内容遮罩。
 */
@RequiresApi(33)
class CouiMetaballRenderer {
    private val shader = RuntimeShader(CouiMetaballShaderSource)
    private val brush = ShaderBrush(shader)

    /** size 决定输出区域; 两个主体、融合范围与输出区域使用同一坐标系, 镜像和位移由宿主处理。 */
    fun draw(
        scope: DrawScope,
        first: CouiMetaballBody,
        second: CouiMetaballBody,
        color: Color,
        blendRangePx: Float,
        fusionEnabled: Boolean = true,
        secondAlpha: Float = 1f,
        blendMode: BlendMode = BlendMode.SrcOver,
        size: Size = scope.size,
    ) {
        require(blendRangePx.isFinite() && blendRangePx >= 0f) { "融合范围必须是有限的非负值" }
        require(secondAlpha in 0f..1f) { "第二主体透明度必须在 0 到 1 之间" }
        require(size.width.isFinite() && size.height.isFinite() && size.width >= 0f && size.height >= 0f) {
            "融合输出尺寸必须是有限的非负值"
        }
        shader.setFloatUniform("cardRect", first.center.x, first.center.y, first.size.width, first.size.height)
        shader.setFloatUniform("actionRect", second.center.x, second.center.y, second.size.width, second.size.height)
        shader.setFloatUniform(
            "corners",
            minOf(first.cornerRadius, first.size.width * 0.5f, first.size.height * 0.5f) * 1.5f,
            minOf(second.cornerRadius, second.size.width * 0.5f, second.size.height * 0.5f) * 1.5f,
        )
        shader.setFloatUniform("blendRange", blendRangePx)
        shader.setFloatUniform("fusionEnabled", if (fusionEnabled) 1f else 0f)
        shader.setFloatUniform("actionAlpha", secondAlpha)
        shader.setColorUniform("bodyColor", color.toArgb())
        scope.drawRect(brush = brush, size = size, blendMode = blendMode)
    }
}

/**
 * OPD2515 SystemUI 16.99.12 的 CONIC 距离场与 blend_pair。
 * 保留像素单位常量和 10 次角点迭代; 同色双主体直接输出融合场, 不需要三菜单颜色分区。
 * CONIC 参数转换见 MetaBallBlurDrawable.MetaBall.toBallParams: corner * 1.5,
 * weight = 8 * 1.1 / (3 * 1.1 + 3)。背景不做图像模糊。
 */
private const val CouiMetaballShaderSource = """
uniform float4 cardRect;
uniform float4 actionRect;
uniform float2 corners;
uniform float blendRange;
uniform float fusionEnabled;
uniform float actionAlpha;
layout(color) uniform half4 bodyColor;

float RBox(float2 p,float2 b,float r){
        float2 q=abs(p)-b+r;
        return min(max(q.x,q.y),0.)+length(max(q,0.))-r;
    }

float cubicBezierDistance(vec2 p, vec2 p0, vec2 p1, vec2 p2, vec2 p3) {

        vec2 c0 = p0;
        vec2 c1 = 3.0 * (p1 - p0);
        vec2 c2 = 3.0 * (p2 - 2.0 * p1 + p0);
        vec2 c3 = p3 - 3.0 * p2 + 3.0 * p1 - p0;
        float t = 0.5;
        for (int i = 0; i < 10; i++) {
            vec2 Bt   = ((c3 * t + c2) * t + c1) * t + c0;
            vec2 dBt  = (3.0 * c3 * t + 2.0 * c2) * t + c1;
            vec2 ddBt = 6.0 * c3 * t + 2.0 * c2;
            vec2 r    = Bt - p;

            float f1 = dot(dBt, r);
            float f2 = dot(ddBt, r) + dot(dBt, dBt);

            float denom = max(f2, 1e-4);
            t = clamp(t - f1 / denom, 0.0, 1.0);
        }
        vec2 Bt = ((c3 * t + c2) * t + c1) * t + c0;
        vec2 re = Bt - p;
        vec2 tangent = (3.0 * c3 * t + 2.0 * c2) * t + c1;
        vec2 normal = vec2(-tangent.y, tangent.x);
        vec2 normalizedNormal = normalize(normal);
        float dis = length(re);
        return dot(re, normalizedNormal) > 0.0 ? dis : -dis;
    }
    float sdBezierDistance(vec2 p, vec2 b, float corner, float weight) {
        float sdf = 0.0;
        float cc = corner;
        if(any(lessThanEqual(abs(p), b - cc))) {
            sdf = RBox(p, b, cc);
        }else{
            float point  = max(cc*weight*0.5,0.001);
            sdf = cubicBezierDistance(abs(p)-b+cc,vec2(cc, 0.0), vec2(cc, point), vec2(point, cc),vec2(0.0, cc));
        }
        return sdf;
    }

const float BLEND_ANTI_ALIASING = 2.0;
    const float BLEND_EDGE_FADE_RANGE_X = 0.0;
    const float BLEND_EDGE_FADE_RANGE_Y = 15.0;
    const float BLEND_SHAPE_CHANGE = 6.0;
    const float BLEND_EDGE_FADE = 20.0;
    const float BLEND_STRENGTH_SMOOTH = 1.0;
    const float BLEND_STRENGTH = 15.0;
    const float BLEND_COLOR_SPEED_PIXEL = 4.0;
    const float BLEND_COLOR_RANGE = 110.0;
    const float BLEND_COLOR_FADE = 1.2;
    const float BLEND_PROCESS_CONTROL = 0.3;
    const float BLEND_ARC_END = 0.85;
    const float BLEND_ARC_START = 1.0;
    const float BLEND_ARC_RANGE = 5;

struct RectCorner {vec2 center;vec2 halfSize;float coner;float weight;};
    struct Rect {vec2 center;vec2 halfSize;float coner;};
    struct ProcessReturn {Rect rect_c;vec2 dir_c;vec2 point_c;float p;float dis;float shortLen;};
    struct RectBlendP {float blendShape;float shapeRangeFactorX;float shapeRangeFactorY;};
    struct MetaballResult {float sdf;float mix_c;};
    const int ARRAY_SIZE = 3;

float gain(float x, float k) {
        float a = 0.5*pow(2.0*((x<0.5)?x:1.0-x), k);
        return (x<0.5)?a:1.0-a;
    }

void combineRect(Rect rect1, Rect rect2, inout Rect rect_c) {
        vec2 pos_left_bottom = min(rect1.center - rect1.halfSize, rect2.center - rect2.halfSize);
        vec2 pos_right_up = max(rect1.center + rect1.halfSize, rect2.center + rect2.halfSize);
        rect_c.center = 0.5 * (pos_left_bottom + pos_right_up);
        rect_c.halfSize = 0.5 * (pos_right_up - pos_left_bottom);
    }

void rectProessX(Rect rect1, Rect rect2, inout ProcessReturn processReturn) {
        Rect rect_c;
        combineRect(rect1, rect2, rect_c);
        float shortLen = (rect1.halfSize.x + rect2.halfSize.x - rect_c.halfSize.x) * 2.0;
        float maxCorner = max(rect1.coner, rect2.coner);
        float sumConer = rect1.coner + rect2.coner;
        float dualP = smoothstep(maxCorner - sumConer, maxCorner, shortLen);
        float maxHalfSizex = max(rect1.halfSize.x, rect2.halfSize.x);
        float unDualP = smoothstep(sumConer * 0.5, 0.0, (rect_c.halfSize.x - maxHalfSizex));
        float p = mix(dualP, unDualP, smoothstep(0.0, sumConer * 0.5, abs(rect1.halfSize.y - rect2.halfSize.y)));
        vec2 dir = vec2(1.0, 0.0);
        vec2 point_c = vec2(0.0);
        float dis = 0.0;
        float cmp_deep = min(rect1.halfSize.x, rect2.halfSize.x);
        Rect Rect_1 = Rect(rect1.center, rect1.halfSize - vec2(cmp_deep, 0.0), 0.0);
        Rect Rect_2 = Rect(rect2.center, rect2.halfSize - vec2(cmp_deep, 0.0), 0.0);
        vec2 minA = Rect_1.center - Rect_1.halfSize;
        vec2 maxA = Rect_1.center + Rect_1.halfSize;
        vec2 minB = Rect_2.center - Rect_2.halfSize;
        vec2 maxB = Rect_2.center + Rect_2.halfSize;
        bool overlapX = maxA.x >= minB.x && maxB.x >= minA.x;
        bool overlapY = maxA.y >= minB.y && maxB.y >= minA.y;
        if (overlapX && overlapY) {
            dis = 0.0;
        } else {
            point_c = vec2((min(maxA.x, maxB.x) + max(minA.x, minB.x)), (min(maxA.y, maxB.y) + max(minA.y, minB.y))) * 0.5;
            float dx = 0.0;
            if (maxA.x < minB.x) {
                dx = minB.x - maxA.x;
            } else if (maxB.x < minA.x) {
                dx = -(minA.x - maxB.x);
            }
            dis = abs(dx);
            dir = (dis > 0.0) ? vec2(sign(dx), 0.0) : vec2(1.0, 0.0);
        }
        processReturn.rect_c = rect_c;
        processReturn.dir_c = dir;
        processReturn.point_c = point_c;
        processReturn.p = p;
        processReturn.dis = dis;
        processReturn.shortLen = shortLen;
    }

void blend_pair( Rect rect1, Rect rect2,
                    RectCorner coner1,  RectCorner coner2,
                    RectBlendP blendP1, RectBlendP blendP2,
                    vec2  p1_in,  vec2  p2_in,
                    float draw,   float drbw,
                    float u_mode, vec2  coords,
                    inout float di,
                    inout  float dj,
                    inout  float dvalue,
                    inout   float rangeOut){
        if (!all(greaterThanEqual(vec4(rect1.halfSize, rect2.halfSize), vec4(0.001)))) {
            rangeOut = 0.0;
            return;
        }

        ProcessReturn pr;
        rectProessX(rect1, rect2, pr);

        float space = max(-pr.shortLen, 0.0);
        float range_blend = blendP1.blendShape + blendP2.blendShape;
        float shapeChange = step(0.0001, pr.dis) * smoothstep(range_blend + BLEND_SHAPE_CHANGE, range_blend, space);

        vec2  p1 = p1_in;
        vec2  p2 = p2_in;
        float change_flag1 = 0.0;
        float change_flag2 = 0.0;

        if (shapeChange > 0.001) {
            vec2  point_dir = coords - pr.point_c;
            float dis_y = abs(dot(point_dir, pr.dir_c));
            float dis_x = abs(dot(point_dir, vec2(pr.dir_c.y, -pr.dir_c.x)));
            float minHS = min(rect1.halfSize.y, rect2.halfSize.y);
            float minHS_ = min(rect1.halfSize.x, rect2.halfSize.x);

            float dis_bet = -shapeChange * range_blend;
            shapeChange *= smoothstep(minHS_, dis_bet, pr.shortLen);

            float u_s = mix(BLEND_ARC_START, BLEND_ARC_END,
                smoothstep(BLEND_ARC_RANGE, 0.0, abs(dis_bet - pr.shortLen)));

            float rd1 = smoothstep(
                max(blendP1.blendShape * blendP1.shapeRangeFactorY + space * 0.5, 0.001),
                0.0, dis_y) *
                gain(smoothstep(max(minHS * blendP1.shapeRangeFactorX, 0.001), 0.0, dis_x), u_s);
            float ss1 = (blendP1.blendShape * rd1) * shapeChange;
            if (ss1 > 0.001) { p1 += normalize(-pr.dir_c) * ss1; change_flag1 = 1.0; }

            float rd2 = smoothstep(max(blendP2.blendShape * blendP2.shapeRangeFactorY + space * 0.5, 0.001),0.0, dis_y) *
                        gain(smoothstep(max(minHS * blendP2.shapeRangeFactorX, 0.001), 0.0, dis_x), u_s);
            float ss2 = (blendP2.blendShape * rd2) * shapeChange;
            if (ss2 > 0.001) { p2 += normalize(pr.dir_c) * ss2; change_flag2 = 1.0; }
        }

float d1 = (change_flag1 < 0.5) ? draw: sdBezierDistance(p1, coner1.halfSize, coner1.coner, coner1.weight);;
        float d2 = (change_flag2 < 0.5) ? drbw: sdBezierDistance(p2, coner2.halfSize, coner2.coner, coner2.weight);;

float shape_d = RBox(coords - pr.rect_c.center, pr.rect_c.halfSize, min(coner1.coner, coner2.coner));

        float mask = 1.0 - smoothstep(BLEND_EDGE_FADE, 0.0, abs(shape_d)) * pr.p;

        float diff = pr.shortLen + shapeChange * range_blend;


        float bstr = (BLEND_STRENGTH *gain(smoothstep(0.0, BLEND_STRENGTH_SMOOTH, diff),BLEND_PROCESS_CONTROL)) * mask;

        float d;
        if (bstr > 0.001) {
            float h = clamp(0.5 + 0.5 * (d1 - d2) / bstr, 0.0, 1.0);
            d = mix(d1, d2, h) - (bstr * h) * (1.0 - h);
        } else {
            d = min(d1, d2);
        }
        rangeOut = smoothstep(0.0, BLEND_COLOR_SPEED_PIXEL, diff);
        if (d1 < di) di = d1;
        if (d2 < dj) dj = d2;
        if (d < dvalue) dvalue = d;
    }

half4 main(float2 coords) {
    RectCorner card = RectCorner(cardRect.xy, cardRect.zw * 0.5, corners.x, 8.8 / 6.3);
    RectCorner action = RectCorner(actionRect.xy, actionRect.zw * 0.5, corners.y, 8.8 / 6.3);
    float2 p0 = coords - card.center;
    float2 p1 = coords - action.center;
    float d0 = 100.0;
    float d1 = 100.0;
    if (all(greaterThanEqual(card.halfSize, float2(0.001)))) {
        d0 = sdBezierDistance(p0, card.halfSize, card.coner, card.weight);
    }
    if (actionAlpha > 0.0 && all(greaterThanEqual(action.halfSize, float2(0.001)))) {
        d1 = sdBezierDistance(p1, action.halfSize, action.coner, action.weight);
    }
    float d = min(d0, d1);
    float colorRange = 0.0;
    if (fusionEnabled > 0.5) {
        RectBlendP blend = RectBlendP(blendRange, 1.2, 2.0);
        blend_pair(Rect(card.center, card.halfSize, card.coner),
                   Rect(action.center, action.halfSize, action.coner),
                   card, action, blend, blend, p0, p1, d0, d1, 0.0, coords,
                   d0, d1, d, colorRange);
    }
    float coverage = smoothstep(0.0, -BLEND_ANTI_ALIASING, d);
    // 系统 mix_c: 像素位于另一主体之外时取满量程, 位于其内部时取 blend_pair 的 rangeOut,
    // 融球越深退晕带越窄, 因此卡片轮廓之外的地方按卡片自身距离场连续淡化。
    float fadeRange = (d1 > 0.0) ? 1.0 : colorRange;
    float edgeFade = pow(
        smoothstep(max(BLEND_COLOR_RANGE * 0.5 * fadeRange, 0.001), 0.0, d0),
        BLEND_COLOR_FADE);
    // 删除按钮主体由自身距离场与 actionAlpha 决定, 两层同色叠加避免卡片右边缘出现硬台阶。
    float actionBody = (d0 > 0.0 && d1 <= 0.0) ? actionAlpha : 0.0;
    float opacity = 1.0 - (1.0 - edgeFade) * (1.0 - actionBody);
    half alpha = bodyColor.a * half(coverage * opacity);
    return half4(bodyColor.rgb * alpha, alpha);
}
"""
