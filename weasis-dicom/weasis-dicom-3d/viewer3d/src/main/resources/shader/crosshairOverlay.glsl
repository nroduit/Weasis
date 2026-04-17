// crosshairOverlay.glsl
// Shared MPR crosshair overlay function.
// Compatible with both GLSL 4.30 (volume.comp) and GLSL 3.30 (volumeFbo.frag).
//
// Required uniforms (declared in voxelUniforms*.glsl):
//   crosshairVisible, crosshairPos, crosshairRot, texelSize,
//   viewMatrix, projectionMatrix  (both pre-inverted on the CPU side).
//
// computeCrosshairColor(pixelCoords, imageDims):
//   Returns the crosshair colour when this pixel falls on a crosshair arm,
//   or vec4(-1.0) (sentinel: alpha < 0) when it does not.
//   The caller should write the returned colour only when alpha >= 0.

// ── Direction-based arm colour (mirrors AxisDirection.getDirectionColor) ─────
//
// Anatomical-axis base colours (matching PatientOrientation.Biped in Java):
//   R / L  (volume x-axis) : blue  = RGB(  0, 174, 207)
//   A / P  (volume y-axis) : red   = RGB(237,  28,  36)
//   H / F  (volume z-axis) : green = RGB( 13, 177,  75)
//
// The colour for a given arm direction is the weighted blend of these three
// colours (weights = absolute-value components of the normalised direction),
// with saturation forced to 1.0 — identical to the Java implementation.

const vec3 AXIS_COLOR_X = vec3(0.000, 0.682, 0.812); // R/L  → blue
const vec3 AXIS_COLOR_Y = vec3(0.929, 0.110, 0.141); // A/P  → red
const vec3 AXIS_COLOR_Z = vec3(0.051, 0.694, 0.294); // H/F  → green

// Mirrors Java: Color.RGBtoHSB(r,g,b) + Color.getHSBColor(hue, 1.0f, brightness).
// Forces saturation to 1.0 while preserving hue and brightness (= max component).
vec3 forceSaturation1(vec3 rgb) {
    float maxC  = max(rgb.r, max(rgb.g, rgb.b));
    float minC  = min(rgb.r, min(rgb.g, rgb.b));
    float delta = maxC - minC;
    // Achromatic or black: nothing to saturate — return as-is.
    if (delta < 1e-5 || maxC < 1e-5) return rgb;

    // Compute hue in [0, 1).
    float h;
    if (maxC == rgb.r) {
        h = mod((rgb.g - rgb.b) / delta, 6.0) / 6.0;
    } else if (maxC == rgb.g) {
        h = ((rgb.b - rgb.r) / delta + 2.0) / 6.0;
    } else {
        h = ((rgb.r - rgb.g) / delta + 4.0) / 6.0;
    }
    if (h < 0.0) h += 1.0;

    // HSV → RGB with s = 1.0, v = maxC  →  c = v, m = 0.
    float v  = maxC;
    float x  = v * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));
    float h6 = h * 6.0;
    if      (h6 < 1.0) return vec3(v, x, 0.0);
    else if (h6 < 2.0) return vec3(x, v, 0.0);
    else if (h6 < 3.0) return vec3(0.0, v, x);
    else if (h6 < 4.0) return vec3(0.0, x, v);
    else if (h6 < 5.0) return vec3(x, 0.0, v);
    else               return vec3(v, 0.0, x);
}

// Compute the crosshair colour for an arm whose volume-space direction is 'dir'.
// Replicates AxisDirection.getDirectionColor(): blends the three anatomical
// axis colours by |dir.x|, |dir.y|, |dir.z|, then forces saturation to 1.
vec3 axisDirectionColor(vec3 dir) {
    vec3 d  = normalize(dir);
    float wx = abs(d.x);
    float wy = abs(d.y);
    float wz = abs(d.z);
    vec3 mixed = clamp(wx * AXIS_COLOR_X + wy * AXIS_COLOR_Y + wz * AXIS_COLOR_Z, 0.0, 1.0);
    return forceSaturation1(mixed);
}
// ─────────────────────────────────────────────────────────────────────────────

