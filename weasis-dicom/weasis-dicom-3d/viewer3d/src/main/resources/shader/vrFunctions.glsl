// *************************************************************************************************
// Ray casting
// *************************************************************************************************
struct Ray {
    vec3 origin;
    vec3 direction;
    vec3 invDirection;
    int sign[3];
};

float dithering(vec2 uv) {
    return fract(sin(uv.x * 12.9898 + uv.y * 78.233) * 43758.5453);
}

float guassianFilter(vec3 uvw, float delta) {
    float dX = delta;
    float dY = delta;
    float dZ = delta;
    float pix = getNormalizedWindowLevel(uvw);
    pix += getNormalizedWindowLevel(uvw + vec3(+ dX, + dY, + dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(+ dX, + dY, -dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(+ dX, -dY, + dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(+ dX, -dY, -dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(-dX, + dY, + dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(-dX, + dY, -dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(-dX, -dY, + dZ));
    pix += getNormalizedWindowLevel(uvw + vec3(-dX, -dY, -dZ));
    return pix / 9;
}

// Computes a simplified lighting equation
vec3 blinnPhong(vec3 N, vec3 V, vec3 L, int lightIndex, float pixelValue, vec3 diffuse) {
    // Material properties
    vec3 Ka = defaultAmbient;
    vec3 Kd = diffuse;
    vec3 Ks = defaultSpecular;

    // Diffuse coefficient
    float diff_coeff = max(dot(L, N), 0.0);

    // Specular coefficient
    vec3 H = normalize(L + V);
    float spec_coeff = diff_coeff > 0.0 ? pow(max(dot(H, N), 0.0), lights[lightIndex].specularPower) : 0.0;


    vec4 light = texture(lightingMap, vec2(pixelValue, 0.0));
    return Ka * light.x + Kd * light.y * diff_coeff + Ks * light.z * spec_coeff;
}

// On-the-fly gradient approximation.
vec3 gradient(vec3 uvw, float delta) {
    vec3 pix1;
    pix1.x = getNormalizedWindowLevel(uvw - vec3(delta, 0, 0)) - getNormalizedWindowLevel(uvw + vec3(delta, 0, 0));
    pix1.y = getNormalizedWindowLevel(uvw - vec3(0, delta, 0)) - getNormalizedWindowLevel(uvw + vec3(0, delta, 0));
    pix1.z = getNormalizedWindowLevel(uvw - vec3(0, 0, delta)) - getNormalizedWindowLevel(uvw + vec3(0, 0, delta));
    return normalize(pix1);
}

Ray makeRay(vec3 origin, vec3 direction) {
    vec3 inv_direction = vec3(1.0) / direction;

    return Ray(origin, direction, inv_direction, int[3](((inv_direction.x < 0.0) ? 1 : 0), ((inv_direction.y < 0.0) ? 1 : 0), ((inv_direction.z < 0.0) ? 1 : 0)));
}

Ray CreateCameraRay(vec2 uv) {
    // Transform the camera origin to world space
    vec3 origin = (viewMatrix * vec4(0.0f, 0.0f, 0.0f, 1.0f)).xyz;

    // Invert the perspective projection of the view-space position
    vec3 direction = (projectionMatrix * vec4(uv, 0.0f, 1.0f)).xyz;
    // Transform the direction from camera to world space and normalize
    direction = (viewMatrix * vec4(direction, 0.0f)).xyz;
    direction = normalize(direction);
    return makeRay(origin, direction);
}

float getAabb(float val, int dir) {
    return dir == 0 ? -val : val;
}

// *************************************************************************************************
// Crosshair cut — mirrors the legacy volumetricCenterSlicing logic.
//
// The cut plane(s) pass through crosshairPos (normalised [0,1]³ texture space) and are oriented by
// crosshairRot (MPR global rotation matrix, volume space → volume space).
//
//   localVec = crosshairRot * (texCoord - crosshairPos)
//   Axes: X = Left/Right,  Y = Up/Down,  Z = Front/Back (view depth)
// *************************************************************************************************
bool isCrosshairCut(vec3 texCoord) {
    if (crosshairCutMode == 0 || !crosshairVisible) return false;

    // Vector from the cut centre to the current sample in the crosshair local frame.
    vec3 v = crosshairRot * (texCoord - crosshairPos);

    // Half-space modes
    if      (crosshairCutMode == 1) return v.x < 0.0;               // RIGHT
    else if (crosshairCutMode == 2) return v.x > 0.0;               // LEFT
    else if (crosshairCutMode == 3) return v.y < 0.0;               // FRONT
    else if (crosshairCutMode == 4) return v.y > 0.0;               // BACK
    else if (crosshairCutMode == 5) return v.z > 0.0;               // UP
    else if (crosshairCutMode == 6) return v.z < 0.0;               // DOWN

    // Quadrant modes — two simultaneous 1/4-space tests along X and Z
    else if (crosshairCutMode == 7) return v.x < 0.0 && v.z > 0.0; // RIGHT_UP
    else if (crosshairCutMode == 8)  return v.x > 0.0 && v.z > 0.0; // LEFT_UP
    else if (crosshairCutMode == 9)  return v.x < 0.0 && v.z < 0.0; // RIGHT_DOWN
    else if (crosshairCutMode == 10)  return v.x > 0.0 && v.z < 0.0; // LEFT_DOWN

    // Octant modes — three simultaneous 1/8-space tests along X, Y, and Z
    else if (crosshairCutMode == 11) return v.x < 0.0 && v.z > 0.0 && v.y < 0.0; // UP_RIGHT_FRONT
    else if (crosshairCutMode == 12) return v.x > 0.0 && v.z > 0.0 && v.y < 0.0; // UP_LEFT_FRONT
    else if (crosshairCutMode == 13) return v.x < 0.0 && v.z < 0.0 && v.y < 0.0; // DOWN_RIGHT_FRONT
    else if (crosshairCutMode == 14) return v.x > 0.0 && v.z < 0.0 && v.y < 0.0; // DOWN_LEFT_FRONT
    else if (crosshairCutMode == 15) return v.x < 0.0 && v.z > 0.0 && v.y > 0.0; // UP_RIGHT_BACK
    else if (crosshairCutMode == 16) return v.x > 0.0 && v.z > 0.0 && v.y > 0.0; // UP_LEFT_BACK
    else if (crosshairCutMode == 17) return v.x < 0.0 && v.z < 0.0 && v.y > 0.0; // DOWN_RIGHT_BACK
    else if (crosshairCutMode == 18) return v.x > 0.0 && v.z < 0.0 && v.y > 0.0; // DOWN_LEFT_BACK

    return false;
}

void intersect(in Ray ray, out float tmin, out float tmax) {
    float tymin, tymax, tzmin, tzmax;
    tmin = (getAabb(texelSize.x, ray.sign[0]) - ray.origin.x) * ray.invDirection.x;
    tmax = (getAabb(texelSize.x, 1 - ray.sign[0]) - ray.origin.x) * ray.invDirection.x;
    tymin = (getAabb(texelSize.y, ray.sign[1]) - ray.origin.y) * ray.invDirection.y;
    tymax = (getAabb(texelSize.y, 1 - ray.sign[1]) - ray.origin.y) * ray.invDirection.y;
    tzmin = (getAabb(texelSize.z, ray.sign[2]) - ray.origin.z) * ray.invDirection.z;
    tzmax = (getAabb(texelSize.z, 1 - ray.sign[2]) - ray.origin.z) * ray.invDirection.z;
    tmin = max(max(tmin, tymin), tzmin);
    tmax = min(min(tmax, tymax), tzmax);
}

vec4 applyTextureColor(float pix){
    return texture(colorMap, vec2(pix, 0.0));
}

// Reference thickness (in normalized volume-texture units) over which a segment's slider opacity is
// realized. The per-sample seg opacity is corrected to this reference so the rendered result no longer
// depends on the ray sampling rate; smaller values make the slider ramp up faster.
const float SEG_OPACITY_REF_DIST = 0.1;

// *************************************************************************************************
// Segmentation overlay — samples the segTexture (usampler3D, GL_R32UI) which now stores per-voxel
// storage IDs (one ID per segment, plus extra IDs for overlap combinations) and looks the colour
// up in segColorMap (RGBA8) which is indexed by storage ID. Overlap combinations are pre-blended
// in the LUT by SegmentationVolume.buildSegmentColorLUT(), so no per-bit compositing is needed
// here. Returns vec4(0) when no segment is present or the overlay is disabled.
// *************************************************************************************************

// Converts [0,1]³ volume-texture coordinates to the integer voxel coordinates texelFetch needs.
ivec3 segVoxel(vec3 texCoord) {
    return ivec3(round(texCoord * vec3(textureSize(segTexture, 0)) - vec3(0.5)));
}

// Storage ID at a voxel; 0 (no segment) outside the volume.
uint segIdAt(ivec3 voxel) {
    ivec3 segSize = textureSize(segTexture, 0);
    if (any(lessThan(voxel, ivec3(0))) || any(greaterThanEqual(voxel, segSize))) return 0u;
    return texelFetch(segTexture, voxel, 0).r;
}

vec4 sampleSegOverlay(vec3 texCoord) {
    if (!segOverlayEnabled || segSegmentCount <= 0) return vec4(0.0);

    uint id = segIdAt(segVoxel(texCoord));
    if (id == 0u) return vec4(0.0);

    // Single LUT lookup — combination IDs already point to a pre-composited RGBA.
    return texelFetch(segColorMap, ivec2(int(id), 0), 0);
}

// Segmentation voxel mask — in include mode only the real voxels inside a visible segment are
// composited (the segmentation acts as a stencil, no colour overlay); in exclude mode those
// voxels are removed from the rendering. Visibility follows the colour LUT, so the mask reacts to
// the Segmentation tool checkboxes like the overlay does.
bool isSegMasked(vec3 texCoord) {
    if (segMaskMode == 0) return false;
    bool inside = sampleSegOverlay(texCoord).a > 0.0;
    return segMaskMode == 1 ? !inside : inside;
}

// Radius, in voxels, of the central differences giving the segment surface normal. The differences
// are summed over every radius up to this one rather than taken at a single one: each tap of a
// binary mask contributes only -1, 0 or +1, so one radius yields a handful of directions and the
// surface resolves into four flat shades. Summing three radii gives seventeen. Going wider keeps
// smoothing but starts rounding off features thinner than the radius.
const int SEG_NORMAL_RADIUS = 3;

// Fixed material weights. The anatomy takes its own from lightingMap, which is indexed by voxel
// intensity — meaningless for a label — so segments are lit with constants instead.
//
// The diffuse response spans [SEG_AMBIENT, 1] and therefore only ever *darkens* the palette
// colour. Letting it brighten instead makes the shading invisible: the palette is saturated, so
// any factor above 1 is clipped on the channel that carries the hue, and with a headlight most of
// a surface faces the viewer. The floor keeps an unlit face readable — a segment is an annotation
// before it is a lit surface.
const float SEG_AMBIENT = 0.35;
const float SEG_SPECULAR = 0.30;

// Occupancy of the *visible* segmentation at a voxel. Visibility matters: a hidden segmentation
// wrapped around a visible one would otherwise read as solid on both sides of the surface and
// flatten the gradient to zero, leaving the visible one unshaded.
float segOccupancy(ivec3 voxel) {
    uint id = segIdAt(voxel);
    return id != 0u && texelFetch(segColorMap, ivec2(int(id), 0), 0).a > 0.0 ? 1.0 : 0.0;
}

// Occupancy difference along one axis, summed over every stencil radius.
float segAxisDelta(ivec3 voxel, ivec3 axis) {
    float delta = 0.0;
    for (int r = 1; r <= SEG_NORMAL_RADIUS; ++r) {
        delta += segOccupancy(voxel - axis * r) - segOccupancy(voxel + axis * r);
    }
    return delta;
}

// Un-normalised surface normal of the visible segmentation, pointing outwards like gradient()
// does for the anatomy. Zero-length inside a segment and in empty space, where there is no surface.
vec3 segNormal(ivec3 voxel) {
    return vec3(
        segAxisDelta(voxel, ivec3(1, 0, 0)),
        segAxisDelta(voxel, ivec3(0, 1, 0)),
        segAxisDelta(voxel, ivec3(0, 0, 1)));
}

// Blinn-Phong response of the segment surface, as (diffuse factor, specular addition). The
// diffuse part is a scalar so the segment keeps its hue — colours are what identify a segment, and
// tinting them towards the light would cost more than the depth cue gains. The specular part is
// added as white on top, which is what makes a highlight read as one instead of clipping the hue.
// Returns (1, 0), i.e. unshaded, when no light is enabled.
vec2 segLighting(vec3 texCoord, vec3 N) {
    float diffuseSum = 0.0;
    float specularSum = 0.0;
    bool anyLight = false;
    for (int i = 0; i < 4; ++i) {
        if (lights[i].enabled) {
            anyLight = true;
            vec3 V = normalize(vec3(viewMatrix * lights[i].position) - texCoord);
            vec3 L = normalize(lights[i].position.xyz - texCoord);
            // Double sided, as the anatomy is: the ray may enter a segment from either side.
            vec3 n = dot(L, N) < 0.0 ? -N : N;
            float diffuseCoeff = max(dot(L, n), 0.0);
            vec3 H = normalize(L + V);
            diffuseSum += diffuseCoeff;
            specularSum +=
                diffuseCoeff > 0.0 ? pow(max(dot(H, n), 0.0), lights[i].specularPower) : 0.0;
        }
    }
    if (!anyLight) {
        return vec2(1.0, 0.0);
    }
    return vec2(
        SEG_AMBIENT + (1.0 - SEG_AMBIENT) * min(diffuseSum, 1.0),
        SEG_SPECULAR * min(specularSum, 1.0));
}

// Outward normal of the crosshair cut surface, from a central difference of the kept half-space.
// Derived from isCrosshairCut() itself rather than from the cut mode, so it follows all eighteen
// half-space, quadrant and octant modes — and any later one — without restating them. Zero when
// the sample is not within {delta} of a cut plane.
vec3 crosshairCutNormal(vec3 texCoord, float delta) {
    if (crosshairCutMode == 0 || !crosshairVisible) {
        return vec3(0.0);
    }
    vec3 dx = vec3(delta, 0.0, 0.0);
    vec3 dy = vec3(0.0, delta, 0.0);
    vec3 dz = vec3(0.0, 0.0, delta);
    return vec3(
        (isCrosshairCut(texCoord - dx) ? 0.0 : 1.0) - (isCrosshairCut(texCoord + dx) ? 0.0 : 1.0),
        (isCrosshairCut(texCoord - dy) ? 0.0 : 1.0) - (isCrosshairCut(texCoord + dy) ? 0.0 : 1.0),
        (isCrosshairCut(texCoord - dz) ? 0.0 : 1.0) - (isCrosshairCut(texCoord + dz) ? 0.0 : 1.0));
}

// *************************************************************************************************
// Accumulates the segmentation overlay along the ray, front to back, shared by the three rendering
// modes. Returns premultiplied RGBA.
//
// Lighting follows the global shading toggle and is evaluated once per ray, at the sample that
// first enters a visible segment — that is where the surface is, and where the normal is defined.
// The resulting scalar then scales every sample behind it, so a translucent segment is shaded as a
// whole rather than only on its first slab, and a ray never pays for more than one normal.
// *************************************************************************************************
vec4 accumulateSegOverlay(vec3 start, vec3 stepPos, vec3 ditheredRayStep, int sampleCount) {
    vec4 segAccum = vec4(0.0);
    vec3 rayPos = start;
    vec2 lighting = vec2(1.0, 0.0);
    bool entered = false;
    bool cameThroughCut = false;
    float stepLength = length(stepPos);

    for (int count = 0; count < sampleCount; count++) {
        rayPos += stepPos;
        vec3 texCoord = rayPos + ditheredRayStep;
        if (isCrosshairCut(texCoord)) {
            cameThroughCut = true;
            continue;
        }
        vec4 segColor = sampleSegOverlay(texCoord);
        if (segColor.a > 0.0) {
            if (!entered) {
                entered = true;
                if (shading) {
                    // A ray stepping straight out of the cut into the segment is looking at the cut
                    // plane, not at the segment's own surface: that face is flush with the segment
                    // interior, where segNormal() is zero, so take the plane's normal instead.
                    vec3 n = cameThroughCut ? crosshairCutNormal(texCoord, stepLength) : vec3(0.0);
                    if (dot(n, n) <= 0.0) {
                        n = segNormal(segVoxel(texCoord));
                    }
                    // Still nothing: the ray began inside the segment and there is no surface to
                    // light. Leaving the colour flat beats lighting it with an arbitrary normal.
                    if (dot(n, n) > 0.0) {
                        lighting = segLighting(texCoord, normalize(n));
                    }
                }
            }
            // Correct the per-voxel opacity for the ray step so the result is independent of the
            // sampling rate (depthSampleNumber) and the opacity slider stays perceptually usable.
            float segA = 1.0 - pow(1.0 - segColor.a, 1.0 / (float(depthSampleNumber) * SEG_OPACITY_REF_DIST));
            float alpha = (1.0 - segAccum.a) * segA;
            segAccum.rgb += alpha * min(segColor.rgb * lighting.x + lighting.y, vec3(1.0));
            segAccum.a += alpha;
            if (segAccum.a >= 0.99) break;
        } else {
            cameThroughCut = false;
        }
    }
    return segAccum;
}

vec4 rayCastingMip(Ray ray, float tmin, float tmax, vec2 uv) {
    vec3 start = (ray.origin.xyz + tmin * ray.direction.xyz + texelSize) / (texelSize + texelSize);
    vec3 end = (ray.origin.xyz + tmax * ray.direction.xyz + texelSize) / (texelSize + texelSize);

    float len = distance(end, start);
    int sampleCount = int(float(depthSampleNumber) * len);

    float mipPix = mipType == mipTypeMin ? 1.0 : 0.0;
    vec3 texCoord = vec3(0.0);
    float pix = 0.0;

    vec3 rayPos = start;
    float stepSize = 1.0 / sampleCount;
    vec3 stepPos = (end - start) * stepSize;
    vec3 ditheredRayStep = ditherRay ? stepPos * dithering(uv) : stepPos;

    int sumNb = 0;
    // In "segmentation only" mode the anatomy volume raymarch is skipped entirely.
    for (int count = 0; count < sampleCount && !segOnly; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
        if (isCrosshairCut(texCoord)) continue;
        if (isSegMasked(texCoord)) continue;
        pix = getNormalizedWindowLevel(texCoord);

        if (mipType == mipTypeMin) {
            vec4 pixel = applyTextureColor(pix);
            if (pixel.a > 0.01) {
                mipPix = min(mipPix, pix);
                sumNb++;
            }
        } else if (mipType == mipTypeMean) {
            vec4 pixel = applyTextureColor(pix);
            if (pixel.a > 0.01) {
                mipPix += pix;
                sumNb++;
            }
        } else {
            mipPix = max(mipPix, pix);
            if (mipPix >= 0.99) {
                break;
            }
        }
    }

    if (mipType == mipTypeMin && sumNb == 0) {
        mipPix = 0.0;
    } else if (mipType == mipTypeMean) {
        mipPix = sumNb == 0 ? 0.0 : mipPix / float(sumNb);
    }
    vec4 pixel = applyTextureColor(mipPix);
    pixel.a = min(pixel.a * opacityFactor, 1.0);

    // Overlay segmentation colours on top of the MIP result (or render them alone in seg-only
    // mode). Skipped in mask modes: the mask shows real voxels, not segment colours.
    if (segMaskMode == 0 && segOverlayEnabled && (pixel.a > 0.0 || segOnly)) {
        vec4 segAccum = accumulateSegOverlay(start, stepPos, ditheredRayStep, sampleCount);
        if (segOnly) {
            pixel = segAccum;
        } else if (segAccum.a > 0.0) {
            // segAccum.rgb is already premultiplied, so blend it over without re-scaling by its alpha
            pixel.rgb = segAccum.rgb + pixel.rgb * (1.0 - segAccum.a);
            pixel.a = max(pixel.a, segAccum.a);
        }
    }

    return pixel;
}

vec4 rayCastingComposite(Ray ray, float tmin, float tmax, vec2 uv) {
    vec3 start = (ray.origin.xyz + tmin * ray.direction.xyz + texelSize) / (texelSize + texelSize);
    vec3 end = (ray.origin.xyz + tmax * ray.direction.xyz + texelSize) / (texelSize + texelSize);

    float len = distance(end, start);
    int sampleCount = int(float(depthSampleNumber) * len);

    vec4 pxColor = vec4(0.0);
    vec3 texCoord = vec3(0.0);
    float pix = 0.0;

    vec3 rayPos = start;
    float stepSize = 1.0 / sampleCount;
    vec3 stepPos = (end - start) * stepSize;
    vec3 ditheredRayStep = ditherRay ? stepPos * dithering(uv) : stepPos;

    // In "segmentation only" mode the anatomy volume raymarch is skipped entirely.
    for (int count = 0; count < sampleCount && !segOnly; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
        if (isCrosshairCut(texCoord)) continue;
        if (isSegMasked(texCoord)) continue;
        pix = getNormalizedWindowLevel(texCoord);
        //pix = guassianFilter(texCoord, stepSize);
        vec4 pixel = applyTextureColor(pix);
        pixel.a = min(pixel.a * opacityFactor, 1.0);

        if (pixel.a > 0.0) {
            float alpha = (1.0 - pixel.a) * pxColor.a;
            if (shading) {
                vec3 normalPos = gradient(texCoord, stepSize);
                for (int i = 0; i < 4; ++i) {
                    if (lights[i].enabled) {
                        vec3 V = normalize(vec3(viewMatrix * lights[i].position) - texCoord);
                        vec3 L = normalize(lights[i].position.xyz - texCoord);
                        // double sided lighting
                        if (dot(L, normalPos) < 0.0) {
                            normalPos = -normalPos;
                        }
                        pxColor.rgb = pixel.rgb * blinnPhong(normalPos, V, L, i, pix, defaultDiffuse) * pixel.a + alpha * pxColor.rgb;
                    }
                }
            } else {
                pxColor.rgb = pixel.a * pixel.rgb + alpha * pxColor.rgb;
            }
            pxColor.a = pixel.a + alpha;
        }
        if (pxColor.a >= 0.99) {
            break;
        }
    }

    // Overlay segmentation colours on top of the composited volume
    if (segMaskMode == 0 && segOverlayEnabled && (pxColor.a > 0.0 || segOnly)) {
        vec4 segAccum = accumulateSegOverlay(start, stepPos, ditheredRayStep, sampleCount);
        if (segOnly) {
            pxColor = segAccum;
        } else if (segAccum.a > 0.0) {
            // segAccum.rgb is already premultiplied, so blend it over the volume without re-scaling by its alpha
            pxColor.rgb = segAccum.rgb + pxColor.rgb * (1.0 - segAccum.a);
            pxColor.a = max(pxColor.a, segAccum.a);
        }
    }

    if (pxColor.a >= 0.99) {
        pxColor.a = 1.0;
    }
    return pxColor;
}

vec4 rayCastingIsoSurface(Ray ray, float tmin, float tmax, vec2 uv) {
    vec3 start = (ray.origin.xyz + tmin * ray.direction.xyz + texelSize) / (texelSize + texelSize);
    vec3 end = (ray.origin.xyz + tmax * ray.direction.xyz + texelSize) / (texelSize + texelSize);

    float len = distance(end, start);
    int sampleCount = int(float(depthSampleNumber) * len);
    float stepLength = len / float(depthSampleNumber);

    vec3 rayPos = start;
    float stepSize = 1.0 / sampleCount;
    vec3 stepPos = (end - start) * stepSize;
    vec3 ditheredRayStep = ditherRay ? stepPos * dithering(uv) : stepPos;

    vec3 texCoord = vec3(0.0);
    vec4 pxColor = vec4(0.0);
    float pix = 0.0;
    float center = (windowCenter - outputLevelMin) / (outputLevelMax - outputLevelMin);

    bool prev_sign = pix < center;

    // In "segmentation only" mode the anatomy volume raymarch is skipped entirely.
    for (int count = 0; count < sampleCount && !segOnly; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
        if (isCrosshairCut(texCoord)) continue;
        if (isSegMasked(texCoord)) continue;
        pix = getNormalizedWindowLevel(texCoord);
        vec4 pixel = applyTextureColor(pix);
        bool sign_cur = pix > center;
        if (pixel.a > 0.0) {
            if (sign_cur != prev_sign) {
                vec3 normalPos = gradient(texCoord, stepSize);
                vec4 diffuse = applyTextureColor(pix);

                for (int i = 0; i < 4; ++i) {
                    if (lights[i].enabled) {
                        vec3 V = normalize(vec3(viewMatrix * lights[i].position) - texCoord);
                        vec3 L = normalize(lights[i].position.xyz - texCoord);
                        // double sided lighting
                        if (dot(L, normalPos) < 0.0) {
                            normalPos = -normalPos;
                        }

                        pxColor.rgb = pxColor.rgb + blinnPhong(normalPos, V, L, i, pix, diffuse.rgb);
                    }
                }
                pxColor.a = diffuse.a;
                break;
            }
        }

        if (pxColor.a >= 0.99) {
            break;
        }
    }

    if (pxColor.a >= 0.99) {
        pxColor.a = 1.0;
    }

    // Overlay segmentation colours on top of the iso-surface result
    if (segMaskMode == 0 && segOverlayEnabled && (pxColor.a > 0.0 || segOnly)) {
        vec4 segAccum = accumulateSegOverlay(start, stepPos, ditheredRayStep, sampleCount);
        if (segOnly) {
            pxColor = segAccum;
        } else if (segAccum.a > 0.0) {
            // segAccum.rgb is already premultiplied, so blend it over without re-scaling by its alpha
            pxColor.rgb = segAccum.rgb + pxColor.rgb * (1.0 - segAccum.a);
            pxColor.a = max(pxColor.a, segAccum.a);
        }
    }

    return pxColor;
}

vec4 slice(vec2 uv) {
    float w = 0.5;
    vec3 origin = vec3(uv, w) * texelSize;
    origin = (vec4(origin, 1.0) * viewMatrix).xyz + sliceOffset;
    float pix = getNormalizedWindowLevel(origin);
    if (textureSize(colorMap, 0).x > 2) {
        vec4 pixel = applyTextureColor(pix);
        pixel.a = min(pixel.a * opacityFactor, 1.0);
        return pixel;
    } else {
        if( applyTextureColor(0.0).r > 0){
            pix = 1.0 - pix;
        }
        return vec4(pix, pix, pix, 1.0f);
    }
}
