/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.utils.bean.Measurement;

public interface ImageStatistics {
    Measurement IMAGE_MIN = new Measurement(Messages.getString("measure.min"), 1, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_MAX = new Measurement(Messages.getString("measure.max"), 2, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_MEAN = new Measurement(Messages.getString("measure.mean"), 3, false, true, true); //$NON-NLS-1$
    Measurement IMAGE_STD = new Measurement(Messages.getString("measure.stdev"), 4, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_SKEW = new Measurement(Messages.getString("measure.skew"), 5, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_KURTOSIS = new Measurement(Messages.getString("measure.kurtosis"), 6, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_PIXELS = new Measurement(Messages.getString("ImageStatistics.pixels"), 7, false, true, false);  //$NON-NLS-1$
    Measurement IMAGE_MEDIAN = new Measurement(Messages.getString("ImageStatistics.median"), 8, false, true, false);  //$NON-NLS-1$
    Measurement IMAGE_ENTROPY = new Measurement(Messages.getString("ImageStatistics.entropy"), 9, false, true, false);  //$NON-NLS-1$
    
    Measurement[] ALL_MEASUREMENTS = { IMAGE_PIXELS, IMAGE_MIN, IMAGE_MAX, IMAGE_MEDIAN, IMAGE_MEAN, IMAGE_STD, IMAGE_SKEW, IMAGE_KURTOSIS, IMAGE_ENTROPY };
}
