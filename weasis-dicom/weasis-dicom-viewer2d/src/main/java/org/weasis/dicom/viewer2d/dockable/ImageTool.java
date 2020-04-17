/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d.dockable;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JViewport;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.ResetTools;

import bibliothek.gui.dock.common.CLocation;

public class ImageTool extends PluginTool {

    public static final String BUTTON_NAME = Messages.getString("ImageTool.img_tool"); //$NON-NLS-1$

    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;

    private final JScrollPane rootPane = new JScrollPane();
    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

    public ImageTool(String pluginName) {
        super(BUTTON_NAME, pluginName, PluginTool.Type.TOOL, 20);
        dockable.setTitleIcon(new ImageIcon(ImageTool.class.getResource("/icon/16x16/image.png"))); //$NON-NLS-1$
        setDockableWidth(290);
        jbInit();

    }

    private void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(getWindowLevelPanel());
        add(getTransformPanel());
        add(getSlicePanel());
        add(getResetPanel());

        final JPanel panel1 = new JPanel();
        panel1.setAlignmentY(Component.TOP_ALIGNMENT);
        panel1.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel1.setLayout(new GridBagLayout());
        add(panel1);
    }

    @Override
    public Component getToolComponent() {
        JViewport viewPort = rootPane.getViewport();
        if (viewPort == null) {
            viewPort = new JViewport();
            rootPane.setViewport(viewPort);
        }
        if (viewPort.getView() != this) {
            viewPort.setView(this);
        }
        return rootPane;
    }

    public JPanel getResetPanel() {
        final JPanel panel2 = new JPanel();
        panel2.setAlignmentY(Component.TOP_ALIGNMENT);
        panel2.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel2.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
        panel2.setBorder(
            BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, Messages.getString("ResetTools.reset"), //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));
        final JComboBox resetComboBox = new JComboBox(ResetTools.values());
        panel2.add(resetComboBox);

        final JButton resetButton = new JButton();
        resetButton.setText(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
        resetButton
            .addActionListener(e -> EventManager.getInstance().reset((ResetTools) resetComboBox.getSelectedItem()));
        panel2.add(resetButton);
        ActionState resetAction = EventManager.getInstance().getAction(ActionW.RESET);
        if (resetAction != null) {
            resetAction.registerActionState(resetButton);
        }
        return panel2;
    }

    public JPanel getSlicePanel() {

        final JPanel framePanel = new JPanel();
        framePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        framePanel.setAlignmentY(Component.TOP_ALIGNMENT);
        framePanel.setLayout(new BoxLayout(framePanel, BoxLayout.Y_AXIS));
        framePanel.setBorder(
            BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, Messages.getString("ImageTool.frame"), //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            SliderCineListener cineAction = (SliderCineListener) sequence;
            final JSliderW frameSlider = cineAction.createSlider(2, true);
            framePanel.add(frameSlider.getParent());

            final JPanel panel3 = new JPanel();
            panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
            final JLabel speedLabel = new JLabel();
            speedLabel.setText(Messages.getString("ImageTool.cine_speed") + StringUtil.COLON); //$NON-NLS-1$
            panel3.add(speedLabel);

            final JSpinner speedSpinner = new JSpinner(cineAction.getSpeedModel());
            JMVUtils.formatCheckAction(speedSpinner);
            panel3.add(speedSpinner);
            final JButton startButton = new JButton();
            startButton.setActionCommand(ActionW.CINESTART.cmd());
            startButton.setPreferredSize(JMVUtils.getBigIconButtonSize());
            startButton.setToolTipText(Messages.getString("ImageTool.cine_start")); //$NON-NLS-1$
            startButton.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/22x22/media-playback-start.png"))); //$NON-NLS-1$
            startButton.addActionListener(EventManager.getInstance());
            panel3.add(startButton);
            cineAction.registerActionState(startButton);

            final JButton stopButton = new JButton();
            stopButton.setActionCommand(ActionW.CINESTOP.cmd());
            stopButton.setPreferredSize(JMVUtils.getBigIconButtonSize());
            stopButton.setToolTipText(Messages.getString("ImageTool.cine_stop")); //$NON-NLS-1$
            stopButton.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/22x22/media-playback-stop.png"))); //$NON-NLS-1$
            stopButton.addActionListener(EventManager.getInstance());
            panel3.add(stopButton);
            cineAction.registerActionState(stopButton);
            framePanel.add(panel3);
        }
        return framePanel;
    }

    public JPanel getWindowLevelPanel() {

        final JPanel winLevelPanel = new JPanel();
        winLevelPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        winLevelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        winLevelPanel.setLayout(new BoxLayout(winLevelPanel, BoxLayout.Y_AXIS));
        winLevelPanel.setBorder(
            BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, Messages.getString("ImageTool.wl"), //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));
        ActionState winAction = EventManager.getInstance().getAction(ActionW.WINDOW);
        if (winAction instanceof SliderChangeListener) {
            final JSliderW windowSlider = ((SliderChangeListener) winAction).createSlider(2, true);
            JMVUtils.setPreferredWidth(windowSlider, 100);
            winLevelPanel.add(windowSlider.getParent());
        }
        ActionState levelAction = EventManager.getInstance().getAction(ActionW.LEVEL);
        if (levelAction instanceof SliderChangeListener) {
            final JSliderW levelSlider = ((SliderChangeListener) levelAction).createSlider(2, true);
            JMVUtils.setPreferredWidth(levelSlider, 100);
            winLevelPanel.add(levelSlider.getParent());
        }

        ActionState presetAction = EventManager.getInstance().getAction(ActionW.PRESET);
        if (presetAction instanceof ComboItemListener) {
            final JPanel panel3 = new JPanel();
            panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel presetsLabel = new JLabel();
            panel3.add(presetsLabel);
            presetsLabel.setText(Messages.getString("ImageTool.presets") + StringUtil.COLON); //$NON-NLS-1$
            final JComboBox presetComboBox = ((ComboItemListener) presetAction).createCombo(160);
            presetComboBox.setMaximumRowCount(10);
            panel3.add(presetComboBox);
            winLevelPanel.add(panel3);
        }

        ActionState lutShapeAction = EventManager.getInstance().getAction(ActionW.LUT_SHAPE);
        if (lutShapeAction instanceof ComboItemListener) {
            final JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel label = new JLabel(ActionW.LUT_SHAPE.getTitle() + StringUtil.COLON);
            pane.add(label);
            final JComboBox combo = ((ComboItemListener) lutShapeAction).createCombo(140);
            combo.setMaximumRowCount(10);
            pane.add(combo);
            winLevelPanel.add(pane);
        }

        ActionState lutAction = EventManager.getInstance().getAction(ActionW.LUT);
        if (lutAction instanceof ComboItemListener) {
            final JPanel panel4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText(Messages.getString("ImageTool.lut") + StringUtil.COLON); //$NON-NLS-1$
            panel4.add(lutLabel);
            final JComboBox lutcomboBox = ((ComboItemListener) lutAction).createCombo(140);
            panel4.add(lutcomboBox);
            ActionState invlutAction = EventManager.getInstance().getAction(ActionW.INVERT_LUT);
            if (invlutAction instanceof ToggleButtonListener) {
                panel4
                    .add(((ToggleButtonListener) invlutAction).createCheckBox(Messages.getString("ImageTool.inverse"))); //$NON-NLS-1$
            }
            winLevelPanel.add(panel4);
        }

        ActionState filterAction = EventManager.getInstance().getAction(ActionW.FILTER);
        if (filterAction instanceof ComboItemListener) {
            final JPanel panel4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText(Messages.getString("ImageTool.filter") + StringUtil.COLON); //$NON-NLS-1$
            panel4.add(lutLabel);
            final JComboBox filtercomboBox = ((ComboItemListener) filterAction).createCombo(160);
            panel4.add(filtercomboBox);
            winLevelPanel.add(panel4);
        }
        return winLevelPanel;
    }

    public JPanel getTransformPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(
            BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, Messages.getString("ImageTool.transform"), //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));
        ActionState zoomAction = EventManager.getInstance().getAction(ActionW.ZOOM);
        if (zoomAction instanceof SliderChangeListener) {
            final JSliderW zoomSlider = ((SliderChangeListener) zoomAction).createSlider(0, true);
            JMVUtils.setPreferredWidth(zoomSlider, 100);
            transform.add(zoomSlider.getParent());
        }
        ActionState rotateAction = EventManager.getInstance().getAction(ActionW.ROTATION);
        if (rotateAction instanceof SliderChangeListener) {
            final JSliderW rotationSlider = ((SliderChangeListener) rotateAction).createSlider(5, true);
            JMVUtils.setPreferredWidth(rotationSlider, 100);
            transform.add(rotationSlider.getParent());
        }
        ActionState flipAction = EventManager.getInstance().getAction(ActionW.FLIP);
        if (flipAction instanceof ToggleButtonListener) {
            JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            pane.add(((ToggleButtonListener) flipAction).createCheckBox(Messages.getString("View2dContainer.flip_h"))); //$NON-NLS-1$
            transform.add(pane);
        }
        return transform;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        // Do nothing
    }

}
