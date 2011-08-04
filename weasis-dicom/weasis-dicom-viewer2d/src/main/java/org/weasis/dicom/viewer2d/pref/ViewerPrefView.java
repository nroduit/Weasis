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
package org.weasis.dicom.viewer2d.pref;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Hashtable;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.ZoomOperation;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.View2dFactory;

public class ViewerPrefView extends AbstractItemDialogPage {
    private final Hashtable labels = new Hashtable();
    private JSlider sliderWindow;
    private JSlider sliderLevel;
    private JSlider sliderZoom;
    private JSlider sliderRotation;
    private JSlider sliderScroll;
    private final JComboBox comboBoxInterpolation;

    public ViewerPrefView() {
        setTitle(View2dFactory.NAME); //$NON-NLS-1$
        labels.put(-100, new JLabel(Messages.getString("ViewerPrefView.low"))); //$NON-NLS-1$
        labels.put(0, new JLabel(Messages.getString("ViewerPrefView.mid"))); //$NON-NLS-1$
        labels.put(100, new JLabel(Messages.getString("ViewerPrefView.high"))); //$NON-NLS-1$
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, Messages.getString("ViewerPrefView.mouse_sens"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        add(panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0, 0, 0, 0, 0 };
        gbl_panel.columnWeights = new double[] { 0.0, 1.0 };
        gbl_panel.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0 };
        panel.setLayout(gbl_panel);

        JPanel panel_1 = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel_1.getLayout();
        flowLayout.setAlignment(FlowLayout.LEADING);
        panel_1.setBorder(new TitledBorder(null, Messages.getString("ViewerPrefView.zoom"), TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$
        add(panel_1);

        JLabel lblInterpolation = new JLabel(Messages.getString("ViewerPrefView.interp")); //$NON-NLS-1$
        panel_1.add(lblInterpolation);
        EventManager eventManager = EventManager.getInstance();

        comboBoxInterpolation = new JComboBox(ZoomOperation.INTERPOLATIONS);
        comboBoxInterpolation.setSelectedIndex(eventManager.getZoomSetting().getInterpolation());
        panel_1.add(comboBoxInterpolation);

        ActionState winAction = eventManager.getAction(ActionW.WINDOW);
        if (winAction instanceof MouseActionAdapter) {
            JLabel lblWindow = new JLabel(Messages.getString("ViewerPrefView.win")); //$NON-NLS-1$
            GridBagConstraints gbc_lblWindow = new GridBagConstraints();
            gbc_lblWindow.anchor = GridBagConstraints.NORTHEAST;
            gbc_lblWindow.insets = new Insets(5, 0, 0, 0);
            gbc_lblWindow.gridx = 0;
            gbc_lblWindow.gridy = 0;
            panel.add(lblWindow, gbc_lblWindow);

            sliderWindow =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) winAction).getMouseSensivity()));
            GridBagConstraints gbc_slider = new GridBagConstraints();
            gbc_slider.fill = GridBagConstraints.HORIZONTAL;
            gbc_slider.anchor = GridBagConstraints.WEST;
            gbc_slider.insets = new Insets(5, 2, 0, 0);
            gbc_slider.gridx = 1;
            gbc_slider.gridy = 0;
            formatSlider(sliderWindow);
            panel.add(sliderWindow, gbc_slider);
        }

        ActionState levelAction = eventManager.getAction(ActionW.LEVEL);
        if (levelAction instanceof MouseActionAdapter) {
            JLabel lblLevel = new JLabel(Messages.getString("ViewerPrefView.level")); //$NON-NLS-1$
            GridBagConstraints gbc_lblLevel = new GridBagConstraints();
            gbc_lblLevel.anchor = GridBagConstraints.NORTHEAST;
            gbc_lblLevel.insets = new Insets(5, 15, 0, 0);
            gbc_lblLevel.gridx = 0;
            gbc_lblLevel.gridy = 1;
            panel.add(lblLevel, gbc_lblLevel);

            sliderLevel =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) levelAction).getMouseSensivity()));
            GridBagConstraints gbc_slider_2 = new GridBagConstraints();
            gbc_slider_2.fill = GridBagConstraints.HORIZONTAL;
            gbc_slider_2.anchor = GridBagConstraints.WEST;
            gbc_slider_2.insets = new Insets(5, 2, 0, 0);
            gbc_slider_2.gridx = 1;
            gbc_slider_2.gridy = 1;
            formatSlider(sliderLevel);
            panel.add(sliderLevel, gbc_slider_2);
        }

        ActionState zoomlAction = eventManager.getAction(ActionW.ZOOM);
        if (zoomlAction instanceof MouseActionAdapter) {
            JLabel lblZoom = new JLabel(Messages.getString("ViewerPrefView.zoom")); //$NON-NLS-1$
            GridBagConstraints gbc_lblZoom = new GridBagConstraints();
            gbc_lblZoom.anchor = GridBagConstraints.NORTHEAST;
            gbc_lblZoom.insets = new Insets(5, 0, 0, 0);
            gbc_lblZoom.gridx = 0;
            gbc_lblZoom.gridy = 2;
            panel.add(lblZoom, gbc_lblZoom);

            sliderZoom =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) zoomlAction).getMouseSensivity()));
            GridBagConstraints gbc_slider = new GridBagConstraints();
            gbc_slider.fill = GridBagConstraints.HORIZONTAL;
            gbc_slider.anchor = GridBagConstraints.WEST;
            gbc_slider.insets = new Insets(5, 2, 0, 0);
            gbc_slider.gridx = 1;
            gbc_slider.gridy = 2;
            formatSlider(sliderZoom);
            panel.add(sliderZoom, gbc_slider);
        }

        ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
        if (rotateAction instanceof MouseActionAdapter) {
            JLabel lblRotation = new JLabel(Messages.getString("ResetTools.rotation")); //$NON-NLS-1$
            GridBagConstraints gbc_lblRotation = new GridBagConstraints();
            gbc_lblRotation.anchor = GridBagConstraints.NORTHEAST;
            gbc_lblRotation.insets = new Insets(5, 15, 0, 0);
            gbc_lblRotation.gridx = 0;
            gbc_lblRotation.gridy = 3;
            panel.add(lblRotation, gbc_lblRotation);

            sliderRotation =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) rotateAction).getMouseSensivity()));
            GridBagConstraints gbc_slider_3 = new GridBagConstraints();
            gbc_slider_3.fill = GridBagConstraints.HORIZONTAL;
            gbc_slider_3.anchor = GridBagConstraints.WEST;
            gbc_slider_3.insets = new Insets(5, 2, 0, 0);
            gbc_slider_3.gridx = 1;
            gbc_slider_3.gridy = 3;
            formatSlider(sliderRotation);
            panel.add(sliderRotation, gbc_slider_3);
        }

        ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
        if (seqAction instanceof MouseActionAdapter) {
            JLabel lblImageScroll = new JLabel(Messages.getString("ViewerPrefView.scrool")); //$NON-NLS-1$
            GridBagConstraints gbc_lblImageScroll = new GridBagConstraints();
            gbc_lblImageScroll.anchor = GridBagConstraints.NORTHEAST;
            gbc_lblImageScroll.insets = new Insets(5, 0, 5, 5);
            gbc_lblImageScroll.gridx = 0;
            gbc_lblImageScroll.gridy = 4;
            panel.add(lblImageScroll, gbc_lblImageScroll);

            sliderScroll =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) seqAction).getMouseSensivity()));
            GridBagConstraints gbc_slider_1 = new GridBagConstraints();
            gbc_slider_1.fill = GridBagConstraints.HORIZONTAL;
            gbc_slider_1.anchor = GridBagConstraints.WEST;
            gbc_slider_1.insets = new Insets(5, 2, 5, 0);
            gbc_slider_1.gridx = 1;
            gbc_slider_1.gridy = 4;
            formatSlider(sliderScroll);
            panel.add(sliderScroll, gbc_slider_1);
        }

        // JButton button = new JButton("Restore Defaults");
        // GridBagConstraints gbc_button = new GridBagConstraints();
        // gbc_button.insets = new Insets(15, 15, 15, 15);
        // gbc_button.anchor = GridBagConstraints.SOUTHEAST;
        // gbc_button.gridx = 1;
        // gbc_button.gridy = 5;
        // panel.add(button, gbc_button);

    }

    @Override
    public void closeAdditionalWindow() {
        EventManager eventManager = EventManager.getInstance();
        ActionState winAction = eventManager.getAction(ActionW.WINDOW);
        if (winAction instanceof MouseActionAdapter) {
            ((MouseActionAdapter) winAction).setMouseSensivity(sliderToRealValue(sliderWindow.getValue()));
        }
        ActionState levelAction = eventManager.getAction(ActionW.LEVEL);
        if (levelAction instanceof MouseActionAdapter) {
            ((MouseActionAdapter) levelAction).setMouseSensivity(sliderToRealValue(sliderLevel.getValue()));
        }
        ActionState zoomlAction = eventManager.getAction(ActionW.ZOOM);
        if (zoomlAction instanceof MouseActionAdapter) {
            ((MouseActionAdapter) zoomlAction).setMouseSensivity(sliderToRealValue(sliderZoom.getValue()));
        }
        ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
        if (rotateAction instanceof MouseActionAdapter) {
            ((MouseActionAdapter) rotateAction).setMouseSensivity(sliderToRealValue(sliderRotation.getValue()));
        }
        ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
        if (seqAction instanceof MouseActionAdapter) {
            ((MouseActionAdapter) seqAction).setMouseSensivity(sliderToRealValue(sliderScroll.getValue()));
        }
        int interpolation = comboBoxInterpolation.getSelectedIndex();
        eventManager.getZoomSetting().setInterpolation(interpolation);
        synchronized (UIManager.VIEWER_PLUGINS) {
            for (final ViewerPlugin<DicomImageElement> p : UIManager.VIEWER_PLUGINS) {
                if (p instanceof View2dContainer) {
                    View2dContainer viewer = (View2dContainer) p;
                    for (DefaultView2d<DicomImageElement> v : viewer.getImagePanels()) {
                        v.changeZoomInterpolation(interpolation);
                    }
                }
            }
        }
    }

    @Override
    public void resetoDefaultValues() {
        // TODO Auto-generated method stub

    }

    private void formatSlider(JSlider slider) {
        slider.setPaintTicks(true);
        slider.setMajorTickSpacing(100);
        slider.setMinorTickSpacing(5);
        slider.setLabelTable(labels);
        slider.setPaintLabels(true);
    }

    private double sliderToRealValue(int value) {
        return Math.pow(10, value * 3.0 / 100.0);
    }

    private int realValueToslider(double value) {
        return (int) (Math.log10(value) * 100.0 / 3.0);
    }
}
