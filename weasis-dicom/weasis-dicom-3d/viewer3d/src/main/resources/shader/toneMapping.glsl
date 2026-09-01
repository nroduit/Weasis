// toneMapping.glsl
// Shared by the ray casters and the path-tracing denoise pass. Requires the exposure uniform.

// ACES filmic curve (Narkowicz fit) on the exposed colour; highlights above 1 roll off instead of
// clipping.
vec3 toneMap(vec3 color) {
    vec3 x = color * exposure;
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// Tone-maps a premultiplied colour without touching its coverage.
vec4 toneMapPremultiplied(vec4 c) {
    if (c.a <= 0.0) return c;
    return vec4(toneMap(c.rgb / c.a) * c.a, c.a);
}
