/*
 * @copyright Copyright (c) 2012 Animati Sistemas de InformÃ¡tica Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.dicom.codec.DicomImageElement;

/**
 * Interface to make Texture-Views and Image-Views use the same
 * container and chain of events.
 * 
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 14 ago
 */
public interface GridElement extends PropertyChangeListener {
   
    /**
     * Set a MediaSeries.
     * @param series Series to set.
     */
    void setSeries(MediaSeries<DicomImageElement> series);

    /**
     * @return True if has a content (image or texture). 
     */
    boolean hasContent();

    void dispose();

    /**
     * Apply any actions that have to be done internally by the viewer
     * when view became selected, or looses selection.
     * 
     * @param selected True for selected, False for unselected.
     */
    void setSelected(boolean selected);
    
    /**
     * Actions-in-view support.
     * 
     * @param action Command of action.
     * @return The action value, or null.
     */
    Object getActionValue(String command);
    
    Object getActionData(String command);
    
    boolean isActionEnabled(String command);
    

    /**
     * Enables mouse listeners and key listeners.
     * @param mouseActions Information about mouse settings.
     */
    void enableMouseAndKeyListener(MouseActions mouseActions);

    MediaSeries getSeries();

    Component getComponent();

    void setActionValue(String cmd, Object value);

    AnnotationsLayer getInfoLayer();

}
