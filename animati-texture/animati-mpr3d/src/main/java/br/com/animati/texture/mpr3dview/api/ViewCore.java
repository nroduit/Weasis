/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 10 oct.
 */
public interface ViewCore<E> extends PropertyChangeListener {
    
    /* Reference values to window/level. */
    public static final int WINDOW_SMALLEST = 0;
    public static final int WINDOW_LARGEST = 4096;
    public static final int WINDOW_DEFAULT = 700;
    public static final int LEVEL_SMALLEST = -1024;
    public static final int LEVEL_LARGEST = 4096;
    public static final int LEVEL_DEFAULT = 300;
    
    /* Reference values to zoom action. */
    public static final int ZOOM_SLIDER_MIN = -100;
    public static final int ZOOM_SLIDER_MAX = 100;

    void setSeries(E series);
    
    E getSeriesObject();
    
    MediaSeries getSeries();

    boolean hasContent();

    /**
     * Can`t use duspose() here becouse that method is used by GLJPanel whith
     * diferent purpose.
     */
    void disposeView();

    Component getComponent();

    /**
     * Corresponds to tag ImagePatientOrientation, double[] with 6 elements.
     * @return ImagePatientOrientation.
     */
    double[] getImagePatientOrientation();
    
    /**
     * Returns the array containing the 4 orientation strings. Has to account
     * for rotation and flip.
     * @return An array of 4 strings: [Left,Top,Rigth,Bottom].
     */
    String[] getOrientationStrings();

    Font getLayerFont();

    Object getActionValue(String cmd);

    void showPixelInfos(MouseEvent e);

    void moveImageOffset(int width, int height);

    MouseActionAdapter getMouseAdapter(String action);

    String getSeriesObjectClassName();

    boolean isContentReadable();

    ViewModel getViewModel();

    AbstractLayerModel getLayerModel();

    Point2D getImageCoordinatesFromMouse(int x, int y);

    AffineTransform getAffineTransform();

    void registerDefaultListeners();

    GeometryOfSlice getSliceGeometry();
    
    Object getActionData(String command);
    
    boolean isActionEnabled(String command);

    void setActionsInView(String action, Object value, boolean repaint);
    
    void fixPosition();
    
    MeasurementsAdapter getMeasurementsAdapter();

}
