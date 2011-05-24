package org.weasis.core.ui.util;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;

public class PaintLabel extends JDialog {
    public final static String[] fontSize = { "8", "9", "10", "11", "12", "13", "14", "15", "16" };

    private JPanel panel1 = new JPanel();
    private BorderLayout borderLayout1 = new BorderLayout();
    private JPanel jPanelChangeCanal = new JPanel();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JButton jButtonApply = new JButton();
    private JPanel jPanel1 = new JPanel();
    private BorderLayout borderLayout2 = new BorderLayout();
    private JPanel jPanel2 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private JLabel jLabelFont = new JLabel();
    private JComboBox jComboName = new JComboBox();
    private JLabel jLabelSize = new JLabel();
    private JComboBox jComboSize = new JComboBox(fontSize);
    private TitledBorder titledBorder1;
    private JButton jButtonClose = new JButton();
    private JCheckBox jCheckBoxBold = new JCheckBox();
    private JCheckBox jCheckBoxItalic = new JCheckBox();
    private ImageViewerEventManager eventManager;

    public PaintLabel(ImageViewerEventManager eventManager) {
        super(WinUtil.getParentDialogOrFrame(eventManager.getSelectedView2dContainer()), "Font Chooser",
            Dialog.ModalityType.APPLICATION_MODAL);
        this.eventManager = eventManager;
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        try {
            JMVUtils.setList(jComboName, "Default", GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
            jbInit();
            initialize();
            pack();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        titledBorder1 = new TitledBorder("Font");
        panel1.setLayout(borderLayout1);
        jPanelChangeCanal.setLayout(gridBagLayout2);
        jButtonApply.setText("Apply");
        jButtonApply.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                apply();
            }
        });

        jPanel1.setLayout(borderLayout2);
        jPanel2.setLayout(gridBagLayout1);
        jLabelFont.setText("Name :");

        jLabelSize.setText("Size :");

        jPanel2.setBorder(titledBorder1);

        jButtonClose.setText("Close");
        jButtonClose.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeWin();
            }
        });

        jCheckBoxBold.setText("Bold");
        jCheckBoxItalic.setText("Italic");
        getContentPane().add(panel1);
        panel1.add(jPanelChangeCanal, BorderLayout.SOUTH);
        jPanelChangeCanal.add(jButtonApply, new GridBagConstraints(0, 0, 1, 1, 0.5, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(25, 25, 10, 15), 0, 0));
        panel1.add(jPanel1, BorderLayout.NORTH);
        jPanel1.add(jPanel2, BorderLayout.CENTER);
        jPanel2.add(jComboSize, new GridBagConstraints(1, 1, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(5, 2, 5, 0), 0, 0));
        jPanel2.add(jLabelFont, new GridBagConstraints(0, 0, 2, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 5, 0, 0), 0, 0));
        jPanel2.add(jComboName, new GridBagConstraints(2, 0, 3, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(0, 2, 0, 5), 0, 0));
        jPanel2.add(jCheckBoxItalic, new GridBagConstraints(4, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        jPanel2.add(jCheckBoxBold, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        jPanel2.add(jLabelSize, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
            GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
        jPanelChangeCanal.add(jButtonClose, new GridBagConstraints(1, 0, 1, 1, 0.5, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(25, 15, 10, 20), 0, 0));

    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            closeWin();
        }
        super.processWindowEvent(e);
    }

    // Close the dialog
    private void closeWin() {
        dispose();
    }

    private void initialize() {
        ViewSetting setting = eventManager.getViewSetting();
        int style = setting.getFontType();
        if (style == (Font.BOLD | Font.ITALIC)) {
            jCheckBoxBold.setSelected(true);
            jCheckBoxItalic.setSelected(true);
        } else if (style == Font.BOLD) {
            jCheckBoxBold.setSelected(true);
        } else if (style == Font.ITALIC) {
            style = Font.ITALIC;
            jCheckBoxItalic.setSelected(true);
        }
        String size = String.valueOf(setting.getFontSize());
        int index = 2;
        for (int i = 0; i < fontSize.length; i++) {
            if (fontSize[i].equals(size)) {
                index = i;
                break;
            }
        }
        jComboSize.setSelectedIndex(index);
        jComboName.setSelectedItem(setting.getFontName());
    }

    public void apply() {
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
        ViewSetting setting = eventManager.getViewSetting();
        setting.setFontName(name);
        setting.setFontSize(size);
        setting.setFontType(style);

        eventManager.getSelectedView2dContainer().repaint();
        // TODO repaint all graphics with labels
        for (Object v : eventManager.getSelectedView2dContainer().getImagePanels()) {

        }
    }
}
