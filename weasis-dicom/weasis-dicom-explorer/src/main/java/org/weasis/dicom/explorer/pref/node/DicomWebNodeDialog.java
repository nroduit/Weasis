package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class DicomWebNodeDialog extends JDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomWebNodeDialog.class);

    private JLabel urlLabel;
    private JTextField urlTf;
    private JButton cancelButton;
    private JLabel descriptionLabel;
    private JTextField descriptionTf;
    private JButton okButton;
    private DicomWebNode dicomNode;
    private JComboBox<DicomWebNode> nodesComboBox;
    private JPanel footPanel;
    private JLabel lblType;
    private JComboBox<DicomWebNode.Type> comboBox;

    public DicomWebNodeDialog(Window parent, String title, DicomWebNode dicomNode,
        JComboBox<DicomWebNode> nodeComboBox) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        initComponents();
        this.dicomNode = dicomNode;
        this.nodesComboBox = nodeComboBox;
        if (dicomNode != null) {
            descriptionTf.setText(dicomNode.getDescription());
            urlTf.setText(dicomNode.getUrl().toString());
            comboBox.setSelectedItem(dicomNode.getType());
        }
        pack();
    }

    private void initComponents() {
        final JPanel rootPane = new JPanel();
        rootPane.setBorder(new EmptyBorder(10, 15, 10, 15));
        this.setContentPane(rootPane);

        final JPanel content = new JPanel();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        rootPane.setLayout(new BorderLayout(0, 0));
        GridBagLayout gridBagLayout = new GridBagLayout();
        content.setLayout(gridBagLayout);
        descriptionLabel = new JLabel();
        GridBagConstraints gbc_descriptionLabel = new GridBagConstraints();
        gbc_descriptionLabel.insets = new Insets(0, 0, 5, 5);
        gbc_descriptionLabel.gridx = 0;
        gbc_descriptionLabel.gridy = 0;
        content.add(descriptionLabel, gbc_descriptionLabel);

        descriptionLabel.setText(Messages.getString("PrinterDialog.desc") + StringUtil.COLON);
        descriptionTf = new JTextField();
        GridBagConstraints gbc_descriptionTf = new GridBagConstraints();
        gbc_descriptionTf.anchor = GridBagConstraints.LINE_START;
        gbc_descriptionTf.insets = new Insets(0, 0, 5, 5);
        gbc_descriptionTf.gridx = 1;
        gbc_descriptionTf.gridy = 0;
        content.add(descriptionTf, gbc_descriptionTf);
        descriptionTf.setColumns(20);

        lblType = new JLabel(Messages.getString("DicomNodeDialog.lblType.text") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcLblType = new GridBagConstraints();
        gbcLblType.anchor = GridBagConstraints.EAST;
        gbcLblType.insets = new Insets(0, 0, 5, 5);
        gbcLblType.gridx = 0;
        gbcLblType.gridy = 1;
        content.add(lblType, gbcLblType);

        comboBox = new JComboBox<>(new DefaultComboBoxModel<>(DicomWebNode.Type.values()));
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.anchor = GridBagConstraints.LINE_START;
        gbcComboBox.insets = new Insets(0, 0, 5, 5);
        gbcComboBox.gridx = 1;
        gbcComboBox.gridy = 1;
        content.add(comboBox, gbcComboBox);

        urlLabel = new JLabel();
        urlLabel.setText("URL" + StringUtil.COLON);
        GridBagConstraints gbcAeTitleLabel = new GridBagConstraints();
        gbcAeTitleLabel.anchor = GridBagConstraints.SOUTHEAST;
        gbcAeTitleLabel.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleLabel.gridx = 0;
        gbcAeTitleLabel.gridy = 2;
        content.add(urlLabel, gbcAeTitleLabel);
        urlTf = new JTextField();
        urlTf.setColumns(30);
        GridBagConstraints gbcAeTitleTf = new GridBagConstraints();
        gbcAeTitleTf.anchor = GridBagConstraints.WEST;
        gbcAeTitleTf.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleTf.gridx = 1;
        gbcAeTitleTf.gridy = 2;
        content.add(urlTf, gbcAeTitleTf);
        NumberFormat myFormat = LocalUtil.getNumberInstance();
        myFormat.setMinimumIntegerDigits(0);
        myFormat.setMaximumIntegerDigits(65535);
        myFormat.setMaximumFractionDigits(0);

        this.getContentPane().add(content, BorderLayout.CENTER);

        footPanel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) footPanel.getLayout();
        flowLayout.setVgap(15);
        flowLayout.setAlignment(FlowLayout.RIGHT);
        flowLayout.setHgap(20);
        getContentPane().add(footPanel, BorderLayout.SOUTH);

        okButton = new JButton();
        footPanel.add(okButton);

        okButton.setText(Messages.getString("PrinterDialog.ok")); //$NON-NLS-1$
        okButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        cancelButton = new JButton();
        footPanel.add(cancelButton);

        cancelButton.setText(Messages.getString("PrinterDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
    }

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {
        dispose();
    }

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {
        String desc = descriptionTf.getText();
        String url = urlTf.getText();

        if (!StringUtil.hasText(desc) || !StringUtil.hasText(url)) {
            JOptionPane.showMessageDialog(this, Messages.getString("PrinterDialog.fill_message"), //$NON-NLS-1$
                Messages.getString("PrinterDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }

        URL validUrl;
        try {
            validUrl = new URL(url);
        } catch (MalformedURLException e) {
            LOGGER.warn("Non valid url", e);
            JOptionPane.showMessageDialog(this, "This URL is not valid", Messages.getString("PrinterDialog.error"), //$NON-NLS-2$
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean addNode = dicomNode == null;
        if (addNode) {
            dicomNode = new DicomWebNode(desc, (DicomWebNode.Type) comboBox.getSelectedItem(), validUrl);
            nodesComboBox.addItem(dicomNode);
            nodesComboBox.setSelectedItem(dicomNode);
        } else {
            dicomNode.setDescription(desc);
            dicomNode.setUrl(validUrl);
            nodesComboBox.repaint();
        }

        DicomWebNode.saveDicomNodes(nodesComboBox);
        dispose();
    }
}
