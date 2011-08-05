package org.weasis.core.ui.graphic;

public interface ImageStatistics {
    Measurement IMAGE_MIN = new Measurement("Min", 1, false, true, false);
    Measurement IMAGE_MAX = new Measurement("Max", 2, false, true, false);
    Measurement IMAGE_MEAN = new Measurement("Mean", 3, false, true, true);
    Measurement IMAGE_STD = new Measurement("StDev", 4, false, false, false);
    Measurement IMAGE_SKEW = new Measurement("Skewness", 5, false, false, false);
    Measurement IMAGE_KURTOSIS = new Measurement("Kurtosis", 6, false, false, false);

    Measurement[] ALL_MEASUREMENTS = { IMAGE_MIN, IMAGE_MAX, IMAGE_MEAN, IMAGE_STD, IMAGE_SKEW, IMAGE_KURTOSIS };
}
