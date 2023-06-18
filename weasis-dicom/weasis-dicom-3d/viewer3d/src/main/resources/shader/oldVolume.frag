#version 130

uniform sampler3D volTexture;
uniform sampler3D colorMap;
uniform sampler2D lightingMap;
in vec2 vTexCoord;

#include "voxelUniforms.glsl"

#include "voxelFunctions.glsl"

#include "vrFunctions.glsl"

void main(){
    // vec2 position = (vTexCoord +1.0)/2.0;
    ivec2 pixelCoords = ivec2(gl_FragCoord.xy);
    vec2 imageDims = vec2(gl_FragCoord.w, gl_FragCoord.z);
    if (pixelCoords.x >= imageDims.x || pixelCoords.y >= imageDims.y) {
        return;
    }

    vec2 uv;
    uv.x = (float(pixelCoords.x * 2 - imageDims.x) / imageDims.x);
    uv.y = (float(pixelCoords.y * 2 - imageDims.y) / imageDims.y);

    vec4 pixelVal = vec4(0);
    if (renderingType >= typeSlice) {
        pixelVal = slice(uv);
    } else {
        // Volume Rendering
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
    gl_FragColor.rgba = pixelVal;
}