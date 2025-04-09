// *************************************************************************************************
// Get voxel value from 3D texture
// *************************************************************************************************

float getVoxelValue(vec3 coordinates) {
    return texture(volTexture, coordinates).r;
}

float getOriginalVoxelValue(float texValue) {
    if (textureDataType == dataTypeByte) {
        return texValue * 255.0;
    } else if (textureDataType == dataTypeSignedSort) {
        return texValue * 65535.0 - 32768.0;
    } else if (textureDataType == dataTypeUnsignedSort) {
        return texValue * 65535.0;
    } else return texValue;
}

float getOriginalVoxelNormalizedValue(vec3 texCoord) {
    float pix = getOriginalVoxelValue(getVoxelValue(texCoord));
    return (pix - outputLevelMin) / (outputLevelMax - outputLevelMin);
}

float getOriginalVoxelNormalizedValue(vec3 texCoord, float min, float max) {
    float pix = getOriginalVoxelValue(getVoxelValue(texCoord));
    if (pix >= min && pix <= max) {
        return (pix - outputLevelMin) / (outputLevelMax - outputLevelMin);
    }
    return 0.0;
}

// *************************************************************************************************
// Window/Level and tranfert function
// *************************************************************************************************

float getWindowLevelLinear(float pixValue) {
    float slope = (outputLevelMax - outputLevelMin) / windowWidth;
    float intercept = outputLevelMax - slope * (windowCenter + (windowWidth / 2.0));
    float val = pixValue * slope + intercept;
    return clamp(val, outputLevelMin, outputLevelMax);
}

float sigmoidLUT(float nFactor, float outRange, float pixValue) {
    return outRange / (1 + exp((2 * nFactor / 10.0) * (pixValue - windowCenter) / windowWidth));
}

float exponentialLUT(float nFactor, float outRange, float pixValue) {
    return outRange * exp((nFactor / 10.0) * (pixValue - windowCenter) / windowWidth);
}

float logarithmiclLUT(float nFactor, float outRange, float pixValue) {
    return outRange * log((nFactor / 10.0) * (1 + (pixValue - windowCenter) / windowWidth));
}

float getWindowLevelFunc(float pixValue, bool normalize, uint type) {
    float nFactor = type == lutShapeSigmoid ? -20 : 20; // factor defined by default in Dicom standard ( -20*2/10 = -4 )
    float outRange = outputLevelMax - outputLevelMin;
    float val = 0;

    if (type == lutShapeSigmoid) {
        val = sigmoidLUT(nFactor, outRange, pixValue);
    } else if (type == lutShapeLogInv) {
        val = exponentialLUT(nFactor, outRange, pixValue);
    } else if (type == lutShapeLog) {
        val = logarithmiclLUT(nFactor, outRange, pixValue);
    }

    if (normalize) {
        float lowLevel = windowCenter - windowWidth / 2.0;
        float highLevel = windowCenter + windowWidth / 2.0;
        float minValue = 0;;
        float maxValue = 0;
        if (type == lutShapeSigmoid) {
            minValue = sigmoidLUT(nFactor, outRange, lowLevel);
            maxValue = sigmoidLUT(nFactor, outRange, highLevel);
        } else if (type == lutShapeLogInv) {
            minValue = exponentialLUT(nFactor, outRange, lowLevel);
            maxValue = exponentialLUT(nFactor, outRange, highLevel);
        } else if (type == lutShapeLog) {
            minValue = logarithmiclLUT(nFactor, outRange, lowLevel);
            maxValue = logarithmiclLUT(nFactor, outRange, highLevel);
        }
        val = (val - minValue) * outRange / abs(maxValue - minValue);
    }

    return clamp(val + outputLevelMin, outputLevelMin, outputLevelMax);;
}

float getWindowLevel(vec3 texCoord) {
    float val = getOriginalVoxelValue(getVoxelValue(texCoord));
    if (lutShape == lutShapeLinear) {
        val = getWindowLevelLinear(val);
    } else if (lutShape == lutShapeSigmoid) {
        val = getWindowLevelFunc(val, false, lutShapeSigmoid);
    } else if (lutShape == lutShapeSigmoidNorm) {
        val = getWindowLevelFunc(val, true, lutShapeSigmoid);
    } else if (lutShape == lutShapeLog) {
        val = getWindowLevelFunc(val, true, lutShapeLog);
    } else if (lutShape == lutShapeLogInv) {
        val = getWindowLevelFunc(val, true, lutShapeLogInv);
    } else {
        val = getWindowLevelLinear(val);
    }
    return val;
}

float getNormalizedWindowLevel(vec3 texCoord) {
    // normalize to 0-1 range according to the outputRange
    return (getWindowLevel(texCoord) - outputLevelMin) / (outputLevelMax - outputLevelMin);
}
