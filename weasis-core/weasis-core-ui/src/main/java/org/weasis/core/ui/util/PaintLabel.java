package org.weasis.core.ui.util;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.graphic.Graphic;

public class PaintLabel extends JDialog {
    public final static String[] fontSize = { "8", "9", "10", "11", "12", "13", "14", "15", "16" };

    private JPanel panel1 = new JPanel();
    private BorderLayout borderLayout1 = new BorderLayout();
    private JPanel jPanelChangeCanal = new JPanel();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JButton jButtonApply = new JButton();
    private JPanel jPanel1 = new JPanel();
    private JPanel jPanel2 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private JLabel jLabelFont = new JLabel();
    private JComboBox jComboName = new JComboBox();
    private JLabel jLabelSize = new JLabel();
    private JComboBox jComboSize = new JComboBox(fontSize);
    private JButton jButtonClose = new JButton();
    private JCheckBox jCheckBoxBold = new JCheckBox();
    private JCheckBox jCheckBoxItalic = new JCheckBox();
    private ImageViewerEventManager eventManager;
    private final JPanel panel = new JPanel();
    private final JLabel lblColor = new JLabel("Color:");
    private final JButton jPVButtonColor = new JButton("Pick");
    private final JLabel lblLineWidth = new JLabel("Line width:");
    private final JSpinner spinner = new JSpinner();

    public PaintLabel(ImageViewerEventManager eventManager) {
        super(WinUtil.getParentDialogOrFrame(eventManager.getSelectedView2dContainer()), "Default Graphic Properties",
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
        panel1.setLayout(borderLayout1);
        jPanelChangeCanal.setLayout(gridBagLayout2);
        jButtonApply.setText("Apply");
        jButtonApply.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                apply();
            }
        });
        jPanel2.setLayout(gridBagLayout1);
        jLabelFont.setText("Name:");
        jLabelSize.setText("Size:");
        jPanel2.setBorder(new TitledBorder("Font (Global)"));
        panel.setBorder(new TitledBorder("Line Property (For new graphics)"));

        jButtonClose.setText("Close");
        jButtonClose.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeWin();
            }
        });

        jPVButtonColor.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JButton button = (JButton) e.getSource();
                Color newColor = JColorChooser.showDialog(PaintLabel.this, "Pick a color", button.getBackground());
                if (newColor != null) {
                    button.setBackground(newColor);
                }
            }
        });
        jCheckBoxBold.setText("Bold");
        jCheckBoxItalic.setText("Italic");
        getContentPane().add(panel1);
        panel1.add(jPanelChangeCanal, BorderLayout.SOUTH);
        jPanelChangeCanal.add(jButtonApply, new GridBagConstraints(0, 0, 1, 1, 0.5, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(25, 25, 10, 15), 0, 0));
        panel1.add(jPanel1, BorderLayout.NORTH);
        jPanel1.setLayout(new BoxLayout(jPanel1, BoxLayout.Y_AXIS));
        jPanel1.add(jPanel2);
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
        jPanel1.add(panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0, 0 };
        gbl_panel.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_panel.rowWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
        panel.setLayout(gbl_panel);

        GridBagConstraints gbc_lblColor = new GridBagConstraints();
        gbc_lblColor.anchor = GridBagConstraints.WEST;
        gbc_lblColor.insets = new Insets(0, 0, 0, 5);
        gbc_lblColor.gridx = 0;
        gbc_lblColor.gridy = 0;
        panel.add(lblColor, gbc_lblColor);

        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.anchor = GridBagConstraints.WEST;
        gbc_button.gridwidth = 2;
        gbc_button.insets = new Insets(0, 2, 0, 5);
        gbc_button.gridx = 1;
        gbc_button.gridy = 0;
        panel.add(jPVButtonColor, gbc_button);

        GridBagConstraints gbc_lblLineWidth = new GridBagConstraints();
        gbc_lblLineWidth.anchor = GridBagConstraints.WEST;
        gbc_lblLineWidth.gridwidth = 2;
        gbc_lblLineWidth.insets = new Insets(5, 0, 0, 5);
        gbc_lblLineWidth.gridx = 0;
        gbc_lblLineWidth.gridy = 1;
        panel.add(lblLineWidth, gbc_lblLineWidth);

        GridBagConstraints gbc_spinner = new GridBagConstraints();
        gbc_spinner.insets = new Insets(5, 2, 0, 5);
        gbc_spinner.anchor = GridBagConstraints.WEST;
        gbc_spinner.gridx = 2;
        gbc_spinner.gridy = 1;
        panel.add(spinner, gbc_spinner);
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

        JMVUtils.setNumberModel(spinner, 1, 1, 8, setting.getLineWidth());
        jPVButtonColor.setBackground(setting.getLineColor());

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

        setting.setLineColor(jPVButtonColor.getBackground());
        setting.setLineWidth((Integer) spinner.getValue());

        ArrayList<Graphic> graphicList = MeasureToolBar.graphicList;
        for (int i = 1; i < graphicList.size(); i++) {
            MeasureToolBar.applyDefaultSetting(setting, graphicList.get(i));
        }

        synchronized (UIManager.VIEWER_PLUGINS) {
            for (int i = UIManager.VIEWER_PLUGINS.size() - 1; i >= 0; i--) {
                ViewerPlugin p = UIManager.VIEWER_PLUGINS.get(i);
                if (p instanceof ImageViewerPlugin) {
                    for (Object v : ((ImageViewerPlugin) p).getImagePanels()) {
                        if (v instanceof DefaultView2d) {
                            DefaultView2d view = (DefaultView2d) v;
                            List<Graphic> list = view.getLayerModel().getdAllGraphics();
                            for (Graphic graphic : list) {
                                graphic.updateLabel(view.getImage(), view);
                            }
                        }
                    }
                }
            }
        }

    }
}
