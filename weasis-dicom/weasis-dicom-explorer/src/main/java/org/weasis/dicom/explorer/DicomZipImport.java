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
package org.weasis.dicom.explorer;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomZipImport extends AbstractItemDialogPage implements ImportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomZipImport.class);

    private static final String lastDICOMDIR = "lastDicomZip";//$NON-NLS-1$

    private File selectedFile;
    private JButton btnOpen;
    private JLabel fileLabel = new JLabel();

    public DicomZipImport() {
        super(Messages.getString("DicomZipImport.title")); //$NON-NLS-1$
        setComponentPosition(3);
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setBorder(new TitledBorder(null, Messages.getString("DicomZipImport.title"), TitledBorder.LEADING, //$NON-NLS-1$
            TitledBorder.TOP, null, null));
        setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));
        btnOpen = new JButton(Messages.getString("DicomZipImport.select_file")); //$NON-NLS-1$
        btnOpen.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseImgFile();
            }
        });
        add(btnOpen);
        add(fileLabel);
    }

    public void browseImgFile() {
        String directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDICOMDIR, "");//$NON-NLS-1$

        JFileChooser fileChooser = new JFileChooser(directory);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(new FileFormatFilter("zip", "ZIP")); //$NON-NLS-1$ //$NON-NLS-2$
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
            || (selectedFile = fileChooser.getSelectedFile()) == null) {
            fileLabel.setText(""); //$NON-NLS-1$
            return;
        } else {
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, selectedFile.getParent());
            fileLabel.setText(selectedFile.getPath());
        }
    }

    protected void initialize(boolean afirst) {
        if (afirst) {

        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {

    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();

    }

    @Override
    public void resetoDefaultValues() {
    }

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        loadDicomZip(selectedFile, dicomModel);
    }

    public static void loadDicomZip(File file, DicomModel dicomModel) {
        if (file != null) {
            List<LoadSeries> loadSeries = null;
            if (file.canRead()) {
                File dir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "zip")); //$NON-NLS-1$ //$NON-NLS-2$
                try {
                    FileUtil.unzip(file, dir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                DicomDirLoader dirImport = new DicomDirLoader(new File(dir, "DICOMDIR"), dicomModel, false); //$NON-NLS-1$
                loadSeries = dirImport.readDicomDir();
            }
            if (loadSeries != null && loadSeries.size() > 0) {
                DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, dicomModel));
            } else {
                LOGGER.error("Cannot import DICOM from {}", file); //$NON-NLS-1$
            }
        }
    }

}
