/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import br.com.animati.texture.mpr3dview.GUIManager;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.graphic.AbstractDragGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.SelectGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.AbstractLayerModel;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * LayerModel implementation to be texture-compatible.
 * 
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 29 Nov
 */
public class DVLayerModel extends AbstractLayerModel {
    
    protected JComponent view;
    
    public DVLayerModel(JComponent ownerView) {
        super(null);
        view = ownerView;
    }
    
    @Override
    public AbstractDragGraphic createDragGraphic(MouseEventDouble mouseevent) {
        Graphic newGraphic = getCreateGraphic();
        AbstractLayer.Identifier layerID = null;

        if (newGraphic == null) {
            newGraphic = MeasureToolBar.selectionGraphic;
        }
        if (newGraphic instanceof SelectGraphic) {
            layerID = AbstractLayer.TEMPDRAGLAYER;
        } else if (newGraphic instanceof AbstractDragGraphic) {
            layerID = newGraphic.getLayerID();
        } else {
            return null;
        }

        AbstractLayer layer = getLayer(layerID);
        if (layer != null && view instanceof ViewCore) {
            boolean draw = true;
            Object actionValue = ((ViewCore) view).getActionValue(ActionW.DRAW.cmd());
            if (actionValue instanceof Boolean) {
                draw = (Boolean) actionValue;
            }
            if (!layer.isVisible() || !draw) {
                JOptionPane
                    .showMessageDialog(
                        view,
                        Messages.getString("AbstractLayerModel.msg_not_vis"),
                        Messages.getString("AbstractLayerModel.draw"),
                        JOptionPane.ERROR_MESSAGE);
            } else {
                newGraphic = ((AbstractDragGraphic) newGraphic).clone();
                layer.addGraphic(newGraphic);
                return (AbstractDragGraphic) newGraphic;
            }
        }

        return null;
    }

    @Override
    public void repaint() {
        view.repaint();
    }
    
    @Override
    public void repaint(Rectangle rectangle) {
        if (rectangle != null && view instanceof ViewCore) {
            // Add the offset of the canvas
            double viewScale = ((ViewCore) view).getViewModel().getViewScale();
            int x = (int) (rectangle.x
                    - ((ViewCore) view).getViewModel().getModelOffsetX() * viewScale);
            int y = (int) (rectangle.y
                    - ((ViewCore) view).getViewModel().getModelOffsetY() * viewScale);
            view.repaint(new Rectangle(x, y, rectangle.width, rectangle.height));
        }
    }
    
    @Override
    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
        view.setCursor(this.cursor);
    }
    
    @Override
    public Rectangle getBounds() {
        return view.getBounds();
    }
    
    /**
     * Gets the next graphics from GUIManager.
     * @return Create one graphic.
     */
    @Override
    public Graphic getCreateGraphic() {
        return GUIManager.getInstance().getNextGraphic();
    }


    @Override
    public void repaintWithRelativeCoord(Rectangle rectangle) {
        view.repaint(rectangle.x,
                rectangle.y, rectangle.width, rectangle.height);
    }
    
    /**
     * Delete Selected Graphic.
     * @param warningMessage not used: as false.
     */
    @Override
    public void deleteSelectedGraphics(boolean warningMessage) {
        List<Graphic> list = getSelectedGraphics();
        if (list != null && list.size() > 0) {
            List<Graphic> selectionList = new ArrayList<Graphic>(list);
            for (Graphic graphic : selectionList) {
                graphic.fireRemoveAction();
            }
            repaint();
        }
    }

}
