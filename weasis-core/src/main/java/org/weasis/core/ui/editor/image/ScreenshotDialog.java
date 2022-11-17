/*
 * Copyright (c) 2009-2018 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.weasis.core.api.gui.Insertable.BLOCK_SEPARATOR;
import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR;
import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR_LARGE;
import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR_SMALL;

import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.Objects;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class ScreenshotDialog<I extends ImageElement> extends JDialog {
  public static final String P_LAST_DIR = "screenshot.last.dir";
  static final String DICOM = "DICOM"; // NON-NLS

  public enum Format {
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

  private final ViewCanvas<I> viewCanvas;
  private final ButtonGroup buttonGroup = new ButtonGroup();
  private final JRadioButton viewRadio = new JRadioButton(Messages.getString("current.view"));
  private final JRadioButton imageRadio = new JRadioButton(Messages.getString("original.image"));
  private final JCheckBox paddingCheckBox =
      new JCheckBox(DICOM + StringUtil.SPACE + ActionW.IMAGE_PIX_PADDING.getTitle(), true);
  private final JCheckBox overlayCheckBox =
      new JCheckBox(DICOM + StringUtil.SPACE + ActionW.IMAGE_OVERLAY.getTitle(), true);
  private final JCheckBox shutterCheckBox =
      new JCheckBox(DICOM + StringUtil.SPACE + ActionW.IMAGE_SHUTTER.getTitle(), true);
  private final JCheckBox preservePixelCheckBox =
      new JCheckBox(
          Messages.getString("preserve.16.bit")
              + " ("
              + String.format(Messages.getString("not.supported.with.s"), Format.JPEG.title)
              + ")",
          false);

  private final JCheckBox anonymize = new JCheckBox(Messages.getString("anonymize"), true);
  private final JLabel labelSize =
      new JLabel(Messages.getString("size") + " (%)" + StringUtil.COLON);
  private final JSpinner spinner = new JSpinner();
  private final JLabel labelSizePix = new JLabel();

  public ScreenshotDialog(Window parent, String title, ViewCanvas<I> viewCanvas) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    spinner.setModel(new SpinnerNumberModel(100, 5, 200, 5));
    spinner
        .getModel()
        .addChangeListener(
            e -> setImageSize((Integer) ((SpinnerNumberModel) e.getSource()).getValue()));
    this.viewCanvas = Objects.requireNonNull(viewCanvas);
    initComponents();
    pack();
  }

  private void setImageSize(int val) {
    I imageElement = viewCanvas.getImage();
    PlanarImage image = imageElement == null ? null : imageElement.getImage(null);
    if (image != null) {
      float ratio = val / 100f;
      labelSizePix.setText(
          String.format(
              "(%dx%d px)", // NON-NLS
              Math.round(image.width() * ratio), Math.round(image.height() * ratio)));
    }
  }

  private void initComponents() {
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(GuiUtils.getEmptyBorder(BLOCK_SEPARATOR));

    buttonGroup.add(viewRadio);
    buttonGroup.add(imageRadio);
    viewRadio.setSelected(true);
    setImageRadioAction(false);

    viewRadio.addActionListener(e -> setImageRadioAction(false));
    imageRadio.addActionListener(e -> setImageRadioAction(true));

    setImageSize(100);
    boolean dicom = viewCanvas.getPanner() == null;
    panel.add(GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 0, ITEM_SEPARATOR_SMALL, viewRadio));
    panel.add(GuiUtils.getHorizontalBoxLayoutPanel(buildViewPanel(dicom)));
    panel.add(GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 0, ITEM_SEPARATOR_SMALL, imageRadio));
    panel.add(GuiUtils.getHorizontalBoxLayoutPanel(buildImagePanel(dicom)));

    JButton clipButton = new JButton(Messages.getString("clipboard"));
    clipButton.addActionListener(
        e -> {
          if (viewRadio.isSelected()) {
            ViewTransferHandler imageTransferHandler =
                new ViewTransferHandler(anonymize.isSelected());
            imageTransferHandler.exportToClipboard(
                viewCanvas.getJComponent(),
                Toolkit.getDefaultToolkit().getSystemClipboard(),
                TransferHandler.COPY);
          } else {
            I img = viewCanvas.getImage();
            if (img != null) {
              double ratio = (Integer) spinner.getValue() / 100.0;
              SimpleOpManager manager =
                  img.buildSimpleOpManager(
                      preservePixelCheckBox.isSelected(),
                      paddingCheckBox.isSelected(),
                      shutterCheckBox.isSelected(),
                      overlayCheckBox.isSelected(),
                      ratio);
              ImageTransferHandler imageTransferHandler = new ImageTransferHandler(manager);
              imageTransferHandler.exportToClipboard(
                  viewCanvas.getJComponent(),
                  Toolkit.getDefaultToolkit().getSystemClipboard(),
                  TransferHandler.COPY);
            }
          }
        });
    JButton saveButton = new JButton(Messages.getString("save"));
    saveButton.addActionListener(
        e -> {
          boolean mustBeReleased = false;
          PlanarImage result = null;
          if (viewRadio.isSelected()) {
            if (viewCanvas instanceof DefaultView2d<I> view2DPane) {
              RenderedImage imgP =
                  ViewTransferHandler.createComponentImage(view2DPane, anonymize.isSelected());
              result = ImageConversion.toMat(imgP);
            }
          } else {
            I img = viewCanvas.getImage();
            if (img != null) {
              double ratio = (Integer) spinner.getValue() / 100.0;
              SimpleOpManager manager =
                  img.buildSimpleOpManager(
                      preservePixelCheckBox.isSelected(),
                      paddingCheckBox.isSelected(),
                      shutterCheckBox.isSelected(),
                      overlayCheckBox.isSelected(),
                      ratio);
              PlanarImage inputImage = manager.getFirstNodeInputImage();
              if (inputImage != null) {
                PlanarImage rimage = manager.process();
                if (rimage == null) {
                  rimage = inputImage;
                }
                mustBeReleased = !Objects.equals(rimage, inputImage);
                result = rimage;
              }
            }
          }
          saveImageFile(result, mustBeReleased);
        });

    getRootPane().setDefaultButton(saveButton);
    saveButton.addActionListener(evt -> doClose());
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING, BLOCK_SEPARATOR, ITEM_SEPARATOR_LARGE, saveButton, clipButton));
    panel.add(GuiUtils.boxYLastElement(ITEM_SEPARATOR));

    this.setContentPane(panel);
  }

  private void saveImageFile(PlanarImage image, boolean mustBeReleased) {
    if (image != null) {
      String targetDirectoryPath = BundleTools.LOCAL_UI_PERSISTENCE.getProperty(P_LAST_DIR, "");
      JFileChooser fileChooser = new JFileChooser(targetDirectoryPath);
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fileChooser.setAcceptAllFileFilterUsed(false);
      FileFormatFilter filter = new FileFormatFilter(Format.PNG.extension, Format.PNG.title);
      fileChooser.addChoosableFileFilter(
          new FileFormatFilter(Format.JP2.extension, Format.JP2.title));
      fileChooser.addChoosableFileFilter(
          new FileFormatFilter(Format.JPEG.extension, Format.JPEG.title));
      fileChooser.addChoosableFileFilter(filter);
      fileChooser.addChoosableFileFilter(
          new FileFormatFilter(Format.TIFF.extension, Format.TIFF.title));
      fileChooser.setFileFilter(filter);

      if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION
          && fileChooser.getSelectedFile() != null) {
        File file = fileChooser.getSelectedFile();
        filter = (FileFormatFilter) fileChooser.getFileFilter();
        String extension = filter == null ? Format.PNG.extension : filter.getDefaultExtension();
        String extFile = "." + extension;
        String filename =
            file.getName().endsWith(extFile) ? file.getPath() : file.getPath() + extFile;

        File destinationFile = new File(filename);
        if (Format.PNG.extension.equals(extension)) {
          ImageProcessor.writePNG(image.toMat(), destinationFile);
        } else {
          MatOfInt map = new MatOfInt();
          if (Format.JPEG.extension.equals(extension)) {
            map.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, 90);
          }
          ImageProcessor.writeImage(image.toMat(), destinationFile, map);
        }
        if (mustBeReleased) {
          ImageConversion.releasePlanarImage(image);
        }
        if (destinationFile.canRead()) {
          BundleTools.LOCAL_UI_PERSISTENCE.setProperty(
              P_LAST_DIR, destinationFile.getParentFile().getPath());
        }
      }
    }
  }

  private void setImageRadioAction(boolean enable) {
    labelSize.setEnabled(enable);
    spinner.setEnabled(enable);
    labelSizePix.setEnabled(enable);
    preservePixelCheckBox.setEnabled(enable);
    paddingCheckBox.setEnabled(enable);
    shutterCheckBox.setEnabled(enable);
    overlayCheckBox.setEnabled(enable);

    anonymize.setEnabled(!enable);
  }

  private JPanel buildViewPanel(boolean dicom) {
    JPanel dataPanel = new JPanel();
    dataPanel.setLayout(new MigLayout("insets 0 25lp 30lp 10lp, fillx", "[grow 0]")); // NON-NLS
    if (dicom) {
      dataPanel.add(anonymize, GuiUtils.NEWLINE);
    }
    return dataPanel;
  }

  private JPanel buildImagePanel(boolean dicom) {
    JPanel dataPanel = new JPanel();
    dataPanel.setLayout(new MigLayout("insets 0 25lp 30lp 10lp, fillx", "[grow 0]")); // NON-NLS

    dataPanel.add(GuiUtils.getFlowLayoutPanel(labelSize, spinner, labelSizePix), GuiUtils.NEWLINE);
    dataPanel.add(preservePixelCheckBox, GuiUtils.NEWLINE);
    if (dicom) {
      dataPanel.add(paddingCheckBox, GuiUtils.NEWLINE);
      dataPanel.add(shutterCheckBox, GuiUtils.NEWLINE);
      dataPanel.add(overlayCheckBox, GuiUtils.NEWLINE);
    }
    return dataPanel;
  }

  private void doClose() {
    dispose();
  }

  public static <E extends ImageElement> void showDialog(ViewCanvas<E> selView) {
    if (selView != null) {
      ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(selView.getJComponent());
      ScreenshotDialog<E> dialog =
          new ScreenshotDialog<>(
              SwingUtilities.getWindowAncestor(selView.getJComponent()),
              ActionW.EXPORT_VIEW.getTitle(),
              selView);
      ColorLayerUI.showCenterScreen(dialog, layer);
    }
  }
}
