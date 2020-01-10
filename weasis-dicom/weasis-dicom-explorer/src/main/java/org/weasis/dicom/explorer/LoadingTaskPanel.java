/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.explorer.wado.DownloadManager;

public class LoadingTaskPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final ExplorerTask<?, ?> task;
    private final boolean globalLoadingManager;
    private final JLabel message = new JLabel();

    public LoadingTaskPanel(ExplorerTask<?, ?> task) {
        this.task = task;
        this.globalLoadingManager = task.isGlobalLoadingManager();
        init();
    }

    public LoadingTaskPanel(boolean globalLoadingManager) {
        this.task = null;
        this.globalLoadingManager = globalLoadingManager;
        init();
    }

    private void init() {
        if (globalLoadingManager) {
            JButton globalResumeButton = new JButton(new Icon() {

                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(Color.GREEN);
                    x += 3;
                    y += 3;
                    int[] xPoints = { x, x + 14, x };
                    int[] yPoints = { y, y + 7, y + 14 };
                    g2d.fillPolygon(xPoints, yPoints, xPoints.length);
                }

                @Override
                public int getIconWidth() {
                    return 20;
                }

                @Override
                public int getIconHeight() {
                    return 20;
                }
            });

            JButton globalStopButton = new JButton(new Icon() {

                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(Color.RED);
                    x += 3;
                    y += 3;
                    g2d.fillRect(x, y, 14, 14);
                }

                @Override
                public int getIconWidth() {
                    return 20;
                }

                @Override
                public int getIconHeight() {
                    return 20;
                }
            });

            globalResumeButton.setToolTipText(Messages.getString("DicomExplorer.resume_all")); //$NON-NLS-1$
            globalResumeButton.addActionListener(e -> DownloadManager.resume());
            this.add(globalResumeButton);
            globalStopButton.setToolTipText(Messages.getString("DicomExplorer.stop_all")); //$NON-NLS-1$
            globalStopButton.addActionListener(e -> DownloadManager.stop());
            this.add(globalStopButton);
        } else {
            JButton cancelButton =
                new JButton(new ImageIcon(UIManager.class.getResource("/icon/22x22/process-stop.png"))); //$NON-NLS-1$
            cancelButton.addActionListener(e -> {
                message.setText(Messages.getString("LoadingTaskPanel.abording")); //$NON-NLS-1$
                if (task != null) {
                    task.cancel();
                }
            });
            cancelButton.setToolTipText(Messages.getString("LoadingTaskPanel.stop_process")); //$NON-NLS-1$
            this.add(cancelButton);
            if (task != null) {
                CircularProgressBar globalProgress = task.getBar();
                this.add(globalProgress);
                globalProgress.setIndeterminate(true);
            }
        }
        if (task != null) {
            message.setText(task.getMessage());
        }
        this.add(message);
        this.revalidate();
        this.repaint();
    }

    public void setMessage(String msg) {
        message.setText(msg);
    }

    public ExplorerTask getTask() {
        return task;
    }
}
