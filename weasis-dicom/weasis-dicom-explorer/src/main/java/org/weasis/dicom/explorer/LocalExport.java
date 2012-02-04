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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;

public class LocalExport extends AbstractItemDialogPage implements ExportDicom {

    private final DicomModel dicomModel;
    private JLabel lblImportAFolder;
    private JTextField textField;
    private JButton button;
    private File outputFolder;
    private JPanel panel;
    private final ExportTree exportTree;

    public LocalExport(DicomModel dicomModel) {
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(dicomModel);
        setTitle(Messages.getString("LocalExport.local_dev")); //$NON-NLS-1$
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        lblImportAFolder = new JLabel(Messages.getString("LocalExport.exp"));
        panel.add(lblImportAFolder);

        textField = new JTextField();
        // textField.setColumns(10);
        JMVUtils.setPreferredWidth(textField, 375, 375);
        panel.add(textField);

        button = new JButton(" ... "); //$NON-NLS-1$
        button.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                browseImgFile();
            }
        });
        panel.add(button);

        add(panel, BorderLayout.NORTH);
        add(exportTree, BorderLayout.CENTER);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {

        }
    }

    public void browseImgFile() {
        String directory = ""; //$NON-NLS-1$
        if (outputFolder != null) {
            directory = outputFolder.isDirectory() ? outputFolder.getPath() : outputFolder.getParent();
        }

        JFileChooser fileChooser = new JFileChooser(directory);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        // FileFormatFilter.setImageDecodeFilters(fileChooser);
        File selectedFiles = null;
        if (fileChooser.showOpenDialog(this) != 0 || (selectedFiles = fileChooser.getSelectedFile()) == null) {
            outputFolder = null;
            return;
        } else {
            outputFolder = selectedFiles;
            textField.setText(selectedFiles.getPath());
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
    public void exportDICOM(DicomModel dicomModel, JProgressBar info) {
        if (outputFolder != null) {
            synchronized (dicomModel) {
                for (Iterator<MediaSeriesGroup> iterator = dicomModel.getChildren(TreeModel.rootNode).iterator(); iterator
                    .hasNext();) {
                    MediaSeriesGroup pt = iterator.next();
                    Collection<MediaSeriesGroup> studies = dicomModel.getChildren(pt);
                    for (Iterator<MediaSeriesGroup> iterator2 = studies.iterator(); iterator2.hasNext();) {
                        MediaSeriesGroup study = iterator2.next();
                        Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(study);
                        for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                            Object item = it.next();
                            if (item instanceof DicomSeries) {
                                DicomSeries series = (DicomSeries) item;
                                for (DicomImageElement dicom : series.getMedias()) {
                                    writeFile(dicom);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean writeFile(DicomImageElement dicom) {
        File file = new File(outputFolder, (String) dicom.getTagValue(TagW.SOPInstanceUID) + ".j2k"); //$NON-NLS-1$
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            DicomInputStream in = new DicomInputStream(dicom.getFile());
            DicomObject dcmObj = in.readDicomObject();
            DicomElement pixelDataDcmElement = dcmObj.get(Tag.PixelData);
            byte[] pixelData = pixelDataDcmElement.getFragment(1);
            os.write(pixelData);
        } catch (OutOfMemoryError e) {
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            FileUtil.safeClose(os);
        }
        return true;
    }
}
