package org.weasis.dicom.wave;

import java.awt.image.DataBuffer;

public class AbstractWaveData implements WaveDataReadable {

    protected final DataBuffer data;
    protected final int nbOfChannels;
    protected final int nbSamplesPerChannel;

    public AbstractWaveData(DataBuffer data, int nbOfChannels, int nbSamplesPerChannel) {
        this.data = data;
        this.nbOfChannels = nbOfChannels;
        this.nbSamplesPerChannel = nbSamplesPerChannel;
    }

    @Override
    public DataBuffer getData() {
        return data;
    }

    @Override
    public int getNbOfChannels() {
        return nbOfChannels;
    }

    @Override
    public int getNbSamplesPerChannel() {
        return nbSamplesPerChannel;
    }

    @Override
    public double getSample(int index, ChannelDefinition channel) {
        return data.getElem(index * nbOfChannels + channel.getPosition()) * channel.getAmplitudeUnitScalingFactor()
            + channel.getBaseline();
    }

    @Override
    public int getRawSample(int index, ChannelDefinition channel) {
        return data.getElem(index * nbOfChannels + channel.getPosition());
    }

}