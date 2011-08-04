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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.Measurement;
import org.weasis.core.ui.util.LabelPrefView;
import org.weasis.core.ui.util.StatisticsPrefView;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;

public class LabelsPrefView extends AbstractItemDialogPage {
    private final JPanel panelList = new JPanel();
    private final ItemListener toolsListener = new ItemListener() {

        @Override
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                selectTool((Graphic) e.getItem());
            }

        }
    };

    public LabelsPrefView() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setTitle(MeasureTool.LABEL_PREF_NAME);
        BorderLayout borderLayout = new BorderLayout();
        setLayout(borderLayout);

        JPanel panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setHgap(2);
        flowLayout.setAlignment(FlowLayout.LEFT);
        add(panel, BorderLayout.NORTH);

        JLabel lblNewLabel = new JLabel("Measure Tool:");
        panel.add(lblNewLabel);
        ArrayList<Graphic> tools = new ArrayList<Graphic>(MeasureToolBar.graphicList);
        tools.remove(0);
        JComboBox comboBox = new JComboBox(tools.toArray());
        comboBox.addItemListener(toolsListener);
        comboBox.setSelectedIndex(0);
        panel.add(comboBox);

        add(panelList);
        panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));

        addSubPage(new LabelPrefView(EventManager.getInstance()));
        addSubPage(new StatisticsPrefView());
    }

    private void init() {
        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2);

        JButton btnNewButton = new JButton(Messages.getString("ViewerPrefView.btnNewButton.text"));
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });
    }

    private void selectTool(Graphic graph) {
        if (graph != null) {
            panelList.removeAll();
            List<Measurement> list = graph.getMeasurementList();
            if (list != null) {
                for (Measurement m : list) {
                    JCheckBox box = new JCheckBox(m.getName(), m.isGraphicLabel());
                    panelList.add(box);
                }
            }
            panelList.revalidate();
            panelList.repaint();
        }

    }

    @Override
    public void closeAdditionalWindow() {
    }

    @Override
    public void resetoDefaultValues() {
        // TODO Auto-generated method stub

    }

}
