/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.tool;

import br.com.animati.texture.mpr3dview.EventPublisher;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.event.ListDataEvent;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.DropDownButton;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.util.WtoolBar;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 9 Dec
 */
public class GraphicsToolbar extends WtoolBar implements PropertyChangeListener {
    
    protected final DropDownButton measureButton;
    
    
    public GraphicsToolbar(int index, Graphic[] graphics) {
        super("Graphics Bar", index);
        
        ComboItemListener combo = new ComboItemListener(
                ActionW.DRAW_MEASURE, graphics) {
            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof Graphic) {
                    EventPublisher.getInstance().publish(
                            new PropertyChangeEvent(GraphicsToolbar.this,
                            ActionW.DRAW_MEASURE.cmd(), null, object));
                }
            }
        };
        
        GroupRadioMenu menu = new MeasureGroupMenu();
        combo.registerActionState(menu);
        
        measureButton = new DropDownButton(ActionW.DRAW_MEASURE.cmd(),
                MeasureToolBar.buildIcon(graphics[0]), menu) {
            @Override
            protected JPopupMenu getPopupMenu() {
                JPopupMenu pop = (getMenuModel() == null)
                        ? new JPopupMenu() : getMenuModel().createJPopupMenu();
                pop.setInvoker(this);
                return pop;
            }
        };
        measureButton.setToolTipText(Messages.getString("MeasureToolBar.tools"));
        
        EventPublisher.getInstance().addPropertyChangeListener(
                ActionW.DRAW_MEASURE.cmd(), this);
        
        add(measureButton);
        
        final JButton deleteButton = new JButton();
        deleteButton.setToolTipText(Messages.getString("MeasureToolBar.del"));
        deleteButton.setIcon(new ImageIcon(Graphic.class.getResource(
                "/icon/32x32/draw-delete.png")));
        deleteButton.addActionListener(new java.awt.event.ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        deleteButton, EventPublisher.VIEWER_DO_ACTION
                        + ActionW.DRAW.cmd() + ".delete", null, null));
            }
        });
        add(deleteButton);
        
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (ActionW.DRAW_MEASURE.cmd().equals(evt.getPropertyName())
                && !evt.getSource().equals(this)) {
            MeasureGroupMenu menuModel = (MeasureGroupMenu) measureButton.getMenuModel();
            menuModel.changeWP(evt.getNewValue());
        }
    }
    
    protected class MeasureGroupMenu extends GroupRadioMenu {

        public MeasureGroupMenu() {
            super();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            super.contentsChanged(e);
            changeButtonState();
        }

        public void changeButtonState() {
            Object sel = dataModel.getSelectedItem();
            if (sel instanceof Graphic && measureButton != null) {
                Icon icon = MeasureToolBar.buildIcon((Graphic) sel);
                measureButton.setIcon(icon);
                measureButton.setActionCommand(sel.toString());
            }
        }
        
        public void changeWP(Object sel) {
            dataModel.setSelectedItem(sel);
            if (sel instanceof Graphic && measureButton != null) {
                Icon icon = MeasureToolBar.buildIcon((Graphic) sel);
                measureButton.setIcon(icon);
                measureButton.setActionCommand(sel.toString());
            }
        } 
    }
}
