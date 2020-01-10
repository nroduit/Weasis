/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.control;

import java.awt.Dimension;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingWorker.StateValue;

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.ImportTask;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.ImageGroupPane;
import org.weasis.acquire.explorer.gui.dialog.AcquireImportDialog;
import org.weasis.acquire.explorer.gui.list.AcquireThumbnailListPane;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.ThreadUtil;

public class ImportPanel extends JPanel {
    private static final long serialVersionUID = -8658686020451614960L;

    public static final ExecutorService IMPORT_IMAGES = ThreadUtil.buildNewSingleThreadExecutor("ImportImage"); //$NON-NLS-1$

    private JButton importBtn = new JButton(Messages.getString("ImportPanel.import")); //$NON-NLS-1$
    private final CircularProgressBar progressBar = new CircularProgressBar(0, 100);

    private final ImageGroupPane centralPane;

    // TODO create ACTION object fpr import
    // so wherever it's called (button / popup/ menuBar ,,) it can be disabled/enabled from the ACTION object

    public ImportPanel(AcquireThumbnailListPane<MediaElement> mainPanel, ImageGroupPane centralPane) {
        this.centralPane = centralPane;

        importBtn.setPreferredSize(new Dimension(150, 40));
        importBtn.setFont(FontTools.getFont12Bold());

        importBtn.addActionListener(e -> {
            List<ImageElement> selected = AcquireManager.toImageElement(mainPanel.getSelectedValuesList());
            if (!selected.isEmpty()) {
                AcquireImportDialog dialog = new AcquireImportDialog(this, selected);
                JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(mainPanel));
            }
        });
        add(importBtn);
        add(progressBar);

        progressBar.setVisible(false);
    }

    public ImageGroupPane getCentralPane() {
        return centralPane;
    }

    public void importImageList(Collection<ImageElement> toImport, SeriesGroup searchedSeries, int maxRangeInMinutes) {

        ImportTask imporTask = new ImportTask(toImport, searchedSeries, maxRangeInMinutes);

        imporTask.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) { //$NON-NLS-1$
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);

            } else if ("state".equals(evt.getPropertyName())) { //$NON-NLS-1$

                if (StateValue.STARTED == evt.getNewValue()) {
                    importBtn.setEnabled(false);
                    progressBar.setVisible(true);
                    progressBar.setValue(0);

                } else if (StateValue.DONE == evt.getNewValue()) {
                    importBtn.setEnabled(true);
                    progressBar.setVisible(false);
                }
            }
        });

        IMPORT_IMAGES.execute(imporTask);
    }

    public boolean isLoading() {
        return !importBtn.isEnabled();
    }
}
