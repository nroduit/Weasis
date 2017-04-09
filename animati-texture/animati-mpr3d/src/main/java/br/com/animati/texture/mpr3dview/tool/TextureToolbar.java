/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.tool;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.WtoolBar;

import br.com.animati.texture.mpr3dview.GUIManager;
import br.com.animati.texture.mpr3dview.HaPrefsPage;
import br.com.animati.texture.mpr3dview.View3DContainer;
import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 *
 * @author Gabriela Carla Bauerman (gabriela@animati.com.br)
 * @version 2015, 14 May.
 */
public class TextureToolbar extends WtoolBar {

    public static final String NAME = Messages.getString("TextureToolbar.name");

    private ImageViewerEventManager eventManager;
    private JToggleButton show3D;
    private ListDataListener layListener = new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
            ActionState action = GUIManager.getInstance().getAction(ActionW.LAYOUT);
            if (action instanceof ComboItemListener) {
                ComboItemListener layout = (ComboItemListener) action;
                if (layout.getModel() != null && layout.getSelectedItem() != null) {
                    Object selectedItem = layout.getSelectedItem();
                    layout.getModel().removeListDataListener(layListener);
                    show3D.setSelected(selectedItem.equals(View3DContainer.VIEWS_2x2_mpr));

                    layout.getModel().addListDataListener(layListener);
                }
            }
        }
    };

    public TextureToolbar(ImageViewerEventManager eventManager, int position) {
        super(NAME, position);
        this.eventManager = eventManager;

        initGui();
    }

    private void initGui() {
        JButton refreshBt = new JButton(new ImageIcon(TextureToolbar.class.getResource("/icon/32x32/refresh.png")));
        refreshBt.setToolTipText(Messages.getString("View3DContainer.refreshTexture"));
        refreshBt.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ImageViewerPlugin container = GUIManager.getInstance().getSelectedView2dContainer();
                ViewCanvas view = container.getSelectedImagePane();
                if (view instanceof ViewTexture) {
                    ((ViewTexture) view).refreshTexture();
                }
            }
        });

        add(refreshBt);

        show3D = new JToggleButton(new ImageIcon(TextureToolbar.class.getResource("/icon/32x32/volume.png")));
        show3D.setToolTipText(Messages.getString("TextureToolbar.show3D"));

        ActionState action = GUIManager.getInstance().getAction(ActionW.LAYOUT);
        if (action instanceof ComboItemListener) {
            ComboItemListener layout = (ComboItemListener) action;
            Object selectedItem = layout.getSelectedItem();
            show3D.setSelected(selectedItem.equals(View3DContainer.VIEWS_2x2_mpr));

            layout.getModel().addListDataListener(layListener);
        }

        show3D.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ActionState action = GUIManager.getInstance().getAction(ActionW.LAYOUT);
                if (action instanceof ComboItemListener) {
                    ComboItemListener lis = (ComboItemListener) action;
                    lis.getModel().removeListDataListener(layListener);
                    if (show3D.isSelected()) {
                        lis.setSelectedItem(View3DContainer.VIEWS_2x2_mpr);
                    } else {
                        lis.setSelectedItem(View3DContainer.VIEWS_2x1_mpr);
                    }
                    lis.getModel().addListDataListener(layListener);
                }
            }
        });

        add(show3D);

        JButton config = new JButton(new ImageIcon(TextureToolbar.class.getResource("/icon/32x32/config.png")));
        config.setToolTipText(Messages.getString("TextureToolbar.config"));
        config.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(TextureToolbar.this);
                PreferenceDialog dialog = new PreferenceDialog(SwingUtilities.getWindowAncestor(TextureToolbar.this));
                dialog.showPage(HaPrefsPage.PAGE_NAME);
                ColorLayerUI.showCenterScreen(dialog, layer);
            }
        });
        add(config);
    }

}
