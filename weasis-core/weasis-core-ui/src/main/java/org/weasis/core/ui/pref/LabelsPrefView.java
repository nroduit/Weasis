/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.PageProps;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.bean.Measurement;

public class LabelsPrefView extends AbstractItemDialogPage {
    private static final long serialVersionUID = -1727609322145775651L;

    private final JPanel panelList = new JPanel();
    private final JComboBox<Graphic> comboBoxTool;

    private final ItemListener toolsListener = e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            selectTool((Graphic) e.getItem());
        }
    };

    public LabelsPrefView() {
        super(MeasureTool.LABEL_PREF_NAME);
        setComponentPosition(20);
        setBorder(new EmptyBorder(15, 10, 10, 10));
        BorderLayout borderLayout = new BorderLayout();
        setLayout(borderLayout);

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2, BorderLayout.SOUTH);

        JButton btnNewButton = new JButton(org.weasis.core.ui.Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());

        JPanel panel1 = new JPanel();
        panel1.setBorder(new TitledBorder(null, Messages.getString("LabelsPrefView.geometric1"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        add(panel1, BorderLayout.CENTER);
        panel1.setLayout(new BorderLayout(0, 0));

        JPanel panel = new JPanel();
        panel1.add(panel, BorderLayout.NORTH);
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setHgap(5);
        flowLayout.setAlignment(FlowLayout.LEFT);

        JLabel lblNewLabel = new JLabel(Messages.getString("LabelsPrefView.geometricshape") + StringUtil.COLON); //$NON-NLS-1$
        panel.add(lblNewLabel);
        ArrayList<Graphic> tools = new ArrayList<>(MeasureToolBar.measureGraphicList);
        tools.remove(0);
        comboBoxTool = new JComboBox<>(tools.stream().toArray(Graphic[]::new));
        comboBoxTool.setMaximumRowCount(12);
        selectTool((Graphic) comboBoxTool.getSelectedItem());
        comboBoxTool.addItemListener(toolsListener);
        panel.add(comboBoxTool);
        panel1.add(panelList);
        panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));

        addSubPage(new LabelPrefView(MeasureTool.viewSetting));
        addSubPage(new StatisticsPrefView());
    }

    private void selectTool(Graphic graph) {
        if (graph != null) {
            panelList.removeAll();
            List<Measurement> list = graph.getMeasurementList();
            if (list != null) {
                for (final Measurement m : list) {
                    JCheckBox box = new JCheckBox(m.getName(), m.getGraphicLabel());
                    box.addActionListener(e -> {
                        Object source = e.getSource();
                        if (source instanceof JCheckBox) {
                            m.setGraphicLabel(((JCheckBox) source).isSelected());
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
        for (PageProps subpage : getSubPages()) {
            subpage.closeAdditionalWindow();
        }
        synchronized (UIManager.VIEWER_PLUGINS) {
            for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                ViewerPlugin<?> p = UIManager.VIEWER_PLUGINS.get(i);
                if (p instanceof ImageViewerPlugin) {
                    for (Object v : ((ImageViewerPlugin<?>) p).getImagePanels()) {
                        if (v instanceof ViewCanvas) {
                            ViewCanvas<?> view = (ViewCanvas<?>) v;
                            GraphicModel graphicList = view.getGraphicManager();
                            graphicList.updateLabels(true, view);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void resetoDefaultValues() {
        MeasureToolBar.measureGraphicList.forEach(g -> {
            List<Measurement> list = g.getMeasurementList();
            Optional.ofNullable(list).ifPresent(l -> l.forEach(Measurement::resetToGraphicLabelValue));
        });

        selectTool((Graphic) comboBoxTool.getSelectedItem());
    }

}
