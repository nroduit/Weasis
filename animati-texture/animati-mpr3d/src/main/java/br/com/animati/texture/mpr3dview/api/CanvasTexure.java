/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JComponent;
import javax.vecmath.Point2i;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.model.ViewModelChangeListener;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.editor.image.Canvas;
import org.weasis.core.ui.editor.image.GraphicsPane;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicModelChangeListener;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;

import br.com.animati.texturedicom.ImageSeries;
import br.com.animati.texturedicom.TextureImageCanvas;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import org.weasis.core.ui.docking.UIManager;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 23 Apr.
 */
public class CanvasTexure extends TextureImageCanvas implements Canvas {

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasTexure.class);

    private static final double ROUNDING_FACTOR = 0.5;

    protected GraphicModel graphicManager;
    protected ViewModel viewModel;
    protected final LayerModelHandler layerModelHandler;
    protected final ViewModelHandler viewModelHandler;

    protected final DrawingsKeyListeners drawingsKeyListeners = new DrawingsKeyListeners();
    protected final HashMap<String, Object> actionsInView = new HashMap<>();
    protected final AffineTransform affineTransform = new AffineTransform();
    protected final AffineTransform inverseTransform = new AffineTransform();

    protected final PropertyChangeListener graphicsChangeHandler = new PropertyChangeHandler();

    public CanvasTexure(ImageSeries parentImageSeries) {
        super(parentImageSeries);
        addRetinaDisplayWorkaround();

        this.layerModelHandler = new LayerModelHandler();

        this.graphicManager = new XmlGraphicModel();
        this.graphicManager.addChangeListener(layerModelHandler);

        this.viewModelHandler = new ViewModelHandler();

        this.viewModel = new TextureViewModel();
        this.viewModel.addViewModelChangeListener(viewModelHandler);
    }

    private void addRetinaDisplayWorkaround() {
        final GLEventListener glEventListener = new GLEventListener() {
            @Override
            public void init(final GLAutoDrawable glad) {
            }

            @Override
            public void dispose(final GLAutoDrawable glad) {
            }

            @Override
            public void display(GLAutoDrawable glad) {
                LOGGER.info("Detecting retina display on canvas first display(): " + !isDefaultScale());
                if (!isDefaultScale()) {
                    UIManager.BASE_AREA.repaint();
                }
                removeGLEventListener(this);
            }

            @Override
            public void reshape(final GLAutoDrawable glad, int i, int i1, int i2, int i3) {
            }
        };
        addGLEventListener(glEventListener);
    }

    @Override
    public JComponent getJComponent() {
        return this;
    }

    @Override
    public AffineTransform getAffineTransform() {
        return affineTransform;
    }

    @Override
    public AffineTransform getInverseTransform() {
        return inverseTransform;
    }

    @Override
    public void disposeView() {
        Optional.ofNullable(viewModel).ifPresent(model -> model.removeViewModelChangeListener(viewModelHandler));
        // Unregister listener
        graphicManager.removeChangeListener(layerModelHandler);
        graphicManager.removeGraphicChangeHandler(graphicsChangeHandler);
    }

    @Override
    public ViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public void setViewModel(ViewModel viewModel) {
        ViewModel viewModelOld = this.viewModel;
        if (viewModelOld != viewModel) {
            if (viewModelOld != null) {
                viewModelOld.removeViewModelChangeListener(viewModelHandler);
            }
            this.viewModel = viewModel;
            if (this.viewModel != null) {
                this.viewModel.addViewModelChangeListener(viewModelHandler);
            }
            firePropertyChange("viewModel", viewModelOld, this.viewModel); //$NON-NLS-1$
        }
    }

    @Override
    public GraphicModel getGraphicManager() {
        return graphicManager;
    }

    @Override
    public void setGraphicManager(GraphicModel graphicManager) {
        Objects.requireNonNull(graphicManager);
        GraphicModel graphicManagerOld = this.graphicManager;
        if (!Objects.equals(graphicManager, graphicManagerOld)) {
            graphicManagerOld.removeChangeListener(layerModelHandler);
            graphicManagerOld.removeGraphicChangeHandler(graphicsChangeHandler);
            this.graphicManager = graphicManager;
            this.graphicManager.addGraphicChangeHandler(graphicsChangeHandler);
            if (this instanceof ViewCanvas) {
                this.graphicManager.updateLabels(Boolean.TRUE, (ViewCanvas) this);
            }
            this.graphicManager.addChangeListener(layerModelHandler);
            firePropertyChange("graphicManager", graphicManagerOld, this.graphicManager);
        }
    }
    @Override
    public Object getActionValue(String action) {
        if (action == null) {
            return null;
        }
        return actionsInView.get(action);
    }

    @Override
    public HashMap<String, Object> getActionsInView() {
        return actionsInView;
    }


    @Override
    public PropertyChangeListener getGraphicsChangeHandler() {
        return graphicsChangeHandler;
    }

    @Override
    public void zoom(Double viewScale) {
        setZoom(viewScale, true); // true for repait texture.
    }

    @Override
    public double getBestFitViewScale() {
        final double viewportWidth = getWidth() - 1;
        final double viewportHeight = getHeight() - 1;
        final Rectangle2D modelArea = viewModel.getModelArea();
        double min = Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight());
        return cropViewScale(min);
    }

    private double cropViewScale(double viewScale) {
        return DefaultViewModel.cropViewScale(viewScale, viewModel.getViewScaleMin(), viewModel.getViewScaleMax());
    }

    @Override
    public Point2D viewToModel(final Double viewX, final Double viewY) {
        // Used for mouse-centered zoom
        Point2D p = getViewCoordinatesOffset();
        p.setLocation(viewX - p.getX(), viewY - p.getY());
        getInverseTransform().transform(p, p);
        return p;
    }


    @Override
    public double viewToModelLength(Double viewLength) {
        return viewLength / viewModel.getViewScale();
    }

    @Override
    public Point2D modelToView(final Double modelX, final Double modelY) {
        // Probably not used!
        Point2D p2 = new Point2D.Double(modelX, modelY);
        getAffineTransform().transform(p2, p2);

        Point2D p = getViewCoordinatesOffset();
        p2.setLocation(p2.getX() + p.getX(), p2.getY() + p.getY());
        return p2;
    }


    @Override
    public double modelToViewLength(Double modelLength) {
        return modelLength * viewModel.getViewScale();
    }
    @Override
    public Point2D getImageCoordinatesFromMouse(Integer x, Integer y) {
        Point2D p = getClipViewCoordinatesOffset();
        p.setLocation(x - p.getX(), y - p.getY());

        getInverseTransform().transform(p, p);
        return p;
    }

    public Point3d getOriginalSystemCoordinatesFromMouse(int x, int y) {
        Point2i position = new Point2i(x, y);
        Vector3d coordinatesTexture = getTextureCoordinatesForPosition(position);
        Vector3d imageSize = getParentImageSeries().getImageSize();
        Point3d osc = textureToOriginalSystemCoordinates(coordinatesTexture, imageSize);
        return osc;
    }

    @Override
    public Point getMouseCoordinatesFromImage(Double x, Double y) {
        // not realy ImageCoordenates... see: Wikipage 'Coordinate Systems'
        Point2D p2 = new Point2D.Double(x, y);
        getAffineTransform().transform(p2, p2);

        Point2D p = getClipViewCoordinatesOffset();
        return new Point((int) Math.floor(p2.getX() + p.getX() + ROUNDING_FACTOR),
                (int) Math.floor(p2.getY() + p.getY() + ROUNDING_FACTOR));

    }

    /**
     * Updates all labels on viewer.
     *
     * @param source
     *            The viewer object.
     */
    public void updateAllLabels(final Component source) {
        // TODO
    }

    /**
     * Converts a position on texture`s coordinates system (0 to 1) to image`s coordinates system. X e Y: 0 to n-1;
     * (number of pixels) Z: 1 to n. (n = number of slices)
     *
     * @param coordinatesTexture
     *            position on texture`s coord. system.
     * @param imageSize
     *            Size or images and stack (the "Original System).
     * @return Position on image`s coordinates System.
     */
    public Point3d textureToOriginalSystemCoordinates(final Vector3d coordinatesTexture, final Vector3d imageSize) {
        Point3d p3 = new Point3d(
                Math.round(coordinatesTexture.x * imageSize.x),
                Math.round(coordinatesTexture.y * imageSize.y),
                Math.round(coordinatesTexture.z * imageSize.z + 1));
        return p3;

    }

    public boolean hasContent() {
        return (getParentImageSeries() != null);
    }

    /**
     * Get ImageRectangle without rotation.
     *
     * @return
     */
    public Rectangle getUnrotatedImageRect() {
        double currentRO = getRotationOffset();
        setRotationOffset(0);
        Rectangle imageRect = getImageRect(true);
        setRotationOffset(currentRO);

        return imageRect;
    }

    /**
     * @return Unrotated and "unzoomed" image rectangle.
     */
    public Rectangle getUntransformedImageRect() {
        double currentRO = getRotationOffset();
        double zoom = getActualDisplayZoom();

        setRotationOffset(0);
        setZoom(1);

        Rectangle imageRect = getImageRect(true);

        setRotationOffset(currentRO);
        setZoom(zoom);

        return imageRect;
    }

    @Override
    public Point2D getClipViewCoordinatesOffset() {
        Point2D p = getViewCoordinatesOffset();
        p.setLocation(p.getX() < 0.0 ? 0.0 : p.getX(), p.getY() < 0.0 ? 0.0 : p.getY());
        return p;
    }

    @Override
    public Point2D getViewCoordinatesOffset() {
        Rectangle2D b = getImageViewBounds(getWidth(), getHeight());
        return new Point2D.Double(b.getX(), b.getY());
    }


    @Override
    public Rectangle2D getImageViewBounds() {
        return getImageViewBounds(getWidth(), getHeight());
    }

    @Override
    public Rectangle2D getImageViewBounds(double viewportWidth, double viewportHeight) {
        return getUnrotatedImageRect();
    }


    /**
     * Uses DefaultViewModel to reuse the Listener system, but all the information came from the TextureImageCanvas
     * state.
     */
    private class TextureViewModel extends DefaultViewModel {

        @Override
        public double getModelOffsetX() {
            if (hasContent()) {
                return -(getUnrotatedImageRect().getX() / getActualDisplayZoom());
            }
            return 0;
        }

        @Override
        public double getModelOffsetY() {
            if (hasContent()) {
                return -(getUnrotatedImageRect().getY() / getActualDisplayZoom());
            }
            return 0;
        }

        @Override
        public double getViewScale() {
            if (hasContent()) {
                double displayZoom = CanvasTexure.this.getActualDisplayZoom();
                if (displayZoom <= 0) { // avoid error trying to generate inverseAffine
                    displayZoom = 1;
                }
                return displayZoom;
            }
            return 1;
        }
    }

    private class LayerModelHandler implements GraphicModelChangeListener {
        @Override
        public void handleModelChanged(GraphicModel modelList) {
            repaint();
        }
    }

    private class ViewModelHandler implements ViewModelChangeListener {
        @Override
        public void handleViewModelChanged(ViewModel viewModel) {
            repaint();
        }
    }

    private class DrawingsKeyListeners implements KeyListener {

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                graphicManager.deleteSelectedGraphics(CanvasTexure.this, false);
            } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                graphicManager.setSelectedGraphic(null);
            } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
                graphicManager.setSelectedAllGraphics();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }

        @Override
        public void keyTyped(KeyEvent e) {
        }
    }

    class PropertyChangeHandler implements PropertyChangeListener, Serializable {
        private static final long serialVersionUID = -9094820911680205527L;

        private PropertyChangeHandler() {
        }

        // This method is called when a property is changed (fired from a graphic)
        @Override
        public void propertyChange(PropertyChangeEvent propertychangeevent) {
            Object obj = propertychangeevent.getSource();
            String s = propertychangeevent.getPropertyName();
            if (obj instanceof Graphic) {
                Graphic graph = (Graphic) obj;
                if ("bounds".equals(s)) { //$NON-NLS-1$
                    graphicBoundsChanged(graph, (Shape) propertychangeevent.getOldValue(),
                        (Shape) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("graphicLabel".equals(s)) { //$NON-NLS-1$
                    labelBoundsChanged(graph, (DefaultGraphicLabel) propertychangeevent.getOldValue(),
                        (DefaultGraphicLabel) propertychangeevent.getNewValue(), getAffineTransform());
                } else if ("remove".equals(s)) { //$NON-NLS-1$
                    removeGraphic(graph);
                } else if ("remove.repaint".equals(s)) { //$NON-NLS-1$
                    removeGraphicAndRepaint(graph);
                } else if ("toFront".equals(s)) { //$NON-NLS-1$
                    toFront(graph);
                } else if ("toBack".equals(s)) { //$NON-NLS-1$
                    toBack(graph);
                }
            }
        }

        public void toFront(Graphic graphic) {
            List<Graphic> list = graphicManager.getModels();
            synchronized (list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).equals(graphic)) {
                        Collections.rotate(list.subList(i, list.size()), -1);
                        break;
                    }
                }
            }
            repaint();

        }

        public void toBack(Graphic graphic) {
            List<Graphic> list = graphicManager.getModels();
            synchronized (list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).equals(graphic)) {
                        Collections.rotate(list.subList(0, i + 1), 1);
                        break;
                    }
                }
            }
            repaint();
        }

        public void removeGraphicAndRepaint(Graphic graphic) {
            removeGraphic(graphic);
            GraphicsPane.repaint(CanvasTexure.this,
                graphic.getTransformedBounds(graphic.getShape(), getAffineTransform()));
        }

        public void removeGraphic(Graphic graphic) {
            if (graphicManager != null) {
                graphicManager.removeGraphic(graphic);
            }
            graphic.removePropertyChangeListener(graphicsChangeHandler);
        }

        protected Rectangle rectangleUnion(Rectangle rectangle, Rectangle rectangle1) {
            if (rectangle == null) {
                return rectangle1;
            }
            return rectangle1 == null ? rectangle : rectangle.union(rectangle1);
        }

        protected void graphicBoundsChanged(Graphic graphic, Shape oldShape, Shape shape, AffineTransform transform) {
            if (graphic != null) {
                if (oldShape == null) {
                    if (shape != null) {
                        Rectangle rect = graphic.getTransformedBounds(shape, transform);
                        GraphicsPane.repaint(CanvasTexure.this, rect);
                    }
                } else {
                    if (shape == null) {
                        Rectangle rect = graphic.getTransformedBounds(oldShape, transform);
                        GraphicsPane.repaint(CanvasTexure.this, rect);
                    } else {
                        Rectangle rect = rectangleUnion(graphic.getTransformedBounds(oldShape, transform),
                            graphic.getTransformedBounds(shape, transform));
                        GraphicsPane.repaint(CanvasTexure.this, rect);
                    }
                }
            }
        }

        protected void labelBoundsChanged(Graphic graphic, DefaultGraphicLabel oldLabel, DefaultGraphicLabel newLabel,
            AffineTransform transform) {

            if (graphic != null) {
                boolean oldNull = oldLabel == null || oldLabel.getLabels() == null;
                boolean newNull = newLabel == null || newLabel.getLabels() == null;
                if (oldNull) {
                    if (!newNull) {
                        Rectangle2D rect = graphic.getTransformedBounds(newLabel, transform);
                        GeomUtil.growRectangle(rect, 2);
                        GraphicsPane.repaint(CanvasTexure.this, rect.getBounds());
                    }
                } else {
                    if (newNull) {
                        Rectangle2D rect = graphic.getTransformedBounds(oldLabel, transform);
                        GeomUtil.growRectangle(rect, 2);
                        GraphicsPane.repaint(CanvasTexure.this, rect.getBounds());
                    } else {
                        Rectangle2D newRect = graphic.getTransformedBounds(newLabel, transform);
                        GeomUtil.growRectangle(newRect, 2);

                        Rectangle2D oldRect = graphic.getTransformedBounds(oldLabel, transform);
                        GeomUtil.growRectangle(oldRect, 2);

                        Rectangle rect = rectangleUnion(oldRect.getBounds(), newRect.getBounds());
                        GraphicsPane.repaint(CanvasTexure.this, rect);
                    }
                }
            }
        }
    }

}
