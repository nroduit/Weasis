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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.StringUtil.Suffix;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.FileExtractor;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

@SuppressWarnings("serial")
public class LocalExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalExport.class);

    public static final String LAST_DIR = "lastExportDir";//$NON-NLS-1$
    public static final String INC_DICOMDIR = "exp.include.dicomdir";//$NON-NLS-1$
    public static final String KEEP_INFO_DIR = "exp.keep.dir.name";//$NON-NLS-1$
    public static final String IMG_QUALITY = "exp.img.quality";//$NON-NLS-1$
    public static final String IMG_16_BIT = "exp.16-bit";//$NON-NLS-1$
    public static final String CD_COMPATIBLE = "exp.cd";//$NON-NLS-1$

    public static final String[] EXPORT_FORMAT = { "DICOM", "DICOM ZIP", "JPEG", "PNG" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private final DicomModel dicomModel;
    private JLabel lblImportAFolder;
    private File outputFolder;
    private JPanel panel;
    private final ExportTree exportTree;

    private JComboBox<String> comboBoxImgFormat;
    private JButton btnNewButton;

    public LocalExport(DicomModel dicomModel, CheckTreeModel treeModel) {
        super(Messages.getString("LocalExport.local_dev")); //$NON-NLS-1$
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(treeModel);
        setComponentPosition(0);
        initGUI();
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        lblImportAFolder = new JLabel(Messages.getString("LocalExport.exp") + StringUtil.COLON); //$NON-NLS-1$
        panel.add(lblImportAFolder);

        comboBoxImgFormat = new JComboBox<>(new DefaultComboBoxModel<>(EXPORT_FORMAT));
        panel.add(comboBoxImgFormat);

        add(panel, BorderLayout.NORTH);

        btnNewButton = new JButton(Messages.getString("LocalExport.options")); //$NON-NLS-1$
        btnNewButton.addActionListener(e -> showExportingOptions());
        panel.add(btnNewButton);
        add(exportTree, BorderLayout.CENTER);
    }

    protected void showExportingOptions() {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        final JCheckBox boxKeepNames = new JCheckBox(Messages.getString("LocalExport.keep_dir"), //$NON-NLS-1$
            Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString())));

        Object selected = comboBoxImgFormat.getSelectedItem();
        if (EXPORT_FORMAT[0].equals(selected)) {
            final JCheckBox box1 = new JCheckBox(Messages.getString("LocalExport.inc_dicomdir"), //$NON-NLS-1$
                Boolean.valueOf(pref.getProperty(INC_DICOMDIR, Boolean.TRUE.toString())));
            final JCheckBox box2 = new JCheckBox(Messages.getString("LocalExport.cd_folders"), //$NON-NLS-1$
                Boolean.valueOf(pref.getProperty(CD_COMPATIBLE, Boolean.FALSE.toString())));
            box2.setEnabled(box1.isSelected());
            boxKeepNames.setEnabled(!box1.isSelected());
            box1.addActionListener(e -> {
                boxKeepNames.setEnabled(!box1.isSelected());
                box2.setEnabled(box1.isSelected());
            });

            Object[] options = { box1, box2, boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(INC_DICOMDIR, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
                pref.setProperty(CD_COMPATIBLE, String.valueOf(box2.isSelected()));
            }
        } else if (EXPORT_FORMAT[1].equals(selected)) {
            // No option
        } else if (EXPORT_FORMAT[2].equals(selected)) {
            final JSlider slider = new JSlider(0, 100, StringUtil.getInt(pref.getProperty(IMG_QUALITY, null), 80));

            final JPanel palenSlider1 = new JPanel();
            palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
            palenSlider1.setBorder(new TitledBorder(
                Messages.getString("LocalExport.jpeg_quality") + StringUtil.COLON_AND_SPACE + slider.getValue())); //$NON-NLS-1$

            slider.setPaintTicks(true);
            slider.setSnapToTicks(false);
            slider.setMajorTickSpacing(10);
            JMVUtils.setPreferredWidth(slider, 145, 145);
            palenSlider1.add(slider);
            slider.addChangeListener(e -> {
                JSlider source = (JSlider) e.getSource();
                ((TitledBorder) palenSlider1.getBorder())
                    .setTitle(Messages.getString("LocalExport.jpeg_quality") + source.getValue()); //$NON-NLS-1$
                palenSlider1.repaint();
            });

            Object[] options = { palenSlider1, boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(IMG_QUALITY, String.valueOf(slider.getValue()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        } else if (EXPORT_FORMAT[3].equals(selected)) {
            final JCheckBox box1 = new JCheckBox(Messages.getString("LocalExport.ch_16"), //$NON-NLS-1$
                Boolean.valueOf(pref.getProperty(IMG_16_BIT, Boolean.FALSE.toString())));
            Object[] options = { box1, boxKeepNames };
            int response = JOptionPane.showOptionDialog(this, options, Messages.getString("LocalExport.export_message"), //$NON-NLS-1$
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
            if (response == JOptionPane.OK_OPTION) {
                pref.setProperty(IMG_16_BIT, String.valueOf(box1.isSelected()));
                pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
            }
        }
    }

    public void browseImgFile(String format) {
        String targetDirectoryPath = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_DIR, "");//$NON-NLS-1$

        boolean isSaveFileMode = EXPORT_FORMAT[1].equals(format);

        JFileChooser fileChooser = new JFileChooser(targetDirectoryPath);

        if (isSaveFileMode) {
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setAcceptAllFileFilterUsed(false);
            FileFormatFilter filter = new FileFormatFilter("zip", "ZIP"); //$NON-NLS-1$ //$NON-NLS-2$
            fileChooser.addChoosableFileFilter(filter);
            fileChooser.setFileFilter(filter);

        } else {
            /**
             * Idea is to show all the files in the directories to give the user some context, but only directories
             * should be accepted as selections. As the effect is L&F dependent, consider using DIRECTORIES_ONLY on
             * platforms that already meet your UI requirements. Empirically, it's platform-dependent, with files
             * appearing gray in all supported L&Fs on Mac OS X. <br>
             * Disabling file selection may be annoying. A solution is just to allow the user to select either a file or
             * a directory and if the user select a file just use the directory where that file is located.
             */

            if (System.getProperty("os.name").startsWith("Mac OS X")) { //$NON-NLS-1$ //$NON-NLS-2$
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else {
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            }

        }

        fileChooser.setMultiSelectionEnabled(false);

        // Set default selection name to enable save button
        if (StringUtil.hasText(targetDirectoryPath)) {
            File targetFile = new File(targetDirectoryPath);
            if (targetFile.exists()) {
                if (targetFile.isFile()) {
                    fileChooser.setSelectedFile(targetFile);
                } else if (targetFile.isDirectory()) {
                    String newExportSelectionName = Messages.getString("LocalExport.newExportSelectionName"); //$NON-NLS-1$
                    fileChooser.setSelectedFile(new File(newExportSelectionName));
                }
            }
        }

        File selectedFile;

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION
            || (selectedFile = fileChooser.getSelectedFile()) == null) {
            outputFolder = null;
            return;
        } else {
            if (isSaveFileMode) {
                outputFolder = ".zip".equals(FileUtil.getExtension(selectedFile.getName())) ? selectedFile //$NON-NLS-1$
                    : new File(selectedFile + ".zip"); //$NON-NLS-1$
            } else {
                outputFolder = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
            }
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DIR,
                outputFolder.isDirectory() ? outputFolder.getPath() : outputFolder.getParent());
        }
    }

    @Override
    public void closeAdditionalWindow() {
        // Do nothing
    }

    @Override
    public void resetoDefaultValues() {
        // Do nothing
    }

    @Override
    public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {
        final String format = (String) comboBoxImgFormat.getSelectedItem();
        browseImgFile(format);
        if (outputFolder != null) {
            final File exportDir = outputFolder.getCanonicalFile();

            final ExplorerTask<Boolean, String> task =
                new ExplorerTask<Boolean, String>(Messages.getString("LocalExport.exporting"), false) { //$NON-NLS-1$

                    @Override
                    protected Boolean doInBackground() throws Exception {
                        dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
                        if (EXPORT_FORMAT[0].equals(format)) {
                            writeDicom(this, exportDir, model, false);
                        } else if (EXPORT_FORMAT[1].equals(format)) {
                            writeDicom(this, exportDir, model, true);
                        } else {
                            writeOther(this, exportDir, model, format);
                        }
                        return true;
                    }

                    @Override
                    protected void done() {
                        dicomModel.firePropertyChange(
                            new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
                    }

                };
            task.execute();
        }
    }

    private static String getinstanceFileName(MediaElement img) {
        Integer instance = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
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
        return TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
    }

    private void writeOther(ExplorerTask task, File exportDir, CheckTreeModel model, String format) {
        Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
        boolean keepNames = Boolean.parseBoolean(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString()));
        int jpegQuality = StringUtil.getInt(pref.getProperty(IMG_QUALITY, null), 80);
        boolean img16 = Boolean.parseBoolean(pref.getProperty(IMG_16_BIT, Boolean.FALSE.toString()));

        try {
            synchronized (exportTree) {
                ArrayList<String> seriesGph = new ArrayList<>();
                TreePath[] paths = model.getCheckingPaths();
                for (TreePath treePath : paths) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                    if (node.getUserObject() instanceof Series) {
                        MediaSeries<?> s = (MediaSeries<?>) node.getUserObject();
                        if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
                            Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
                            if (series != null) {
                                seriesGph.add((String) series.getTagValue(TagD.get(Tag.SeriesInstanceUID)));
                            }
                        }
                    }
                }

                for (TreePath treePath : paths) {
                    if (task.isCancelled()) {
                        return;
                    }

                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        // Get instance number instead SOPInstanceUID to handle multiframe
                        String instance = getinstanceFileName(img);
                        if (!keepNames) {
                            instance = makeFileIDs(instance);
                        }
                        String path = buildPath(img, keepNames, node);
                        File destinationDir = new File(exportDir, path);
                        destinationDir.mkdirs();
                        
                        boolean mustBeReleased = false;
                        PlanarImage image = img.getImage(null);
                        if (image != null && !img16) {
                            PlanarImage rimage = img.getRenderedImage(image);
                            mustBeReleased = !Objects.equals(rimage, image);
                            image = rimage;
                        }
                        if (image != null) {
                            File destinationFile = new File(destinationDir, instance + getExtension(format));
                            if (EXPORT_FORMAT[3].equals(format)) {
                                ImageProcessor.writePNG(image.toMat(), destinationFile);
                            } else {
                                MatOfInt map = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality);
                                ImageProcessor.writeImage(image.toMat(), destinationFile, map);
                            }
                            if(mustBeReleased) {
                                ImageConversion.releasePlanarImage(image);
                            }
                            if (seriesGph.contains(img.getTagValue(TagD.get(Tag.SeriesInstanceUID)))) {
                                XmlSerializer.writePresentation(img, destinationFile);
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file to {}: {}", format, //$NON-NLS-1$
                                img.getFileCache().getOriginalFile().orElse(null));
                        }
                    } else if (node.getUserObject() instanceof MediaElement
                        && node.getUserObject() instanceof FileExtractor) {
                        MediaElement dcm = (MediaElement) node.getUserObject();
                        File fileSrc = ((FileExtractor) dcm).getExtractFile();
                        if (fileSrc != null) {
                            // Get instance number instead SOPInstanceUID to handle multiframe
                            String instance = getinstanceFileName(dcm);
                            if (!keepNames) {
                                instance = makeFileIDs(instance);
                            }
                            String path = buildPath(dcm, keepNames, node);
                            File destinationDir = new File(exportDir, path);
                            destinationDir.mkdirs();

                            File destinationFile =
                                new File(destinationDir, instance + FileUtil.getExtension(fileSrc.getName()));
                            FileUtil.nioCopyFile(fileSrc, destinationFile);
                        }
                    }
                }

            }
        } catch (Exception e) {
            LOGGER.error("Cannot extract media from DICOM", e); //$NON-NLS-1$
        }
    }

    private static String getExtension(String format) {
        if (EXPORT_FORMAT[3].equals(format)) {
            return ".png"; //$NON-NLS-1$
        }
        return ".jpg"; //$NON-NLS-1$
    }

    private void writeDicom(ExplorerTask task, File exportDir, CheckTreeModel model, boolean zipFile)
        throws IOException {
        boolean keepNames;
        boolean writeDicomdir;
        boolean cdCompatible;

        File writeDir;

        if (zipFile) {
            keepNames = false;
            writeDicomdir = true;
            cdCompatible = true;
            writeDir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "zip")); //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            Properties pref = Activator.IMPORT_EXPORT_PERSISTENCE;
            writeDicomdir = Boolean.valueOf(pref.getProperty(INC_DICOMDIR, Boolean.TRUE.toString()));
            keepNames = writeDicomdir ? false : Boolean.valueOf(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString()));
            cdCompatible = Boolean.valueOf(pref.getProperty(CD_COMPATIBLE, Boolean.FALSE.toString()));
            writeDir = exportDir;
        }

        DicomDirWriter writer = null;
        try {

            if (writeDicomdir) {
                File dcmdirFile = new File(writeDir, "DICOMDIR"); //$NON-NLS-1$
                writer = DicomDirLoader.open(dcmdirFile);
            }

            synchronized (exportTree) {
                ArrayList<String> uids = new ArrayList<>();
                TreePath[] paths = model.getCheckingPaths();
                for (TreePath treePath : paths) {
                    if (task.isCancelled()) {
                        return;
                    }

                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        String iuid = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
                        int index = uids.indexOf(iuid);
                        if (index == -1) {
                            uids.add(iuid);
                        } else {
                            // Write only once the file for multiframe
                            continue;
                        }
                        if (!keepNames) {
                            iuid = makeFileIDs(iuid);
                        }

                        String path = buildPath(img, keepNames, writeDicomdir, cdCompatible, node);
                        File destinationDir = new File(writeDir, path);
                        destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (img.saveToFile(destinationFile)) {
                            writeInDicomDir(writer, img, node, iuid, destinationFile);
                        } else {
                            LOGGER.error("Cannot export DICOM file: {}", //$NON-NLS-1$
                                img.getFileCache().getOriginalFile().orElse(null));
                        }
                    } else if (node.getUserObject() instanceof MediaElement) {
                        MediaElement dcm = (MediaElement) node.getUserObject();
                        String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);
                        if (!keepNames) {
                            iuid = makeFileIDs(iuid);
                        }

                        String path = buildPath(dcm, keepNames, writeDicomdir, cdCompatible, node);
                        File destinationDir = new File(writeDir, path);
                        destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (dcm.saveToFile(destinationFile)) {
                            writeInDicomDir(writer, dcm, node, iuid, destinationFile);
                        }
                    } else if (node.getUserObject() instanceof Series) {
                        MediaSeries<?> s = (MediaSeries<?>) node.getUserObject();
                        if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
                            Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
                            if (series != null) {
                                String seriesInstanceUID = UIDUtils.createUID();
                                for (MediaElement dcm : series.getMedias(null, null)) {
                                    GraphicModel grModel = (GraphicModel) dcm.getTagValue(TagW.PresentationModel);
                                    if (grModel != null && grModel.hasSerializableGraphics()) {
                                        String path = buildPath(dcm, keepNames, writeDicomdir, cdCompatible, node);
                                        buildAndWritePR(dcm, keepNames, new File(writeDir, path), writer, node,
                                            seriesInstanceUID);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Cannot export DICOM", e); //$NON-NLS-1$
        } finally {
            if (writer != null) {
                // Commit DICOMDIR changes and close the file
                writer.close();
            }
        }

        if (zipFile) {
            try {
                FileUtil.zip(writeDir, exportDir);
            } catch (Exception e) {
                LOGGER.error("Cannot export DICOM ZIP file: {}", exportDir, e); //$NON-NLS-1$
            } finally {
                FileUtil.recursiveDelete(writeDir);
            }
        }
    }

    public static Attributes buildAndWritePR(MediaElement img, boolean keepNames, File destinationDir,
        DicomDirWriter writer, DefaultMutableTreeNode node, String seriesInstanceUID) {
        Attributes imgAttributes = img.getMediaReader() instanceof DcmMediaReader
            ? ((DcmMediaReader) img.getMediaReader()).getDicomObject() : null;
        if (imgAttributes != null) {
            GraphicModel grModel = (GraphicModel) img.getTagValue(TagW.PresentationModel);
            if (grModel != null && grModel.hasSerializableGraphics()) {
                String prUid = UIDUtils.createUID();
                File outputFile = new File(destinationDir, keepNames ? prUid : makeFileIDs(prUid));
                destinationDir.mkdirs();
                Attributes prAttributes =
                    DicomPrSerializer.writePresentation(grModel, imgAttributes, outputFile, seriesInstanceUID, prUid);
                if (prAttributes != null) {
                    try {
                        writeInDicomDir(writer, prAttributes, node, outputFile.getName(), outputFile);
                    } catch (IOException e) {
                        LOGGER.error("Writing DICOMDIR", e); //$NON-NLS-1$
                    }
                }
            }
        }
        return imgAttributes;
    }

    public static String buildPath(MediaElement img, boolean keepNames, boolean writeDicomdir, boolean cdCompatible,
        DefaultMutableTreeNode node) {
        StringBuilder buffer = new StringBuilder();
        // Cannot keep folders names with DICOMDIR (could be not valid)
        if (keepNames && !writeDicomdir) {
            TreeNode[] objects = node.getPath();
            if (objects.length > 2) {
                for (int i = 1; i < objects.length - 1; i++) {
                    buffer.append(buildFolderName(objects[i].toString(), 30));
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
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.StudyInstanceUID, String.class)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
        }
        return buffer.toString();
    }

    public static String buildPath(MediaElement img, boolean keepNames, DefaultMutableTreeNode node) {
        StringBuilder buffer = new StringBuilder();
        if (keepNames) {
            TreeNode[] objects = node.getPath();
            if (objects.length > 3) {
                buffer.append(buildFolderName(objects[1].toString(), 30));
                buffer.append(File.separator);
                buffer.append(buildFolderName(objects[2].toString(), 30));
                buffer.append(File.separator);
                buffer.append(buildFolderName(objects[3].toString(), 25));
                buffer.append('-');
                // Hash of UID to guaranty the unique behavior of the name.
                buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
            }
        } else {
            buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.StudyInstanceUID, String.class)));
            buffer.append(File.separator);
            buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
        }
        return buffer.toString();
    }

    private static String buildFolderName(String str, int length) {
        String value = FileUtil.getValidFileNameWithoutHTML(str);
        value = StringUtil.getTruncatedString(value, length, Suffix.UNDERSCORE).trim();
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value; //$NON-NLS-1$
    }

    private static boolean writeInDicomDir(DicomDirWriter writer, MediaElement img, DefaultMutableTreeNode node,
        String iuid, File destinationFile) throws IOException {
        if (writer != null) {
            if (!(img.getMediaReader() instanceof DcmMediaReader)
                || ((DcmMediaReader) img.getMediaReader()).getDicomObject() == null) {
                LOGGER.error("Cannot export DICOM file: {}", img.getFileCache().getOriginalFile().orElse(null)); //$NON-NLS-1$
                return false;
            }
            return writeInDicomDir(writer, ((DcmMediaReader) img.getMediaReader()).getDicomObject(), node, iuid,
                destinationFile);
        }
        return false;
    }

    private static boolean writeInDicomDir(DicomDirWriter writer, Attributes dataset, DefaultMutableTreeNode node,
        String iuid, File destinationFile) throws IOException {
        if (writer != null && dataset != null) {
            Attributes fmi = dataset.createFileMetaInformation(UID.ImplicitVRLittleEndian);

            String miuid = fmi.getString(Tag.MediaStorageSOPInstanceUID, null);

            String pid = dataset.getString(Tag.PatientID, null);
            String styuid = dataset.getString(Tag.StudyInstanceUID, null);
            String seruid = dataset.getString(Tag.SeriesInstanceUID, null);

            if (styuid != null && seruid != null) {
                if (pid == null) {
                    pid = styuid;
                    dataset.setString(Tag.PatientID, VR.LO, pid);
                }
                Attributes patRec = writer.findPatientRecord(pid);
                if (patRec == null) {
                    patRec = DicomDirLoader.RecordFactory.createRecord(RecordType.PATIENT, null, dataset, null, null);
                    writer.addRootDirectoryRecord(patRec);
                }
                Attributes studyRec = writer.findStudyRecord(patRec, styuid);
                if (studyRec == null) {
                    studyRec = DicomDirLoader.RecordFactory.createRecord(RecordType.STUDY, null, dataset, null, null);
                    writer.addLowerDirectoryRecord(patRec, studyRec);
                }
                Attributes seriesRec = writer.findSeriesRecord(studyRec, seruid);
                if (seriesRec == null) {
                    seriesRec = DicomDirLoader.RecordFactory.createRecord(RecordType.SERIES, null, dataset, null, null);
                    /*
                     * Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It may or may
                     * not correspond to one of the images of the Series.
                     */
                    if (seriesRec != null && node.getParent() instanceof DefaultMutableTreeNode) {
                        Object userObject = ((DefaultMutableTreeNode) node.getParent()).getUserObject();
                        if (userObject instanceof DicomSeries) {
                            DicomImageElement midImage =
                                ((DicomSeries) userObject).getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
                            Attributes iconItem = mkIconItem(midImage);
                            if (iconItem != null) {
                                seriesRec.newSequence(Tag.IconImageSequence, 1).add(iconItem);
                            }
                        }
                    }
                    writer.addLowerDirectoryRecord(studyRec, seriesRec);
                }
                Attributes instRec;
                if (writer.findLowerInstanceRecord(seriesRec, false, iuid) == null) {
                    instRec =
                        DicomDirLoader.RecordFactory.createRecord(dataset, fmi, writer.toFileIDs(destinationFile));
                    writer.addLowerDirectoryRecord(seriesRec, instRec);
                }
            } else {
                if (writer.findRootInstanceRecord(false, miuid) == null) {
                    Attributes instRec =
                        DicomDirLoader.RecordFactory.createRecord(dataset, fmi, writer.toFileIDs(destinationFile));
                    writer.addRootDirectoryRecord(instRec);
                }
            }
        }
        return true;
    }

    public static String makeFileIDs(String uid) {
        if (uid != null) {
            return Integer.toHexString(uid.hashCode());
        }
        return null;
    }

    public static Attributes mkIconItem(DicomImageElement image) {
        if (image == null) {
            return null;
        }
        PlanarImage thumbnail = null;
        PlanarImage imgPl = image.getImage(null);
        if (imgPl != null) {
            try (PlanarImage img = image.getRenderedImage(imgPl)) {
                thumbnail = ImageProcessor.buildThumbnail(img, new Dimension(128, 128), true);
            }
        }

        if (thumbnail == null) {
            return null;
        }
        int w = thumbnail.width();
        int h = thumbnail.height();

        String pmi = TagD.getTagValue(image, Tag.PhotometricInterpretation, String.class);
        if (thumbnail.channels() >= 3) {
            pmi = "PALETTE COLOR"; //$NON-NLS-1$
        }

        byte[] iconPixelData = new byte[w * h];
        Attributes iconItem = new Attributes();

        if ("PALETTE COLOR".equals(pmi)) { //$NON-NLS-1$
            BufferedImage bi =
                ImageConversion.convertTo(ImageConversion.toBufferedImage(thumbnail), BufferedImage.TYPE_BYTE_INDEXED);
            IndexColorModel cm = (IndexColorModel) bi.getColorModel();
            int[] lutDesc = { cm.getMapSize(), 0, 8 };
            byte[] r = new byte[lutDesc[0]];
            byte[] g = new byte[lutDesc[0]];
            byte[] b = new byte[lutDesc[0]];
            cm.getReds(r);
            cm.getGreens(g);
            cm.getBlues(b);
            iconItem.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, r);
            iconItem.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, g);
            iconItem.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, b);

            Raster raster = bi.getRaster();
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) raster.getSample(x, y, 0);
                }
            }
        } else {
            pmi = "MONOCHROME2"; //$NON-NLS-1$
            thumbnail.get(0, 0, iconPixelData);
        }
        iconItem.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
        iconItem.setInt(Tag.Rows, VR.US, h);
        iconItem.setInt(Tag.Columns, VR.US, w);
        iconItem.setInt(Tag.SamplesPerPixel, VR.US, 1);
        iconItem.setInt(Tag.BitsAllocated, VR.US, 8);
        iconItem.setInt(Tag.BitsStored, VR.US, 8);
        iconItem.setInt(Tag.HighBit, VR.US, 7);
        iconItem.setBytes(Tag.PixelData, VR.OW, iconPixelData);
        return iconItem;
    }

}
