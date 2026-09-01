#version 330 core
// Denoise pass of the path tracer (FBO path): filters the running average into the colour
// attachment and redraws the crosshair overlay on top, as volumeFbo.frag does for the other modes.

layout (location = 0) out vec4 fragColor;

in vec2 quadCoordinates;

uniform sampler2D historyMap;   // unit 7 — set from Java
uniform sampler2D featureMap;   // unit 8 — set from Java

#include "voxelUniforms330.glsl"

#include "toneMapping.glsl"

#include "denoiseFunctions.glsl"

#include "crosshairOverlay.glsl"

void main() {
    // Same derivation of the viewport size as volumeFbo.frag, for the overlay.
    vec2 invStep = abs(vec2(dFdx(quadCoordinates.x), dFdy(quadCoordinates.y)));
    ivec2 viewportDims = ivec2(round(2.0 / max(invStep, vec2(1e-6))));
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);

    vec4 pixelVal = toneMapPremultiplied(denoise(pixelCoords));
    vec4 crosshairColor = computeCrosshairColor(pixelCoords, viewportDims);
    fragColor = crosshairColor.a >= 0.0 ? crosshairColor : pixelVal;
}
