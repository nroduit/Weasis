/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomElement;
import org.weasis.dicom.codec.DicomElement.DicomExportParameters;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.FileExtractor;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.dicom.param.AttributeEditor;
import org.weasis.dicom.param.DefaultAttributeEditor;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class LocalExport extends AbstractItemDialogPage implements ExportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalExport.class);

  public static final String LAST_DIR = "lastExportDir";
  public static final String INC_DICOMDIR = "exp.include.dicomdir";
  public static final String KEEP_INFO_DIR = "exp.keep.dir.name";
  public static final String IMG_QUALITY = "exp.img.quality";
  public static final String IMG_16_BIT = "exp.16-bit"; // NON-NLS
  public static final String IMG_PIXEL_PADDING = "exp.padding"; // NON-NLS
  public static final String IMG_SHUTTER = "exp.shutter"; // NON-NLS
  public static final String IMG_OVERLAY = "exp.overlay"; // NON-NLS
  public static final String DICOM_TSUID = "exp.dicom.tsuid"; // NON-NLS
  public static final String DICOM_ONLY_RAW = "exp.dicom.only.raw"; // NON-NLS
  public static final String DICOM_NEW_UID = "exp.dicom.new.uid"; // NON-NLS
  public static final String CD_COMPATIBLE = "exp.cd";

  public enum Format {
    DICOM("DICOM", "dcm"),
    DICOM_ZIP("DICOM ZIP", "zip"), // NON-NLS
    JPEG("JPEG Lossy", "jpg"), // NON-NLS
    PNG("PNG", "png"),
    TIFF("TIFF", "tif"),
    JP2("JPEG 2000", "jp2"); // NON-NLS

    private final String title;
    private final String extension;

    Format(String title, String extension) {
      this.title = title;
      this.extension = extension;
    }

    @Override
    public String toString() {
      return title;
    }

    public String getTitle() {
      return title;
    }

    public String getExtension() {
      return extension;
    }
  }

  protected final DicomModel dicomModel;
  protected File outputFolder;
  protected final ExportTree exportTree;
  protected final List<TransferSyntax> transferSyntaxList =
      List.of(
          TransferSyntax.NONE,
          TransferSyntax.EXPLICIT_VR_LE,
          TransferSyntax.JPEG_LOSSY_8,
          TransferSyntax.JPEG_LOSSY_12,
          TransferSyntax.JPEG_LOSSLESS_70,
          TransferSyntax.JPEGLS_LOSSLESS,
          TransferSyntax.JPEGLS_NEAR_LOSSLESS,
          TransferSyntax.JPEG2000_LOSSLESS,
          TransferSyntax.JPEG2000);
  protected final JComboBox<Format> comboBoxImgFormat;

  public LocalExport(DicomModel dicomModel, CheckTreeModel treeModel) {
    this(Messages.getString("LocalExport.local_dev"), 0, dicomModel, treeModel);
  }

  public LocalExport(
      String title, int pagePosition, DicomModel dicomModel, CheckTreeModel treeModel) {
    super(title, pagePosition);
    this.dicomModel = dicomModel;
    this.exportTree = new ExportTree(treeModel);
    this.comboBoxImgFormat = new JComboBox<>(new DefaultComboBoxModel<>(Format.values()));
    initGUI();
  }

  public void initGUI() {
    JLabel lblImportAFolder = new JLabel(Messages.getString("LocalExport.exp") + StringUtil.COLON);
    JButton btnNewButton = new JButton(Messages.getString("LocalExport.options"));
    btnNewButton.addActionListener(e -> showExportingOptions());

    List<JComponent> list = new ArrayList<>();
    list.add(lblImportAFolder);
    list.add(comboBoxImgFormat);
    list.add(GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR));
    list.add(btnNewButton);
    list.addAll(getAdditionalOption());

    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, 0, list.toArray(JComponent[]::new)));
    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    exportTree.setBorder(UIManager.getBorder("ScrollPane.border"));
    add(exportTree);
  }

  protected List<JComponent> getAdditionalOption() {
    return Collections.emptyList();
  }

  protected Properties getPreferences() {
    return Activator.IMPORT_EXPORT_PERSISTENCE;
  }

  protected void showExportingOptions() {
    Properties pref = getPreferences();
    boolean forceDICOMDIR =
        Boolean.parseBoolean(pref.getProperty("force.dicomdir", Boolean.FALSE.toString()));
    final JCheckBox boxKeepNames =
        new JCheckBox(
            Messages.getString("LocalExport.keep_dir"),
            Boolean.parseBoolean(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString())));

    Format selected = (Format) comboBoxImgFormat.getSelectedItem();
    boolean dicomZip = Format.DICOM_ZIP == selected;
    if (Format.DICOM == selected || dicomZip) {
      if (dicomZip) {
        forceDICOMDIR = true;
      }
      List<Component> options = new ArrayList<>();
      JSlider slider = buildQualitySlider(pref);
      JCheckBox onlyUncompressed =
          new JCheckBox(
              Messages.getString("transcode.only.uncompressed"),
              Boolean.parseBoolean(pref.getProperty(DICOM_ONLY_RAW, Boolean.TRUE.toString())));
      JComboBox<TransferSyntax> syntaxComboBox = new JComboBox<>(new Vector<>(transferSyntaxList));
      syntaxComboBox.setSelectedItem(null);
      syntaxComboBox.addItemListener(
          e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              TransferSyntax t = (TransferSyntax) e.getItem();
              boolean realTsuid = t != null && t != TransferSyntax.NONE;
              slider.setEnabled(
                  realTsuid && TransferSyntaxType.isLossyCompression(t.getTransferSyntaxUID()));
              onlyUncompressed.setEnabled(
                  realTsuid && !DicomUtils.isNative(t.getTransferSyntaxUID()));
            }
          });

      syntaxComboBox.setSelectedItem(
          TransferSyntax.getTransferSyntax(
              pref.getProperty(DICOM_TSUID, TransferSyntax.NONE.name())));
      JPanel transcodingPanel =
          GuiUtils.getVerticalBoxLayoutPanel(
              GuiUtils.getFlowLayoutPanel(syntaxComboBox),
              GuiUtils.getFlowLayoutPanel(onlyUncompressed),
              slider);
      Border spaceY = GuiUtils.getEmptyBorder(10, 5, 10, 5);
      transcodingPanel.setBorder(
          BorderFactory.createCompoundBorder(
              spaceY, GuiUtils.getTitledBorder(Messages.getString("transcoding"))));
      options.add(transcodingPanel);

      JCheckBox newUidCheckBox =
          new JCheckBox(
              Messages.getString("generate.new.unique.identifiers"),
              Boolean.parseBoolean(pref.getProperty(DICOM_NEW_UID, Boolean.FALSE.toString())));
      options.add(newUidCheckBox);

      final JCheckBox box1 =
          new JCheckBox(
              Messages.getString("LocalExport.inc_dicomdir"),
              Boolean.parseBoolean(pref.getProperty(INC_DICOMDIR, Boolean.TRUE.toString())));
      final JCheckBox box2 =
          new JCheckBox(
              Messages.getString("LocalExport.cd_folders"),
              Boolean.parseBoolean(pref.getProperty(CD_COMPATIBLE, Boolean.FALSE.toString())));
      box2.setEnabled(box1.isSelected());
      boxKeepNames.setEnabled(!box1.isSelected());
      if (!forceDICOMDIR) {
        options.add(box1);
        options.add(box2);
        options.add(boxKeepNames);
      }
      box1.addActionListener(
          e -> {
            boxKeepNames.setEnabled(!box1.isSelected());
            box2.setEnabled(box1.isSelected());
          });

      int response =
          JOptionPane.showOptionDialog(
              WinUtil.getParentWindow(this), // Use parent because this has large size
              options.toArray(),
              Messages.getString("LocalExport.export_message"),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE,
              null,
              null,
              null);
      if (response == JOptionPane.OK_OPTION) {
        pref.setProperty(IMG_QUALITY, String.valueOf(slider.getValue()));
        pref.setProperty(
            DICOM_TSUID,
            StringUtil.getEmptyStringIfNullEnum((TransferSyntax) syntaxComboBox.getSelectedItem()));
        pref.setProperty(DICOM_ONLY_RAW, String.valueOf(onlyUncompressed.isSelected()));
        pref.setProperty(DICOM_NEW_UID, String.valueOf(newUidCheckBox.isSelected()));
        if (!forceDICOMDIR) {
          pref.setProperty(INC_DICOMDIR, String.valueOf(box1.isSelected()));
          pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
          pref.setProperty(CD_COMPATIBLE, String.valueOf(box2.isSelected()));
        }
      }
    } else if (Format.JPEG == selected
        || Format.PNG == selected
        || Format.TIFF == selected
        || Format.JP2 == selected) {
      boolean lossy = Format.JPEG == selected;
      JSlider slider = null;
      JCheckBox preservePixelCheckBox;
      String dicom = "DICOM "; // NON-NLS
      JCheckBox paddingCheckBox =
          new JCheckBox(
              dicom + ActionW.IMAGE_PIX_PADDING.getTitle(),
              Boolean.parseBoolean(pref.getProperty(IMG_PIXEL_PADDING, Boolean.TRUE.toString())));
      JCheckBox overlayCheckBox =
          new JCheckBox(
              dicom + ActionW.IMAGE_OVERLAY.getTitle(),
              Boolean.parseBoolean(pref.getProperty(IMG_OVERLAY, Boolean.TRUE.toString())));
      JCheckBox shutterCheckBox =
          new JCheckBox(
              dicom + ActionW.IMAGE_SHUTTER.getTitle(),
              Boolean.parseBoolean(pref.getProperty(IMG_SHUTTER, Boolean.TRUE.toString())));
      List<Component> options = new ArrayList<>();

      if (lossy) {
        slider = buildQualitySlider(pref);
        options.add(slider);
      }

      preservePixelCheckBox =
          new JCheckBox(
              Messages.getString("LocalExport.ch_16"),
              Boolean.parseBoolean(pref.getProperty(IMG_16_BIT, Boolean.FALSE.toString())));
      preservePixelCheckBox.setEnabled(Format.JPEG != selected);
      options.add(preservePixelCheckBox);
      options.add(paddingCheckBox);
      options.add(shutterCheckBox);
      options.add(overlayCheckBox);
      options.add(boxKeepNames);

      int response =
          JOptionPane.showOptionDialog(
              WinUtil.getParentWindow(this), // Use parent because this has large size
              options.toArray(),
              Messages.getString("LocalExport.export_message"),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE,
              null,
              null,
              null);
      if (response == JOptionPane.OK_OPTION) {
        if (slider != null) {
          pref.setProperty(IMG_QUALITY, String.valueOf(slider.getValue()));
        }
        pref.setProperty(IMG_16_BIT, String.valueOf(preservePixelCheckBox.isSelected()));
        pref.setProperty(IMG_PIXEL_PADDING, String.valueOf(paddingCheckBox.isSelected()));
        pref.setProperty(IMG_SHUTTER, String.valueOf(shutterCheckBox.isSelected()));
        pref.setProperty(IMG_OVERLAY, String.valueOf(overlayCheckBox.isSelected()));
        pref.setProperty(KEEP_INFO_DIR, String.valueOf(boxKeepNames.isSelected()));
      }
    }
  }

  private static JSlider buildQualitySlider(Properties pref) {
    JSlider slider =
        new JSlider(1, 100, StringUtil.getInt(pref.getProperty(IMG_QUALITY, null), 85));
    TitledBorder titledBorder =
        new TitledBorder(
            BorderFactory.createEmptyBorder(),
            Messages.getString("LocalExport.jpeg_quality")
                + StringUtil.COLON_AND_SPACE
                + slider.getValue(),
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            FontItem.MEDIUM.getFont(),
            null);
    slider.setBorder(titledBorder);
    slider.setPaintTicks(true);
    slider.setSnapToTicks(false);
    slider.setMajorTickSpacing(10);
    slider.addChangeListener(
        e -> {
          JSlider source = (JSlider) e.getSource();
          ((TitledBorder) slider.getBorder())
              .setTitle(
                  Messages.getString("LocalExport.jpeg_quality")
                      + StringUtil.COLON_AND_SPACE
                      + source.getValue());
          slider.repaint();
        });
    return slider;
  }

  public void browseImgFile(Format format) {
    Properties pref = getPreferences();
    String targetDirectoryPath = pref.getProperty(LAST_DIR, "");

    boolean isSaveFileMode = format == Format.DICOM_ZIP;

    JFileChooser fileChooser = new JFileChooser(targetDirectoryPath);

    if (isSaveFileMode) {
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fileChooser.setAcceptAllFileFilterUsed(false);
      FileFormatFilter filter = new FileFormatFilter("zip", "ZIP"); // NON-NLS
      fileChooser.addChoosableFileFilter(filter);
      fileChooser.setFileFilter(filter);

    } else {
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    }

    fileChooser.setMultiSelectionEnabled(false);

    // Set default selection name to enable save button
    if (StringUtil.hasText(targetDirectoryPath)) {
      File targetFile = new File(targetDirectoryPath);
      if (targetFile.exists()) {
        if (targetFile.isFile()) {
          fileChooser.setSelectedFile(targetFile);
        } else if (targetFile.isDirectory()) {
          String newExportSelectionName = Messages.getString("LocalExport.newExportSelectionName");
          fileChooser.setSelectedFile(new File(newExportSelectionName));
        }
      }
    }

    File selectedFile;

    if (fileChooser.showSaveDialog(WinUtil.getParentWindow(this))
            != JFileChooser.APPROVE_OPTION // Use parent window because this has large size
        || (selectedFile = fileChooser.getSelectedFile()) == null) {
      outputFolder = null;
    } else {
      if (isSaveFileMode) {
        outputFolder =
            ".zip".equals(FileUtil.getExtension(selectedFile.getName()))
                ? selectedFile
                : new File(selectedFile + ".zip");
      } else {
        outputFolder = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
      }
      pref.setProperty(
          LAST_DIR, outputFolder.isDirectory() ? outputFolder.getPath() : outputFolder.getParent());
    }
  }

  @Override
  public void closeAdditionalWindow() {
    // Do nothing
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }

  @Override
  public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {
    Format format = (Format) comboBoxImgFormat.getSelectedItem();
    browseImgFile(format);
    if (outputFolder != null) {
      final File exportDir = outputFolder.getCanonicalFile();

      final ExplorerTask<Boolean, String> task =
          new ExplorerTask<>(Messages.getString("LocalExport.exporting"), false) {

            @Override
            protected Boolean doInBackground() throws Exception {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
              Properties pref = getPreferences();
              if (format == Format.DICOM) {
                writeDicom(this, exportDir, model, pref);
              } else if (format == Format.DICOM_ZIP) {
                pref.setProperty(INC_DICOMDIR, Boolean.TRUE.toString());
                pref.setProperty(CD_COMPATIBLE, Boolean.TRUE.toString());
                File writeDir =
                    FileUtil.createTempDir(
                        AppProperties.buildAccessibleTempDirectory(
                            "tmp", Format.DICOM_ZIP.extension)); // NON-NLS
                writeDicom(this, writeDir, model, pref);
                try {
                  FileUtil.zip(writeDir, exportDir);
                } catch (Exception e) {
                  LOGGER.error("Cannot export DICOM ZIP file: {}", exportDir, e);
                } finally {
                  FileUtil.recursiveDelete(writeDir);
                }
              } else {
                writeOther(this, exportDir, model, format, pref);
              }
              return true;
            }

            @Override
            protected void done() {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
            }
          };
      task.execute();
    }
  }

  private static String instanceFileName(MediaElement img) {
    String iUid = makeFileIDs(TagD.getTagValue(img, Tag.SOPInstanceUID, String.class));
    Integer instance = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
    if (instance != null) {
      String val = instance.toString();
      if (val.length() < 5) {
        char[] chars = new char[5 - val.length()];
        Arrays.fill(chars, '0');
        return new String(chars) + val + "-" + iUid;
      } else {
        return val + "-" + iUid;
      }
    }
    return iUid;
  }

  protected void writeOther(
      ExplorerTask task, File exportDir, CheckTreeModel model, Format format, Properties pref) {
    boolean keepNames =
        Boolean.parseBoolean(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString()));
    int jpegQuality = StringUtil.getInt(pref.getProperty(IMG_QUALITY), 80);
    boolean padding =
        Boolean.parseBoolean(pref.getProperty(IMG_PIXEL_PADDING, Boolean.TRUE.toString()));
    boolean overlay = Boolean.parseBoolean(pref.getProperty(IMG_OVERLAY, Boolean.TRUE.toString()));
    boolean shutter = Boolean.parseBoolean(pref.getProperty(IMG_SHUTTER, Boolean.TRUE.toString()));
    boolean img16 = Boolean.parseBoolean(pref.getProperty(IMG_16_BIT, Boolean.FALSE.toString()));
    if (format == Format.JPEG) {
      img16 = false;
    }

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

          if (node.getUserObject() instanceof DicomImageElement img) {
            // Get instance number instead SOPInstanceUID to handle multiframe
            String instance = instanceFileName(img);
            String path = buildPath(img, keepNames, node);
            File destinationDir = new File(exportDir, path);
            destinationDir.mkdirs();

            SimpleOpManager manager =
                img.buildSimpleOpManager(img16, padding, shutter, overlay, 1.0);
            PlanarImage image = manager.getFirstNodeInputImage();
            if (image != null) {
              PlanarImage rimage = manager.process();
              if (rimage == null) {
                rimage = image;
              }
              boolean mustBeReleased = !Objects.equals(rimage, image);
              image = rimage;

              File destinationFile = new File(destinationDir, instance + "." + format.extension);
              if (format == Format.PNG) {
                ImageProcessor.writePNG(image.toMat(), destinationFile);
              } else {
                MatOfInt map = new MatOfInt();
                if (format == Format.JPEG) {
                  map.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality);
                }
                ImageProcessor.writeImage(image.toMat(), destinationFile, map);
              }
              if (mustBeReleased) {
                ImageConversion.releasePlanarImage(image);
              }
              if (seriesGph.contains(img.getTagValue(TagD.get(Tag.SeriesInstanceUID)))) {
                XmlSerializer.writePresentation(img, destinationFile);
              }
            } else {
              LOGGER.error(
                  "Cannot export DICOM file to {}: {}",
                  format,
                  img.getFileCache().getOriginalFile().orElse(null));
            }
          } else if (node.getUserObject() instanceof MediaElement dcm
              && node.getUserObject() instanceof FileExtractor) {
            File fileSrc = ((FileExtractor) dcm).getExtractFile();
            if (fileSrc != null) {
              // Get instance number instead SOPInstanceUID to handle multiframe
              String instance = instanceFileName(dcm);
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
      LOGGER.error("Cannot extract media from DICOM", e);
    }
  }

  private static Attributes getUIDs(DefaultAttributeEditor editor, MediaElement dcm) {
    Attributes uidTags = new Attributes();
    uidTags.setString(Tag.PatientID, VR.LO, (String) dcm.getTagValue(TagW.PatientPseudoUID));
    uidTags.setString(
        Tag.StudyInstanceUID, VR.UI, TagD.getTagValue(dcm, Tag.StudyInstanceUID, String.class));
    uidTags.setString(
        Tag.SeriesInstanceUID, VR.UI, TagD.getTagValue(dcm, Tag.SeriesInstanceUID, String.class));
    uidTags.setString(
        Tag.SOPInstanceUID, VR.UI, TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class));
    editor.apply(uidTags, null);
    return uidTags;
  }

  protected void writeDicom(
      ExplorerTask task, File exportDir, CheckTreeModel model, Properties pref) throws IOException {
    boolean keepNames;
    boolean writeDicomdir;
    boolean cdCompatible;

    int jpegQuality = StringUtil.getInt(pref.getProperty(IMG_QUALITY), 80);
    int compressionRatio = 100 - jpegQuality; // Ratio from 0 to 99
    boolean newUID =
        Boolean.parseBoolean(pref.getProperty(DICOM_NEW_UID, Boolean.FALSE.toString()));
    boolean onlyRaw =
        Boolean.parseBoolean(pref.getProperty(DICOM_ONLY_RAW, Boolean.TRUE.toString()));
    TransferSyntax tsuid =
        TransferSyntax.getTransferSyntax(pref.getProperty(DICOM_TSUID, TransferSyntax.NONE.name()));
    boolean realTsuid = tsuid != TransferSyntax.NONE;
    if (realTsuid && DicomUtils.isNative(tsuid.getTransferSyntaxUID())) {
      onlyRaw = false;
    }
    DefaultAttributeEditor editor = new DefaultAttributeEditor(newUID, null);

    writeDicomdir = Boolean.parseBoolean(pref.getProperty(INC_DICOMDIR, Boolean.TRUE.toString()));
    keepNames =
        !writeDicomdir
            && Boolean.parseBoolean(pref.getProperty(KEEP_INFO_DIR, Boolean.TRUE.toString()));
    cdCompatible = Boolean.parseBoolean(pref.getProperty(CD_COMPATIBLE, Boolean.FALSE.toString()));

    DicomDirWriter writer = null;
    try {
      if (writeDicomdir) {
        File dcmdirFile = new File(exportDir, "DICOMDIR");
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
          if (node.getUserObject() instanceof DicomImageElement img) {
            Attributes uidTags = getUIDs(editor, img);
            String iuid = uidTags.getString(Tag.SOPInstanceUID);
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

            String path = buildPath(img, keepNames, cdCompatible, node, uidTags);
            File destinationDir = new File(exportDir, path);
            destinationDir.mkdirs();

            List<AttributeEditor> dicomEditors = getAttributeEditors(editor);
            File destinationFile = new File(destinationDir, iuid);

            DicomExportParameters dicomExportParameters =
                new DicomExportParameters(
                    tsuid, onlyRaw, dicomEditors, jpegQuality, compressionRatio);
            Attributes attributes = img.saveToFile(destinationFile, dicomExportParameters);
            if (attributes != null) {
              writeInDicomDir(writer, attributes, node, iuid, destinationFile);
            }
          } else if (node.getUserObject() instanceof DicomElement dcm) {
            Attributes uidTags = getUIDs(editor, (MediaElement) dcm);
            String iuid = uidTags.getString(Tag.SOPInstanceUID);
            if (!keepNames) {
              iuid = makeFileIDs(iuid);
            }

            String path = buildPath((MediaElement) dcm, keepNames, cdCompatible, node, uidTags);
            File destinationDir = new File(exportDir, path);
            destinationDir.mkdirs();

            File destinationFile = new File(destinationDir, iuid);
            DicomExportParameters dicomExportParameters =
                new DicomExportParameters(
                    null, onlyRaw, getAttributeEditors(editor), jpegQuality, compressionRatio);
            Attributes attributes = dcm.saveToFile(destinationFile, dicomExportParameters);
            if (attributes != null) {
              writeInDicomDir(writer, attributes, node, iuid, destinationFile);
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
                    String path =
                        buildPath(dcm, keepNames, cdCompatible, node, getUIDs(editor, dcm));
                    buildAndWritePR(
                        dcm, keepNames, new File(exportDir, path), writer, node, seriesInstanceUID);
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
      LOGGER.error("Cannot export DICOM", e);
    } finally {
      if (writer != null) {
        // Commit DICOMDIR changes and close the file
        writer.close();
      }
    }
  }

  private static List<AttributeEditor> getAttributeEditors(DefaultAttributeEditor editor) {
    if (editor.isGenerateUIDs() || editor.getTagToOverride() != null) {
      return List.of(editor);
    }
    return Collections.emptyList();
  }

  public static Attributes buildAndWritePR(
      MediaElement img,
      boolean keepNames,
      File destinationDir,
      DicomDirWriter writer,
      DefaultMutableTreeNode node,
      String seriesInstanceUID) {
    Attributes imgAttributes =
        img.getMediaReader() instanceof DcmMediaReader reader ? reader.getDicomObject() : null;
    if (imgAttributes != null) {
      GraphicModel grModel = (GraphicModel) img.getTagValue(TagW.PresentationModel);
      if (grModel != null && grModel.hasSerializableGraphics()) {
        String prUid = UIDUtils.createUID();
        File outputFile = new File(destinationDir, keepNames ? prUid : makeFileIDs(prUid));
        destinationDir.mkdirs();
        Attributes prAttributes =
            DicomPrSerializer.writePresentation(
                grModel, imgAttributes, outputFile, seriesInstanceUID, prUid);
        if (prAttributes != null) {
          try {
            writeInDicomDir(writer, prAttributes, node, outputFile.getName(), outputFile);
          } catch (IOException e) {
            LOGGER.error("Writing DICOMDIR", e);
          }
        }
      }
    }
    return imgAttributes;
  }

  public static String buildPath(
      MediaElement img,
      boolean preservePath,
      boolean cdCompatible,
      DefaultMutableTreeNode node,
      Attributes uidTags) {
    StringBuilder buffer = new StringBuilder();
    // Cannot keep folders names with DICOMDIR (could be not valid)
    if (preservePath) {
      TreeNode[] objects = node.getPath();
      if (objects.length > 2) {
        for (int i = 1; i < objects.length - 1; i++) {
          buffer.append(buildFolderName(objects[i].toString(), 30));
          buffer.append(File.separator);
        }
      }
    } else {
      if (cdCompatible) {
        buffer.append("DICOM");
        buffer.append(File.separator);
      }

      String patientUID = null;
      String studyUID = null;
      String seriesUID = null;
      if (uidTags != null) {
        patientUID = uidTags.getString(Tag.PatientID);
        studyUID = uidTags.getString(Tag.StudyInstanceUID);
        seriesUID = uidTags.getString(Tag.SeriesInstanceUID);
      }

      if (!StringUtil.hasText(patientUID)) {
        patientUID = (String) img.getTagValue(TagW.PatientPseudoUID);
      }
      if (!StringUtil.hasText(studyUID)) {
        studyUID = TagD.getTagValue(img, Tag.StudyInstanceUID, String.class);
      }
      if (!StringUtil.hasText(seriesUID)) {
        seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      }
      buffer.append(makeFileIDs(patientUID));
      buffer.append(File.separator);
      buffer.append(makeFileIDs(studyUID));
      buffer.append(File.separator);
      buffer.append(makeFileIDs(seriesUID));
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
    return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
  }

  private static boolean writeInDicomDir(
      DicomDirWriter writer,
      Attributes dataset,
      DefaultMutableTreeNode node,
      String iuid,
      File destinationFile)
      throws IOException {
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
          patRec =
              DicomDirLoader.RecordFactory.createRecord(
                  RecordType.PATIENT, null, dataset, null, null);
          writer.addRootDirectoryRecord(patRec);
        }
        Attributes studyRec = writer.findStudyRecord(patRec, styuid);
        if (studyRec == null) {
          studyRec =
              DicomDirLoader.RecordFactory.createRecord(
                  RecordType.STUDY, null, dataset, null, null);
          writer.addLowerDirectoryRecord(patRec, studyRec);
        }
        Attributes seriesRec = writer.findSeriesRecord(studyRec, seruid);
        if (seriesRec == null) {
          seriesRec =
              DicomDirLoader.RecordFactory.createRecord(
                  RecordType.SERIES, null, dataset, null, null);
          /*
           * Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It may or may
           * not correspond to one of the images of the Series.
           */
          if (seriesRec != null && node.getParent() instanceof DefaultMutableTreeNode treeNode) {
            if (treeNode.getUserObject() instanceof DicomSeries dicomSeries) {
              DicomImageElement midImage =
                  dicomSeries.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
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
              DicomDirLoader.RecordFactory.createRecord(
                  dataset, fmi, writer.toFileIDs(destinationFile));
          writer.addLowerDirectoryRecord(seriesRec, instRec);
        }
      } else {
        if (writer.findRootInstanceRecord(false, miuid) == null) {
          Attributes instRec =
              DicomDirLoader.RecordFactory.createRecord(
                  dataset, fmi, writer.toFileIDs(destinationFile));
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
      SimpleOpManager manager = new SimpleOpManager();
      manager.addImageOperationAction(new WindowAndPresetsOp());
      manager.setParamValue(WindowOp.OP_NAME, WindowOp.P_IMAGE_ELEMENT, image);
      manager.setParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), true);
      manager.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), true);

      ZoomOp rectifyZoomOp = image.getRectifyAspectRatioZoomOp();
      if (rectifyZoomOp != null) {
        manager.addImageOperationAction(rectifyZoomOp);
      }
      manager.setFirstNode(imgPl);
      thumbnail = ImageProcessor.buildThumbnail(manager.process(), new Dimension(128, 128), true);
    }

    if (thumbnail == null) {
      return null;
    }
    int w = thumbnail.width();
    int h = thumbnail.height();

    String pmi = TagD.getTagValue(image, Tag.PhotometricInterpretation, String.class);
    if (thumbnail.channels() >= 3) {
      pmi = "PALETTE COLOR"; // NON-NLS
    }

    byte[] iconPixelData = new byte[w * h];
    Attributes iconItem = new Attributes();

    if ("PALETTE COLOR".equals(pmi)) { // NON-NLS
      BufferedImage bi =
          ImageConversion.convertTo(
              ImageConversion.toBufferedImage(thumbnail), BufferedImage.TYPE_BYTE_INDEXED);
      IndexColorModel cm = (IndexColorModel) bi.getColorModel();
      int[] lutDesc = {cm.getMapSize(), 0, 8};
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
      pmi = "MONOCHROME2";
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
