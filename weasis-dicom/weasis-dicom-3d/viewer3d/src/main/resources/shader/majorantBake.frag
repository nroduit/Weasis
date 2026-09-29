#version 330 core
// Bakes the path tracer's extinction bounds, see MajorantMap: for each block of the majorant grid,
// the highest LUT alpha over the block's windowed value range (level 0), or the highest of a 4×4×4
// group of blocks (level 2). One layer of one level per draw; blocks beyond the grid bound nothing.

layout (location = 0) out float alphaBound;

in vec2 quadCoordinates;

uniform sampler3D volTexture;    // unit 0 — declared for the include, not sampled here
uniform sampler3D majorantGrid;  // unit 9 — set from Java
uniform sampler2D alphaRangeMap; // unit 10 — set from Java
uniform int bakeLevel;
uniform int bakeLayer;

#include "voxelUniforms330.glsl"

#include "voxelFunctions.glsl"

// Alpha bound of one block, or 0 when the window and the visible range leave it transparent.
float blockAlphaBound(ivec3 block) {
    if (any(greaterThanEqual(block, textureSize(majorantGrid, 0)))) return 0.0;
    vec2 range = texelFetch(majorantGrid, block, 0).rg;
    float lo = normalizedWindowLevelOf(getOriginalVoxelValue(range.x));
    float hi = normalizedWindowLevelOf(getOriginalVoxelValue(range.y));
    if (lo > hi) {
        float swap = lo;
        lo = hi;
        hi = swap;
    }
    lo = max(lo, visibleMin);
    hi = min(hi, visibleMax);
    if (lo > hi) return 0.0;
    int bins = textureSize(alphaRangeMap, 0).x;
    ivec2 bin = clamp(ivec2(floor(vec2(lo, hi) * float(bins))), ivec2(0), ivec2(bins - 1));
    return texelFetch(alphaRangeMap, bin, 0).r;
}

void main() {
    ivec3 cell = ivec3(ivec2(gl_FragCoord.xy), bakeLayer);
    if (bakeLevel == 0) {
        alphaBound = blockAlphaBound(cell);
        return;
    }
    int span = 1 << bakeLevel;
    ivec3 base = cell * span;
    float bound = 0.0;
    for (int z = 0; z < span; ++z) {
        for (int y = 0; y < span; ++y) {
            for (int x = 0; x < span; ++x) {
                bound = max(bound, blockAlphaBound(base + ivec3(x, y, z)));
            }
        }
    }
    alphaBound = bound;
}
