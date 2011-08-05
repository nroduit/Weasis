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
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.Measurement;
import org.weasis.core.ui.util.LabelPrefView;
import org.weasis.core.ui.util.StatisticsPrefView;
import org.weasis.dicom.viewer2d.EventManager;

public class LabelsPrefView extends AbstractItemDialogPage {
    private final JPanel panelList = new JPanel();
    private JComboBox comboBoxTool;
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

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values"));
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });

        JPanel panel1 = new JPanel();
        panel1.setBorder(new TitledBorder(null, "Visible Measurements", TitledBorder.LEADING, TitledBorder.TOP, null,
            null));
        add(panel1, BorderLayout.CENTER);
        panel1.setLayout(new BorderLayout(0, 0));

        JPanel panel = new JPanel();
        panel1.add(panel, BorderLayout.NORTH);
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setHgap(2);
        flowLayout.setAlignment(FlowLayout.LEFT);

        JLabel lblNewLabel = new JLabel("Measure Tool:");
        panel.add(lblNewLabel);
        ArrayList<Graphic> tools = new ArrayList<Graphic>(MeasureToolBar.graphicList);
        tools.remove(0);
        comboBoxTool = new JComboBox(tools.toArray());
        selectTool((Graphic) comboBoxTool.getSelectedItem());
        comboBoxTool.addItemListener(toolsListener);
        panel.add(comboBoxTool);
        panel1.add(panelList);
        panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));

        addSubPage(new LabelPrefView(EventManager.getInstance()));
        addSubPage(new StatisticsPrefView());
    }

    private void selectTool(Graphic graph) {
        if (graph != null) {
            panelList.removeAll();
            List<Measurement> list = graph.getMeasurementList();
            if (list != null) {
                for (final Measurement m : list) {
                    JCheckBox box = new JCheckBox(m.getName(), m.isGraphicLabel());
                    box.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            Object source = e.getSource();
                            if (source instanceof JCheckBox) {
                                m.setGraphicLabel(((JCheckBox) source).isSelected());
                            }
                        }
                    });
                    panelList.add(box);
                }
            }
            panelList.revalidate();
            panelList.repaint();
        }

    }

    @Override
    public void closeAdditionalWindow() {
        synchronized (UIManager.VIEWER_PLUGINS) {
            for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                if (p instanceof ImageViewerPlugin) {
                    for (Object v : ((ImageViewerPlugin) p).getImagePanels()) {
                        if (v instanceof DefaultView2d) {
                            DefaultView2d view = (DefaultView2d) v;
                            List<Graphic> list = view.getLayerModel().getAllGraphics();
                            for (Graphic graphic : list) {
                                graphic.updateLabel(view.getImage(), view);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void resetoDefaultValues() {
        for (Graphic graph : MeasureToolBar.graphicList) {
            List<Measurement> list = graph.getMeasurementList();
            if (list != null) {
                for (Measurement m : list) {
                    m.resetToGraphicLabelValue();
                }
            }
        }
        selectTool((Graphic) comboBoxTool.getSelectedItem());
    }

}
