package org.weasis.core.ui.editor.image;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.SliderChangeListener;

/**
 * The Class PopUpMenuOnZoom.
 * 
 * @author Nicolas Roduit
 */
public class PopUpMenuOnZoom extends JPopupMenu {

    /** The display image zone */
    private final ZoomWin zoomWin;
    private JMenuItem jMenuItemZoom = new JMenuItem();
    private ButtonGroup buttonMagnify = new ButtonGroup();
    private int[] magnify = { 1, 2, 3, 4, 6 };
    private JRadioButtonMenuItem[] jRadioButtonMenuItemMagnify;
    private ActionListener magnifyListener = new java.awt.event.ActionListener() {

        public void actionPerformed(ActionEvent e) {
            magnifyActionPerformed(e);
        }
    };
    private JMenu jMenuMagnify = new JMenu();
    private JMenu jMenuImage = new JMenu();
    private JRadioButtonMenuItem jMenuItemMagnifyOther = new JRadioButtonMenuItem();
    private JCheckBoxMenuItem jCheckBoxMenuItemDraw = new JCheckBoxMenuItem();
    private JCheckBoxMenuItem jCheckBoxMenutemSychronize = new JCheckBoxMenuItem();
    private JMenuItem freeze = new JMenuItem("Freeze parent image");
    private JMenuItem resetFreeze = new JMenuItem("Reset freeze");

    public PopUpMenuOnZoom(ZoomWin zoomWin) {
        if (zoomWin == null) {
            throw new IllegalArgumentException("ZoomWin cannot be null"); //$NON-NLS-1$
        }
        this.zoomWin = zoomWin;
        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        jMenuItemZoom.setText("Hide Lens");
        jMenuItemZoom.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                zoomWin.hideZoom();
            }
        });
        jCheckBoxMenuItemDraw.setText("Show Drawings");
        jCheckBoxMenuItemDraw.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                zoomWin.setActionInView(ActionW.DRAW.cmd(), jCheckBoxMenuItemDraw.isSelected());
                zoomWin.repaint();
            }
        });
        jMenuImage.setText("Image");
        freeze.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                zoomWin.setFreezeImage(zoomWin.freezeParentImage());
            }
        });
        jMenuImage.add(freeze);
        resetFreeze.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                zoomWin.setFreezeImage(null);
            }
        });
        jMenuImage.add(resetFreeze);

        jMenuMagnify.setText("Magnify");
        jCheckBoxMenutemSychronize.setText("Synchronize to parent zoom");
        jCheckBoxMenutemSychronize.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(ActionEvent e) {
                zoomWin.setActionInView(ZoomWin.SYNCH_CMD, jCheckBoxMenutemSychronize.isSelected());
                zoomWin.updateZoom();
            }
        });
        this.add(jMenuItemZoom);
        this.addSeparator();
        this.add(jCheckBoxMenutemSychronize);
        this.add(jCheckBoxMenuItemDraw);
        this.add(jMenuImage);
        this.add(jMenuMagnify);
        iniMenuItemZoomMagnify();
        this.addSeparator();
    }

    public void iniMenuItemZoomMagnify() {
        jRadioButtonMenuItemMagnify = new JRadioButtonMenuItem[magnify.length];
        for (int i = 0; i < jRadioButtonMenuItemMagnify.length; i++) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem();
            item.setText(magnify[i] + "X");
            buttonMagnify.add(item);
            item.addActionListener(magnifyListener);
            jMenuMagnify.add(item);
            jRadioButtonMenuItemMagnify[i] = item;
        }
    }

    public void enableMenuItem() {
        // Do not trigger actionLinstener
        jCheckBoxMenutemSychronize.setSelected((Boolean) zoomWin.getActionValue(ZoomWin.SYNCH_CMD));
        jCheckBoxMenuItemDraw.setSelected((Boolean) zoomWin.getActionValue(ActionW.DRAW.cmd()));
        Object img = zoomWin.getActionValue(ZoomWin.FREEZE_CMD);
        resetFreeze.setEnabled(img != null);

        // essaie de récupérer le magnitude du zoom, si ne correspond pas aux
        // magnitude possible : pas de sélection
        boolean noselection = true;
        if (jRadioButtonMenuItemMagnify.length < jMenuMagnify.getItemCount()) {
            jMenuMagnify.remove(jMenuItemMagnifyOther);
        }
        Double ratio = (Double) zoomWin.getActionValue(ActionW.ZOOM.cmd());
        if (ratio == null) {
            ratio = 1.0;
        }
        int currentZoomRatio = (int) (ratio * 100.0);
        for (int i = 0; i < jRadioButtonMenuItemMagnify.length; i++) {
            if ((magnify[i] * 100) == currentZoomRatio) {
                JRadioButtonMenuItem item3 = jRadioButtonMenuItemMagnify[i];
                // action setSelected ne déclenche pas l'actionLinstener
                item3.setSelected(true);
                noselection = false;
                break;
            }
        }
        if (noselection) {
            ratio = Math.abs(ratio);
            jMenuItemMagnifyOther.setText(ratio + "X");
            buttonMagnify.add(jMenuItemMagnifyOther);
            if ((magnify[magnify.length - 1]) < ratio) {
                jMenuMagnify.add(jMenuItemMagnifyOther);
            } else {
                int k = 0;
                for (int i = 0; i < magnify.length; i++) {
                    if ((magnify[i]) > ratio) {
                        k = i;
                        break;
                    }
                }
                jMenuMagnify.add(jMenuItemMagnifyOther, k);
            }
            jMenuItemMagnifyOther.setSelected(true);
        }
    }

    private void magnifyActionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JRadioButtonMenuItem) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) e.getSource();
            for (int i = 0; i < jRadioButtonMenuItemMagnify.length; i++) {
                if (item.equals(jRadioButtonMenuItemMagnify[i])) {
                    ImageViewerEventManager manager = zoomWin.getView2d().getEventManager();
                    ActionState zoomAction = manager.getAction(ActionW.LENSZOOM);
                    if (zoomAction instanceof SliderChangeListener) {
                        ((SliderChangeListener) zoomAction).setValue(manager.viewScaleToSliderValue(magnify[i]));
                    }
                    break;
                }
            }
        }
    }

}
