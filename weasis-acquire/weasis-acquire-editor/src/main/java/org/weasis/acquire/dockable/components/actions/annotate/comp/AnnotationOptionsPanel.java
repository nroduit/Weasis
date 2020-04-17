/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.annotate.comp;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;

import org.weasis.acquire.Messages;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.pref.ViewSetting;

@SuppressWarnings("serial")
public class AnnotationOptionsPanel extends JPanel {

    private final JPanel lineStylePanel;
    private final JPanel drawOncePanel;
    private final JPanel unitPanel;

    private final Border border = BorderFactory.createEmptyBorder(5, 10, 5, 10);
    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

    public static final Font TITLE_FONT = FontTools.getFont12Bold();
    public static final Color TITLE_COLOR = Color.GRAY;

    public AnnotationOptionsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(spaceY,
            new TitledBorder(null, Messages.getString("AnnotationOptionsPanel.options"), //$NON-NLS-1$
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, TITLE_FONT, TITLE_COLOR)));

        lineStylePanel = createLineStylePanel();
        drawOncePanel = createDrawOnePanel();
        unitPanel = createUnitPanel();

        add(lineStylePanel);
        add(unitPanel);
        add(drawOncePanel);
    }

    private JPanel createLineStylePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 0));
        panel.setBorder(border);

        JLabel label = new JLabel(org.weasis.core.ui.Messages.getString("MeasureToolBar.line") + StringUtil.COLON); //$NON-NLS-1$
        panel.add(label);

        JButton button = new JButton(org.weasis.core.ui.Messages.getString("MeasureTool.pick")); //$NON-NLS-1$
        button.setBackground(MeasureTool.viewSetting.getLineColor());
        button.addActionListener(pickColorAction);
        panel.add(button);

        JSpinner spinner = new JSpinner();
        JMVUtils.setNumberModel(spinner, MeasureTool.viewSetting.getLineWidth(), 1, 8, 1);
        spinner.addChangeListener(changeLineWidth);
        panel.add(spinner);

        return panel;
    }

    private JPanel createDrawOnePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(border);

        ActionState drawOnceAction = EventManager.getInstance().getAction(ActionW.DRAW_ONLY_ONCE);
        if (drawOnceAction instanceof ToggleButtonListener) {
            JCheckBox checkDraw =
                ((ToggleButtonListener) drawOnceAction).createCheckBox(ActionW.DRAW_ONLY_ONCE.getTitle());
            checkDraw.setSelected(MeasureTool.viewSetting.isDrawOnlyOnce());
            checkDraw.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(checkDraw);
        }
        return panel;
    }

    private JPanel createUnitPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 3));

        ActionState spUnitAction = EventManager.getInstance().getAction(ActionW.SPATIAL_UNIT);
        if (spUnitAction instanceof ComboItemListener) {
            JLabel label = new JLabel(org.weasis.core.ui.Messages.getString("MeasureTool.unit") + StringUtil.COLON); //$NON-NLS-1$
            panel.add(label);
            @SuppressWarnings("unchecked")
            JComboBox<Unit> unitComboBox = ((ComboItemListener) spUnitAction).createCombo(120);
            unitComboBox.setSelectedItem(Unit.PIXEL);
            panel.add(unitComboBox);
        }
        return panel;
    }

    private void updateMeasureProperties(final ViewSetting setting) {
        if (setting != null) {
            MeasureToolBar.measureGraphicList.forEach(g -> MeasureToolBar.applyDefaultSetting(setting, g));
            MeasureToolBar.drawGraphicList.forEach(g -> MeasureToolBar.applyDefaultSetting(setting, g));
        }
    }

    public JPanel getLineStylePanel() {
        return lineStylePanel;
    }

    private ActionListener pickColorAction = e -> {
        JButton button = (JButton) e.getSource();
        Color newColor = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(AnnotationOptionsPanel.this),
            org.weasis.core.ui.Messages.getString("MeasureTool.pick_color"), //$NON-NLS-1$
            button.getBackground());
        if (newColor != null) {
            button.setBackground(newColor);
            MeasureTool.viewSetting.setLineColor(newColor);
            updateMeasureProperties(MeasureTool.viewSetting);
        }
    };

    private ChangeListener changeLineWidth = e -> {
        Object val = ((JSpinner) e.getSource()).getValue();
        if (val instanceof Integer) {
            MeasureTool.viewSetting.setLineWidth((Integer) val);
            updateMeasureProperties(MeasureTool.viewSetting);
        }
    };
}
