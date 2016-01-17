/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.weasis.core.ui.graphic.Graphic;

@XmlRootElement(name = "graphicList")
@XmlAccessorType(XmlAccessType.NONE)
public class GraphicList {
    @XmlElements({
        // basicGraphic is the default class (used when no mapping is found)
        @XmlElement(name = "basicGraphic", type = org.weasis.core.ui.graphic.BasicGraphic.class),
        @XmlElement(name = "angle", type = org.weasis.core.ui.graphic.AngleToolGraphic.class),
        @XmlElement(name = "annotation", type = org.weasis.core.ui.graphic.AnnotationGraphic.class),
        @XmlElement(name = "cobbAngle", type = org.weasis.core.ui.graphic.CobbAngleToolGraphic.class),
        @XmlElement(name = "ellipse", type = org.weasis.core.ui.graphic.EllipseGraphic.class),
        @XmlElement(name = "fourPointsAngle", type = org.weasis.core.ui.graphic.FourPointsAngleToolGraphic.class),
        @XmlElement(name = "line", type = org.weasis.core.ui.graphic.LineGraphic.class),
        @XmlElement(name = "lineWithGap", type = org.weasis.core.ui.graphic.LineWithGapGraphic.class),
        @XmlElement(name = "openAngle", type = org.weasis.core.ui.graphic.OpenAngleToolGraphic.class),
        @XmlElement(name = "ParallelLine", type = org.weasis.core.ui.graphic.ParallelLineGraphic.class),
        @XmlElement(name = "perpendicularLine", type = org.weasis.core.ui.graphic.PerpendicularLineGraphic.class),
        @XmlElement(name = "pixelInfo", type = org.weasis.core.ui.graphic.PixelInfoGraphic.class),
        @XmlElement(name = "point", type = org.weasis.core.ui.graphic.PointGraphic.class),
        @XmlElement(name = "polygon", type = org.weasis.core.ui.graphic.PolygonGraphic.class),
        @XmlElement(name = "polyline", type = org.weasis.core.ui.graphic.PolylineGraphic.class),
        @XmlElement(name = "rectangle", type = org.weasis.core.ui.graphic.RectangleGraphic.class),
        @XmlElement(name = "threePointsCircle", type = org.weasis.core.ui.graphic.ThreePointsCircleGraphic.class) })
    public final List<Graphic> list;

    private ArrayList<AbstractLayer> layers = null;

    public GraphicList() {
        list = Collections.synchronizedList(new ArrayList<Graphic>());
    }

    public ArrayList<AbstractLayer> getLayers() {
        return layers;
    }

    public int getLayerSize() {
        if (layers == null) {
            return -1;
        }
        return layers.size();
    }

    public void addLayer(AbstractLayer layer) {
        if (layer != null) {
            if (layers == null) {
                layers = new ArrayList<AbstractLayer>();
            }
            if (!layers.contains(layer)) {
                layers.add(layer);
            }
        }
    }

    public void removeLayer(AbstractLayer layer) {
        if (layers != null && layer != null) {
            layers.remove(layer);
        }
    }
}
