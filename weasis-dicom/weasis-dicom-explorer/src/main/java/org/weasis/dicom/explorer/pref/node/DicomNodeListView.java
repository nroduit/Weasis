package org.weasis.dicom.explorer.pref.node;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicComboPopup;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.JMVUtils;

public class DicomNodeListView extends AbstractItemDialogPage {

    public DicomNodeListView() {
        super("Dicom node list");
        initGUI();
    }

    private void initGUI() {
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, "DICOM Node", TitledBorder.LEADING, TitledBorder.TOP, null, null));
        add(panel);
        GridBagLayout gblPanel = new GridBagLayout();
        panel.setLayout(gblPanel);

        JLabel label = new JLabel();
        label.setText("Node:");
        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.anchor = GridBagConstraints.WEST;
        gbcLabel.insets = new Insets(0, 0, 5, 5);
        gbcLabel.gridx = 0;
        gbcLabel.gridy = 0;
        panel.add(label, gbcLabel);

        final JComboBox<DicomNodeEx> nodeComboBox = new JComboBox<>();
        DicomNodeEx.loadPrefDicomNodes(nodeComboBox);
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.NORTHWEST;
        gbcComboBox.insets = new Insets(0, 0, 5, 5);
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 0;
        panel.add(nodeComboBox, gbcComboBox);
        JMVUtils.setPreferredWidth(nodeComboBox, 185, 185);
        addTooltipToComboList(nodeComboBox);

        Component horizontalStrut = Box.createHorizontalStrut(20);
        GridBagConstraints gbcHorizontalStrut = new GridBagConstraints();
        gbcHorizontalStrut.anchor = GridBagConstraints.WEST;
        gbcHorizontalStrut.insets = new Insets(0, 0, 5, 5);
        gbcHorizontalStrut.gridx = 2;
        gbcHorizontalStrut.gridy = 0;
        panel.add(horizontalStrut, gbcHorizontalStrut);

        JButton editButton = new JButton();
        editButton.setText("Edit");
        GridBagConstraints gbcButton1 = new GridBagConstraints();
        gbcButton1.anchor = GridBagConstraints.NORTHWEST;
        gbcButton1.insets = new Insets(0, 0, 5, 5);
        gbcButton1.gridx = 3;
        gbcButton1.gridy = 0;
        panel.add(editButton, gbcButton1);

        JButton deleteButton = new JButton();
        deleteButton.setText("Delete");
        GridBagConstraints gbcButton2 = new GridBagConstraints();
        gbcButton2.insets = new Insets(0, 0, 5, 5);
        gbcButton2.anchor = GridBagConstraints.NORTHWEST;
        gbcButton2.gridx = 4;
        gbcButton2.gridy = 0;
        panel.add(deleteButton, gbcButton2);

        JButton addNodeButton = new JButton("Add new");
        GridBagConstraints gbcButton = new GridBagConstraints();
        gbcButton.gridwidth = 2;
        gbcButton.anchor = GridBagConstraints.NORTHWEST;
        gbcButton.insets = new Insets(10, 0, 5, 5);
        gbcButton.gridx = 3;
        gbcButton.gridy = 1;
        panel.add(addNodeButton, gbcButton);

        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                int index = nodeComboBox.getSelectedIndex();
                if (index >= 0) {
                    int response = JOptionPane.showConfirmDialog(null,
                        String.format("Do you really want to delete \"%s\"?", nodeComboBox.getSelectedItem()),
                        "DICOM Node", //$NON-NLS-1$
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                    if (response == 0) {
                        nodeComboBox.removeItemAt(index);
                        DicomNodeEx.savePrefDicomNodes(nodeComboBox);
                    }

                }
            }
        });
        editButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DicomNodeDialog dialog =
                    new DicomNodeDialog(SwingUtilities.getWindowAncestor((Component) evt.getSource()), "DICOM Node", //$NON-NLS-1$
                        (DicomNodeEx) nodeComboBox.getSelectedItem(), nodeComboBox);
                JMVUtils.showCenterScreen(dialog, (Component) evt.getSource());
            }
        });
        addNodeButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DicomNodeDialog dialog = new DicomNodeDialog(
                    SwingUtilities.getWindowAncestor((Component) evt.getSource()), "DICOM Node", null, nodeComboBox); //$NON-NLS-1$
                JMVUtils.showCenterScreen(dialog, (Component) evt.getSource());
            }
        });

        Box verticalBox = Box.createVerticalBox();
        GridBagConstraints gbc_verticalBox = new GridBagConstraints();
        gbc_verticalBox.weighty = 1.0;
        gbc_verticalBox.weightx = 1.0;
        gbc_verticalBox.insets = new Insets(0, 0, 5, 0);
        gbc_verticalBox.fill = GridBagConstraints.BOTH;
        gbc_verticalBox.anchor = GridBagConstraints.NORTHWEST;
        gbc_verticalBox.gridx = 4;
        gbc_verticalBox.gridy = 2;
        panel.add(verticalBox, gbc_verticalBox);

        JPanel panel1 = new JPanel();
        panel1.setBorder(new TitledBorder(new LineBorder(new Color(184, 207, 229)), "DICOM WEB Node",
            TitledBorder.LEADING, TitledBorder.TOP, null, new Color(51, 51, 51)));
        add(panel1);
        GridBagLayout gblPanel1 = new GridBagLayout();
        panel1.setLayout(gblPanel1);

        JLabel label_1 = new JLabel();
        label_1.setText("Node:");
        GridBagConstraints gbc_label_1 = new GridBagConstraints();
        gbc_label_1.anchor = GridBagConstraints.WEST;
        gbc_label_1.insets = new Insets(0, 0, 5, 5);
        gbc_label_1.gridx = 0;
        gbc_label_1.gridy = 0;
        panel1.add(label_1, gbc_label_1);

        final JComboBox<DicomWebNode> comboBoxWeb = new JComboBox<>();
        DicomWebNode.loadDicomNodes(comboBoxWeb);
        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.anchor = GridBagConstraints.NORTHWEST;
        gbc_comboBox.insets = new Insets(0, 0, 5, 5);
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 0;
        panel1.add(comboBoxWeb, gbc_comboBox);
        JMVUtils.setPreferredWidth(comboBoxWeb, 185, 185);
        addTooltipToComboList(comboBoxWeb);

        Component horizontalStrut_1 = Box.createHorizontalStrut(20);
        GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
        gbc_horizontalStrut_1.anchor = GridBagConstraints.WEST;
        gbc_horizontalStrut_1.insets = new Insets(0, 0, 5, 5);
        gbc_horizontalStrut_1.gridx = 2;
        gbc_horizontalStrut_1.gridy = 0;
        panel1.add(horizontalStrut_1, gbc_horizontalStrut_1);

        JButton editBtn1 = new JButton();
        editBtn1.setText("Edit");
        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.anchor = GridBagConstraints.NORTHWEST;
        gbc_button.insets = new Insets(0, 0, 5, 5);
        gbc_button.gridx = 3;
        gbc_button.gridy = 0;
        panel1.add(editBtn1, gbc_button);

        JButton deleteBtn1 = new JButton();
        deleteBtn1.setText("Delete");
        GridBagConstraints gbc_button_1 = new GridBagConstraints();
        gbc_button_1.anchor = GridBagConstraints.NORTHWEST;
        gbc_button_1.insets = new Insets(0, 0, 5, 0);
        gbc_button_1.gridx = 4;
        gbc_button_1.gridy = 0;
        panel1.add(deleteBtn1, gbc_button_1);

        JButton addBtn1 = new JButton("Add new");
        GridBagConstraints gbc_button_2 = new GridBagConstraints();
        gbc_button_2.anchor = GridBagConstraints.NORTHWEST;
        gbc_button_2.gridwidth = 2;
        gbc_button_2.insets = new Insets(10, 0, 5, 0);
        gbc_button_2.gridx = 3;
        gbc_button_2.gridy = 1;
        panel1.add(addBtn1, gbc_button_2);

        Box verticalBox1 = Box.createVerticalBox();
        GridBagConstraints gbc_verticalBox1 = new GridBagConstraints();
        gbc_verticalBox1.weighty = 1.0;
        gbc_verticalBox1.weightx = 1.0;
        gbc_verticalBox1.insets = new Insets(0, 0, 5, 0);
        gbc_verticalBox1.fill = GridBagConstraints.BOTH;
        gbc_verticalBox1.anchor = GridBagConstraints.NORTHWEST;
        gbc_verticalBox1.gridx = 4;
        gbc_verticalBox1.gridy = 2;
        panel1.add(verticalBox1, gbc_verticalBox1);

        deleteBtn1.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                int index = comboBoxWeb.getSelectedIndex();
                if (index >= 0) {
                    int response = JOptionPane.showConfirmDialog(null,
                        String.format("Do you really want to delete \"%s\"?", comboBoxWeb.getSelectedItem()),
                        "DICOM WEB Node", //$NON-NLS-1$
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                    if (response == 0) {
                        comboBoxWeb.removeItemAt(index);
                        DicomWebNode.saveDicomNodes(comboBoxWeb);
                    }
                }
            }
        });
        editBtn1.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DicomWebNodeDialog dialog = new DicomWebNodeDialog(
                    SwingUtilities.getWindowAncestor((Component) evt.getSource()), "DICOM WEB Node", //$NON-NLS-1$
                    (DicomWebNode) comboBoxWeb.getSelectedItem(), comboBoxWeb);
                JMVUtils.showCenterScreen(dialog, (Component) evt.getSource());
            }
        });
        addBtn1.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DicomWebNodeDialog dialog = new DicomWebNodeDialog(
                    SwingUtilities.getWindowAncestor((Component) evt.getSource()), "DICOM WEB Node", //$NON-NLS-1$
                    null, comboBoxWeb);
                JMVUtils.showCenterScreen(dialog, (Component) evt.getSource());
            }
        });

    }

    @Override
    public void closeAdditionalWindow() {

    }

    @Override
    public void resetoDefaultValues() {

    }

    private static void addTooltipToComboList(final JComboBox<? extends DcmNode> combo) {
        Object comp = combo.getUI().getAccessibleChild(combo, 0);
        if (comp instanceof BasicComboPopup) {
            final BasicComboPopup popup = (BasicComboPopup) comp;
            popup.getList().getSelectionModel().addListSelectionListener(new ListSelectionListener() {

                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        ListSelectionModel model = (ListSelectionModel) e.getSource();
                        int first = model.getMinSelectionIndex();
                        if (first >= 0) {
                            DcmNode item = combo.getItemAt(first);
                            ((JComponent) combo.getRenderer()).setToolTipText(item.getToolTips());
                        }
                    }
                }
            });
        }
    }
}
