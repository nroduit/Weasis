/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.wave;

import java.awt.image.DataBuffer;

public interface WaveDataReadable {

    DataBuffer getData();

    double getSample(int index, ChannelDefinition channel);

    int getRawSample(int index, ChannelDefinition channel);

    int getNbOfChannels();

    int getNbSamplesPerChannel();

}