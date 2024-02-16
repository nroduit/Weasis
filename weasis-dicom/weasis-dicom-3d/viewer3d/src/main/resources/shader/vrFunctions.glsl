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
    for (int count = 0; count < sampleCount; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
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

    for (int count = 0; count < sampleCount; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
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

    for (int count = 0; count < sampleCount; count++) {
        rayPos += stepPos;
        texCoord = rayPos + ditheredRayStep;
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