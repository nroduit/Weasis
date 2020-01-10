/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.bind.annotation.XmlElement;

import org.weasis.core.ui.model.utils.imp.DefaultUUID;

public class ReferencedSeries extends DefaultUUID {
    private static final long serialVersionUID = -6824893762236081155L;

    private List<ReferencedImage> images;

    public ReferencedSeries() {
        this(null, null);
    }

    public ReferencedSeries(String uuid) {
        this(uuid, null);
    }

    public ReferencedSeries(String uuid, List<ReferencedImage> images) {
        super(uuid);
        setImages(images);
    }

    @XmlElement(name = "image")
    public List<ReferencedImage> getImages() {
        return images;
    }

    public void setImages(List<ReferencedImage> images) {
        this.images = Optional.ofNullable(images).orElseGet(ArrayList::new);
    }
}
