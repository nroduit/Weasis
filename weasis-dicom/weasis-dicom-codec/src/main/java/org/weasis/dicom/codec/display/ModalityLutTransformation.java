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
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.image.AbstractOperation;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.DicomImageElement;

@Deprecated
public class ModalityLutTransformation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModalityLutTransformation.class);

    public static final String name = "ModalityLutTransformation";

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {

        // RenderedImage sourceImage = (imageOperation != null) ? imageOperation.getSourceImage() : null;
        ImageElement image = (imageOperation != null) ? imageOperation.getImage() : null;
        // SampleModel sampleModel = (source != null) ? source.getSampleModel() : null;

        result = source;
        // if (image == null || source == null || sampleModel == null) {
        if (image == null || source == null) {
            LOGGER.warn("Cannot apply \"{}\" ", name);
        } else if (image instanceof DicomImageElement) {
            LookupTableJAI lookup = ((DicomImageElement) image).getModalityLookup();

            if (lookup != null) {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(lookup);
                // Will add tiles in cache tile memory
                result = JAI.create("lookup", pb, null);
            }
        }

        return result;
    }

    @Override
    public String getOperationName() {
        return name;
    }
}
