/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.display;

import java.awt.image.RenderedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AbstractOperation;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.DicomImageElement;

public class WindowLevelOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowLevelOperation.class);

    public static final String name = Messages.getString("WindowLevelOperation.title"); //$NON-NLS-1$

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        ImageElement image = (imageOperation != null) ? imageOperation.getImage() : null;

        Float window = (Float) imageOperation.getActionValue(ActionW.WINDOW.cmd());
        Float level = (Float) imageOperation.getActionValue(ActionW.LEVEL.cmd());

        if (image == null || source == null) {
            LOGGER.warn("Cannot apply \"{}\" ", name);
        } else if (image instanceof DicomImageElement) {
            // LookupTableJAI lookup = ((DicomImageElement) image).getVOILookup(window, level);
            //
            // if (lookup != null) {
            // ParameterBlock pb = new ParameterBlock();
            // pb.addSource(source);
            // pb.add(lookup);
            // // Will add tiles in cache tile memory
            // result = JAI.create("lookup", pb, null);
            // }

            result = ImageToolkit.getDefaultRenderedImage(image, source, window, level);
        }

        return result;
    }

    @Override
    public String getOperationName() {
        return name;
    }

}
