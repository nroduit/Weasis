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

// Rendering type
uniform uint renderingType;
const uint typeComposite = 0x00000000u;
const uint typeMip = 0x00000001u;
const uint typeIsoSurface = 0x00000002u;
const uint typeSlice = 0x00000003u;
const uint typeSliceAxial = 0x00000004u;
const uint typeSliceCoronal = 0x00000005u;
const uint typeSliceSagittal = 0x00000006u;

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

const vec3 sliceOffset = vec3(0.5, 0.5, 0.0);