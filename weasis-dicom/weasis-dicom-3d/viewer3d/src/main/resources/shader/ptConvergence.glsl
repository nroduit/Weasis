// ptConvergence.glsl
// Per-pixel convergence of the path tracer, shared by the tracer and the denoise pass. Next to
// the colour average, each pixel keeps in momentMap the mean of its squared luminance (x) and the
// number of samples it has averaged (y), which give the standard error of its mean. A pixel whose
// error is under the target stops tracing, so the frames spend their samples where noise remains,
// and is shown as is by the denoiser, whose tolerance follows that error elsewhere.

// Standard error of the mean, in linear premultiplied luminance, at which a pixel is converged:
// about two display levels, one sigma, before the denoise pass smooths what is left.
const float PT_TARGET_ERROR = 0.01;
// Samples a pixel averages before its error estimate is trusted.
const float PT_MIN_SAMPLES = 32.0;

float ptLuminance(vec4 premultiplied) {
    return dot(premultiplied.rgb, vec3(0.2126, 0.7152, 0.0722));
}

// Standard error of the pixel's mean, from its colour average and moments.
float ptStandardError(vec4 history, vec2 moment) {
    float mean = ptLuminance(history);
    float variance = max(moment.x - mean * mean, 0.0);
    return sqrt(variance / max(moment.y, 1.0));
}

bool ptConverged(vec4 history, vec2 moment) {
    return moment.y >= PT_MIN_SAMPLES && ptStandardError(history, moment) < PT_TARGET_ERROR;
}
