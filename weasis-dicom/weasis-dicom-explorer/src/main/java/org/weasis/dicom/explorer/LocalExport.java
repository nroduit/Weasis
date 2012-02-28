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
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.internal.Activator;

public class LocalExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalExport.class);

    private static final String DICOM = "DICOM";
    private static final char[] HEX_DIGIT = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
        'E', 'F' };

    private static final String LAST_DIR = "lastExportDir";//$NON-NLS-1$
    private static final String INC_DICOMDIR = "exp.include.dicomdir";//$NON-NLS-1$
    private static final String KEEP_INFO_DIR = "exp.keep.dir.name";//$NON-NLS-1$
    private static final String IMG_QUALITY = "exp.img.quality";//$NON-NLS-1$
    private static final String HEIGHT_BITS = "exp.8bis";//$NON-NLS-1$

    public static final String[] EXPORT_FORMAT = { "DICOM", "JPEG", "PNG", "TIFF" };
    private final DicomModel dicomModel;
    private JLabel lblImportAFolder;
    private File outputFolder;
    private JPanel panel;
    private final ExportTree exportTree;
    private final ApplicationProfile dicomStruct = new StdGenJPEGApplicationProfile();

    private JComboBox comboBoxImgFormat;
    private JButton btnNewButton;

    public LocalExport(DicomModel dicomModel, ExportTree exportTree) {
        super(Messages.getString("LocalExport.local_dev")); //$NON-NLS-1$
        this.dicomModel = dicomModel;
        this.exportTree = exportTree;
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

        comboBoxImgFormat.setModel(new DefaultComboBoxModel(EXPORT_FORMAT));
        panel.add(comboBoxImgFormat);

        add(panel, BorderLayout.NORTH);

        btnNewButton = new JButton("Options");
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportingOptions();
            }
        });
        panel.add(btnNewButton);
        add(exportTree, BorderLayout.CENTER);
    }

    protected void showExportingOptions() {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        final JCheckBox boxKeepNames =
            new JCheckBox("Keep directory names", Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, "true")));

        Object seltected = comboBoxImgFormat.getSelectedItem();
        if (EXPORT_FORMAT[0].equals(seltected)) {
            final JCheckBox box1 =
                new JCheckBox("Include DICOMDIR", Boolean.valueOf(pref.getProperty(INC_DICOMDIR, "true")));

            boxKeepNames.setEnabled(!box1.isSelected());
            box1.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    boxKeepNames.setEnabled(!box1.isSelected());
                }
            });

            Object[] options = { box1, boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options, "Image Export Options", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(INC_DICOMDIR, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[1].equals(seltected)) {
            final JSlider slider =
                new JSlider(0, 100, JMVUtils.getIntValueFromString(pref.getProperty(IMG_QUALITY, null), 80));

            final JPanel palenSlider1 = new JPanel();
            palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
            palenSlider1.setBorder(new TitledBorder("JPEG Quality: " + slider.getValue()));

            slider.setPaintTicks(true);
            slider.setSnapToTicks(false);
            slider.setMajorTickSpacing(10);
            JMVUtils.setPreferredWidth(slider, 145, 145);
            palenSlider1.add(slider);
            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSlider source = (JSlider) e.getSource();
                    ((TitledBorder) palenSlider1.getBorder()).setTitle("JPEG Quality: " + source.getValue());
                    palenSlider1.repaint();
                }
            });

            Object[] options = { palenSlider1, boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options, "Image Export Options", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(IMG_QUALITY, String.valueOf(slider.getValue()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[2].equals(seltected)) {
            Object[] options = { boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options, "Image Export Options", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[3].equals(seltected)) {
            final JCheckBox box1 =
                new JCheckBox("Allow TIFF more than 8 bits", Boolean.valueOf(pref.getProperty(HEIGHT_BITS, "false")));
            Object[] options = { box1, boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options, "Image Export Options", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(HEIGHT_BITS, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        }
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
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        int jpegQuality = JMVUtils.getIntValueFromString(pref.getProperty(IMG_QUALITY, null), 80);
        boolean more8bits = Boolean.valueOf(pref.getProperty(HEIGHT_BITS, "false"));
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
                            image = ImageToolkit.getDefaultRenderedImage(img, image);
                        }
                        if (image != null) {
                            ImageFiler.writeJPG(new File(destinationDir, (String) img.getTagValue(TagW.SOPInstanceUID)
                                + ".jpg"), image, jpegQuality / 100.0f);
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
                            if (!more8bits) {
                                image = ImageToolkit.getDefaultRenderedImage(img, image);
                            }
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
            boolean writeDicomdir =
                Boolean.valueOf(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(INC_DICOMDIR, "true"));//$NON-NLS-1$
            if (writeDicomdir) {
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

    private String toFilePath(String[] fileIDs, int n) {
        StringBuffer sb = new StringBuffer(fileIDs[0]);
        for (int i = 1; i < n; ++i) {
            sb.append(File.separatorChar).append(fileIDs[i]);
        }
        return sb.toString();
    }

    private static String toHex(int val) {
        char[] ch8 = new char[8];
        for (int i = 8; --i >= 0; val >>= 4) {
            ch8[i] = HEX_DIGIT[val & 0xf];
        }

        return String.valueOf(ch8);
    }

    private static String[] makeFileIDs(String pid, String suid, String seruid, String iuid) {
        return new String[] { toHex(pid.hashCode()), toHex(suid.hashCode()), toHex(seruid.hashCode()),
            toHex(iuid.hashCode()), };
    }
}
