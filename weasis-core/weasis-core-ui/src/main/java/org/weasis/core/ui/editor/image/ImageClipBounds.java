package org.weasis.core.ui.editor.image;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import org.weasis.core.api.gui.model.ViewModel;

public class ImageClipBounds extends Rectangle2D.Double {
    private static final long serialVersionUID = -6005745219695404637L;

    private final Canvas canvas;

    public ImageClipBounds(Canvas canvas) {
        super();
        this.canvas = Objects.requireNonNull(canvas);
        setFrame(canvas.getAffineTransform().createTransformedShape(canvas.getViewModel().getModelArea()).getBounds2D());
    }

    public Rectangle2D getClipViewImageBounds() {
        Point2D p = canvas.getClipViewCoordinatesOffset();
        return new Rectangle2D.Double(p.getX(), p.getY(), getWidth(), getHeight());
    }
    
    public Rectangle2D getViewImageBounds() {
        Point2D p = canvas.getViewCoordinatesOffset();
        return new Rectangle2D.Double(p.getX(), p.getY(), getWidth(), getHeight());
    }
    
    public Rectangle2D getViewImageBounds(double viewportWidth, double viewportHeight) {
        ViewModel model = canvas.getViewModel();
        double viewOffsetX = (viewportWidth - getWidth()) * 0.5;
        double viewOffsetY = (viewportHeight - getHeight()) * 0.5;
        double offsetX = viewOffsetX - model.getModelOffsetX() * model.getViewScale();
        double offsetY = viewOffsetY - model.getModelOffsetY() * model.getViewScale();
        return new Rectangle2D.Double(offsetX, offsetY, getWidth(), getHeight());
    }
}
