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
package org.weasis.base.viewer2d.dockable;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.ResetTools;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.MouseActions;

public class ImageTool extends PluginTool {

    public final static String BUTTON_NAME = "Image Tool";

    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;

    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

    public ImageTool(String pluginName, Icon icon) {
        super(BUTTON_NAME, pluginName, ToolWindowAnchor.RIGHT);
        setDockableWidth(290);
        jbInit();

    }

    private void jbInit() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(getWindowLevelPanel());
        add(getTransformPanel());
        add(getSlicePanel());
        add(getResetPanel());

        final JPanel panel_1 = new JPanel();
        panel_1.setAlignmentY(Component.TOP_ALIGNMENT);
        panel_1.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel_1.setLayout(new GridBagLayout());
        add(panel_1);
    }

    @Override
    public Component getToolComponent() {
        return new JScrollPane(this);
    }

    public JPanel getResetPanel() {
        final JPanel panel_2 = new JPanel();
        panel_2.setAlignmentY(Component.TOP_ALIGNMENT);
        panel_2.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel_2.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
        panel_2.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Reset",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));
        final JComboBox resetComboBox = new JComboBox(ResetTools.values());
        panel_2.add(resetComboBox);

        final JButton resetButton = new JButton();
        resetButton.setText("Reset");
        resetButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                EventManager.getInstance().reset((ResetTools) resetComboBox.getSelectedItem());
            }
        });
        panel_2.add(resetButton);
        return panel_2;
    }

    public JPanel getSlicePanel() {

        final JPanel framePanel = new JPanel();
        framePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        framePanel.setAlignmentY(Component.TOP_ALIGNMENT);
        framePanel.setLayout(new BoxLayout(framePanel, BoxLayout.Y_AXIS));
        framePanel.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Frame",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        ActionState sequence = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
        if (sequence instanceof SliderCineListener) {
            SliderCineListener cineAction = (SliderCineListener) sequence;
            final JSliderW frameSlider = cineAction.createSlider(4, false);
            framePanel.add(frameSlider.getParent());

            final JPanel panel_3 = new JPanel();
            panel_3.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
            final JLabel speedLabel = new JLabel();
            speedLabel.setText("Cine Speed (fpm):");
            panel_3.add(speedLabel);

            final JSpinner speedSpinner = new JSpinner(cineAction.getSpeedModel());
            JMVUtils.formatCheckAction(speedSpinner);
            panel_3.add(speedSpinner);
            final JButton startButton = new JButton();
            startButton.setActionCommand(ActionW.CINESTART.cmd());
            startButton.setPreferredSize(JMVUtils.getBigIconButtonSize());
            startButton.setToolTipText("Cine start");
            startButton.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/22x22/media-playback-start.png")));
            startButton.addActionListener(EventManager.getInstance());
            panel_3.add(startButton);

            final JButton stopButton = new JButton();
            stopButton.setActionCommand(ActionW.CINESTOP.cmd());
            stopButton.setPreferredSize(JMVUtils.getBigIconButtonSize());
            stopButton.setToolTipText("Cine stop");
            stopButton.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/22x22/media-playback-stop.png")));
            stopButton.addActionListener(EventManager.getInstance());
            panel_3.add(stopButton);
            framePanel.add(panel_3);
        }
        return framePanel;
    }

    public JPanel getWindowLevelPanel() {

        final JPanel winLevelPanel = new JPanel();
        winLevelPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        winLevelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        winLevelPanel.setLayout(new BoxLayout(winLevelPanel, BoxLayout.Y_AXIS));
        winLevelPanel.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, " Window / level ",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));
        ActionState winAction = EventManager.getInstance().getAction(ActionW.WINDOW);
        if (winAction instanceof SliderChangeListener) {
            final JSliderW windowSlider = ((SliderChangeListener) winAction).createSlider(4, false);
            // windowSlider.setMajorTickSpacing((largestWindow - smallestWindow) / 4);
            JMVUtils.setPreferredWidth(windowSlider, 100);
            winLevelPanel.add(windowSlider.getParent());
        }
        ActionState levelAction = EventManager.getInstance().getAction(ActionW.LEVEL);
        if (levelAction instanceof SliderChangeListener) {
            final JSliderW levelSlider = ((SliderChangeListener) levelAction).createSlider(4, false);
            levelSlider
                .setMajorTickSpacing((ImageViewerEventManager.LEVEL_LARGEST - ImageViewerEventManager.LEVEL_SMALLEST) / 4);
            JMVUtils.setPreferredWidth(levelSlider, 100);
            winLevelPanel.add(levelSlider.getParent());
        }
        ActionState presetAction = EventManager.getInstance().getAction(ActionW.PRESET);
        if (presetAction instanceof ComboItemListener) {
            final JPanel panel_3 = new JPanel();
            panel_3.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel presetsLabel = new JLabel();
            panel_3.add(presetsLabel);
            presetsLabel.setText("Preset:");
            final JComboBox presetComboBox = ((ComboItemListener) presetAction).createCombo();
            presetComboBox.setMaximumRowCount(10);
            panel_3.add(presetComboBox);
            winLevelPanel.add(panel_3);
        }
        ActionState lutAction = EventManager.getInstance().getAction(ActionW.LUT);
        if (lutAction instanceof ComboItemListener) {
            final JPanel panel_4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText("LUT:");
            panel_4.add(lutLabel);
            final JComboBox lutcomboBox = ((ComboItemListener) lutAction).createCombo();
            panel_4.add(lutcomboBox);
            ActionState invlutAction = EventManager.getInstance().getAction(ActionW.INVERSELUT);
            if (invlutAction instanceof ToggleButtonListener) {
                panel_4.add(((ToggleButtonListener) invlutAction).createCheckBox("Inverse"));
            }
            winLevelPanel.add(panel_4);
        }
        ActionState filterAction = EventManager.getInstance().getAction(ActionW.FILTER);
        if (filterAction instanceof ComboItemListener) {
            final JPanel panel_4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 3));
            final JLabel lutLabel = new JLabel();
            lutLabel.setText("Filter");
            panel_4.add(lutLabel);
            final JComboBox filtercomboBox = ((ComboItemListener) filterAction).createCombo();
            panel_4.add(filtercomboBox);
            winLevelPanel.add(panel_4);
        }
        return winLevelPanel;
    }

    public JPanel getTransformPanel() {
        final JPanel transform = new JPanel();
        transform.setAlignmentY(Component.TOP_ALIGNMENT);
        transform.setAlignmentX(Component.LEFT_ALIGNMENT);
        transform.setLayout(new BoxLayout(transform, BoxLayout.Y_AXIS));
        transform.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, "Transform",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));
        ActionState zoomAction = EventManager.getInstance().getAction(ActionW.ZOOM);
        if (zoomAction instanceof SliderChangeListener) {
            final JSliderW zoomSlider = ((SliderChangeListener) zoomAction).createSlider(0, false);
            JMVUtils.setPreferredWidth(zoomSlider, 100);
            transform.add(zoomSlider.getParent());
        }
        ActionState rotateAction = EventManager.getInstance().getAction(ActionW.ROTATION);
        if (rotateAction instanceof SliderChangeListener) {
            final JSliderW rotationSlider = ((SliderChangeListener) rotateAction).createSlider(4, false);
            JMVUtils.setPreferredWidth(rotationSlider, 100);
            transform.add(rotationSlider.getParent());
        }
        ActionState flipAction = EventManager.getInstance().getAction(ActionW.FLIP);
        if (flipAction instanceof ToggleButtonListener) {
            JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 3));
            pane.add(((ToggleButtonListener) flipAction).createCheckBox("Flip Image"));
            transform.add(pane);
        }
        return transform;
    }

    @Override
    protected void changeToolWindowAnchor(ToolWindowAnchor anchor) {
        // TODO Auto-generated method stub

    }

}
