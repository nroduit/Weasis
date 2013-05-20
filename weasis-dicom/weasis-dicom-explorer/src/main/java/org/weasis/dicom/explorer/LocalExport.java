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
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Properties;

import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
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

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.StopTagInputHandler;
import org.dcm4che2.media.ApplicationProfile;
import org.dcm4che2.media.DicomDirWriter;
import org.dcm4che2.media.FileSetInformation;
import org.dcm4che2.media.StdGenJPEGApplicationProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.ui.serialize.DefaultSerializer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.internal.Activator;

public class LocalExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalExport.class);

    private static final char[] HEX_DIGIT = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
        'E', 'F' };

    private static final String LAST_DIR = "lastExportDir";//$NON-NLS-1$
    private static final String INC_DICOMDIR = "exp.include.dicomdir";//$NON-NLS-1$
    private static final String KEEP_INFO_DIR = "exp.keep.dir.name";//$NON-NLS-1$
    private static final String IMG_QUALITY = "exp.img.quality";//$NON-NLS-1$
    private static final String HEIGHT_BITS = "exp.8bis";//$NON-NLS-1$
    private static final String CD_COMPATIBLE = "exp.cd";//$NON-NLS-1$

    public static final String[] EXPORT_FORMAT = { "DICOM", "DICOM ZIP", "JPEG", "PNG", "TIFF" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private final DicomModel dicomModel;
    private JLabel lblImportAFolder;
    private File outputFolder;
    private JPanel panel;
    private final ExportTree exportTree;
    private final ApplicationProfile dicomStruct = new StdGenJPEGApplicationProfile();

    private JComboBox comboBoxImgFormat;
    private JButton btnNewButton;
    private JCheckBox chckbxGraphics;

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

        lblImportAFolder = new JLabel(Messages.getString("LocalExport.exp")); //$NON-NLS-1$
        panel.add(lblImportAFolder);

        comboBoxImgFormat = new JComboBox();

        comboBoxImgFormat.setModel(new DefaultComboBoxModel(EXPORT_FORMAT));
        panel.add(comboBoxImgFormat);

        add(panel, BorderLayout.NORTH);

        btnNewButton = new JButton(Messages.getString("LocalExport.options")); //$NON-NLS-1$
        btnNewButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportingOptions();
            }
        });
        panel.add(btnNewButton);

        chckbxGraphics = new JCheckBox("Graphics", true);

        panel.add(chckbxGraphics);
        add(exportTree, BorderLayout.CENTER);
    }

    protected void showExportingOptions() {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        final JCheckBox boxKeepNames =
            new JCheckBox(
                Messages.getString("LocalExport.keep_dir"), Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, "true"))); //$NON-NLS-1$ //$NON-NLS-2$

        Object seltected = comboBoxImgFormat.getSelectedItem();
        if (EXPORT_FORMAT[0].equals(seltected)) {
            final JCheckBox box1 =
                new JCheckBox(
                    Messages.getString("LocalExport.inc_dicomdir"), Boolean.valueOf(pref.getProperty(INC_DICOMDIR, "true"))); //$NON-NLS-1$ //$NON-NLS-2$
            final JCheckBox box2 =
                new JCheckBox(
                    Messages.getString("LocalExport.cd_folders"), Boolean.valueOf(pref.getProperty(CD_COMPATIBLE, "false"))); //$NON-NLS-1$ //$NON-NLS-2$
            box2.setEnabled(box1.isSelected());
            boxKeepNames.setEnabled(!box1.isSelected());
            box1.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    boxKeepNames.setEnabled(!box1.isSelected());
                    box2.setEnabled(box1.isSelected());
                }
            });

            Object[] options = { box1, box2, boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options,
                    Messages.getString("LocalExport.export_message"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(INC_DICOMDIR, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
                pref.setProperty(CD_COMPATIBLE, String.valueOf(box2.isSelected()));
            }
        } else if (EXPORT_FORMAT[1].equals(seltected)) {
            // No option
        } else if (EXPORT_FORMAT[2].equals(seltected)) {
            final JSlider slider =
                new JSlider(0, 100, JMVUtils.getIntValueFromString(pref.getProperty(IMG_QUALITY, null), 80));

            final JPanel palenSlider1 = new JPanel();
            palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
            palenSlider1.setBorder(new TitledBorder(
                Messages.getString("LocalExport.jpeg_quality") + " " + slider.getValue())); //$NON-NLS-1$ //$NON-NLS-2$

            slider.setPaintTicks(true);
            slider.setSnapToTicks(false);
            slider.setMajorTickSpacing(10);
            JMVUtils.setPreferredWidth(slider, 145, 145);
            palenSlider1.add(slider);
            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSlider source = (JSlider) e.getSource();
                    ((TitledBorder) palenSlider1.getBorder()).setTitle(Messages.getString("LocalExport.jpeg_quality") + source.getValue()); //$NON-NLS-1$
                    palenSlider1.repaint();
                }
            });

            Object[] options = { palenSlider1, boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options,
                    Messages.getString("LocalExport.export_message"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(IMG_QUALITY, String.valueOf(slider.getValue()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[3].equals(seltected)) {
            Object[] options = { boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options,
                    Messages.getString("LocalExport.export_message"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
                    JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[4].equals(seltected)) {
            final JCheckBox box1 =
                new JCheckBox(
                    Messages.getString("LocalExport.tiff_sup_8bits"), Boolean.valueOf(pref.getProperty(HEIGHT_BITS, "false"))); //$NON-NLS-1$ //$NON-NLS-2$
            Object[] options = { box1, boxKeepNames };
            int response =
                JOptionPane.showOptionDialog(this, options,
                    Messages.getString("LocalExport.export_message"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
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

    public void browseImgFile(String format) {
        String directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_DIR, "");//$NON-NLS-1$
        boolean saveFile = EXPORT_FORMAT[1].equals(format);
        JFileChooser fileChooser = new JFileChooser(directory);
        if (saveFile) {
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setAcceptAllFileFilterUsed(false);
            FileFormatFilter.creatOneFilter(fileChooser, "zip", "ZIP", false);
        } else {
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        fileChooser.setMultiSelectionEnabled(false);
        File folder = null;
        if (fileChooser.showSaveDialog(this) != 0 || (folder = fileChooser.getSelectedFile()) == null) {
            outputFolder = null;
            return;
        } else {
            if (saveFile) {
                outputFolder =
                    "zip".equals(FileUtil.getExtension(folder.getName())) ? folder : new File(folder + ".zip");
            } else {
                outputFolder = folder;
            }
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DIR, saveFile ? folder.getParent() : folder.getPath());
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
    public void exportDICOM(final ExportTree tree, JProgressBar info) throws IOException {
        final String format = (String) comboBoxImgFormat.getSelectedItem();
        browseImgFile(format);
        if (outputFolder != null) {
            final File exportDir = outputFolder.getCanonicalFile();
            ExplorerTask task = new ExplorerTask("Exporting...") { //$NON-NLS-1$

                    @Override
                    protected Boolean doInBackground() throws Exception {
                        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart,
                            dicomModel, null, this));
                        if (EXPORT_FORMAT[0].equals(format)) {
                            writeDicom(exportDir, tree, false);
                        } else if (EXPORT_FORMAT[1].equals(format)) {
                            writeDicom(exportDir, tree, true);
                        } else {
                            writeOther(exportDir, tree, format);
                        }
                        return true;
                    }

                    @Override
                    protected void done() {
                        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop,
                            dicomModel, null, this));
                    }

                };
            task.execute();
        }
    }

    private String getinstanceFileName(DicomImageElement img) {
        Integer instance = (Integer) img.getTagValue(TagW.InstanceNumber);
        if (instance != null) {
            String val = instance.toString();
            if (val.length() < 5) {
                char[] chars = new char[5 - val.length()];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = '0';
                }

                return new String(chars) + val;

            } else {
                return val;
            }
        }
        return (String) img.getTagValue(TagW.SOPInstanceUID);
    }

    private void writeOther(File exportDir, ExportTree tree, String format) {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        boolean keepNames = Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, "true"));//$NON-NLS-1$
        int jpegQuality = JMVUtils.getIntValueFromString(pref.getProperty(IMG_QUALITY, null), 80);
        boolean more8bits = Boolean.valueOf(pref.getProperty(HEIGHT_BITS, "false")); //$NON-NLS-1$
        boolean writeGraphics = chckbxGraphics.isSelected();

        synchronized (tree) {
            TreePath[] paths = tree.getTree().getCheckingPaths();
            for (TreePath treePath : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                if (node.getUserObject() instanceof DicomImageElement) {
                    DicomImageElement img = (DicomImageElement) node.getUserObject();
                    // Get instance number instead SOPInstanceUID to handle multiframe
                    String instance = getinstanceFileName(img);
                    StringBuffer buffer = new StringBuffer();
                    if (keepNames) {
                        TreeNode[] objects = node.getPath();
                        if (objects.length > 3) {
                            buffer.append(FileUtil.getValidFileName(objects[1].toString()));
                            buffer.append(File.separator);
                            buffer.append(FileUtil.getValidFileName(objects[2].toString()));
                            buffer.append(File.separator);
                            String seriesName = FileUtil.getValidFileName(objects[3].toString());
                            if (seriesName.length() > 30) {
                                buffer.append(seriesName, 0, 27);
                                buffer.append("...");
                            } else {
                                buffer.append(seriesName);
                            }
                            buffer.append('-');
                            // Hash of UID to guaranty the unique behavior of the name.
                            buffer.append(makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        }
                    } else {
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
                        buffer.append(File.separator);
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.StudyInstanceUID)));
                        buffer.append(File.separator);
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        instance = makeFileIDs(instance);
                    }

                    File destinationDir = new File(exportDir, buffer.toString());
                    destinationDir.mkdirs();

                    RenderedImage image = img.getImage(null);

                    if (EXPORT_FORMAT[2].equals(format)) {
                        if (image != null) {
                            image = img.getRenderedImage(image);
                        }
                        if (image != null) {
                            File destinationFile = new File(destinationDir, instance + ".jpg");
                            ImageFiler.writeJPG(destinationFile, image, jpegQuality / 100.0f); //$NON-NLS-1$
                            if (writeGraphics) {
                                DefaultSerializer.writeMeasurementGraphics(img, destinationFile);
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", format, img.getFile()); //$NON-NLS-1$
                        }
                    }
                    if (EXPORT_FORMAT[3].equals(format)) {
                        if (image != null) {
                            image = img.getRenderedImage(image);
                        }
                        if (image != null) {
                            File destinationFile = new File(destinationDir, instance + ".png");
                            ImageFiler.writePNG(destinationFile, image); //$NON-NLS-1$
                            if (writeGraphics) {
                                DefaultSerializer.writeMeasurementGraphics(img, destinationFile);
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", format, img.getFile()); //$NON-NLS-1$
                        }
                    }
                    if (EXPORT_FORMAT[4].equals(format)) {
                        if (image != null) {
                            if (!more8bits) {
                                image = img.getRenderedImage(image);
                            }
                            File destinationFile = new File(destinationDir, instance + ".tif");
                            ImageFiler.writeTIFF(destinationFile, image, false, false, false); //$NON-NLS-1$
                            if (writeGraphics) {
                                DefaultSerializer.writeMeasurementGraphics(img, destinationFile);
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", format, img.getFile()); //$NON-NLS-1$
                        }
                    }
                }
            }
        }

    }

    private void writeDicom(File exportDir, ExportTree tree, boolean zipFile) throws IOException {
        boolean keepNames;
        boolean writeDicomdir;
        boolean cdCompatible;
        boolean writeGraphics = chckbxGraphics.isSelected();

        File writeDir;

        if (zipFile) {
            keepNames = false;
            writeDicomdir = true;
            cdCompatible = true;
            writeDir = FileUtil.createTempDir("zip-");
        } else {
            Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
            keepNames = Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, "true"));//$NON-NLS-1$
            writeDicomdir = Boolean.valueOf(pref.getProperty(INC_DICOMDIR, "true"));//$NON-NLS-1$
            cdCompatible = Boolean.valueOf(pref.getProperty(CD_COMPATIBLE, "false"));//$NON-NLS-1$
            writeDir = exportDir;
        }

        DicomDirWriter writer = null;
        try {

            if (writeDicomdir) {
                File dcmdirFile = new File(writeDir, "DICOMDIR"); //$NON-NLS-1$
                if (dcmdirFile.createNewFile()) {
                    FileSetInformation fsinfo = new FileSetInformation();
                    fsinfo.init();
                    writer = new DicomDirWriter(dcmdirFile, fsinfo);
                } else {
                    writer = new DicomDirWriter(dcmdirFile);
                }
            }

            synchronized (tree) {
                ArrayList<String> uids = new ArrayList<String>();
                TreePath[] paths = tree.getTree().getCheckingPaths();
                for (TreePath treePath : paths) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        String iuid = (String) img.getTagValue(TagW.SOPInstanceUID);
                        int index = uids.indexOf(iuid);
                        if (index == -1) {
                            uids.add(iuid);
                        } else {
                            // Write only once the file for multiframe
                            continue;
                        }
                        StringBuffer buffer = new StringBuffer();
                        // Cannot keep folders names with DICOMDIR (could be not valid)
                        if (keepNames && !writeDicomdir) {
                            TreeNode[] objects = node.getPath();
                            if (objects.length > 2) {
                                for (int i = 1; i < objects.length - 1; i++) {
                                    buffer.append(FileUtil.getValidFileName(objects[i].toString()));
                                    buffer.append(File.separator);
                                }
                            }
                        } else {
                            if (cdCompatible) {
                                buffer.append("DICOM"); //$NON-NLS-1$
                                buffer.append(File.separator);
                            }
                            buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
                            buffer.append(File.separator);
                            buffer.append(makeFileIDs((String) img.getTagValue(TagW.StudyInstanceUID)));
                            buffer.append(File.separator);
                            buffer.append(makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                            iuid = makeFileIDs(iuid);
                        }
                        File destinationDir = new File(writeDir, buffer.toString());
                        boolean newSeries = destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (FileUtil.nioCopyFile(img.getFile(), destinationFile)) {

                            if (writeGraphics) {
                                DefaultSerializer.writeMeasurementGraphics(img, destinationFile);
                            }

                            if (writer != null) {
                                DicomInputStream in = null;
                                try {
                                    in = new DicomInputStream(destinationFile);
                                    in.setHandler(new StopTagInputHandler(Tag.PixelData));
                                    DicomObject dcmobj = in.readDicomObject();
                                    DicomObject patrec = dicomStruct.makePatientDirectoryRecord(dcmobj);
                                    DicomObject styrec = dicomStruct.makeStudyDirectoryRecord(dcmobj);
                                    DicomObject serrec = dicomStruct.makeSeriesDirectoryRecord(dcmobj);

                                    // Icon Image Sequence (0088,0200).This Icon Image is representative of the Series.
                                    // It may or may not correspond to one of the images of the Series.
                                    if (newSeries && node.getParent() instanceof DefaultMutableTreeNode) {
                                        DicomImageElement midImage =
                                            ((DicomSeries) ((DefaultMutableTreeNode) node.getParent()).getUserObject())
                                                .getMedia(MEDIA_POSITION.MIDDLE, null, null);
                                        DicomObject seq = mkIconItem(midImage);
                                        if (seq != null) {
                                            serrec.putNestedDicomObject(Tag.IconImageSequence, seq);
                                        }
                                    }

                                    DicomObject instrec =
                                        dicomStruct.makeInstanceDirectoryRecord(dcmobj,
                                            writer.toFileID(destinationFile));
                                    DicomObject rec = writer.addPatientRecord(patrec);
                                    rec = writer.addStudyRecord(rec, styrec);
                                    rec = writer.addSeriesRecord(rec, serrec);
                                    String miuid = dcmobj.getString(Tag.MediaStorageSOPInstanceUID);
                                    if (writer.findInstanceRecord(rec, miuid) == null) {
                                        writer.addChildRecord(rec, instrec);
                                    }
                                } finally {
                                    FileUtil.safeClose(in);
                                }
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", img.getFile()); //$NON-NLS-1$
                        }
                    }
                }
            }
            if (zipFile) {
                try {
                    FileUtil.zip(writeDir, exportDir);
                } catch (Exception e) {
                    LOGGER.error("Cannot export DICOM ZIP file: ", exportDir); //$NON-NLS-1$
                } finally {
                    // FileUtil.delete(writeDir);
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String toHex(int val) {
        char[] ch8 = new char[8];
        for (int i = 8; --i >= 0; val >>= 4) {
            ch8[i] = HEX_DIGIT[val & 0xf];
        }

        return String.valueOf(ch8);
    }

    private static String makeFileIDs(String uid) {
        return toHex(uid.hashCode());
    }

    private DicomObject mkIconItem(DicomImageElement image) {
        if (image == null) {
            return null;
        }
        BufferedImage thumbnail = null;
        PlanarImage imgPl = image.getImage(null);
        if (imgPl != null) {
            RenderedImage img = image.getRenderedImage(imgPl);
            final double scale = Math.min(128 / (double) img.getHeight(), 128 / (double) img.getWidth());
            final PlanarImage thumb =
                scale < 1.0 ? SubsampleAverageDescriptor.create(img, scale, scale, Thumbnail.DownScaleQualityHints)
                    .getRendering() : PlanarImage.wrapRenderedImage(img);
            thumbnail = thumb.getAsBufferedImage();
        }
        if (thumbnail == null) {
            return null;
        }
        int w = thumbnail.getWidth();
        int h = thumbnail.getHeight();

        String pmi = (String) image.getTagValue(TagW.PhotometricInterpretation);
        BufferedImage bi = thumbnail;
        if (thumbnail.getColorModel().getColorSpace().getType() != ColorSpace.CS_GRAY) {
            bi = convertBI(thumbnail, BufferedImage.TYPE_BYTE_INDEXED);
            pmi = "PALETTE COLOR"; //$NON-NLS-1$
        }

        byte[] iconPixelData = new byte[w * h];
        DicomObject iconItem = new BasicDicomObject();

        if ("PALETTE COLOR".equals(pmi)) { //$NON-NLS-1$
            IndexColorModel cm = (IndexColorModel) bi.getColorModel();
            int[] lutDesc = { cm.getMapSize(), 0, 8 };
            byte[] r = new byte[lutDesc[0]];
            byte[] g = new byte[lutDesc[0]];
            byte[] b = new byte[lutDesc[0]];
            cm.getReds(r);
            cm.getGreens(g);
            cm.getBlues(b);
            iconItem.putInts(Tag.RedPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.putInts(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.putInts(Tag.BluePaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.putBytes(Tag.RedPaletteColorLookupTableData, VR.OW, r, false);
            iconItem.putBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, g, false);
            iconItem.putBytes(Tag.BluePaletteColorLookupTableData, VR.OW, b, false);

            Raster raster = bi.getRaster();
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) raster.getSample(x, y, 0);
                }
            }
        } else {
            pmi = "MONOCHROME2"; //$NON-NLS-1$
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) bi.getRGB(x, y);
                }
            }
        }
        iconItem.putString(Tag.PhotometricInterpretation, VR.CS, pmi);
        iconItem.putInt(Tag.Rows, VR.US, h);
        iconItem.putInt(Tag.Columns, VR.US, w);
        iconItem.putInt(Tag.SamplesPerPixel, VR.US, 1);
        iconItem.putInt(Tag.BitsAllocated, VR.US, 8);
        iconItem.putInt(Tag.BitsStored, VR.US, 8);
        iconItem.putInt(Tag.HighBit, VR.US, 7);
        iconItem.putBytes(Tag.PixelData, VR.OW, iconPixelData);
        return iconItem;
    }

    private BufferedImage convertBI(BufferedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawImage(src, 0, 0, null);
        } finally {
            big.dispose();
        }
        return dst;
    }

    public static boolean writeCompressedImageFromDICOM(DicomImageElement dicom, File outputFolder, String extension) {
        File file = new File(outputFolder, (String) dicom.getTagValue(TagW.SOPInstanceUID) + extension); //$NON-NLS-1$
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        DicomInputStream in = null;
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            in = new DicomInputStream(dicom.getFile());
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
            FileUtil.safeClose(in);
            FileUtil.safeClose(os);
        }
        return true;
    }

}
