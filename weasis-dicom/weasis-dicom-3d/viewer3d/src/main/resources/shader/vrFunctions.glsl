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

// Sampling rate the presets' opacity refers to (the default rendering quality).
const float OPACITY_REFERENCE_SAMPLES = 1024.0;

// On-the-fly gradient approximation (central differences of the windowed value, not normalized).
vec3 gradientRaw(vec3 uvw, float delta) {
    vec3 pix1;
    pix1.x = getNormalizedWindowLevel(uvw - vec3(delta, 0, 0)) - getNormalizedWindowLevel(uvw + vec3(delta, 0, 0));
    pix1.y = getNormalizedWindowLevel(uvw - vec3(0, delta, 0)) - getNormalizedWindowLevel(uvw + vec3(0, delta, 0));
    pix1.z = getNormalizedWindowLevel(uvw - vec3(0, 0, delta)) - getNormalizedWindowLevel(uvw + vec3(0, 0, delta));
    return pix1;
}

vec3 gradient(vec3 uvw, float delta) {
    return normalize(gradientRaw(uvw, delta));
}

// Opacity factor for a gradient magnitude in [0, 1], linearly interpolated in the uploaded table.
float gradientOpacityFactor(float magnitude) {
    float x = clamp(magnitude, 0.0, 1.0) * float(gradientOpacitySamples - 1);
    int i = int(floor(x));
    int j = min(i + 1, gradientOpacitySamples - 1);
    return mix(gradientOpacity[i], gradientOpacity[j], x - float(i));
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

// A colour volume already carries the colour that matters — a fusion blend, a Doppler map — so it
// keeps its own RGB and takes only the opacity from the preset LUT, which stays driven by the
// window/level applied to the voxel luminance.
vec4 applyVoxelColor(vec3 texCoord, float pix) {
    vec4 color = applyTextureColor(pix);
    return isColorTexture() ? vec4(getVoxelColor(texCoord), color.a) : color;
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
// Cinematic lighting — one directional key light casting shadows through the volume, local ambient
// occlusion around each lit sample and filmic tone mapping. Shadow and occlusion probes see the
// volume through the preset opacity only (no gradient opacity, no shading) and march at a coarser
// step than the view ray: their result is smooth by nature, and that saving is what keeps the mode
// interactive. Crosshair cuts and segmentation masks are honoured so removed voxels cast nothing.
// *************************************************************************************************

// Distance, in normalized volume units, a shadow probe travels before the light counts as unblocked.
const float SHADOW_RAY_LENGTH = 0.5;
// Reach of the occlusion probes around a sample.
const float AO_RADIUS = 0.05;
// Samples fainter than this reuse the shadow and occlusion of the last evaluated one...
const float CINEMATIC_MIN_ALPHA = 0.02;
// ...but at most this many visible samples in a row, so faint presets still get lit.
const int CINEMATIC_REFRESH_INTERVAL = 8;

const float AO_DIAG = 0.57735027;
const vec3 AO_DIRECTIONS[8] = vec3[8](
    vec3( AO_DIAG,  AO_DIAG,  AO_DIAG), vec3(-AO_DIAG,  AO_DIAG,  AO_DIAG),
    vec3( AO_DIAG, -AO_DIAG,  AO_DIAG), vec3(-AO_DIAG, -AO_DIAG,  AO_DIAG),
    vec3( AO_DIAG,  AO_DIAG, -AO_DIAG), vec3(-AO_DIAG,  AO_DIAG, -AO_DIAG),
    vec3( AO_DIAG, -AO_DIAG, -AO_DIAG), vec3(-AO_DIAG, -AO_DIAG, -AO_DIAG));

// The world box spans ±texelSize and the texture [0, 1]³ (see rayCasting*): positions shift and
// scale, directions only scale.
vec3 worldToTexture(vec3 world) {
    return (world + texelSize) / (texelSize + texelSize);
}

vec3 cameraPositionTex() {
    return worldToTexture((viewMatrix * vec4(0.0, 0.0, 0.0, 1.0)).xyz);
}

// Unit direction towards the key light in texture space; viewMatrix is the camera-to-world matrix.
vec3 keyLightDirTex() {
    vec3 world = mat3(viewMatrix) * keyLightDir;
    return normalize(world / texelSize);
}

bool outsideVolume(vec3 pos) {
    return any(lessThan(pos, vec3(0.0))) || any(greaterThan(pos, vec3(1.0)));
}

// Opacity of the preset at {pos} over a march step of {stepLength}, zero where a cut or a mask
// removed the voxel.
float probeOpacity(vec3 pos, float stepLength) {
    if (isCrosshairCut(pos) || isSegMasked(pos)) return 0.0;
    float pix = getNormalizedWindowLevel(pos);
    if (pix < visibleMin || pix > visibleMax) return 0.0;
    float a = min(applyTextureColor(pix).a * opacityFactor, 1.0);
    return 1.0 - pow(1.0 - a, OPACITY_REFERENCE_SAMPLES * stepLength);
}

// Fraction of the key light reaching {origin}: the transmittance of a coarse march towards it. The
// start is pushed a little along {n} (the normal facing the light) so a lit surface does not shadow
// itself with its own boundary voxels.
float shadowTransmittance(vec3 origin, vec3 L, vec3 n, float jitter) {
    int steps = max(int(float(shadowSteps) * cinematicScale + 0.5), 4);
    float s = SHADOW_RAY_LENGTH / float(steps);
    vec3 pos = origin + n * s * 0.5 + L * s * (0.5 + jitter);
    float t = 1.0;
    for (int i = 0; i < steps; ++i) {
        if (outsideVolume(pos)) break;
        t *= 1.0 - probeOpacity(pos, s);
        if (t < 0.02) return 0.0;
        pos += L * s;
    }
    return t;
}

// Mean transmittance of short probes over the hemisphere around the outward normal {N}: 1 in the
// open, towards 0 inside a cavity or against a neighbouring structure.
float ambientOcclusion(vec3 origin, vec3 N, float jitter) {
    int steps = max(int(float(aoSteps) * cinematicScale + 0.5), 1);
    float s = AO_RADIUS / float(steps);
    float open = 0.0;
    for (int d = 0; d < 8; ++d) {
        vec3 dir = AO_DIRECTIONS[d];
        if (dot(dir, N) < 0.0) dir = -dir;
        vec3 pos = origin + dir * s * (0.5 + jitter);
        float t = 1.0;
        for (int i = 0; i < steps; ++i) {
            if (outsideVolume(pos)) break;
            t *= 1.0 - probeOpacity(pos, s);
            pos += dir * s;
        }
        open += t;
    }
    return open / 8.0;
}

// ---- Image-based lighting ----------------------------------------------------------------------
// The environment is described in view space (y up, +z towards the viewer) so it turns with the
// camera. viewMatrix is the camera-to-world rotation (plus translation), so its transpose brings
// world vectors to view space. Texture-space normals scale by 1/texelSize on the way to world
// space, directions by texelSize (the box spans ±texelSize for [0, 1]³).

vec3 normalToView(vec3 n) {
    return normalize(transpose(mat3(viewMatrix)) * (n / texelSize));
}

vec3 directionToView(vec3 d) {
    return normalize(transpose(mat3(viewMatrix)) * (d * texelSize));
}

// Equirectangular lookup, matching EnvironmentMap.direction() on the Java side.
vec2 equirectUv(vec3 d) {
    float u = atan(d.x, d.z) / (2.0 * 3.14159265) + 0.5;
    float v = acos(clamp(d.y, -1.0, 1.0)) / 3.14159265;
    return vec2(u, v);
}

// Irradiance around n from the nine SH coefficients (Ramamoorthi & Hanrahan), already over π.
vec3 shIrradiance(vec3 n) {
    const float c1 = 0.429043, c2 = 0.511664, c3 = 0.743125, c4 = 0.886227, c5 = 0.247708;
    return c1 * envSh[8] * (n.x * n.x - n.y * n.y)
         + c3 * envSh[6] * n.z * n.z
         + c4 * envSh[0]
         - c5 * envSh[6]
         + 2.0 * c1 * (envSh[4] * n.x * n.y + envSh[7] * n.x * n.z + envSh[5] * n.y * n.z)
         + 2.0 * c2 * (envSh[3] * n.x + envSh[1] * n.y + envSh[2] * n.z);
}

// Split-sum environment BRDF (Karis' analytic fit): x scales F0, y is the bias.
vec2 envBrdf(float NoV, float roughness) {
    const vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
    const vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
    vec4 r = roughness * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y;
    return vec2(-1.04, 1.04) * a004 + r.zw;
}

// Perceptual roughness matching the Blinn-Phong exponent, so the environment blur follows the
// shininess the user already controls.
float envRoughness() {
    return clamp(sqrt(2.0 / (lights[0].specularPower + 2.0)), 0.05, 1.0);
}

// Environment light on a sample: diffuse irradiance around n plus the prefiltered reflection,
// both weighted by the preset material like the key light's terms.
vec3 environmentLight(vec3 albedo, vec3 n, vec3 V, vec4 material) {
    vec3 nv = normalToView(n);
    vec3 vv = directionToView(V);
    float NoV = max(dot(nv, vv), 1e-4);
    float roughness = envRoughness();
    vec3 diffuse = albedo * material.y * shIrradiance(nv);
    vec3 prefiltered = textureLod(envMap, equirectUv(reflect(-vv, nv)), roughness * envMaxLod).rgb;
    vec2 brdf = envBrdf(NoV, roughness);
    vec3 specular = prefiltered * material.z * (0.04 * brdf.x + brdf.y);
    return envStrength * (diffuse + specular);
}

// Lit colour of a sample under the key light and, when enabled, the environment. The key light's
// diffuse and specular are scaled by the shadow; the environment (or the flat ambient without one)
// by the occlusion. The specular highlight is added untinted so it reads as a highlight on coloured
// tissue rather than a brighter patch of it. Double sided like blinnPhong(); the cast shadow, not
// the normal, is what darkens the side facing away from the light.
vec3 cinematicShade(vec3 albedo, vec3 N, vec3 V, vec3 L, float pix, float shadow, float occlusion) {
    vec4 material = texture(lightingMap, vec2(pix, 0.0));
    vec3 n = dot(L, N) < 0.0 ? -N : N;
    float diff = max(dot(L, n), 0.0);
    vec3 H = normalize(L + V);
    float spec = diff > 0.0 ? pow(max(dot(H, n), 0.0), lights[0].specularPower) : 0.0;
    vec3 indirect = envEnabled
        ? environmentLight(albedo, n, V, material)
        : lightColor * albedo * material.x;
    vec3 diffuse = albedo * material.y * diff * shadow;
    vec3 specular = vec3(material.z * spec * shadow);
    return indirect * occlusion + lightColor * (diffuse + specular);
}

// Shadow and occlusion factors of a sample, each already blended with its strength.
vec2 cinematicFactors(vec3 texCoord, vec3 N, vec3 L, float jitter) {
    vec3 n = dot(L, N) < 0.0 ? -N : N;
    float shadow = shadowStrength > 0.0
        ? mix(1.0, shadowTransmittance(texCoord, L, n, jitter), shadowStrength) : 1.0;
    float occlusion = aoStrength > 0.0
        ? mix(1.0, ambientOcclusion(texCoord, N, jitter), aoStrength) : 1.0;
    return vec2(shadow, occlusion);
}

#include "toneMapping.glsl"

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

// Blends the segmentation overlay accumulated along the ray over a rendering result, or replaces
// it in "segmentation only" mode. segAccum is premultiplied, so it composes without re-scaling.
vec4 blendSegOverlay(vec4 color, vec4 segAccum) {
    if (segOnly) {
        return segAccum;
    }
    if (segAccum.a > 0.0) {
        color.rgb = segAccum.rgb + color.rgb * (1.0 - segAccum.a);
        color.a = max(color.a, segAccum.a);
    }
    return color;
}

vec4 rayCastingMip(Ray ray, float tmin, float tmax, vec2 uv) {
    vec3 start = (ray.origin.xyz + tmin * ray.direction.xyz + texelSize) / (texelSize + texelSize);
    vec3 end = (ray.origin.xyz + tmax * ray.direction.xyz + texelSize) / (texelSize + texelSize);

    float len = distance(end, start);
    int sampleCount = int(float(depthSampleNumber) * len);

    float mipPix = mipType == mipTypeMin ? 1.0 : 0.0;
    // Colour of the sample the projection retains, so a colour volume keeps its hue through MIP.
    vec3 mipColor = vec3(0.0);
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
                if (pix < mipPix && isColorTexture()) {
                    mipColor = getVoxelColor(texCoord);
                }
                mipPix = min(mipPix, pix);
                sumNb++;
            }
        } else if (mipType == mipTypeMean) {
            vec4 pixel = applyTextureColor(pix);
            if (pixel.a > 0.01) {
                if (isColorTexture()) {
                    mipColor += getVoxelColor(texCoord);
                }
                mipPix += pix;
                sumNb++;
            }
        } else {
            if (pix > mipPix && isColorTexture()) {
                mipColor = getVoxelColor(texCoord);
            }
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
        mipColor = sumNb == 0 ? vec3(0.0) : mipColor / float(sumNb);
    }
    vec4 pixel = applyTextureColor(mipPix);
    if (isColorTexture()) {
        pixel.rgb = mipColor;
    }
    pixel.a = min(pixel.a * opacityFactor, 1.0);

    // Overlay segmentation colours on top of the MIP result (or render them alone in seg-only
    // mode). Skipped in mask modes: the mask shows real voxels, not segment colours.
    if (segMaskMode == 0 && segOverlayEnabled && (pixel.a > 0.0 || segOnly)) {
        pixel = blendSegOverlay(pixel, accumulateSegOverlay(start, stepPos, ditheredRayStep, sampleCount));
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
    float jitter = dithering(uv);
    vec3 ditheredRayStep = ditherRay ? stepPos * jitter : stepPos;

    bool cine = shading && cinematic;
    vec3 keyL = cine ? keyLightDirTex() : vec3(0.0);
    vec3 cameraTex = cine ? cameraPositionTex() : vec3(0.0);
    // Shadow and occlusion of the last evaluated sample, reused by the faint ones in between.
    vec2 cineFactors = vec2(1.0);
    int sinceEval = CINEMATIC_REFRESH_INTERVAL;

    // In "segmentation only" mode the anatomy volume raymarch is skipped entirely.
    for (int count = 0; count < sampleCount && !segOnly; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
        if (isCrosshairCut(texCoord)) continue;
        if (isSegMasked(texCoord)) continue;
        pix = getNormalizedWindowLevel(texCoord);
        // Empty-space hint: nothing to fetch where the preset has no opacity.
        if (pix < visibleMin || pix > visibleMax) continue;
        vec4 pixel = applyVoxelColor(texCoord, pix);
        // Presets are authored for the reference sampling rate: keep their opacity per unit length
        // whatever the current rate, so quality changes do not thicken or thin the rendering.
        pixel.a = 1.0 - pow(1.0 - min(pixel.a * opacityFactor, 1.0), OPACITY_REFERENCE_SAMPLES / float(depthSampleNumber));

        // One gradient serves both the edge emphasis and the shading.
        vec3 grad = vec3(0.0);
        if (pixel.a > 0.0 && (gradientOpacityEnabled || shading)) {
            grad = gradientRaw(texCoord, stepSize);
            if (gradientOpacityEnabled) {
                pixel.a *= gradientOpacityFactor(length(grad));
            }
        }

        if (pixel.a > 0.0) {
            float alpha = (1.0 - pixel.a) * pxColor.a;
            if (cine) {
                vec3 N = length(grad) > 0.0 ? normalize(grad) : vec3(0.0, 0.0, 1.0);
                sinceEval++;
                if (pixel.a >= CINEMATIC_MIN_ALPHA || sinceEval >= CINEMATIC_REFRESH_INTERVAL) {
                    cineFactors = cinematicFactors(texCoord, N, keyL, jitter);
                    sinceEval = 0;
                }
                vec3 V = normalize(cameraTex - texCoord);
                vec3 lit = cinematicShade(pixel.rgb, N, V, keyL, pix, cineFactors.x, cineFactors.y);
                pxColor.rgb = lit * pixel.a + alpha * pxColor.rgb;
            } else if (shading) {
                vec3 normalPos = length(grad) > 0.0 ? normalize(grad) : vec3(0.0, 0.0, 1.0);
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
    if (cine) {
        pxColor = toneMapPremultiplied(pxColor);
    }

    // Overlay segmentation colours on top of the composited volume
    if (segMaskMode == 0 && segOverlayEnabled && (pxColor.a > 0.0 || segOnly)) {
        pxColor = blendSegOverlay(pxColor, accumulateSegOverlay(start, stepPos, ditheredRayStep, sampleCount));
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
    float jitter = dithering(uv);
    vec3 ditheredRayStep = ditherRay ? stepPos * jitter : stepPos;
    bool cine = shading && cinematic;

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
        vec4 pixel = applyVoxelColor(texCoord, pix);
        bool sign_cur = pix > center;
        if (pixel.a > 0.0) {
            if (sign_cur != prev_sign) {
                vec3 normalPos = gradient(texCoord, stepSize);
                vec4 diffuse = pixel;

                if (cine) {
                    vec3 L = keyLightDirTex();
                    vec3 V = normalize(cameraPositionTex() - texCoord);
                    vec2 factors = cinematicFactors(texCoord, normalPos, L, jitter);
                    pxColor.rgb = cinematicShade(diffuse.rgb, normalPos, V, L, pix, factors.x, factors.y);
                    pxColor.a = diffuse.a;
                    break;
                }
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
    if (cine) {
        pxColor.rgb = toneMap(pxColor.rgb);
    }

    // Overlay segmentation colours on top of the iso-surface result
    if (segMaskMode == 0 && segOverlayEnabled && (pxColor.a > 0.0 || segOnly)) {
        pxColor = blendSegOverlay(pxColor, accumulateSegOverlay(start, stepPos, ditheredRayStep, sampleCount));
    }

    return pxColor;
}

vec4 slice(vec2 uv) {
    float w = 0.5;
    vec3 origin = vec3(uv, w) * texelSize;
    origin = (vec4(origin, 1.0) * viewMatrix).xyz + sliceOffset;
    float pix = getNormalizedWindowLevel(origin);
    if (textureSize(colorMap, 0).x > 2) {
        vec4 pixel = applyVoxelColor(origin, pix);
        pixel.a = min(pixel.a * opacityFactor, 1.0);
        return pixel;
    } else if (isColorTexture()) {
        return vec4(getVoxelColor(origin), 1.0f);
    } else {
        if( applyTextureColor(0.0).r > 0){
            pix = 1.0 - pix;
        }
        return vec4(pix, pix, pix, 1.0f);
    }
}

#include "ptConvergence.glsl"

// *************************************************************************************************
// Progressive path tracing — Monte Carlo samples per pixel averaged into historyMap across frames
// while the scene stands still. The LUT opacity acts as a density: delta tracking finds the next
// collision, and each collision scatters like a surface (Lambert plus a Blinn-Phong highlight) or
// like a cloud (isotropic phase), chosen with a probability that grows with the gradient magnitude.
// The key light and the environment are both gathered at every collision through ratio-tracked
// shadow rays, the environment as its cosine-weighted mean around the normal so only the visibility
// stays stochastic; the lighting of a collision blends the two lobes by that same probability, so
// the choice only steers the continuation. Everything is linear radiance: the display image is
// tone-mapped from the average, never the average itself.
//
// Two random sources: the decisions that shape a path (pixel jitter, lobe choice, roulette and the
// scattering direction) come from a padded 2D Sobol sequence with hash-based Owen scrambling
// (Burley 2020), which converges faster than white noise and leaves the denoiser blue-ish noise;
// the tracking loops, which draw an unbounded number of values, use a hash.
// *************************************************************************************************

// Extinction, per unit of normalized volume length, of a fully opaque LUT entry. Bounds the null
// collision rate: the mean free path of an opaque voxel is a couple of voxels on a 512³ volume.
const float PT_DENSITY = 256.0;
// Central-difference half width of the gradient giving the surface normal.
const float PT_GRADIENT_DELTA = 1.0 / 384.0;
// Radiance of the key light relative to the environments, whose sky sits around 0.5.
const float PT_KEY_LIGHT = 2.0;
// Sky radiance when no environment is selected.
const float PT_AMBIENT_SKY = 0.3;
// Bounds the tracking loops whatever the density.
const int PT_MAX_STEPS = 2048;
// Blocks of the majorant grid a ray may cross before its walk gives up.
const int PT_MAX_BLOCKS = 512;
// Depth recorded for a primary ray that met nothing; beyond any distance inside the unit cube.
const float PT_ESCAPED_DEPTH = 4.0;

uint ptState;
uint ptSampleIndex;
uint ptPixelSeed;
uint ptPair;

uint ptHash(uint v) {
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

// White noise for the tracking loops.
float ptRand() {
    ptState = ptHash(ptState);
    return float(ptState) / 4294967296.0;
}

void ptSeed(ivec2 pixel, int sampleIndex) {
    ptPixelSeed = ptHash(uint(pixel.x) * 1973u + uint(pixel.y) * 9277u + 1u);
    ptState = ptHash(ptPixelSeed + uint(sampleIndex) * 26699u);
    ptSampleIndex = uint(sampleIndex);
    ptPair = 0u;
}

uint ptReverseBits(uint x) {
    x = ((x & 0x55555555u) << 1u) | ((x & 0xAAAAAAAAu) >> 1u);
    x = ((x & 0x33333333u) << 2u) | ((x & 0xCCCCCCCCu) >> 2u);
    x = ((x & 0x0F0F0F0Fu) << 4u) | ((x & 0xF0F0F0F0u) >> 4u);
    x = ((x & 0x00FF00FFu) << 8u) | ((x & 0xFF00FF00u) >> 8u);
    return (x << 16u) | (x >> 16u);
}

// Nested uniform (Owen) scramble of a base-2 sequence value by a hash: each bit is flipped by a
// function of the more significant ones (Laine–Karras permutation on the reversed bits).
uint ptOwenScramble(uint x, uint seed) {
    x = ptReverseBits(x);
    x ^= x * 0x3d20adeau;
    x += seed;
    x *= (seed >> 16u) | 1u;
    x ^= x * 0x05526c56u;
    x ^= x * 0x53a22864u;
    return ptReverseBits(x);
}

// Second Sobol dimension of index i; the first one is its bit reversal.
uint ptSobolSecond(uint i) {
    uint y = 0u;
    uint v = 1u << 31u;
    for (int b = 0; b < 32 && i != 0u; ++b) {
        if ((i & 1u) != 0u) y ^= v;
        i >>= 1u;
        v ^= v >> 1u;
    }
    return y;
}

// Next 2D low-discrepancy point of the sample. Each pair of dimensions is the 2D Sobol sequence
// under its own per-pixel shuffle and scrambles, so pairs are decorrelated while every pixel still
// sees a stratified prefix of the sequence as the samples accumulate.
vec2 ptLds2() {
    uint seed = ptHash(ptPixelSeed + ptPair * 0x9E3779B9u);
    ptPair++;
    uint index = ptOwenScramble(ptSampleIndex, seed);
    uint x = ptOwenScramble(ptReverseBits(index), ptHash(seed + 1u));
    uint y = ptOwenScramble(ptSobolSecond(index), ptHash(seed + 2u));
    return vec2(x, y) / 4294967296.0;
}

// Extinction at pos from the LUT opacity; zero where a cut or a mask removed the voxel.
float ptExtinction(vec3 pos, out vec3 albedo, out float pix) {
    albedo = vec3(0.0);
    pix = 0.0;
    if (isCrosshairCut(pos) || isSegMasked(pos)) return 0.0;
    pix = getNormalizedWindowLevel(pos);
    if (pix < visibleMin || pix > visibleMax) return 0.0;
    vec4 c = applyVoxelColor(pos, pix);
    albedo = c.rgb;
    return min(c.a * opacityFactor, 1.0) * PT_DENSITY;
}

// Distance along dir at which a ray starting inside [0, 1]³ leaves it.
float ptExitDistance(vec3 origin, vec3 dir) {
    vec3 inv = 1.0 / dir;
    vec3 t1 = (vec3(0.0) - origin) * inv;
    vec3 t2 = (vec3(1.0) - origin) * inv;
    vec3 tfar = max(t1, t2);
    return max(min(tfar.x, min(tfar.y, tfar.z)), 0.0);
}

// ---- Majorant map -------------------------------------------------------------------------------
// The global bound sigmaMax is the extinction of the most opaque LUT entry: through air or faint
// tissue, delta tracking at that rate takes hundreds of null collisions that each cost a volume
// and a LUT fetch and yield nothing. The majorant map holds, baked for the current preset and
// window, the highest LUT alpha of each block of the volume, zero wherever the preset leaves the
// block transparent, and on its level 2 the highest alpha of each 4×4×4 group of blocks. A ray
// walks the blocks it crosses, leaps over the empty groups, skips the empty blocks and tracks the
// others at their own bound.

// Blocks per axis a coarse cell of the map spans, and its mip level.
const int PT_COARSE_SPAN = 4;
const int PT_COARSE_LEVEL = 2;

// Extinction bound of a block. Until the map of the volume is baked, a single placeholder block
// carries the global bound.
float ptBlockMajorant(ivec3 block, float sigmaMax) {
    if (!ptGridEnabled) return sigmaMax;
    float alpha = texelFetch(majorantMap, block, 0).r;
    return min(min(alpha * opacityFactor, 1.0) * PT_DENSITY, sigmaMax);
}

// Whether the coarse cell holding the block bounds nothing at all.
bool ptCoarseEmpty(ivec3 block) {
    return ptGridEnabled && texelFetch(majorantMap, block / PT_COARSE_SPAN, PT_COARSE_LEVEL).r <= 0.0;
}

// Distance along the ray at which it leaves the coarse cell holding the block.
float ptCoarseExit(vec3 origin, vec3 dir, ivec3 block) {
    vec3 cell = float(PT_COARSE_SPAN) / vec3(ptGridSize);
    vec3 lo = vec3(block / PT_COARSE_SPAN) * cell;
    vec3 inv = 1.0 / vec3(
        abs(dir.x) < 1e-8 ? 1e-8 : dir.x,
        abs(dir.y) < 1e-8 ? 1e-8 : dir.y,
        abs(dir.z) < 1e-8 ? 1e-8 : dir.z);
    vec3 t1 = (lo - origin) * inv;
    vec3 t2 = (lo + cell - origin) * inv;
    vec3 tfar = max(t1, t2);
    return min(tfar.x, min(tfar.y, tfar.z));
}

// Position of a ray in the block grid: the block it is in and the distances along the ray at which
// it entered it and will leave it, both bounded by the distance the ray is followed for.
struct GridWalk {
    ivec3 block;
    ivec3 step;
    vec3 tNext;
    vec3 tDelta;
    float tEnter;
    float tExit;
};

// The walk from distance t0 along the ray; the block is taken a hair beyond t0 so a start on a
// boundary lands in the block being entered.
GridWalk ptGridStart(vec3 origin, vec3 dir, float tmax, float t0) {
    ivec3 size = ptGridSize;
    vec3 cell = 1.0 / vec3(size);
    GridWalk w;
    vec3 p = origin + dir * (t0 + 1e-5);
    w.block = clamp(ivec3(floor(p * vec3(size))), ivec3(0), size - 1);
    w.step = ivec3(sign(dir));
    vec3 safeDir = vec3(
        abs(dir.x) < 1e-8 ? 1e-8 : dir.x,
        abs(dir.y) < 1e-8 ? 1e-8 : dir.y,
        abs(dir.z) < 1e-8 ? 1e-8 : dir.z);
    w.tDelta = abs(cell / safeDir);
    vec3 boundary = (vec3(w.block) + max(vec3(w.step), vec3(0.0))) * cell;
    w.tNext = (boundary - origin) / safeDir;
    // An axis the ray does not move along is never crossed.
    if (abs(dir.x) < 1e-8) w.tNext.x = 1e30;
    if (abs(dir.y) < 1e-8) w.tNext.y = 1e30;
    if (abs(dir.z) < 1e-8) w.tNext.z = 1e30;
    w.tEnter = t0;
    w.tExit = min(min(w.tNext.x, min(w.tNext.y, w.tNext.z)), tmax);
    return w;
}

// Steps into the next block; false once the ray has left the grid or covered tmax.
bool ptGridAdvance(inout GridWalk w, float tmax) {
    if (w.tExit >= tmax) return false;
    if (w.tNext.x <= w.tNext.y && w.tNext.x <= w.tNext.z) {
        w.block.x += w.step.x;
        w.tNext.x += w.tDelta.x;
    } else if (w.tNext.y <= w.tNext.z) {
        w.block.y += w.step.y;
        w.tNext.y += w.tDelta.y;
    } else {
        w.block.z += w.step.z;
        w.tNext.z += w.tDelta.z;
    }
    if (any(lessThan(w.block, ivec3(0))) || any(greaterThanEqual(w.block, ptGridSize))) return false;
    w.tEnter = w.tExit;
    w.tExit = min(min(w.tNext.x, min(w.tNext.y, w.tNext.z)), tmax);
    return true;
}

// Moves the walk to the next block with a bound, leaping over the coarse cells that have none.
// False once the ray has left the grid or covered tmax first.
bool ptNextSegment(inout GridWalk w, vec3 origin, vec3 dir, float tmax, float sigmaMax, out float sigmaBlock) {
    sigmaBlock = 0.0;
    for (int i = 0; i < PT_MAX_BLOCKS; ++i) {
        if (ptCoarseEmpty(w.block)) {
            float tLeave = ptCoarseExit(origin, dir, w.block);
            if (tLeave >= tmax) return false;
            w = ptGridStart(origin, dir, tmax, tLeave);
            continue;
        }
        sigmaBlock = ptBlockMajorant(w.block, sigmaMax);
        if (sigmaBlock > 0.0) return true;
        if (!ptGridAdvance(w, tmax)) return false;
    }
    return false;
}

// Delta tracking: distance to the next real collision, or -1 when the ray leaves the volume first.
float ptFreeFlight(vec3 origin, vec3 dir, float tmax, float sigmaMax, out vec3 albedo, out float pix) {
    albedo = vec3(0.0);
    pix = 0.0;
    GridWalk w = ptGridStart(origin, dir, tmax, 0.0);
    int steps = 0;
    for (int b = 0; b < PT_MAX_BLOCKS; ++b) {
        float sigmaBlock;
        if (!ptNextSegment(w, origin, dir, tmax, sigmaMax, sigmaBlock)) return -1.0;
        float t = w.tEnter;
        while (steps < PT_MAX_STEPS) {
            ++steps;
            t -= log(max(1.0 - ptRand(), 1e-6)) / sigmaBlock;
            if (t >= w.tExit) break;
            float sigma = ptExtinction(origin + dir * t, albedo, pix);
            if (ptRand() * sigmaBlock < sigma) return t;
        }
        if (steps >= PT_MAX_STEPS || !ptGridAdvance(w, tmax)) return -1.0;
    }
    return -1.0;
}

// Ratio tracking: transmittance from origin to the edge of the volume along dir.
float ptTransmittance(vec3 origin, vec3 dir, float sigmaMax) {
    float tmax = ptExitDistance(origin, dir);
    float transmittance = 1.0;
    vec3 albedo;
    float pix;
    GridWalk w = ptGridStart(origin, dir, tmax, 0.0);
    int steps = 0;
    for (int b = 0; b < PT_MAX_BLOCKS; ++b) {
        float sigmaBlock;
        if (!ptNextSegment(w, origin, dir, tmax, sigmaMax, sigmaBlock)) break;
        float t = w.tEnter;
        while (steps < PT_MAX_STEPS) {
            ++steps;
            t -= log(max(1.0 - ptRand(), 1e-6)) / sigmaBlock;
            if (t >= w.tExit) break;
            transmittance *= 1.0 - ptExtinction(origin + dir * t, albedo, pix) / sigmaBlock;
            if (transmittance < 0.01) return 0.0;
        }
        if (steps >= PT_MAX_STEPS || !ptGridAdvance(w, tmax)) break;
    }
    return transmittance;
}

// Cosine-weighted mean of the environment radiance around a texture-space normal.
vec3 ptEnvDiffuse(vec3 n) {
    if (!envEnabled) return lightColor * PT_AMBIENT_SKY;
    return envStrength * shIrradiance(normalToView(n));
}

// Mean environment radiance over the sphere: the DC term of the irradiance over π.
vec3 ptEnvMean() {
    if (!envEnabled) return lightColor * PT_AMBIENT_SKY;
    return envStrength * 0.886227 * envSh[0];
}

vec3 ptCosineDirection(vec3 n, vec2 u) {
    float phi = 6.2831853 * u.x;
    float r = sqrt(u.y);
    vec3 t = normalize(abs(n.x) > 0.9 ? cross(n, vec3(0.0, 1.0, 0.0)) : cross(n, vec3(1.0, 0.0, 0.0)));
    vec3 b = cross(n, t);
    return normalize(t * (r * cos(phi)) + b * (r * sin(phi)) + n * sqrt(max(1.0 - u.y, 0.0)));
}

vec3 ptSphereDirection(vec2 u) {
    float z = 1.0 - 2.0 * u.x;
    float phi = 6.2831853 * u.y;
    float r = sqrt(max(1.0 - z * z, 0.0));
    return vec3(r * cos(phi), r * sin(phi), z);
}

// One radiance sample of the camera ray, premultiplied: alpha is 1 once the primary ray collides.
// feature receives the denoiser's guides: the normal at the first collision, scaled by how much of
// a surface it is (zero for a cloud or an escaped ray), and that collision's distance along the ray.
vec4 ptTraceSample(Ray ray, float tmin, out vec4 feature) {
    vec3 pos = clamp(worldToTexture(ray.origin + ray.direction * tmin), 0.0, 1.0);
    vec3 dir = normalize(ray.direction / texelSize);
    float sigmaMax = max(ptMaxAlpha * opacityFactor, 0.01) * PT_DENSITY;
    vec3 L = keyLightDirTex();
    vec3 radiance = vec3(0.0);
    vec3 throughput = vec3(1.0);
    float alpha = 0.0;
    float tEnd = ptExitDistance(pos, dir);
    feature = vec4(0.0, 0.0, 0.0, PT_ESCAPED_DEPTH);

    for (int bounce = 0; bounce <= ptMaxBounces; ++bounce) {
        vec3 albedo;
        float pix;
        float t = ptFreeFlight(pos, dir, tEnd, sigmaMax, albedo, pix);
        // An escaping path adds nothing: the environment was gathered at its last collision.
        if (t < 0.0) break;
        alpha = 1.0;
        pos += dir * t;
        vec4 material = texture(lightingMap, vec2(pix, 0.0));
        vec3 grad = gradientRaw(pos, PT_GRADIENT_DELTA);
        float gradLength = length(grad);
        float surfaceness = smoothstep(0.02, 0.15, gradLength);
        vec3 n = gradLength > 1e-6 ? grad / gradLength : vec3(0.0);
        if (dot(n, dir) > 0.0) n = -n;
        if (bounce == 0) feature = vec4(n * surfaceness, t);
        vec3 scatterAlbedo = albedo * material.y;
        vec2 u = ptLds2();
        bool surface = u.x < surfaceness;

        // Key light: both lobes, blended by the surface probability, under one shadow ray.
        float keyTransmittance = ptTransmittance(pos, L, sigmaMax);
        if (keyTransmittance > 0.0) {
            vec3 light = lightColor * (PT_KEY_LIGHT * keyTransmittance);
            float diff = max(dot(n, L), 0.0);
            vec3 H = normalize(L - dir);
            float spec = diff > 0.0 ? pow(max(dot(H, n), 0.0), lights[0].specularPower) : 0.0;
            vec3 surfaceTerm = scatterAlbedo * diff + material.z * spec;
            // Isotropic phase (1/4π) against the Lambert lobe (1/π) the throughput assumes.
            vec3 cloudTerm = scatterAlbedo * 0.25;
            radiance += throughput * light * mix(cloudTerm, surfaceTerm, surfaceness);
        }

        // Environment: its mean over the lobe, seen through one ratio-tracked ray in a direction
        // drawn from that lobe, which the path then continues along.
        vec2 v = ptLds2();
        vec3 next = surface ? ptCosineDirection(n, v) : ptSphereDirection(v);
        float envTransmittance = ptTransmittance(pos, next, sigmaMax);
        if (envTransmittance > 0.0) {
            vec3 env = ptEnvMean();
            if (surfaceness > 0.0) env = mix(env, ptEnvDiffuse(n), surfaceness);
            radiance += throughput * scatterAlbedo * envTransmittance * env;
        }

        throughput *= scatterAlbedo;
        dir = next;
        if (surface) pos += n * PT_GRADIENT_DELTA;
        if (bounce >= 2) {
            float survive = max(throughput.r, max(throughput.g, throughput.b));
            if (u.y > survive) break;
            throughput /= max(survive, 1e-3);
        }
        tEnd = ptExitDistance(pos, dir);
    }
    return vec4(radiance, alpha);
}

// Segmentation overlay along a camera ray through the volume, jittered by a step.
vec4 ptSegOverlay(Ray ray, float tmin, float tmax) {
    vec3 start = worldToTexture(ray.origin + tmin * ray.direction);
    vec3 end = worldToTexture(ray.origin + tmax * ray.direction);
    int sampleCount = max(int(float(depthSampleNumber) * distance(end, start)), 1);
    vec3 stepPos = (end - start) / float(sampleCount);
    return accumulateSegOverlay(start, stepPos, stepPos * ptRand(), sampleCount);
}

// Traces ptSamples jittered samples for the pixel and folds them, with their features and
// moments, into the running averages; a pixel already converged keeps its averages untouched.
// Returns the linear premultiplied average; the denoise pass tone-maps it for display.
vec4 pathTrace(vec2 uv, ivec2 pixelCoords, ivec2 imageDims, out vec4 feature, out vec2 moment) {
    vec4 history = vec4(0.0);
    vec2 historyMoment = vec2(0.0);
    if (frameIndex > 0) {
        history = texelFetch(historyMap, pixelCoords, 0);
        historyMoment = texelFetch(momentMap, pixelCoords, 0).rg;
        if (ptConverged(history, historyMoment)) {
            feature = texelFetch(featureMap, pixelCoords, 0);
            moment = historyMoment;
            return history;
        }
    }
    int samples = max(ptSamples, 1);
    bool overlayWanted = segMaskMode == 0 && segOverlayEnabled;
    // The overlay is deterministic and composes linearly, so averaging it with the samples is the
    // same as drawing it over the average; one march per frame serves every sample of the pixel.
    bool overlayTraced = false;
    vec4 overlay = vec4(0.0);
    vec4 sum = vec4(0.0);
    vec4 featureSum = vec4(0.0);
    float squareSum = 0.0;
    for (int s = 0; s < samples; ++s) {
        // Indexed by the pixel's own count, so a pixel that paused keeps a stratified prefix.
        ptSeed(pixelCoords, int(historyMoment.y) + s);
        vec2 jittered = uv + (ptLds2() - 0.5) * 2.0 / vec2(imageDims);
        Ray ray = CreateCameraRay(jittered);
        float tmin = 0.0;
        float tmax = 0.0;
        intersect(ray, tmin, tmax);
        vec4 result = vec4(0.0);
        vec4 sampleFeature = vec4(0.0, 0.0, 0.0, PT_ESCAPED_DEPTH);
        if (tmax >= tmin) {
            if (!segOnly) {
                result = ptTraceSample(ray, max(tmin, 0.0), sampleFeature);
            }
            if (overlayWanted && (result.a > 0.0 || segOnly)) {
                if (!overlayTraced) {
                    overlay = ptSegOverlay(ray, tmin, tmax);
                    overlayTraced = true;
                }
                result = blendSegOverlay(result, overlay);
            }
        }
        sum += result;
        featureSum += sampleFeature;
        float luminance = ptLuminance(result);
        squareSum += luminance * luminance;
    }
    float count = float(samples);
    vec4 result = sum / count;
    feature = featureSum / count;
    moment = vec2(squareSum / count, count);
    if (frameIndex > 0) {
        float weight = count / (historyMoment.y + count);
        result = mix(history, result, weight);
        feature = mix(texelFetch(featureMap, pixelCoords, 0), feature, weight);
        moment = vec2(mix(historyMoment.x, moment.x, weight), historyMoment.y + count);
    }
    return result;
}
