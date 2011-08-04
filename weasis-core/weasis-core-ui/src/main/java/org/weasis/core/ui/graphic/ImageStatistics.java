package org.weasis.core.ui.graphic;

public interface ImageStatistics {
    Measurement IMAGE_MIN = new Measurement("Min", false, true, false);
    Measurement IMAGE_MAX = new Measurement("Max", false, true, false);
    Measurement IMAGE_MEAN = new Measurement("Mean", false, true, true);
    Measurement IMAGE_STD = new Measurement("StDev", false, false, false);
    Measurement IMAGE_SKEW = new Measurement("Skewness", false, false, false);
    Measurement IMAGE_KURTOSIS = new Measurement("Kurtosis", false, false, false);

    Measurement[] ALL_MEASUREMENTS = { IMAGE_MIN, IMAGE_MAX, IMAGE_MEAN, IMAGE_STD, IMAGE_SKEW, IMAGE_KURTOSIS };
}
