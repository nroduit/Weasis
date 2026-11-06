/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.core.util.StringUtil;

/**
 * A PDF file as a destination: every page rendered at the chosen resolution and written lossless by
 * {@link PdfWriter}, without the virtual printer of the operating system.
 */
public final class PdfFileDestination implements PrintDestination {
  private static final Logger LOGGER = LoggerFactory.getLogger(PdfFileDestination.class);

  public static final String ID = "file:pdf"; // NON-NLS
  public static final int DEFAULT_DPI = 300;
  public static final List<Integer> RESOLUTIONS = List.of(150, 200, 300, 400, 600);

  private static final String PREF_DIR = "print.file.pdf.dir"; // NON-NLS
  private static final String EXTENSION = ".pdf"; // NON-NLS

  private Path file;
  private int resolution = DEFAULT_DPI;

  public Optional<Path> file() {
    return Optional.ofNullable(file);
  }

  public void setFile(Path file) {
    this.file = file == null ? null : withExtension(file);
  }

  public void setResolution(int dpi) {
    this.resolution = Math.clamp(dpi, PrintResolution.MIN_DPI, PrintResolution.maxDpi());
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return Messages.getString("PrintDialog.pdf_file");
  }

  @Override
  public List<Paper> papers() {
    List<Paper> papers = new java.util.ArrayList<>(Paper.CATALOGUE);
    Paper first = defaultPaper();
    papers.remove(first);
    papers.addFirst(first);
    return papers;
  }

  @Override
  public Paper defaultPaper() {
    return SystemPrinterDestination.localeDefault();
  }

  @Override
  public Color defaultBackground() {
    return Color.WHITE;
  }

  @Override
  public PageSpec pageSpec(Paper paper, Orientation orientation, Color background, Color border) {
    return new PageSpec(
        paper,
        orientation,
        PageSpec.Margins.uniform(PageSpec.DEFAULT_MARGIN_MM),
        PageSpec.DEFAULT_GUTTER_MM,
        background,
        border);
  }

  @Override
  public OptionalInt resolutionDpi() {
    return OptionalInt.of(resolution);
  }

  @Override
  public Optional<JComponent> optionsComponent(Runnable onChange) {
    JTextField fileField = new JTextField(file == null ? "" : file.toString(), 28);
    fileField.setEditable(false);
    JButton browse = new JButton(Messages.getString("PrintDialog.browse"));
    browse.addActionListener(
        e -> {
          if (chooseFile(browse)) {
            fileField.setText(file.toString());
          }
        });
    JComboBox<Integer> dpiCombo = new JComboBox<>(RESOLUTIONS.toArray(Integer[]::new));
    dpiCombo.setSelectedItem(resolution);
    dpiCombo.addActionListener(
        e -> {
          if (dpiCombo.getSelectedItem() instanceof Integer dpi) {
            setResolution(dpi);
            onChange.run();
          }
        });
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx", "[right]rel[grow,fill]rel[]")); // NON-NLS
    panel.add(new JLabel(Messages.getString("PrintDialog.file") + StringUtil.COLON));
    panel.add(fileField);
    panel.add(browse, "wrap"); // NON-NLS
    panel.add(new JLabel(Messages.getString("PrintDialog.resolution_label") + StringUtil.COLON));
    panel.add(dpiCombo, "growx 0"); // NON-NLS
    return Optional.of(panel);
  }

  /** Asks for the file to write; returns whether one was chosen. */
  public boolean chooseFile(Component parent) {
    WProperties prefs = GuiUtils.getUICore().getLocalPersistence();
    JFileChooser chooser = new JFileChooser(prefs.getProperty(PREF_DIR));
    chooser.setDialogType(JFileChooser.SAVE_DIALOG);
    chooser.setFileFilter(new FileNameExtensionFilter("PDF", "pdf")); // NON-NLS
    if (file != null) {
      chooser.setSelectedFile(file.toFile());
    }
    if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION
        || chooser.getSelectedFile() == null) {
      return false;
    }
    setFile(chooser.getSelectedFile().toPath());
    if (file.getParent() != null) {
      prefs.setProperty(PREF_DIR, file.getParent().toString());
    }
    return true;
  }

  @Override
  public void print(PageSource pages, PageSpec spec, PrintOptions options, int copies)
      throws PrinterException {
    if (file == null) {
      throw new PrinterException(Messages.getString("PrintDialog.no_file"));
    }
    try {
      write(pages, spec, options, resolution, file);
    } catch (IOException e) {
      PrinterException failure = new PrinterException(e.getMessage());
      failure.initCause(e);
      throw failure;
    }
  }

  /** Writes every page of the source at {@code dpi} into {@code file}. */
  public static void write(
      PageSource pages, PageSpec spec, PrintOptions options, int dpi, Path file)
      throws IOException {
    double pixelsPerMm = PrintResolution.pixelsPerMm(dpi);
    if (file.getParent() != null) {
      Files.createDirectories(file.getParent());
    }
    int count = pages.pageCount();
    try (PdfWriter writer = new PdfWriter(file)) {
      for (int i = 0; i < count; i++) {
        BufferedImage page =
            PageRenderer.renderPage(pages.page(i), spec, pixelsPerMm, options, false, i, count);
        writer.addPage(page, Paper.mmToPoints(spec.widthMm()), Paper.mmToPoints(spec.heightMm()));
      }
    }
    LOGGER.info("Wrote {} page(s) at {} dpi to {}", count, dpi, file);
  }

  private static Path withExtension(Path path) {
    String name = path.getFileName().toString();
    return name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)
        ? path
        : path.resolveSibling(name + EXTENSION);
  }

  @Override
  public String toString() {
    return title();
  }
}
