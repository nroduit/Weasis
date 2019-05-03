package org.weasis.dicom.explorer.pref.node;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.Border;

import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.Messages;

public class HttpHeadersEditor extends JDialog {
    private DefaultListSelectionModel selctedModel = new DefaultListSelectionModel();
    private JPanel panel1 = new JPanel();
    private BorderLayout borderLayout1 = new BorderLayout();
    private JButton jButtonClose = new JButton();
    private GridBagLayout gridBagLayout3 = new GridBagLayout();
    private JPanel jPanelComponentBar = new JPanel();
    private JList<String> jList1 = new JList<>();
    private JPanel jPanelComponentAction = new JPanel();
    private JButton jButtonDelete = new JButton();
    private JButton jButtonEdit = new JButton();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JButton jButtonAdd = new JButton();
    private JScrollPane jScrollPane1 = new JScrollPane();
    private DicomWebNode node;

    public HttpHeadersEditor(Window parent, DicomWebNode node) {
        super(parent, Messages.getString("DicomWebNodeDialog.httpHeaders"), ModalityType.APPLICATION_MODAL);
        this.node = node;
        jList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jbInit();
        initializeList();
        jList1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    editHeader();
                } 
            }
        });
        pack();
    }

    private void jbInit() {
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        selctedModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        Border border1 = BorderFactory.createEmptyBorder(5, 5, 5, 5);
        Border border2 = BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
            BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel1.setLayout(borderLayout1);

        jButtonClose.setText("Close");
        jButtonClose.addActionListener(e -> cancel());
        jPanelComponentBar.setLayout(gridBagLayout3);
        jList1.setBorder(border2);
        jList1.setSelectionModel(selctedModel);
        jPanelComponentAction.setLayout(gridBagLayout2);
        jButtonDelete.addActionListener(e -> deleteSelectedComponents());
        jButtonDelete.setText("Delete");

        jButtonEdit.setText("Edit");
        jButtonEdit.addActionListener(e -> editHeader());

        jButtonAdd.addActionListener(e -> add());
        jButtonAdd.setText("Add");

        jScrollPane1.setBorder(border1);
        jScrollPane1.setPreferredSize(new Dimension(300, 150));
        panel1.add(jPanelComponentBar, BorderLayout.SOUTH);

        jPanelComponentBar.add(jButtonClose, new GridBagConstraints(2, 0, 1, 1, 0.5, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(10, 0, 10, 20), 0, 0));
        panel1.add(jPanelComponentAction, BorderLayout.EAST);
        panel1.add(jScrollPane1, BorderLayout.CENTER);
        jScrollPane1.getViewport().add(jList1, null);
        this.getContentPane().add(panel1, BorderLayout.CENTER);
        jPanelComponentAction.add(jButtonEdit, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(7, 5, 0, 10), 0, 0));
        jPanelComponentAction.add(jButtonAdd, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(15, 0, 0, 5), 0, 0));
        jPanelComponentAction.add(jButtonDelete, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(7, 0, 0, 5), 0, 0));
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            cancel();
        }
        super.processWindowEvent(e);
    }

    // Close the dialog
    public void cancel() {
        dispose();
    }

    private synchronized void initializeList() {
        if (node.getHeaders().isEmpty()) {
            jList1.setListData(new String[0]);
        } else {
            jList1.setListData(node.getHeaders().entrySet().stream().map(m -> m.getKey() + ": " + m.getValue())
                .toArray(String[]::new));
        }
    }

    public void deleteSelectedComponents() {
        if (isNoComponentSelected()) {
            return;
        }

        List<String> selItems = jList1.getSelectedValuesList();
        for (String val : selItems) {
            String[] kv = val.split(":", 2);
            if (kv.length == 2) {
                node.removeHeader(kv[0]);
            }
        }
        selctedModel.clearSelection();
        initializeList();
    }

    public void editHeader() {
        if (isNoComponentSelected()) {
            return;
        }

        List<String> selItems = jList1.getSelectedValuesList();
        if (selItems.size() == 1) {
            modifiy(selItems.get(0));
        } else {
            JOptionPane.showMessageDialog(this, "Only one header must be selected!", this.getTitle(),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isNoComponentSelected() {
        if (selctedModel.isSelectionEmpty()) {
            JOptionPane.showMessageDialog(this, "No header is selected!", this.getTitle(), JOptionPane.ERROR_MESSAGE);
            return true;
        }
        return false;
    }

    private void add() {
        modifiy(null);
    }

    private void modifiy(String input) {
        String property =
            (String) JOptionPane.showInputDialog(this, "Header entry => {key}: {value}",
                this.getTitle(), JOptionPane.PLAIN_MESSAGE, null, null, input);
        if (StringUtil.hasLength(property)) {
            String[] kv = property.split(":", 2);
            if (kv.length == 2) {
                node.addHeader(kv[0].trim(), kv[1].trim());
            }
            initializeList();
        }
    }
}
