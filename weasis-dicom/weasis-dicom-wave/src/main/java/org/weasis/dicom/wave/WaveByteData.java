package org.weasis.dicom.wave;

import java.awt.image.DataBufferByte;

public class WaveByteData extends AbstractWaveData {

    public WaveByteData(DataBufferByte data, int nbOfChannels, int nbSamplesPerChannel) {
        super(data, nbOfChannels, nbSamplesPerChannel);
    }

}
