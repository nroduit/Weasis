package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.text.NumberFormat;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.text.NumberFormatter;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class DicomNodeDialog extends JDialog {
    private JLabel aeTitleLabel;
    private JTextField aeTitleTf;
    private JButton cancelButton;
    private JCheckBox colorPrintSupportCheckBox;
    private JLabel descriptionLabel;
    private JTextField descriptionTf;
    private JLabel hostnameLabel;
    private JTextField hostnameTf;
    private JButton okButton;
    private JLabel portLabel;
    private JFormattedTextField portTf;
    private JPanel footPanel;

    private DicomNodeEx dicomNode;
    private final JComboBox<DicomNodeEx> nodesComboBox;
    private final DicomNodeEx.Type typeNode;

    public DicomNodeDialog(Window parent, String title, DicomNodeEx dicomNode, JComboBox<DicomNodeEx> nodeComboBox) {
        this(parent, title, dicomNode, nodeComboBox, DicomNodeEx.Type.ARCHIVE);
    }

    public DicomNodeDialog(Window parent, String title, DicomNodeEx dicomNode, JComboBox<DicomNodeEx> nodeComboBox,
        DicomNodeEx.Type typeNode) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        this.typeNode =
            dicomNode == null ? typeNode == null ? DicomNodeEx.Type.ARCHIVE : typeNode : dicomNode.getType();
        initComponents();
        this.dicomNode = dicomNode;
        this.nodesComboBox = nodeComboBox;
        if (dicomNode != null) {
            descriptionTf.setText(dicomNode.getDescription());
            aeTitleTf.setText(dicomNode.getAeTitle());
            hostnameTf.setText(dicomNode.getHostname());
            portTf.setValue(dicomNode.getPort());
            colorPrintSupportCheckBox.setSelected(dicomNode.isColorPrintSupported());
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
        GridBagConstraints gbcDescriptionLabel = new GridBagConstraints();
        gbcDescriptionLabel.insets = new Insets(0, 0, 5, 5);
        gbcDescriptionLabel.gridx = 0;
        gbcDescriptionLabel.gridy = 0;
        content.add(descriptionLabel, gbcDescriptionLabel);

        descriptionLabel.setText(Messages.getString("PrinterDialog.desc") + StringUtil.COLON);
        descriptionTf = new JTextField();
        GridBagConstraints gbcDescriptionTf = new GridBagConstraints();
        gbcDescriptionTf.insets = new Insets(0, 0, 5, 5);
        gbcDescriptionTf.gridx = 1;
        gbcDescriptionTf.gridy = 0;
        content.add(descriptionTf, gbcDescriptionTf);
        descriptionTf.setColumns(15);

        colorPrintSupportCheckBox = new JCheckBox();
        if (typeNode == DicomNodeEx.Type.PRINTER) {
            colorPrintSupportCheckBox.setText(Messages.getString("PrinterDialog.color")); //$NON-NLS-1$
            GridBagConstraints gbcColorPrintSupportCheckBox = new GridBagConstraints();
            gbcColorPrintSupportCheckBox.anchor = GridBagConstraints.WEST;
            gbcColorPrintSupportCheckBox.insets = new Insets(0, 0, 0, 5);
            gbcColorPrintSupportCheckBox.gridwidth = 2;
            gbcColorPrintSupportCheckBox.gridx = 0;
            gbcColorPrintSupportCheckBox.gridy = 3;
            content.add(colorPrintSupportCheckBox, gbcColorPrintSupportCheckBox);
        }

        aeTitleLabel = new JLabel();
        aeTitleLabel.setText(Messages.getString("PrinterDialog.aet") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcAeTitleLabel = new GridBagConstraints();
        gbcAeTitleLabel.anchor = GridBagConstraints.SOUTHEAST;
        gbcAeTitleLabel.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleLabel.gridx = 0;
        gbcAeTitleLabel.gridy = 1;
        content.add(aeTitleLabel, gbcAeTitleLabel);
        aeTitleTf = new JTextField();
        aeTitleTf.setColumns(15);
        GridBagConstraints gbcAeTitleTf = new GridBagConstraints();
        gbcAeTitleTf.anchor = GridBagConstraints.WEST;
        gbcAeTitleTf.insets = new Insets(0, 0, 5, 5);
        gbcAeTitleTf.gridx = 1;
        gbcAeTitleTf.gridy = 1;
        content.add(aeTitleTf, gbcAeTitleTf);
        hostnameLabel = new JLabel();

        hostnameLabel.setText(Messages.getString("PrinterDialog.host") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcHostnameLabel = new GridBagConstraints();
        gbcHostnameLabel.anchor = GridBagConstraints.EAST;
        gbcHostnameLabel.insets = new Insets(0, 0, 5, 5);
        gbcHostnameLabel.gridx = 0;
        gbcHostnameLabel.gridy = 2;
        content.add(hostnameLabel, gbcHostnameLabel);
        hostnameTf = new JTextField();
        hostnameTf.setColumns(15);
        GridBagConstraints gbcHostnameTf = new GridBagConstraints();
        gbcHostnameTf.anchor = GridBagConstraints.WEST;
        gbcHostnameTf.insets = new Insets(0, 0, 5, 5);
        gbcHostnameTf.gridx = 1;
        gbcHostnameTf.gridy = 2;
        content.add(hostnameTf, gbcHostnameTf);
        portLabel = new JLabel();

        portLabel.setText(Messages.getString("PrinterDialog.port") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints gbcPortLabel = new GridBagConstraints();
        gbcPortLabel.anchor = GridBagConstraints.WEST;
        gbcPortLabel.insets = new Insets(0, 0, 5, 5);
        gbcPortLabel.gridx = 2;
        gbcPortLabel.gridy = 2;
        content.add(portLabel, gbcPortLabel);
        NumberFormat myFormat = LocalUtil.getNumberInstance();
        myFormat.setMinimumIntegerDigits(0);
        myFormat.setMaximumIntegerDigits(65535);
        myFormat.setMaximumFractionDigits(0);
        portTf = new JFormattedTextField(new NumberFormatter(myFormat));
        portTf.setColumns(5);
        JMVUtils.setPreferredWidth(portTf, 60);
        JMVUtils.addCheckAction(portTf);
        GridBagConstraints gbcPortTf = new GridBagConstraints();
        gbcPortTf.anchor = GridBagConstraints.WEST;
        gbcPortTf.insets = new Insets(0, 0, 5, 0);
        gbcPortTf.gridx = 3;
        gbcPortTf.gridy = 2;
        content.add(portTf, gbcPortTf);

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
                okButtonActionPerformed();
            }
        });
        cancelButton = new JButton();
        footPanel.add(cancelButton);

        cancelButton.setText(Messages.getString("PrinterDialog.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dispose();
            }
        });
    }

    private void okButtonActionPerformed() {
        String desc = descriptionTf.getText();
        String aeTitle = aeTitleTf.getText();
        String hostname = hostnameTf.getText();
        Number port = JMVUtils.getFormattedValue(portTf);

        if (!StringUtil.hasText(desc) || !StringUtil.hasText(aeTitle) || !StringUtil.hasText(hostname)
            || port == null) {
            JOptionPane.showMessageDialog(this, Messages.getString("PrinterDialog.fill_message"), //$NON-NLS-1$
                Messages.getString("PrinterDialog.error"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }

        boolean addNode = dicomNode == null;
        if (addNode) {
            dicomNode = new DicomNodeEx(desc, aeTitle, hostname, port.intValue());
            nodesComboBox.addItem(dicomNode);
        } else {
            dicomNode.setDescription(desc);
            dicomNode.setAeTitle(aeTitle);
            dicomNode.setHostname(hostname);
            dicomNode.setPort(port.intValue());
        }

        dicomNode.setType(typeNode);
        dicomNode.setColorPrintSupported(colorPrintSupportCheckBox.isSelected());
        if (addNode) {
            nodesComboBox.setSelectedItem(dicomNode);
        } else {
            nodesComboBox.repaint();
        }

        DicomNodeEx.saveDicomNodes(nodesComboBox, typeNode);
        dispose();
    }
}
