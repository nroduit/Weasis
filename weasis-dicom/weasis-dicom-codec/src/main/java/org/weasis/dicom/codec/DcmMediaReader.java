/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec;

import org.dcm4che3.data.Attributes;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeriesGroup;

public interface DcmMediaReader extends MediaReader {

    Attributes getDicomObject();

    void writeMetaData(MediaSeriesGroup group);
}
