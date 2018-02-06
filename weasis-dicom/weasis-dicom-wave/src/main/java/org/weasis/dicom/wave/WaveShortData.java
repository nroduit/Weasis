package org.weasis.dicom.wave;

import java.awt.image.DataBufferShort;

public class WaveShortData extends AbstractWaveData {
    public WaveShortData(DataBufferShort data, int nbOfChannels, int nbSamplesPerChannel) {
        super(data, nbOfChannels, nbSamplesPerChannel);
    }
}
