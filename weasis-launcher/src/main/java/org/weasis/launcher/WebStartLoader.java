/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.osgi.framework.BundleContext;

public class WebStartLoader {

    private static final long serialVersionUID = 2275651212116198327L;

    public static final String LBL_LOADING = Messages.getString("WebStartLoader.load"); //$NON-NLS-1$
    public static final String LBL_DOWNLOADING = Messages.getString("WebStartLoader.download"); //$NON-NLS-1$
    public static final String FRM_TITLE = Messages.getString("WebStartLoader.title"); //$NON-NLS-1$
    public static final String PRG_STRING_FORMAT = Messages.getString("WebStartLoader.end"); //$NON-NLS-1$

    private volatile Window window;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel loadingLabel;
    private volatile javax.swing.JProgressBar downloadProgress;

    public WebStartLoader() {
    }

    public void writeLabel(String text) {
        loadingLabel.setText(text);
    }

    /*
     * Init splashScreen
     */
    public void initGUI() {
        loadingLabel = new javax.swing.JLabel();
        loadingLabel.setFont(new Font("Dialog", Font.PLAIN, 10)); //$NON-NLS-1$
        downloadProgress = new javax.swing.JProgressBar();
        Font font = new Font("Dialog", Font.PLAIN, 12); //$NON-NLS-1$
        downloadProgress.setFont(font);
        cancelButton = new javax.swing.JButton();
        cancelButton.setFont(font);
        window = new Window(null);
        window.addWindowListener(new java.awt.event.WindowAdapter() {

            @Override
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closing();
            }
        });

        loadingLabel.setText(LBL_LOADING);
        loadingLabel.setFocusable(false);

        downloadProgress.setFocusable(false);
        downloadProgress.setStringPainted(true);
        downloadProgress.setString(LBL_LOADING);

        cancelButton.setText(Messages.getString("WebStartLoader.cancel")); //$NON-NLS-1$
        cancelButton.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closing();
            }
        });
        JLabel imagePane =
            new JLabel(FRM_TITLE, new ImageIcon(getClass().getResource("/splash.png")), SwingConstants.CENTER); //$NON-NLS-1$
        imagePane.setFont(new Font("Dialog", Font.BOLD, 16)); //$NON-NLS-1$
        imagePane.setVerticalTextPosition(SwingConstants.TOP);
        imagePane.setHorizontalTextPosition(SwingConstants.CENTER);
        imagePane.setFocusable(false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.add(imagePane, BorderLayout.CENTER);
        JPanel panelProgress = new JPanel(new BorderLayout());
        panelProgress.setBackground(Color.WHITE);
        panelProgress.add(loadingLabel, BorderLayout.NORTH);
        panelProgress.add(downloadProgress, BorderLayout.CENTER);
        panelProgress.add(cancelButton, BorderLayout.EAST);
        panel.add(panelProgress, BorderLayout.SOUTH);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)));

        window.add(panel, BorderLayout.CENTER);
        window.pack();
    }

    public Window getWindow() {
        return window;
    }

    /*
     * Set maximum value for progress bar
     */
    public void setMax(final int max) {
        if (isClosed()) {
            return;
        }
        EventQueue.invokeLater(new Runnable() {

            public void run() {
                downloadProgress.setMaximum(max);
            }
        });
    }

    /*
     * Set actual value of progress bar
     */
    public void setValue(final int val) {
        if (isClosed()) {
            return;
        }
        EventQueue.invokeLater(new Runnable() {

            public void run() {
                downloadProgress.setString(String.format(PRG_STRING_FORMAT, val, downloadProgress.getMaximum()));
                downloadProgress.setValue(val);
                downloadProgress.repaint();
            }
        });

    }

    private void closing() {
        System.exit(0);
    }

    public boolean isClosed() {
        return window == null;
    }

    public void open() {
        try {
            EventQueue.invokeAndWait(new Runnable() {

                public void run() {
                    if (window == null) {
                        initGUI();
                    }
                    displayOnScreen();
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (isClosed()) {
            return;
        }
        EventQueue.invokeLater(new Runnable() {

            public void run() {
                window.setVisible(false);
                window.dispose();
                window = null;
                cancelButton = null;
                downloadProgress = null;
                loadingLabel = null;
            }
        });
    }

    private void displayOnScreen() {
        try {
            Rectangle bound =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                    .getBounds();
            window.setLocation(bound.x + (bound.width - window.getWidth()) / 2,
                bound.y + (bound.height - window.getHeight()) / 2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        window.setVisible(true);
    }

    public void setFelix(Properties configProps, BundleContext bundleContext) {
        AutoProcessor.process(configProps, bundleContext, this);
    }
}
