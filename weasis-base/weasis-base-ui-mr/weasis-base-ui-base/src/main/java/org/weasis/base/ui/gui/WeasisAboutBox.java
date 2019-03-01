/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.ui.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.Border;

import org.weasis.base.ui.Messages;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.util.SimpleTableModel;

public class WeasisAboutBox extends JDialog implements ActionListener {

    private final JPanel jpanelRoot = new JPanel();
    private final JPanel jPanelClose = new JPanel();
    private final JButton jButtonclose = new JButton();
    private final BorderLayout borderLayout1 = new BorderLayout();
    private JTable sysTable;
    private final JScrollPane jScrollPane1 = new JScrollPane();
    private final JTabbedPane jTabbedPane1 = new JTabbedPane();
    private final JPanel jPanelAbout = new JPanel();
    private final JPanel jPanelInfoSys = new JPanel();
    private final Border border1 = BorderFactory.createEmptyBorder(10, 10, 10, 10);
    private final FlowLayout flowLayout1 = new FlowLayout();
    private final BorderLayout borderLayout2 = new BorderLayout();
    private final GridBagLayout gridBagLayout1 = new GridBagLayout();

    private final JTextPane jTextPane1 = new JTextPane();

    private final JPanel jPanel3 = new JPanel();
    private final BorderLayout borderLayout3 = new BorderLayout();
    private final JLabel jLabel1 = new JLabel();
    private final JPanel jPanel1 = new JPanel();
    private final JScrollPane jScrollPane3 = new JScrollPane();

    public WeasisAboutBox(Frame owner) {
        super(owner, String.format(Messages.getString("WeasisAboutBox.about"), AppProperties.WEASIS_NAME), true); //$NON-NLS-1$
        sysTable = new JTable(new SimpleTableModel(new String[] { Messages.getString("WeasisAboutBox.prop"), //$NON-NLS-1$
            Messages.getString("WeasisAboutBox.val") }, //$NON-NLS-1$
            createSysInfo()));
        sysTable.getColumnModel().setColumnMargin(5);
        JMVUtils.formatTableHeaders(sysTable, SwingConstants.CENTER);
        init();
        pack();
    }

    private void init() {
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.setModal(true);
        jpanelRoot.setLayout(borderLayout1);
        jPanelClose.setLayout(flowLayout1);
        flowLayout1.setAlignment(FlowLayout.RIGHT);
        flowLayout1.setHgap(15);
        flowLayout1.setVgap(10);

        jButtonclose.setText(Messages.getString("WeasisAboutBox.close")); //$NON-NLS-1$

        jButtonclose.addActionListener(this);
        jPanelInfoSys.setBorder(border1);
        jPanelInfoSys.setLayout(borderLayout2);

        jPanelAbout.setLayout(gridBagLayout1);
        jTextPane1.setEditorKit(JMVUtils.buildHTMLEditorKit(jTextPane1));
        jTextPane1.setContentType("text/html"); //$NON-NLS-1$
        jTextPane1.setEditable(false);

        jTextPane1.addHyperlinkListener(JMVUtils.buildHyperlinkListener());
        final StringBuilder message = new StringBuilder("<div align=\"center\"><H2>"); //$NON-NLS-1$
        message.append(AppProperties.WEASIS_NAME);
        message.append(" "); //$NON-NLS-1$
        message.append(AppProperties.WEASIS_VERSION);
        message.append("</H2>"); //$NON-NLS-1$

        String rn = Messages.getString("WeasisWin.release"); //$NON-NLS-1$
        message.append(String.format("<a href=\"%s", //$NON-NLS-1$
            BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.releasenotes", ""))); //$NON-NLS-1$ //$NON-NLS-2$
        message.append("\" style=\"color:#FF9900\">"); //$NON-NLS-1$
        message.append(rn);
        message.append("</a>");//$NON-NLS-1$
        message.append("<BR>"); //$NON-NLS-1$
        message.append(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.copyrights", "")); //$NON-NLS-1$ //$NON-NLS-2$
        message.append("</div>"); //$NON-NLS-1$
        jTextPane1.setText(message.toString());
        jLabel1.setBorder(BorderFactory.createLineBorder(Color.black, 2));

        jLabel1.setIcon(ResourceUtil.getLargeLogo());
        jPanel3.setLayout(borderLayout3);

        jTabbedPane1.add(jPanel3, this.getTitle());
        jPanel3.add(jPanel1, BorderLayout.NORTH);
        jPanel1.add(jLabel1, null);
        jPanel3.add(jScrollPane3, BorderLayout.CENTER);
        jScrollPane3.getViewport().add(jTextPane1, null);
        jTabbedPane1.add(jPanelInfoSys, Messages.getString("WeasisAboutBox.sys")); //$NON-NLS-1$
        jPanelInfoSys.add(jScrollPane1, BorderLayout.CENTER);
        jScrollPane1.setPreferredSize(new Dimension(320, 270));
        jScrollPane1.getViewport().add(sysTable, null);
        jPanelClose.add(jButtonclose, null);

        jpanelRoot.add(jPanelClose, BorderLayout.SOUTH);
        jpanelRoot.add(jTabbedPane1, BorderLayout.CENTER);

        this.getContentPane().add(jpanelRoot, null);
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            cancel();
        }
        super.processWindowEvent(e);
    }

    void cancel() {
        dispose();
    }

    // Close the dialog on a button event
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == jButtonclose) {
            cancel();
        }
    }

    private static Object[][] createSysInfo() {
        Properties sysProps = System.getProperties();
        Object[][] dataArray = new String[sysProps.size()][2];
        Enumeration<?> enumer = sysProps.propertyNames();
        for (int i = 0; i < dataArray.length; i++) {
            dataArray[i][0] = enumer.nextElement();
            dataArray[i][1] = sysProps.getProperty(dataArray[i][0].toString());
        }
        return dataArray;

    }

}
