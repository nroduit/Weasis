package org.weasis.core.ui.editor.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.swing.Action;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.graphic.ImageLayerChangeListener;
import org.weasis.core.ui.graphic.PanPoint;
import org.weasis.core.ui.graphic.model.Canvas;

public interface ViewCanvas<E extends ImageElement>
    extends Canvas, Image2DViewer<E>, PropertyChangeListener, FocusListener, ImageLayerChangeListener<E>, KeyListener {

    public static final String zoomTypeCmd = "zoom.type"; //$NON-NLS-1$
    public static final ImageIcon SYNCH_ICON = new ImageIcon(DefaultView2d.class.getResource("/icon/22x22/synch.png")); //$NON-NLS-1$
    public static final int CENTER_POINTER = 1 << 1;
    public static final int HIGHLIGHTED_POINTER = 1 << 2;

    public static final Color focusColor = Color.orange;
    public static final Color lostFocusColor = new Color(255, 224, 178);

    void registerDefaultListeners();

    void copyActionWState(HashMap<String, Object> actionsInView);

    ImageViewerEventManager<E> getEventManager();

    void updateSynchState();

    PixelInfo getPixelInfo(Point p);

    Panner getPanner();

    void setSeries(MediaSeries<E> series);

    void setSeries(MediaSeries<E> newSeries, E selectedMedia);

    void setFocused(boolean focused);

    double getRealWorldViewScale();

    AnnotationsLayer getInfoLayer();

    int getTileOffset();

    void setTileOffset(int tileOffset);

    void center();

    void setCenter(double x, double y);

    void moveOrigin(PanPoint point);

    Comparator<E> getCurrentSortComparator();

    void setActionsInView(String action, Object value);

    void setActionsInView(String action, Object value, boolean repaint);

    void setSelected(boolean selected);

    Font getFont();

    Font getLayerFont();

    void setDrawingsVisibility(boolean visible);

    Object getLensActionValue(String action);

    void changeZoomInterpolation(int interpolation);

    OpManager getDisplayOpManager();

    void disableMouseAndKeyListener();

    void iniDefaultMouseListener();

    void iniDefaultKeyListener();

    int getPointerType();

    void setPointerType(int pointerType);

    void addPointerType(int i);

    void resetPointerType(int i);

    Point2D getHighlightedPosition();

    void drawPointer(Graphics2D g, double x, double y);

    List<Action> getExportToClipboardAction();

    void enableMouseAndKeyListener(MouseActions mouseActions);

    void resetZoom();

    void resetPan();

    void reset();

    List<ViewButton> getViewButtons();

    void closeLens();

}