vec4 computeCrosshairColor(ivec2 pixelCoords, ivec2 imageDims) {
    if (!crosshairVisible) return vec4(-1.0);

    // 1. Bring crosshair centre from volume [0,1]³ into model space.
    //    Model space: rotateX(90°) · translate(-0.5,-0.5,-0.5)
    //    AABB spans [-texelSize, +texelSize], so scale by 2*texelSize.
    vec3 centreModel = (crosshairPos - vec3(0.5)) * 2.0 * texelSize;

    // True view/proj matrices (uniforms are pre-inverted on the CPU).
    mat4 trueView = inverse(viewMatrix);
    mat4 trueProj = inverse(projectionMatrix);

    // 2. Arm directions in volume space, rotated by MPR global rotation.
    //    Arm 1: volume X axis (1,0,0) = R/L
    //    Arm 2: volume Y axis (0,1,0) = A/P
    //    Arm 3: volume Z axis (0,0,1) = S/I
    vec3 modelDir1 = normalize(crosshairRot * vec3(1.0, 0.0, 0.0));
    vec3 modelDir2 = normalize(crosshairRot * vec3(0.0, 1.0, 0.0));
    vec3 modelDir3 = normalize(crosshairRot * vec3(0.0, 0.0, 1.0));

    // 3. Project centre and arm endpoints to NDC.
    vec4 centreClip = trueProj * (trueView * vec4(centreModel, 1.0));
    if (abs(centreClip.w) < 1e-6) return vec4(-1.0);
    vec2 centreNDC = centreClip.xy / centreClip.w;

    float armStep = max(max(texelSize.x, texelSize.y), texelSize.z) * 0.3;
    vec4 end1Clip = trueProj * (trueView * vec4(centreModel + modelDir1 * armStep, 1.0));
    vec4 end2Clip = trueProj * (trueView * vec4(centreModel + modelDir2 * armStep, 1.0));
    vec4 end3Clip = trueProj * (trueView * vec4(centreModel + modelDir3 * armStep, 1.0));
    if (abs(end1Clip.w) < 1e-6 || abs(end2Clip.w) < 1e-6 || abs(end3Clip.w) < 1e-6)
        return vec4(-1.0);

    vec2 end1NDC = end1Clip.xy / end1Clip.w;
    vec2 end2NDC = end2Clip.xy / end2Clip.w;
    vec2 end3NDC = end3Clip.xy / end3Clip.w;

    // NDC → pixel coords.
    ivec2 centre = ivec2(round((centreNDC * 0.5 + 0.5) * vec2(imageDims)));
    ivec2 end1Px = ivec2(round((end1NDC   * 0.5 + 0.5) * vec2(imageDims)));
    ivec2 end2Px = ivec2(round((end2NDC   * 0.5 + 0.5) * vec2(imageDims)));
    ivec2 end3Px = ivec2(round((end3NDC   * 0.5 + 0.5) * vec2(imageDims)));

    // Screen-space arm directions — guard against degenerate case (axis into/out-of screen).
    vec2 rawDir1 = vec2(end1Px - centre);
    vec2 rawDir2 = vec2(end2Px - centre);
    vec2 rawDir3 = vec2(end3Px - centre);
    if (dot(rawDir1, rawDir1) < 0.5) rawDir1 = (end1NDC - centreNDC) * vec2(imageDims);
    if (dot(rawDir2, rawDir2) < 0.5) rawDir2 = (end2NDC - centreNDC) * vec2(imageDims);
    if (dot(rawDir3, rawDir3) < 0.5) rawDir3 = (end3NDC - centreNDC) * vec2(imageDims);
    vec2 screenDir1 = normalize(rawDir1);
    vec2 screenDir2 = normalize(rawDir2);
    vec2 screenDir3 = normalize(rawDir3);

    // 4. Per-pixel line test.
    const int ARM = 31; // half-length in pixels
    const int GAP = 5;  // gap around centre

    vec2 offset = vec2(pixelCoords - centre);

    float proj1 = dot(offset, screenDir1);
    float perp1 = abs(dot(offset, vec2(-screenDir1.y, screenDir1.x)));
    bool  onLine1 = (perp1 <= 1.5) && (abs(proj1) >= float(GAP)) && (abs(proj1) <= float(ARM));

    float proj2 = dot(offset, screenDir2);
    float perp2 = abs(dot(offset, vec2(-screenDir2.y, screenDir2.x)));
    bool  onLine2 = (perp2 <= 1.5) && (abs(proj2) >= float(GAP)) && (abs(proj2) <= float(ARM));

    float proj3 = dot(offset, screenDir3);
    float perp3 = abs(dot(offset, vec2(-screenDir3.y, screenDir3.x)));
    bool  onLine3 = (perp3 <= 1.5) && (abs(proj3) >= float(GAP)) && (abs(proj3) <= float(ARM));

    if (onLine1 || onLine2 || onLine3) {
        // Per-arm direction-based colour: mirrors AxisDirection.getDirectionColor().
        // Arm 1 = rotated X axis (R/L → blue when unrotated).
        // Arm 2 = rotated Y axis (A/P → red when unrotated).
        // Arm 3 = rotated Z axis (H/F → green when unrotated).
        // When two arms overlap the first (lowest index) takes priority.
        vec3 armCol;
        if      (onLine1) armCol = axisDirectionColor(modelDir1);
        else if (onLine2) armCol = axisDirectionColor(modelDir2);
        else              armCol = axisDirectionColor(modelDir3);
        return vec4(armCol, 1.0);
    }

    return vec4(-1.0); // not on any crosshair arm
}

