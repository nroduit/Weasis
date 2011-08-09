package org.weasis.core.ui.pref;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.util.ViewSetting;

public class LabelPrefView extends AbstractItemDialogPage {
    public static final String[] fontSize = { "8", "9", "10", "11", "12", "13", "14", "15", "16" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
    private final JButton jButtonApply = new JButton();
    private final JPanel jPanel2 = new JPanel();
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();
    private final JLabel jLabelFont = new JLabel();
    private final JComboBox jComboName = new JComboBox();
    private final JLabel jLabelSize = new JLabel();
    private final JComboBox jComboSize = new JComboBox(fontSize);
    private final JCheckBox jCheckBoxBold = new JCheckBox();
    private final JCheckBox jCheckBoxItalic = new JCheckBox();
    private final ViewSetting viewSetting;
    private final Component verticalStrut = Box.createVerticalStrut(20);

    public LabelPrefView(ViewSetting viewSetting) {
        if (viewSetting == null) {
            throw new IllegalArgumentException("ViewSetting cannot be null"); //$NON-NLS-1$
        }
        this.viewSetting = viewSetting;
        setBorder(new EmptyBorder(15, 10, 10, 10));
        setTitle(Messages.getString("LabelPrefView.font")); //$NON-NLS-1$
        try {
            JMVUtils.setList(jComboName,
                Messages.getString("LabelPrefView.default"), GraphicsEnvironment.getLocalGraphicsEnvironment() //$NON-NLS-1$
                    .getAvailableFontFamilyNames());
            jbInit();
            initialize();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() throws Exception {
        this.setLayout(new BorderLayout());
        this.add(jPanel2, BorderLayout.CENTER);
        jPanel2.setLayout(gridBagLayout1);
        jLabelFont.setText(Messages.getString("LabelPrefView.name")); //$NON-NLS-1$
        jLabelSize.setText(Messages.getString("LabelPrefView.size")); //$NON-NLS-1$
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

        GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
        gbc_verticalStrut.weighty = 1.0;
        gbc_verticalStrut.weightx = 1.0;
        gbc_verticalStrut.gridx = 4;
        gbc_verticalStrut.gridy = 2;
        jPanel2.add(verticalStrut, gbc_verticalStrut);

        JPanel panel_2 = new JPanel();
        FlowLayout flowLayout_1 = (FlowLayout) panel_2.getLayout();
        flowLayout_1.setHgap(10);
        flowLayout_1.setAlignment(FlowLayout.RIGHT);
        flowLayout_1.setVgap(7);
        add(panel_2, BorderLayout.SOUTH);
        panel_2.add(jButtonApply);
        jButtonApply.setText(Messages.getString("LabelPrefView.apply")); //$NON-NLS-1$
        jButtonApply.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                apply();
            }
        });

        JButton btnNewButton = new JButton(Messages.getString("restore.values")); //$NON-NLS-1$
        panel_2.add(btnNewButton);
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetoDefaultValues();
            }
        });

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
        jComboName.setSelectedItem(viewSetting.getFontName());

    }

    public void apply() {
        closeAdditionalWindow();
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
        viewSetting.setFontName(Messages.getString("LabelPrefView.default")); //$NON-NLS-1$
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

        viewSetting.setFontName(name);
        viewSetting.setFontSize(size);
        viewSetting.setFontType(style);

        ArrayList<Graphic> graphicList = MeasureToolBar.graphicList;
        for (int i = 1; i < graphicList.size(); i++) {
            MeasureToolBar.applyDefaultSetting(viewSetting, graphicList.get(i));
        }
    }
}
