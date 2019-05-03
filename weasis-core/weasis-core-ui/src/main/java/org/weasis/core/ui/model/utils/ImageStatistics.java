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
package org.weasis.core.ui.model.utils;

import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.utils.bean.Measurement;

public interface ImageStatistics {
    Measurement IMAGE_PIXELS = new Measurement("Pixels", 1, false, true, false); 
    Measurement IMAGE_MIN = new Measurement(Messages.getString("measure.min"), 2, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_MAX = new Measurement(Messages.getString("measure.max"), 3, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_MEDIAN = new Measurement("Median", 4, false, true, false); 
    Measurement IMAGE_MEAN = new Measurement(Messages.getString("measure.mean"), 5, false, true, true); //$NON-NLS-1$    
    Measurement IMAGE_STD = new Measurement(Messages.getString("measure.stdev"), 6, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_SKEW = new Measurement(Messages.getString("measure.skew"), 7, false, true, false); //$NON-NLS-1$
    Measurement IMAGE_KURTOSIS = new Measurement(Messages.getString("measure.kurtosis"), 8, false, true, false); //$NON-NLS-1$

    Measurement[] ALL_MEASUREMENTS = { IMAGE_MIN, IMAGE_MAX, IMAGE_MEAN, IMAGE_STD, IMAGE_SKEW, IMAGE_KURTOSIS };
}
