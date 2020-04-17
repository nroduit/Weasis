/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d.pref;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Hashtable;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.PRManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.View2dFactory;

public class ViewerPrefView extends AbstractItemDialogPage {
    private final Hashtable<Integer, JLabel> labels = new Hashtable<>();
    private JSlider sliderWindow;
    private JSlider sliderLevel;
    private JSlider sliderZoom;
    private JSlider sliderRotation;
    private JSlider sliderScroll;
    private JComboBox<String> comboBoxInterpolation;
    private JCheckBox checkBoxWLcolor;
    private JCheckBox checkBoxLevelInverse;
    private JCheckBox checkBoxApplyPR;

    public ViewerPrefView() {
        super(View2dFactory.NAME);
        setComponentPosition(150);
        initGUI();
    }

    private final void initGUI() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        labels.put(-100, new JLabel(Messages.getString("ViewerPrefView.low"))); //$NON-NLS-1$
        labels.put(0, new JLabel(Messages.getString("ViewerPrefView.mid"))); //$NON-NLS-1$
        labels.put(100, new JLabel(Messages.getString("ViewerPrefView.high"))); //$NON-NLS-1$
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, Messages.getString("ViewerPrefView.mouse_sens"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        add(panel);
        GridBagLayout gblpanel = new GridBagLayout();
        gblpanel.columnWidths = new int[] { 0, 0 };
        gblpanel.rowHeights = new int[] { 0, 0, 0, 0, 0, 0 };
        gblpanel.columnWeights = new double[] { 0.0, 1.0 };
        gblpanel.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 1.0 };
        panel.setLayout(gblpanel);

