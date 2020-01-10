/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.dialog;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.acquire.explorer.AcquireExplorer;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.control.ImportPanel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;

public class AcquireImportDialog extends JDialog implements PropertyChangeListener {
    private static final long serialVersionUID = -8736946182228791444L;

    private static final String P_MAX_RANGE = "maxMinuteRange"; //$NON-NLS-1$

    private final ImportPanel importPanel;

    public static final ExecutorService IMPORT_IMAGES = ThreadUtil.buildNewSingleThreadExecutor("ImportImage"); //$NON-NLS-1$

    static final Object[] OPTIONS =
        { Messages.getString("AcquireImportDialog.validate"), Messages.getString("AcquireImportDialog.cancel") }; //$NON-NLS-1$ //$NON-NLS-2$
    static final String REVALIDATE = "ReValidate"; //$NON-NLS-1$

    private final JTextField serieName = new JTextField();
    private final ButtonGroup btnGrp = new ButtonGroup();

    private final JRadioButton btn1 = new JRadioButton(Messages.getString("AcquireImportDialog.no_grp")); //$NON-NLS-1$
    private final JRadioButton btn2 = new JRadioButton(Messages.getString("AcquireImportDialog.date_grp")); //$NON-NLS-1$
    private final JRadioButton btn3 = new JRadioButton(Messages.getString("AcquireImportDialog.name_grp")); //$NON-NLS-1$
    private final JSpinner spinner;

    private JOptionPane optionPane;

    private List<ImageElement> mediaList;

    public AcquireImportDialog(ImportPanel importPanel, List<ImageElement> mediaList) {
        super();
        this.importPanel = importPanel;
        this.mediaList = mediaList;

        int maxRange = 60;
        Preferences prefs =
            BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
        if (prefs != null) {
            Preferences p = prefs.node(AcquireExplorer.PREFERENCE_NODE);
            maxRange = p.getInt(P_MAX_RANGE, maxRange);
        }
        spinner = new JSpinner(new SpinnerNumberModel(maxRange, 1, 5256000, 5)); // <=> 4 years

        optionPane = new JOptionPane(initPanel(), JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null,
            OPTIONS, OPTIONS[0]);
        optionPane.addPropertyChangeListener(this);
        setContentPane(optionPane);
        setModal(true);
        setLocationRelativeTo(null);
        pack();
    }

    private JPanel initPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(10, 10, 20, 15));
        panel.setLayout(new GridBagLayout());

        JLabel question = new JLabel(Messages.getString("AcquireImportDialog.grp_msg") + StringUtil.COLON); //$NON-NLS-1$
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 15, 0);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(question, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(btn1, c);

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.WEST;
        panel.add(btn2, c);

        JMVUtils.setPreferredWidth(spinner, 75);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.WEST;
        panel.add(spinner, c);
        installFocusListener(spinner);

        c = new GridBagConstraints();
        c.insets = new Insets(5, 2, 0, 0);
        c.gridx = 2;
        c.gridy = 2;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(Messages.getString("AcquireImportDialog.max_range_min")), c); //$NON-NLS-1$

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(btn3, c);

        JMVUtils.setPreferredWidth(serieName, 150);
        serieName.addFocusListener(new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
                // Do nothing
            }

            @Override
            public void focusGained(FocusEvent e) {
                btnGrp.setSelected(btn3.getModel(), true);
            }
        });
        c = new GridBagConstraints();
        c.insets = new Insets(5, 2, 0, 0);
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(serieName, c);

        btnGrp.add(btn1);
        btnGrp.add(btn2);
        btnGrp.add(btn3);
        btnGrp.setSelected(btn1.getModel(), true);

        return panel;
    }

    public void installFocusListener(JSpinner spinner) {
        JComponent spinnerEditor = spinner.getEditor();
        if (spinnerEditor != null) {
            Component c = spinnerEditor.getComponent(0);
            if (c != null) {
                c.addFocusListener(new FocusListener() {

                    @Override
                    public void focusLost(FocusEvent e) {
                        // Do nothing
                    }

                    @Override
                    public void focusGained(FocusEvent e) {
                        btnGrp.setSelected(btn2.getModel(), true);
                    }
                });
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object action = evt.getNewValue();
        if (action != null) {
            boolean close = true;
            if (action.equals(OPTIONS[0])) {
                SeriesGroup serieType = null;
                if (btnGrp.getSelection().equals(btn1.getModel())) {
                    serieType = null;
                } else if (btnGrp.getSelection().equals(btn2.getModel())) {
                    serieType = SeriesGroup.DATE_SERIE;
                } else {
                    if (serieName.getText() != null && !serieName.getText().isEmpty()) {
                        serieType = new SeriesGroup(serieName.getText());
                        serieType.setNeedUpateFromGlobaTags(true);
                    } else {
                        JOptionPane.showMessageDialog(this, Messages.getString("AcquireImportDialog.add_name_msg"), //$NON-NLS-1$
                            Messages.getString("AcquireImportDialog.add_name_title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
                        optionPane.setValue(REVALIDATE);
                        close = false;
                    }
                }

                if (close) {
                    importPanel.getCentralPane().setSelectedAndGetFocus();

                    Integer maxRangeInMinutes = (Integer) spinner.getValue();
                    Preferences prefs = BundlePreferences
                        .getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
                    if (prefs != null) {
                        Preferences p = prefs.node(AcquireExplorer.PREFERENCE_NODE);
                        BundlePreferences.putIntPreferences(p, P_MAX_RANGE, maxRangeInMinutes);
                    }

                    importPanel.importImageList(mediaList, serieType, maxRangeInMinutes);

                }
            } else if (action.equals(REVALIDATE)) {
                close = false;
            }

            if (close) {
                clearAndHide();
            }
        }

    }

    public void clearAndHide() {
        serieName.setText(null);
        setVisible(false);
    }
}
