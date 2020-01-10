/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.imp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;

@XmlRootElement(name = "presentation")
public class XmlGraphicModel extends AbstractGraphicModel {
    private static final long serialVersionUID = 2427740058858913568L;

    public XmlGraphicModel() {
        super();
    }

    public XmlGraphicModel(ImageElement img) {
        super(buildReferences(img));
    }

    private static List<ReferencedSeries> buildReferences(ImageElement img) {
        String seriesUUID = (String) img.getTagValue(TagW.get("SeriesInstanceUID")); //$NON-NLS-1$
        if (seriesUUID == null) {
            seriesUUID = java.util.UUID.randomUUID().toString();
            img.setTag(TagW.get("SeriesInstanceUID"), seriesUUID); //$NON-NLS-1$
        }

        String uid = (String) img.getTagValue(TagW.get("SOPInstanceUID")); //$NON-NLS-1$
        if (uid == null) {
            uid = java.util.UUID.randomUUID().toString();
            img.setTag(TagW.get("SOPInstanceUID"), uid); //$NON-NLS-1$
        }

        List<Integer> frameList = new ArrayList<>(1);
        int frames = img.getMediaReader().getMediaElementNumber();
        if (frames > 1 && img.getKey() instanceof Integer) {
            frameList.add((Integer) img.getKey());
        }

        return Arrays.asList(new ReferencedSeries(seriesUUID, Arrays.asList(new ReferencedImage(uid, frameList))));
    }
}