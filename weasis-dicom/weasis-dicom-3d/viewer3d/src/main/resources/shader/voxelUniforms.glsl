// Texture data type
uniform uint textureDataType;
const uint dataTypeByte = 0x00000000u;
const uint dataTypeSignedSort = 0x00000001u;
const uint dataTypeUnsignedSort = 0x00000002u;
const uint dataTypeRGB8 = 0x00000003u;
const uint dataTypeRGBA8 = 0x00000004u;
const uint dataTypeRGBA32F = 0x00000005u;
const uint dataTypeFLOAT = 0x00000006u;

// LUT shape type
uniform uint lutShape;
const uint lutShapeLinear = 0x00000000u;
const uint lutShapeSigmoid = 0x00000001u;
const uint lutShapeSigmoidNorm = 0x00000002u;
const uint lutShapeLog = 0x00000003u;
const uint lutShapeLogInv = 0x00000004u;

// Geometry
uniform vec3 texelSize;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform int depthSampleNumber;

// Window/Level
uniform float inputLevelMin;
uniform float inputLevelMax;
uniform float outputLevelMin;
uniform float outputLevelMax;
uniform float windowWidth;
uniform float windowCenter;

// Rendering parameters
uniform bool shading;
uniform vec3 backgroundColor;
uniform vec3 lightColor;
uniform bool ditherRay = true;
uniform float opacityFactor;

// Empty-space hint: normalized LUT coordinates outside [visibleMin, visibleMax] have no opacity.
uniform float visibleMin = -1e30;
uniform float visibleMax = 1e30;
// Opacity factor by normalized gradient magnitude, sampled evenly over [0, 1].
const int gradientOpacitySamples = 32;
uniform bool gradientOpacityEnabled = false;
uniform float gradientOpacity[gradientOpacitySamples];

// Rendering type
uniform uint renderingType;
const uint typeComposite = 0x00000000u;
const uint typeMip = 0x00000001u;
const uint typeIsoSurface = 0x00000002u;
const uint typeSlice = 0x00000003u;
const uint typeSliceAxial = 0x00000004u;
const uint typeSliceCoronal = 0x00000005u;
const uint typeSliceSagittal = 0x00000006u;
const uint typePathTracing = 0x00000007u;

// MIP type
uniform uint mipType;
const uint mipTypeNone = 0x00000000u;
const uint mipTypeMin = 0x00000001u;
const uint mipTypeMean = 0x00000002u;
const uint mipTypeMax = 0x00000003u;

// Lighting
const vec4 lightPositionWorld = vec4(10.0, 0, 0, 1.0);
vec3 defaultAmbient = lightColor;
vec3 defaultDiffuse = lightColor;
vec3 defaultSpecular = lightColor;
struct LightParameters {
    vec4 position;
    float specularPower;
    bool enabled;
};
uniform LightParameters lights[4];

// Cinematic lighting: one directional key light casting shadows through the volume, local ambient
// occlusion and filmic tone mapping on top of the shading. Ignored while shading is off.
uniform bool  cinematic = false;
// Unit vector towards the key light, in view space (+z towards the viewer).
uniform vec3  keyLightDir = vec3(0.0, 0.0, 1.0);
uniform float shadowStrength = 0.0;
uniform int   shadowSteps = 24;
uniform float aoStrength = 0.0;
uniform int   aoSteps = 3;
uniform float exposure = 1.0;
// Scale applied to the shadow and occlusion step counts while the camera is being dragged.
uniform float cinematicScale = 1.0;
// Image-based lighting from the environment map bound on unit 6: nine RGB spherical-harmonic
// coefficients of its irradiance (order L00, L1-1, L10, L11, L2-2, L2-1, L20, L21, L22) and the
// highest mip level of its roughness-prefiltered radiance.
uniform bool  envEnabled = false;
uniform float envStrength = 1.0;
uniform vec3  envSh[9];
uniform float envMaxLod = 5.0;
// Progressive path tracing: index of the frame being accumulated (0 restarts the average held in
// historyMap on unit 7), the bounce budget and the highest LUT opacity, which bounds the extinction.
uniform int   frameIndex = 0;
uniform int   ptMaxBounces = 4;
uniform float ptMaxAlpha = 1.0;

const vec3 sliceOffset = vec3(0.5, 0.5, 0.5);

// MPR crosshair
uniform vec3  crosshairPos;
uniform mat3  crosshairRot;
uniform bool  crosshairVisible;
uniform int   crosshairCutMode;

// Ratio of the ray-cast resolution to the on-screen one, so pixel-sized overlays keep a constant
// apparent size when the pass is rendered at reduced resolution. Always 1.0 on the compute path.
uniform float overlayScale = 1.0;

// Segmentation overlay
uniform bool segOverlayEnabled = false;
// When true, only the segmentation is rendered; the anatomy volume raymarch is skipped.
uniform bool segOnly = false;
uniform int  segSegmentCount = 0;
// Segmentation voxel mask: 0 = none, 1 = include (render only the real voxels inside visible
// segments), 2 = exclude (remove those voxels from the rendering). No colour overlay is drawn.
uniform int  segMaskMode = 0;

