/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.GraphicModel;

public class LabelPrefView extends AbstractItemDialogPage {
    private static final long serialVersionUID = -189458600074707084L;

    private static final String[] fontSize = { "8", "9", "10", "11", "12", "13", "14", "15", "16" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$

    private static final String DEFAULT_LABEL = Messages.getString("LabelPrefView.default"); //$NON-NLS-1$

    private final JButton jButtonApply = new JButton();
    private final JPanel jPanel2 = new JPanel();
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JLabel jLabelFont = new JLabel();
    private final JComboBox<String> jComboName = new JComboBox<>();
    private final JLabel jLabelSize = new JLabel();
    private final JComboBox<String> jComboSize = new JComboBox<>(fontSize);
    private final JCheckBox jCheckBoxBold = new JCheckBox();
    private final JCheckBox jCheckBoxItalic = new JCheckBox();
    private final ViewSetting viewSetting;
    private final Component verticalStrut = Box.createVerticalStrut(20);

    public LabelPrefView(ViewSetting viewSetting) {
        super(Messages.getString("LabelPrefView.font")); //$NON-NLS-1$
        if (viewSetting == null) {
            throw new IllegalArgumentException("ViewSetting cannot be null"); //$NON-NLS-1$
        }
        this.viewSetting = viewSetting;
        setComponentPosition(5);
        setBorder(new EmptyBorder(15, 10, 10, 10));

        jComboName.addItem(DEFAULT_LABEL);
        Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .forEach(jComboName::addItem);
        jbInit();
        initialize();
    }

    private void jbInit() {
        this.setLayout(new BorderLayout());
        this.add(jPanel2, BorderLayout.CENTER);
        jPanel2.setLayout(gridBagLayout1);
        jLabelFont.setText(Messages.getString("LabelPrefView.name") + StringUtil.COLON); //$NON-NLS-1$
        jLabelSize.setText(Messages.getString("LabelPrefView.size") + StringUtil.COLON); //$NON-NLS-1$
        jPanel2.setBorder(new TitledBorder(Messages.getString("LabelPrefView.font"))); //$NON-NLS-1$
        jCheckBoxBold.setText(Messages.getString("LabelPrefView.bold")); //$NON-NLS-1$
        jCheckBoxItalic.setText(Messages.getString("LabelPrefView.italic")); //$NON-NLS-1$
        jPanel2.add(jComboSize, new GridBagConstraints(1, 1, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(5, 2, 5, 5), 0, 0));
        jPanel2.add(jLabelFont, new GridBagConstraints(0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 5, 5, 5), 0, 0));
        jPanel2.add(jComboName, new GridBagConstraints(2, 0, 3, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 2, 5, 0), 0, 0));
        jPanel2.add(jCheckBoxItalic, new GridBagConstraints(4, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        jPanel2.add(jCheckBoxBold, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        jPanel2.add(jLabelSize, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));

        GridBagConstraints gbcVerticalStrut = new GridBagConstraints();
        gbcVerticalStrut.weighty = 1.0;
        gbcVerticalStrut.weightx = 1.0;
        gbcVerticalStrut.gridx = 4;
        gbcVerticalStrut.gridy = 2;
        jPanel2.add(verticalStrut, gbcVerticalStrut);

        JPanel panel2 = new JPanel();
        FlowLayout flowLayout1 = (FlowLayout) panel2.getLayout();
        flowLayout1.setHgap(10);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setVgap(7);
        add(panel2, BorderLayout.SOUTH);
        panel2.add(jButtonApply);
        jButtonApply.setText(Messages.getString("LabelPrefView.apply")); //$NON-NLS-1$
        jButtonApply.addActionListener(e -> apply());

        JButton btnNewButton = new JButton(Messages.getString("restore.values")); //$NON-NLS-1$
        panel2.add(btnNewButton);
        btnNewButton.addActionListener(e -> resetoDefaultValues());
    }

    private void initialize() {
        int style = viewSetting.getFontType();
        boolean italic = false;
        boolean bold = false;
        if (style == (Font.BOLD | Font.ITALIC)) {
            italic = true;
            bold = true;
        } else if (style == Font.BOLD) {
            bold = true;
        } else if (style == Font.ITALIC) {
            italic = true;
        }
        jCheckBoxItalic.setSelected(italic);
        jCheckBoxBold.setSelected(bold);
        String size = String.valueOf(viewSetting.getFontSize());
        int index = 2;
        for (int i = 0; i < fontSize.length; i++) {
            if (fontSize[i].equals(size)) {
                index = i;
                break;
            }
        }
        jComboSize.setSelectedIndex(index);
        jComboName.setSelectedItem("default".equals(viewSetting.getFontName()) ? Messages //$NON-NLS-1$
            .getString("LabelPrefView.default") : viewSetting.getFontName()); //$NON-NLS-1$

    }

    public void apply() {
        closeAdditionalWindow();
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
        viewSetting.setFontName(DEFAULT_LABEL);
        viewSetting.setFontType(0);
        viewSetting.setFontSize(12);
        initialize();
    }

    @Override
    public void closeAdditionalWindow() {
        int size = Integer.parseInt(jComboSize.getSelectedItem().toString());
        int style = 0;
        if (jCheckBoxBold.isSelected() && jCheckBoxItalic.isSelected()) {
            style = Font.BOLD | Font.ITALIC;
        } else if (jCheckBoxBold.isSelected()) {
            style = Font.BOLD;
        } else if (jCheckBoxItalic.isSelected()) {
            style = Font.ITALIC;
        }
        String name = jComboName.getSelectedItem().toString();

        viewSetting.setFontName(DEFAULT_LABEL.equals(name) ? "default" : name); //$NON-NLS-1$
        viewSetting.setFontSize(size);
        viewSetting.setFontType(style);

        MeasureToolBar.measureGraphicList.forEach(g -> MeasureToolBar.applyDefaultSetting(viewSetting, g));
    }
}