        JPanel panel1 = new JPanel();
        ((FlowLayout) panel1.getLayout()).setAlignment(FlowLayout.LEADING);
        panel1.setBorder(new TitledBorder(null, Messages.getString("ViewerPrefView.zoom"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        add(panel1);

        JLabel lblInterpolation = new JLabel(Messages.getString("ViewerPrefView.interp") + StringUtil.COLON); //$NON-NLS-1$
        panel1.add(lblInterpolation);
        EventManager eventManager = EventManager.getInstance();

        comboBoxInterpolation = new JComboBox<>(ZoomOp.INTERPOLATIONS);
        comboBoxInterpolation.setSelectedIndex(eventManager.getZoomSetting().getInterpolation());
        panel1.add(comboBoxInterpolation);

        final JPanel winLevelPanel = new JPanel();
        winLevelPanel.setBorder(new TitledBorder(null, Messages.getString("ViewerPrefView.other"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        add(winLevelPanel);
        GridBagLayout gblwinLevelPanel = new GridBagLayout();
        gblwinLevelPanel.columnWeights = new double[] { 0.0 };
        gblwinLevelPanel.rowWeights = new double[] { 0.0, 0.0 };
        winLevelPanel.setLayout(gblwinLevelPanel);

        checkBoxWLcolor =
            new JCheckBox(Messages.getString("ViewerPrefView.wl_color"), eventManager.getOptions().getBooleanProperty( //$NON-NLS-1$
                WindowOp.P_APPLY_WL_COLOR, true));
        GridBagConstraints gbccheckBoxWLcolor = new GridBagConstraints();
        gbccheckBoxWLcolor.anchor = GridBagConstraints.WEST;
        gbccheckBoxWLcolor.gridx = 0;
        gbccheckBoxWLcolor.gridy = 0;
        winLevelPanel.add(checkBoxWLcolor, gbccheckBoxWLcolor);

        checkBoxLevelInverse = new JCheckBox(Messages.getString("ViewerPrefView.inverse_wl"), //$NON-NLS-1$
            eventManager.getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
        GridBagConstraints gbccheckBoxLevelInverse = new GridBagConstraints();
        gbccheckBoxLevelInverse.anchor = GridBagConstraints.WEST;
        gbccheckBoxLevelInverse.gridx = 0;
        gbccheckBoxLevelInverse.gridy = 1;
        winLevelPanel.add(checkBoxLevelInverse, gbccheckBoxLevelInverse);

        checkBoxApplyPR = new JCheckBox(Messages.getString("ViewerPrefView.apply_pr"), //$NON-NLS-1$
            eventManager.getOptions().getBooleanProperty(PRManager.PR_APPLY, false));
        GridBagConstraints gbccheckBoxapplyPR = new GridBagConstraints();
        gbccheckBoxapplyPR.insets = new Insets(0, 0, 5, 0);
        gbccheckBoxapplyPR.weightx = 1.0;
        gbccheckBoxapplyPR.anchor = GridBagConstraints.WEST;
        gbccheckBoxapplyPR.gridx = 0;
        gbccheckBoxapplyPR.gridy = 2;
        winLevelPanel.add(checkBoxApplyPR, gbccheckBoxapplyPR);

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());

        ActionState winAction = eventManager.getAction(ActionW.WINDOW);
        if (winAction instanceof MouseActionAdapter) {
            JLabel lblWindow = new JLabel(Messages.getString("ViewerPrefView.win")); //$NON-NLS-1$
            GridBagConstraints gbclblWindow = new GridBagConstraints();
            gbclblWindow.anchor = GridBagConstraints.NORTHEAST;
            gbclblWindow.insets = new Insets(5, 0, 0, 0);
            gbclblWindow.gridx = 0;
            gbclblWindow.gridy = 0;
            panel.add(lblWindow, gbclblWindow);

            sliderWindow =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) winAction).getMouseSensivity()));
            GridBagConstraints gbcslider = new GridBagConstraints();
            gbcslider.fill = GridBagConstraints.HORIZONTAL;
            gbcslider.anchor = GridBagConstraints.WEST;
            gbcslider.insets = new Insets(5, 2, 0, 0);
            gbcslider.gridx = 1;
            gbcslider.gridy = 0;
            formatSlider(sliderWindow);
            panel.add(sliderWindow, gbcslider);
        }

        ActionState levelAction = eventManager.getAction(ActionW.LEVEL);
        if (levelAction instanceof MouseActionAdapter) {
            JLabel lblLevel = new JLabel(Messages.getString("ViewerPrefView.level")); //$NON-NLS-1$
            GridBagConstraints gbclblLevel = new GridBagConstraints();
            gbclblLevel.anchor = GridBagConstraints.NORTHEAST;
            gbclblLevel.insets = new Insets(5, 15, 0, 0);
            gbclblLevel.gridx = 0;
            gbclblLevel.gridy = 1;
            panel.add(lblLevel, gbclblLevel);

            sliderLevel =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) levelAction).getMouseSensivity()));
            GridBagConstraints gbcslider2 = new GridBagConstraints();
            gbcslider2.fill = GridBagConstraints.HORIZONTAL;
            gbcslider2.anchor = GridBagConstraints.WEST;
            gbcslider2.insets = new Insets(5, 2, 0, 0);
            gbcslider2.gridx = 1;
            gbcslider2.gridy = 1;
            formatSlider(sliderLevel);
            panel.add(sliderLevel, gbcslider2);
        }

        ActionState zoomlAction = eventManager.getAction(ActionW.ZOOM);
        if (zoomlAction instanceof MouseActionAdapter) {
            JLabel lblZoom = new JLabel(Messages.getString("ViewerPrefView.zoom")); //$NON-NLS-1$
            GridBagConstraints gbclblZoom = new GridBagConstraints();
            gbclblZoom.anchor = GridBagConstraints.NORTHEAST;
            gbclblZoom.insets = new Insets(5, 0, 0, 0);
            gbclblZoom.gridx = 0;
            gbclblZoom.gridy = 2;
            panel.add(lblZoom, gbclblZoom);

            sliderZoom =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) zoomlAction).getMouseSensivity()));
            GridBagConstraints gbcslider = new GridBagConstraints();
            gbcslider.fill = GridBagConstraints.HORIZONTAL;
            gbcslider.anchor = GridBagConstraints.WEST;
            gbcslider.insets = new Insets(5, 2, 0, 0);
            gbcslider.gridx = 1;
            gbcslider.gridy = 2;
            formatSlider(sliderZoom);
            panel.add(sliderZoom, gbcslider);
        }

        ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
        if (rotateAction instanceof MouseActionAdapter) {
            JLabel lblRotation = new JLabel(Messages.getString("ResetTools.rotation")); //$NON-NLS-1$
            GridBagConstraints gbclblRotation = new GridBagConstraints();
            gbclblRotation.anchor = GridBagConstraints.NORTHEAST;
            gbclblRotation.insets = new Insets(5, 15, 0, 0);
            gbclblRotation.gridx = 0;
            gbclblRotation.gridy = 3;
            panel.add(lblRotation, gbclblRotation);

            sliderRotation =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) rotateAction).getMouseSensivity()));
            GridBagConstraints gbcslider3 = new GridBagConstraints();
            gbcslider3.fill = GridBagConstraints.HORIZONTAL;
            gbcslider3.anchor = GridBagConstraints.WEST;
            gbcslider3.insets = new Insets(5, 2, 0, 0);
            gbcslider3.gridx = 1;
            gbcslider3.gridy = 3;
            formatSlider(sliderRotation);
            panel.add(sliderRotation, gbcslider3);
        }

        ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
        if (seqAction instanceof MouseActionAdapter) {
            JLabel lblImageScroll = new JLabel(Messages.getString("ViewerPrefView.scrool")); //$NON-NLS-1$
            GridBagConstraints gbclblImageScroll = new GridBagConstraints();
            gbclblImageScroll.anchor = GridBagConstraints.NORTHEAST;
            gbclblImageScroll.insets = new Insets(5, 0, 5, 5);
            gbclblImageScroll.gridx = 0;
            gbclblImageScroll.gridy = 4;
            panel.add(lblImageScroll, gbclblImageScroll);

            sliderScroll =
                new JSlider(-100, 100, realValueToslider(((MouseActionAdapter) seqAction).getMouseSensivity()));
            GridBagConstraints gbcslider1 = new GridBagConstraints();
            gbcslider1.fill = GridBagConstraints.HORIZONTAL;
            gbcslider1.anchor = GridBagConstraints.WEST;
            gbcslider1.insets = new Insets(5, 2, 5, 0);
            gbcslider1.gridx = 1;
            gbcslider1.gridy = 4;
            formatSlider(sliderScroll);
            panel.add(sliderScroll, gbcslider1);
        }
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
        boolean applyWLcolor = checkBoxWLcolor.isSelected();
        eventManager.getOptions().putBooleanProperty(WindowOp.P_APPLY_WL_COLOR, applyWLcolor);

        eventManager.getOptions().putBooleanProperty(PRManager.PR_APPLY, checkBoxApplyPR.isSelected());
        eventManager.getOptions().putBooleanProperty(WindowOp.P_INVERSE_LEVEL, checkBoxLevelInverse.isSelected());
        ImageViewerPlugin<DicomImageElement> view = eventManager.getSelectedView2dContainer();
        if (view != null) {
            view.setMouseActions(eventManager.getMouseActions());
        }

        synchronized (UIManager.VIEWER_PLUGINS) {
            for (final ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
                if (p instanceof View2dContainer) {
                    View2dContainer viewer = (View2dContainer) p;
                    for (ViewCanvas<DicomImageElement> v : viewer.getImagePanels()) {
                        OpManager disOp = v.getDisplayOpManager();
                        disOp.setParamValue(WindowOp.OP_NAME, WindowOp.P_APPLY_WL_COLOR, applyWLcolor);
                        v.changeZoomInterpolation(interpolation);
                    }
                }
            }
        }
    }

    @Override
    public void resetoDefaultValues() {
        sliderWindow.setValue(realValueToslider(1.25));
        sliderLevel.setValue(realValueToslider(1.25));
        sliderScroll.setValue(realValueToslider(0.1));
        sliderRotation.setValue(realValueToslider(0.25));
        sliderZoom.setValue(realValueToslider(0.1));
        comboBoxInterpolation.setSelectedIndex(1);

        // Get the default server configuration and if no value take the default value in parameter.
        EventManager eventManager = EventManager.getInstance();
        eventManager.getOptions().resetProperty(WindowOp.P_APPLY_WL_COLOR, Boolean.TRUE.toString());

        checkBoxWLcolor.setSelected(eventManager.getOptions().getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, true));
        checkBoxLevelInverse.setSelected(eventManager.getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
        checkBoxApplyPR.setSelected(eventManager.getOptions().getBooleanProperty(PRManager.PR_APPLY, false));
    }

    private void formatSlider(JSlider slider) {
        slider.setPaintTicks(true);
        slider.setMajorTickSpacing(100);
        slider.setMinorTickSpacing(5);
        slider.setLabelTable(labels);
        slider.setPaintLabels(true);
    }

    private static double sliderToRealValue(int value) {
        return Math.pow(10, value * 3.0 / 100.0);
    }

    private static int realValueToslider(double value) {
        return (int) (Math.log10(value) * 100.0 / 3.0);
    }
}
