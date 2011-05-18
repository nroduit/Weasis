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
package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.graphic.model.LayerModel;
import org.weasis.core.ui.graphic.model.Tools;

/**
 * The Class TempLayer.
 * 
 * @author Nicolas Roduit
 */
public class TempLayer extends DragLayer {

    private static final long serialVersionUID = 3600434231972858782L;
    // private transient AbstractLayer layer = null;
    private transient Color color = null;
    private transient Font font = FontTools.getFont10();
    private transient ArrayList<Point> points = null;

    public TempLayer(LayerModel canvas1) {
        super(canvas1, Tools.TEMPDRAGLAYER.getId());
    }

    public ArrayList<Point> getPoints() {
        return points;
    }

    public void setPoints(ArrayList<Point> points) {
        this.points = points;
    }

    @Override
    public void paint(Graphics2D g2, AffineTransform transform, AffineTransform inverseTransform, Rectangle2D bound) {
        if (graphics != null) {
            for (int i = 0; i < graphics.size(); i++) {
                Graphic graphic = graphics.get(i);
                // rectangle correspond ï¿½ la zone d'affiche de ImageDisplay (zone de l'image visible)
                // si le rectangle intersecte (si le bounding box du graphic est contenu ou intesecte avec le rectangle)
                // revoie
                // true
                // if (bound == null || bound.intersects(graphic.getRepaintBounds())) {
                if (bound == null || bound.intersects(graphic.getRepaintBounds(transform))) {
                    graphic.paint(g2, transform);
                }
            }
        }
        if (points != null) {
            g2.setPaint(Color.blue);
            for (int i = 0; i < points.size(); i++) {
                Point p = points.get(i);
                g2.drawRect(p.x - 1, p.y - 1, 3, 3);
            }
        }
    }

    // @Override
    // public void paintSVG(SVGGraphics2D g2) {
    // // if (layer != null) {
    // // g2.setFont(font, "Labels");
    // // g2.setPaint(color);
    // // java.util.List list2 = layer.getGraphics();
    // // for (int i = 0; i < list2.size(); i++) {
    // // Graphic lab = (Graphic) list2.get(i);
    // // if (lab.getLabel() != null) {
    // // Rectangle rect = lab.getBounds();
    // // int x = rect.x + rect.width / 2;
    // // int y = rect.y + rect.height / 2;
    // // for (String l : lab.getLabel()) {
    // // g2.drawString(l, x, y);
    // // }
    // // }
    // // }
    // // g2.closeAllTags();
    // // }
    // boolean noteLayer = this.getDrawType() == Tools.NOTE.getId();
    // String iconName = null;
    // if (noteLayer) {
    // ImageIcon icon = new ImageIcon(getClass().getResource("/com/jmvision/icon/info.gif"));
    // iconName = g2.writeOnlyImage(SVGGraphics2D.createRenderedImage(icon.getImage(), null, null));
    // }
    // for (int i = 0; i < graphics.size(); i++) {
    // AbstractDragGraphic graphic = (AbstractDragGraphic) graphics.get(i);
    // Color borderColor = (Color) graphic.getPaint();
    // Color fillColor = null;
    // if (graphic.isFilled()) {
    // fillColor = borderColor;
    // }
    // g2.setObjectStyle(getDrawinType(graphic), borderColor, fillColor, graphic.getStroke());
    // g2.draw(graphic.getShape(), false);
    // if (noteLayer) {
    // Rectangle rect = graphic.getBounds();
    // rect.x += rect.width - 12;
    // rect.y += rect.height / 2;
    // rect.width = 32;
    // rect.height = 32;
    // g2.writeOnlyImageReference(iconName, rect);
    // }
    // g2.closeAllTags();
    // }
    // }

    public void setFont(Font font) {
        this.font = font;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
