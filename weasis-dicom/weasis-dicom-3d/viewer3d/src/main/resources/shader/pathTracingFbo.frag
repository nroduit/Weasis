#version 330 core
// Progressive path tracing pass of the FBO path, see pathTrace() in vrFunctions.glsl. Kept apart
// from volumeFbo.frag so each program links in a fraction of the time the two took together; the
// display image is produced by the denoise pass (denoiseFbo.frag) that follows.

// Colour attachment 0 is the display image, left for the denoise pass.
layout (location = 0) out vec4 fragColor;
// Linear running average of the path tracer (colour attachment 1).
layout (location = 1) out vec4 accumColor;
// Averaged first-hit normal and depth guiding the denoise pass (colour attachment 2).
layout (location = 2) out vec4 featureColor;
// Squared-luminance mean and sample count of the pixel (colour attachment 3).
layout (location = 3) out vec2 momentColor;

in vec2 quadCoordinates;

uniform sampler3D volTexture;   // unit 0 — set from Java
uniform sampler2D colorMap;     // unit 1 — set from Java
uniform sampler2D lightingMap;  // unit 2 — set from Java
uniform usampler3D segTexture;  // unit 4 — set from Java
uniform sampler2D segColorMap;  // unit 5 — set from Java
uniform sampler2D envMap;       // unit 6 — set from Java
uniform sampler2D historyMap;   // unit 7 — set from Java
uniform sampler2D featureMap;   // unit 8 — set from Java
uniform sampler2D momentMap;    // unit 12 — set from Java
uniform sampler3D majorantMap;  // unit 11 — set from Java

#include "voxelUniforms330.glsl"

#include "voxelFunctions.glsl"

#include "vrFunctions.glsl"

void main() {
    // Same conventions as volumeFbo.frag: NDC from the quad, pixel size from the derivatives.
    vec2 uv = quadCoordinates;
    vec2 invStep = abs(vec2(dFdx(quadCoordinates.x), dFdy(quadCoordinates.y)));
    ivec2 viewportDims = ivec2(round(2.0 / max(invStep, vec2(1e-6))));
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);

    accumColor = pathTrace(uv, pixelCoords, viewportDims, featureColor, momentColor);
    fragColor = vec4(0.0);
}
