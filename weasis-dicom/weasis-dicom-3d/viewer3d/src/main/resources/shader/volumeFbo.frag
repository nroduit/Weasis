#version 330 core
// FBO-based volume rendering fragment shader.
// This is the OpenGL 3.3-compatible fallback for the compute shader (volume.comp).
// It performs identical ray-casting but as a fragment shader over a full-screen quad,
// writing directly to an FBO color attachment instead of using imageStore().
//
// Requires OpenGL 3.3 / GLSL 3.30 minimum. No 4.x features are used:
//   - No compute shaders, no imageStore(), no layout(binding=N) on samplers (4.2+).
//   - FBOs, sampler3D, uint uniforms, structs, textureSize are all core since OpenGL 3.0/3.3.
//
// Sampler units are assigned from Java via glUniform1i.

layout (location = 0) out vec4 fragColor;

in vec2 quadCoordinates;

uniform sampler3D volTexture;   // unit 0 — set from Java
uniform sampler2D colorMap;     // unit 1 — set from Java
uniform sampler2D lightingMap;  // unit 2 — set from Java

#include "voxelUniforms410.glsl"

#include "voxelFunctions.glsl"

#include "vrFunctions.glsl"

void main() {
    // quadCoordinates is in [-1, 1] — same NDC convention as the compute shader's uv.
    vec2 uv = quadCoordinates;

    vec4 pixelVal = vec4(0.0);
    if (renderingType >= typeSlice) {
        pixelVal = slice(uv);
    } else {
        float tmin = 0.0;
        float tmax = 0.0;
        Ray ray = CreateCameraRay(uv);
        intersect(ray, tmin, tmax);
        if (tmax >= tmin) {
            if (renderingType == typeComposite) {
                pixelVal = rayCastingComposite(ray, tmin, tmax, uv);
            } else if (renderingType == typeMip) {
                pixelVal = rayCastingMip(ray, tmin, tmax, uv);
            } else if (renderingType == typeIsoSurface) {
                pixelVal = rayCastingIsoSurface(ray, tmin, tmax, uv);
            }
        }
    }
    fragColor = pixelVal;
}

