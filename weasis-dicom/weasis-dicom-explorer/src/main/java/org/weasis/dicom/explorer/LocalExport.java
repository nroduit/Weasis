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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.StopTagInputHandler;
import org.dcm4che2.media.ApplicationProfile;
import org.dcm4che2.media.DicomDirWriter;
import org.dcm4che2.media.FileSetInformation;
import org.dcm4che2.media.StdGenJPEGApplicationProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.internal.Activator;

public class LocalExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalExport.class);

    private static final String LAST_DIR = "lastExportDir";//$NON-NLS-1$
    private static final String[] EXPORT_FORMAT = { "DICOM", "JPEG", "PNG", "TIFF" };
    private final DicomModel dicomModel;
    private JLabel lblImportAFolder;
    private File outputFolder;
    private JPanel panel;
    private final ExportTree exportTree;
    private final ApplicationProfile dicomStruct = new StdGenJPEGApplicationProfile();

    private JComboBox comboBoxImgFormat;
    private JCheckBox chckbxDicomdir;
    private JButton btnNewButton;

    public LocalExport(DicomModel dicomModel, ExportTree exportTree) {
        this.dicomModel = dicomModel;
        this.exportTree = exportTree;
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

        comboBoxImgFormat = new JComboBox();
        comboBoxImgFormat.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    chckbxDicomdir.setEnabled(EXPORT_FORMAT[0].equals(e.getItem()));
                }
            }
        });

        comboBoxImgFormat.setModel(new DefaultComboBoxModel(EXPORT_FORMAT));
        panel.add(comboBoxImgFormat);

        chckbxDicomdir = new JCheckBox("DICOMDIR");
        panel.add(chckbxDicomdir);

        add(panel, BorderLayout.NORTH);

        btnNewButton = new JButton("Options");
        panel.add(btnNewButton);
        add(exportTree, BorderLayout.CENTER);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {

        }
    }

    public void browseImgFile() {
        String directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_DIR, "");//$NON-NLS-1$

        JFileChooser fileChooser = new JFileChooser(directory);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        File folder = null;
        if (fileChooser.showOpenDialog(this) != 0 || (folder = fileChooser.getSelectedFile()) == null) {
            outputFolder = null;
            return;
        } else {
            outputFolder = folder;
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DIR, folder.getPath());
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
    public void exportDICOM(ExportTree tree, JProgressBar info) throws IOException {
        browseImgFile();
        if (outputFolder != null) {
            File exportDir = outputFolder.getCanonicalFile();
            String format = (String) comboBoxImgFormat.getSelectedItem();

            if (EXPORT_FORMAT[0].equals(format)) {
                writeDicom(exportDir, tree);
            } else {
                writeOther(exportDir, tree, format);
            }
        }
    }

    private void writeOther(File exportDir, ExportTree tree, String format) {

        synchronized (tree) {
            TreePath[] paths = tree.getTree().getCheckingPaths();
            for (TreePath treePath : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                if (node.getUserObject() instanceof DicomImageElement) {
                    DicomImageElement img = (DicomImageElement) node.getUserObject();
                    TreeNode[] objects = node.getPath();
                    StringBuffer buffer = new StringBuffer();
                    if (objects.length > 2) {
                        for (int i = 1; i < objects.length - 1; i++) {
                            buffer.append(FileUtil.getValidFileName(objects[i].toString()));
                            buffer.append(File.separator);
                        }
                    }

                    File destinationDir = new File(exportDir, buffer.toString());
                    destinationDir.mkdirs();

                    RenderedImage image = img.getImage(null);

                    if (EXPORT_FORMAT[1].equals(format)) {
                        if (image != null) {
                            // image = ImageToolkit.getDefaultRenderedImage(img, image);
                            image = img.getRenderedImage(image);
                        }
                        if (image != null) {
                            ImageFiler.writeJPG(new File(destinationDir, (String) img.getTagValue(TagW.SOPInstanceUID)
                                + ".jpg"), image, 0.8f);
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", format, img.getFile());
                        }
                    }
                    if (EXPORT_FORMAT[2].equals(format)) {
                        if (image != null) {
                            image = ImageToolkit.getDefaultRenderedImage(img, image);
                        }
                        if (image != null) {
                            ImageFiler.writePNG(new File(destinationDir, (String) img.getTagValue(TagW.SOPInstanceUID)
                                + ".png"), image);
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", format, img.getFile());
                        }
                    }
                    if (EXPORT_FORMAT[3].equals(format)) {
                        if (image != null) {
                            // image = ImageToolkit.getDefaultRenderedImage(img, image);
                        }
                        if (image != null) {
                            ImageFiler.writeTIFF(new File(destinationDir, (String) img.getTagValue(TagW.SOPInstanceUID)
                                + ".tif"), image);
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", format, img.getFile());
                        }
                    }
                }
            }
        }

    }

    private void writeDicom(File exportDir, ExportTree tree) throws IOException {
        DicomDirWriter writer = null;
        try {
            if (chckbxDicomdir.isSelected()) {
                File dcmdirFile = new File(exportDir, "DICOMDIR");
                if (dcmdirFile.createNewFile()) {
                    FileSetInformation fsinfo = new FileSetInformation();
                    fsinfo.init();
                    writer = new DicomDirWriter(dcmdirFile, fsinfo);
                } else {
                    writer = new DicomDirWriter(dcmdirFile);
                }
            }

            synchronized (tree) {
                TreePath[] paths = tree.getTree().getCheckingPaths();
                for (TreePath treePath : paths) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        TreeNode[] objects = node.getPath();
                        StringBuffer buffer = new StringBuffer();
                        if (objects.length > 2) {
                            for (int i = 1; i < objects.length - 1; i++) {
                                buffer.append(FileUtil.getValidFileName(objects[i].toString()));
                                buffer.append(File.separator);
                            }
                        }

                        File destinationDir = new File(exportDir, buffer.toString());
                        destinationDir.mkdirs();
                        File destinationFile = new File(destinationDir, (String) img.getTagValue(TagW.SOPInstanceUID));
                        if (FileUtil.nioCopyFile(img.getFile(), destinationFile)) {
                            if (writer != null) {
                                DicomInputStream in = new DicomInputStream(destinationFile);
                                in.setHandler(new StopTagInputHandler(Tag.PixelData));
                                DicomObject dcmobj = in.readDicomObject();
                                DicomObject patrec = dicomStruct.makePatientDirectoryRecord(dcmobj);
                                DicomObject styrec = dicomStruct.makeStudyDirectoryRecord(dcmobj);
                                DicomObject serrec = dicomStruct.makeSeriesDirectoryRecord(dcmobj);
                                DicomObject instrec =
                                    dicomStruct.makeInstanceDirectoryRecord(dcmobj, writer.toFileID(destinationFile));
                                DicomObject rec = writer.addPatientRecord(patrec);
                                rec = writer.addStudyRecord(rec, styrec);
                                rec = writer.addSeriesRecord(rec, serrec);
                                String iuid = dcmobj.getString(Tag.MediaStorageSOPInstanceUID);
                                if (writer.findInstanceRecord(rec, iuid) == null) {
                                    writer.addChildRecord(rec, instrec);
                                }
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", img.getFile());
                        }
                    }
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
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
