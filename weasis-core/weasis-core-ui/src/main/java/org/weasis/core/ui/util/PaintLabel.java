package org.weasis.core.ui.util;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.graphic.Graphic;

public class PaintLabel extends AbstractItemDialogPage {
    public static final String[] fontSize = { "8", "9", "10", "11", "12", "13", "14", "15", "16" };

    private JPanel jPanelChangeCanal = new JPanel();
    private GridBagLayout gridBagLayout2 = new GridBagLayout();
    private JButton jButtonApply = new JButton();
    private JPanel jPanel2 = new JPanel();
    private GridBagLayout gridBagLayout1 = new GridBagLayout();
    private JLabel jLabelFont = new JLabel();
    private JComboBox jComboName = new JComboBox();
    private JLabel jLabelSize = new JLabel();
    private JComboBox jComboSize = new JComboBox(fontSize);
    private JCheckBox jCheckBoxBold = new JCheckBox();
    private JCheckBox jCheckBoxItalic = new JCheckBox();
    private ImageViewerEventManager eventManager;

    public PaintLabel(ImageViewerEventManager eventManager) {
        setTitle("Font");
        this.eventManager = eventManager;
        try {
            JMVUtils.setList(jComboName, "Default", GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
            jbInit();
            initialize();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        this.setLayout(new BorderLayout());
        jPanelChangeCanal.setLayout(gridBagLayout2);
        jButtonApply.setText("Apply");
        jButtonApply.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                apply();
            }
        });
        this.add(jPanelChangeCanal, BorderLayout.SOUTH);
        jPanelChangeCanal.add(jButtonApply, new GridBagConstraints(0, 0, 1, 1, 0.5, 0.0, GridBagConstraints.CENTER,
            GridBagConstraints.NONE, new Insets(25, 25, 10, 15), 0, 0));
        this.add(jPanel2, BorderLayout.WEST);
        jPanel2.setLayout(gridBagLayout1);
        jLabelFont.setText("Name:");
        jLabelSize.setText("Size:");
        jPanel2.setBorder(new TitledBorder("Font (Global)"));
        jCheckBoxBold.setText("Bold");
        jCheckBoxItalic.setText("Italic");
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
        // TODO Auto-generated method stub

    }

    @Override
    public void closeAdditionalWindow() {
        apply();
    }
}
