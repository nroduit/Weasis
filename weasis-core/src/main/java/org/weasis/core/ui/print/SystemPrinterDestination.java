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
import java.awt.Window;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.DialogOwner;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.util.PrintOptions;

/**
 * A printer of the operating system. Its papers and their printable area come from the print
 * service; the attribute set the native dialog returns (tray, duplex, driver resolution) is kept
 * and sent with the job. The page the preview shows is the one {@link PrinterJob#getPageFormat}
 * builds for the same attributes, so both sides agree by construction.
 */
public final class SystemPrinterDestination implements PrintDestination {
  private static final Logger LOGGER = LoggerFactory.getLogger(SystemPrinterDestination.class);

  private static final Set<String> LETTER_COUNTRIES = Set.of("US", "CA", "MX"); // NON-NLS
  private static final double SAME_SIZE_MM = 0.5;

  private PrintService service;
  private final boolean systemDefault;
  private PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

  public SystemPrinterDestination(PrintService service, boolean systemDefault) {
    this.service = Objects.requireNonNull(service);
    this.systemDefault = systemDefault;
  }

  /** The printers of the system, the default one first. */
  public static List<SystemPrinterDestination> lookup() {
    List<SystemPrinterDestination> list = new ArrayList<>();
    PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
    if (defaultService != null) {
      list.add(new SystemPrinterDestination(defaultService, true));
    }
    for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
      if (!s.equals(defaultService)) {
        list.add(new SystemPrinterDestination(s, false));
      }
    }
    return list;
  }

  /** The paper of the United States, Canada and Mexico is Letter; A4 everywhere else. */
  public static Paper localeDefault() {
    return LETTER_COUNTRIES.contains(Locale.getDefault().getCountry()) ? Paper.LETTER : Paper.A4;
  }

  /** The paper of a media size: the catalogue one, or one named after the media. */
  public static Optional<Paper> paperOf(MediaSizeName media) {
    Optional<Paper> known = Paper.fromMedia(media);
    if (known.isPresent()) {
      return known;
    }
    MediaSize size = MediaSize.getMediaSizeForName(media);
    if (size == null) {
      return Optional.empty();
    }
    return Optional.of(
        new Paper(media.toString(), size.getX(MediaSize.MM), size.getY(MediaSize.MM), null, media));
  }

  public PrintService getService() {
    return service;
  }

  public boolean isSystemDefault() {
    return systemDefault;
  }

  /** What the native dialog returned, or an empty set. */
  public PrintRequestAttributeSet getAttributes() {
    return attributes;
  }

  @Override
  public String id() {
    return "printer:" + service.getName(); // NON-NLS
  }

  @Override
  public String title() {
    return systemDefault
        ? Messages.format("PrintDialog.default_printer", service.getName())
        : service.getName();
  }

  @Override
  public List<Paper> papers() {
    List<Paper> papers = new ArrayList<>();
    if (supported(Media.class, null) instanceof Media[] medias) {
      for (Media media : medias) {
        if (media instanceof MediaSizeName name) {
          paperOf(name).ifPresent(p -> addUnique(papers, p));
        }
      }
    }
    if (papers.isEmpty()) {
      papers.addAll(Paper.CATALOGUE);
    }
    Paper first = defaultPaper();
    papers.removeIf(p -> sameSize(p, first));
    papers.addFirst(first);
    return papers;
  }

  @Override
  public Paper defaultPaper() {
    try {
      if (service.getDefaultAttributeValue(Media.class) instanceof MediaSizeName name) {
        Optional<Paper> paper = paperOf(name);
        if (paper.isPresent()) {
          return paper.get();
        }
      }
    } catch (RuntimeException e) {
      LOGGER.debug("No default media for {}", service.getName(), e);
    }
    return localeDefault();
  }

  @Override
  public Color defaultBackground() {
    return Color.WHITE;
  }

  /** The paper the attribute set names, after the native dialog. */
  public Optional<Paper> paper() {
    return attributes.get(Media.class) instanceof MediaSizeName name
        ? paperOf(name)
        : Optional.empty();
  }

  /** The orientation the attribute set names, after the native dialog. */
  public Optional<Orientation> orientation() {
    Attribute value = attributes.get(OrientationRequested.class);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(
        OrientationRequested.PORTRAIT.equals(value) ? Orientation.PORTRAIT : Orientation.LANDSCAPE);
  }

  /** The copies the attribute set names, after the native dialog. */
  public OptionalInt copies() {
    return attributes.get(Copies.class) instanceof Copies copies
        ? OptionalInt.of(copies.getValue())
        : OptionalInt.empty();
  }

  @Override
  public PageSpec pageSpec(Paper paper, Orientation orientation, Color background, Color border) {
    PageFormat format = job().getPageFormat(attributes(paper, orientation, 1));
    PageSpec spec = PageSpec.of(format, background, border);
    return new PageSpec(
        paper, spec.orientation(), spec.margins(), spec.gutterMm(), background, border);
  }

  @Override
  public OptionalInt resolutionDpi() {
    OptionalInt fromJob = PrintResolution.fromAttributes(attributes);
    return fromJob.isPresent() ? fromJob : PrintResolution.fromService(service);
  }

  /**
   * The attributes of a job on this paper: what the native dialog returned, with the media, the
   * orientation and the copies set. A printable area returned for another media is dropped; when
   * neither the dialog nor the service gives one, the default margins apply.
   */
  public PrintRequestAttributeSet attributes(Paper paper, Orientation orientation, int copies) {
    PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet(attributes);
    MediaSizeName media = mediaOf(paper);
    if (!media.equals(attributes.get(Media.class))) {
      aset.remove(MediaPrintableArea.class);
    }
    aset.add(media);
    aset.add(
        orientation == Orientation.LANDSCAPE
            ? OrientationRequested.LANDSCAPE
            : OrientationRequested.PORTRAIT);
    aset.add(new Copies(Math.max(1, copies)));
    if (aset.get(MediaPrintableArea.class) == null) {
      // Always set: without it the job falls back to one-inch margins, not to the device's area
      aset.add(servicePrintableArea(aset, paper).orElseGet(() -> defaultPrintableArea(paper)));
    }
    return aset;
  }

  /**
   * Shows the native print dialog for this paper, orientation and copies. When the user confirms,
   * the service and the attributes become those of the dialog and {@code true} is returned; the
   * caller then reads {@link #paper()}, {@link #orientation()} and {@link #copies()} back.
   */
  public boolean showNativeDialog(Window owner, Paper paper, Orientation orientation, int copies) {
    PrinterJob job = job();
    PrintRequestAttributeSet aset = attributes(paper, orientation, copies);
    if (owner != null) {
      aset.add(new DialogOwner(owner));
    }
    if (!job.printDialog(aset)) {
      return false;
    }
    aset.remove(DialogOwner.class);
    PrintService chosen = job.getPrintService();
    if (chosen != null) {
      service = chosen;
    }
    attributes = aset;
    return true;
  }

  @Override
  public void print(PageSource pages, PageSpec spec, PrintOptions options, int copies)
      throws PrinterException {
    new PagePrintable(pages, options)
        .print(service, attributes(spec.paper(), spec.orientation(), copies));
  }

  @Override
  public String toString() {
    return title();
  }

  private PrinterJob job() {
    PrinterJob job = PrinterJob.getPrinterJob();
    try {
      job.setPrintService(service);
    } catch (PrinterException e) {
      LOGGER.warn("Cannot select {}", service.getName(), e);
    }
    return job;
  }

  private Object supported(Class<? extends Attribute> category, PrintRequestAttributeSet aset) {
    try {
      return service.getSupportedAttributeValues(category, null, aset);
    } catch (RuntimeException e) {
      LOGGER.debug("{} not reported by {}", category.getSimpleName(), service.getName(), e);
      return null;
    }
  }

  /**
   * The printable area the service reports for the media of {@code aset}, when it fits the paper.
   */
  private Optional<MediaPrintableArea> servicePrintableArea(
      PrintRequestAttributeSet aset, Paper paper) {
    if (supported(MediaPrintableArea.class, aset) instanceof MediaPrintableArea[] areas
        && areas.length > 0
        && fits(areas[0], paper)) {
      return Optional.of(areas[0]);
    }
    return Optional.empty();
  }

  private static boolean fits(MediaPrintableArea area, Paper paper) {
    int mm = MediaPrintableArea.MM;
    return area.getX(mm) + area.getWidth(mm) <= paper.widthMm() + SAME_SIZE_MM
        && area.getY(mm) + area.getHeight(mm) <= paper.heightMm() + SAME_SIZE_MM;
  }

  static MediaSizeName mediaOf(Paper paper) {
    if (paper.media() != null) {
      return paper.media();
    }
    return MediaSize.findMedia((float) paper.widthMm(), (float) paper.heightMm(), MediaSize.MM);
  }

  static MediaPrintableArea defaultPrintableArea(Paper paper) {
    float margin = (float) PageSpec.DEFAULT_MARGIN_MM;
    return new MediaPrintableArea(
        margin,
        margin,
        (float) (paper.widthMm() - 2 * margin),
        (float) (paper.heightMm() - 2 * margin),
        MediaPrintableArea.MM);
  }

  private static boolean sameSize(Paper a, Paper b) {
    return Math.abs(a.widthMm() - b.widthMm()) <= SAME_SIZE_MM
        && Math.abs(a.heightMm() - b.heightMm()) <= SAME_SIZE_MM;
  }

  private static void addUnique(List<Paper> papers, Paper paper) {
    if (papers.stream().noneMatch(p -> sameSize(p, paper))) {
      papers.add(paper);
    }
  }
}
