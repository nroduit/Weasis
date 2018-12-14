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
package org.weasis.dicom.explorer;

import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

@SuppressWarnings("serial")
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
        btnOpen.addActionListener(e -> browseImgFile());
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
        // Do nothing
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {
        // Do nothing
    }

    protected void updateChanges() {
        // Do nothing
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();

    }

    @Override
    public void resetoDefaultValues() {
        // Do nothing
    }

    @Override
    public void importDICOM(DicomModel dicomModel, JProgressBar info) {
        loadDicomZip(selectedFile, dicomModel);
    }

    public static void loadDicomZip(File file, DicomModel dicomModel) {
        if (file != null) {
            if (file.canRead()) {
                File dir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "zip")); //$NON-NLS-1$ //$NON-NLS-2$
                try {
                    FileUtil.unzip(file, dir);
                } catch (IOException e) {
                    LOGGER.error("unzipping", e); //$NON-NLS-1$
                }
                File dicomdir = new File(dir, "DICOMDIR"); //$NON-NLS-1$
                if (dicomdir.canRead()) {
                    DicomDirLoader dirImport = new DicomDirLoader(dicomdir, dicomModel, false); // $NON-NLS-1$
                    List<LoadSeries> loadSeries = dirImport.readDicomDir();
                    if (loadSeries != null && !loadSeries.isEmpty()) {
                        DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, dicomModel));
                    } else {
                        LOGGER.error("Cannot import DICOM from {}", file); //$NON-NLS-1$
                    }
                } else {
                    LoadLocalDicom dicom = new LoadLocalDicom(new File[] { dir }, true, dicomModel);
                    DicomModel.LOADING_EXECUTOR.execute(dicom);
                }
            }
        }
    }

    public static void loadDicomZip(String uri, DicomModel dicomModel) {
        if (StringUtil.hasText(uri)) {
            InputStream stream = null;
            File tempFile = null;
            try {
                URI u = new URI(uri);
                if (u.toString().startsWith("file:")) { //$NON-NLS-1$
                    tempFile = new File(u.getPath());
                } else {
                    tempFile = File.createTempFile("dicom_", ".zip", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                    stream = NetworkUtil.getUrlInputStream(u.toURL().openConnection(), BundleTools.SESSION_TAGS_FILE);
                    FileUtil.writeStreamWithIOException(stream, tempFile);
                }
            } catch (Exception e) {
                LOGGER.error("Loading DICOM Zip", e); //$NON-NLS-1$
            } finally {
                FileUtil.safeClose(stream);
            }
            loadDicomZip(tempFile, dicomModel);
        }
    }
}
