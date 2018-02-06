package org.weasis.dicom.wave;

import java.awt.image.DataBuffer;

public interface WaveDataReadable {

    DataBuffer getData();

    double getSample(int index, ChannelDefinition channel);

    int getRawSample(int index, ChannelDefinition channel);

    int getNbOfChannels();

    int getNbSamplesPerChannel();

}