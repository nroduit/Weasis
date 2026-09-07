// denoiseFunctions.glsl
// Edge-aware spatial filter over the path tracer's running average, run after every accumulated
// frame. The temporal part of the denoising is the average itself; this pass hides the noise
// while a pixel's standard error is still high and fades out with it: the tolerance vanishes
// with the error, so a pixel that has converged is blended with its equals only.
//
// Requires: historyMap (linear premultiplied average), featureMap (averaged first-hit normal in
// xyz, depth in w) and momentMap (squared-luminance mean and sample count, see ptConvergence.glsl).

#include "ptConvergence.glsl"

// Half width of the filter window, in pixels.
const int DENOISE_RADIUS = 3;
// Colour difference that halves a tap's weight, as a multiple of the pixel's standard error: a
// difference explained by the noise of the two means passes, a real edge does not.
const float DENOISE_COLOR_SIGMA = 3.0;
// Tolerance assumed before the error estimate is trustworthy: half a unit for a lone sample,
// shrinking with the square root of the count as the noise does.
const float DENOISE_PRIOR_SIGMA = 0.5;
// Depth difference, in normalized volume units, that halves a tap's weight.
const float DENOISE_DEPTH_SIGMA = 0.03;

float denoiseFeatureWeight(vec4 f, vec4 g) {
    float ln = length(f.xyz);
    float lg = length(g.xyz);
    bool fSurface = ln > 0.1;
    bool gSurface = lg > 0.1;
    float w = exp(-abs(f.w - g.w) / DENOISE_DEPTH_SIGMA);
    if (fSurface && gSurface) {
        w *= pow(max(dot(f.xyz, g.xyz) / (ln * lg), 0.0), 16.0);
    } else if (fSurface != gSurface) {
        w *= 0.2;
    }
    return w;
}

vec4 denoise(ivec2 p) {
    vec4 c = texelFetch(historyMap, p, 0);
    vec2 m = texelFetch(momentMap, p, 0).rg;
    vec4 f = texelFetch(featureMap, p, 0);
    ivec2 dims = textureSize(historyMap, 0);
    float sigmaC = max(
        DENOISE_COLOR_SIGMA * ptStandardError(c, m), DENOISE_PRIOR_SIGMA / sqrt(max(m.y, 1.0)));
    float spatial = 2.0 * float(DENOISE_RADIUS * DENOISE_RADIUS) * 0.5;
    vec4 sum = vec4(0.0);
    float weightSum = 0.0;
    for (int dy = -DENOISE_RADIUS; dy <= DENOISE_RADIUS; ++dy) {
        for (int dx = -DENOISE_RADIUS; dx <= DENOISE_RADIUS; ++dx) {
            ivec2 q = p + ivec2(dx, dy);
            if (any(lessThan(q, ivec2(0))) || any(greaterThanEqual(q, dims))) continue;
            vec4 cq = texelFetch(historyMap, q, 0);
            vec4 fq = texelFetch(featureMap, q, 0);
            float w = exp(-float(dx * dx + dy * dy) / spatial);
            w *= exp(-abs(c.a - cq.a) * 8.0);
            w *= denoiseFeatureWeight(f, fq);
            vec3 dc = cq.rgb - c.rgb;
            w *= exp(-dot(dc, dc) / (2.0 * sigmaC * sigmaC));
            sum += cq * w;
            weightSum += w;
        }
    }
    return weightSum > 0.0 ? sum / weightSum : c;
}
