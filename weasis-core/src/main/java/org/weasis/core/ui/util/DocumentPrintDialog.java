/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.print.PageHeader;
import org.weasis.core.ui.print.PagePreview;
import org.weasis.core.ui.print.PagePrintable;
import org.weasis.core.ui.print.PageSpec;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.ui.print.SystemPrinterDestination;
import org.weasis.core.ui.print.TextSize;
import org.weasis.core.util.StringUtil;

/**
 * The print dialog of a document that lays itself out on pages, such as a report or a waveform: a
 * system printer, its paper, the orientation, the copies, how much identity leaves with the pages,
 * and a preview of the pages as they will be printed.
 */
public class DocumentPrintDialog extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPrintDialog.class);

  private static final String PREF = "print.document."; // NON-NLS
  private static final String PREF_DESTINATION = PREF + "destination"; // NON-NLS
  private static final String KEY_PAPER = ".paper"; // NON-NLS
  private static final String KEY_ORIENTATION = ".orientation"; // NON-NLS

  /** What is printed: the document lays itself out on the pages of a format. */
  public interface Document {

    /**
     * The pages of the document on this page format; called on the event dispatch thread, under the
     * identity profile chosen in the dialog, as are the pages when they print. A document that
     * holds text made beforehand builds it again here, so that the profile applies.
     */
    Pageable paginate(PageFormat format);

    /** The orientation offered when none is remembered for the printer. */
    default Orientation defaultOrientation() {
      return Orientation.PORTRAIT;
    }

    /** The name of the print job, or {@code null} for the default one. */
    default String jobName() {
      return null;
    }

    /**
     * The settings of the document itself, shown under those of the dialog; {@code onChange} is run
     * when one of them changes, and the document is laid out again.
     */
    default List<Option> options(Runnable onChange) {
      return List.of();
    }
  }

  /**
   * A setting of a document: its component and its label, {@code null} for a component that says
   * what it is, such as a check box.
   */
  public record Option(JLabel label, JComponent component) {}

  /**
   * A one-page document showing a captured image, fitted and centered inside the margins. The image
   * is captured again for each layout, under the identity profile of the dialog.
   *
   * @param capture gives the image, or {@code null} when there is nothing to print
   */
  public static Document ofImage(Supplier<BufferedImage> capture, String jobName) {
    return ofImage(capture, () -> null, jobName);
  }

  /**
   * As {@link #ofImage(Supplier, String)}, with a header line above the image when {@code header}
   * gives a text: that text, and the page number.
   *
   * @param header gives the text of the header line, or {@code null} for a page without one
   */
  public static Document ofImage(
      Supplier<BufferedImage> capture, Supplier<String> header, String jobName) {
    return new Document() {
      @Override
      public Pageable paginate(PageFormat format) {
        BufferedImage image = capture.get();
        String text = header.get();
        Book book = new Book();
        if (image != null) {
          book.append((g, f, index) -> printImage((Graphics2D) g, f, index, image, text), format);
        }
        return book;
      }

      @Override
      public Orientation defaultOrientation() {
        BufferedImage image = capture.get();
        return image != null && image.getWidth() > image.getHeight()
            ? Orientation.LANDSCAPE
            : Orientation.PORTRAIT;
      }

      @Override
      public String jobName() {
        return jobName;
      }
    };
  }

  private static int printImage(
      Graphics2D g, PageFormat format, int index, BufferedImage image, String header) {
    if (index > 0) {
      return Printable.NO_SUCH_PAGE;
    }
    // A printer that reports no margin still gets a page with a frame
    double min = Paper.mmToPoints(PageSpec.MIN_MARGIN_MM);
    double left = Math.max(min, format.getImageableX());
    double top = Math.max(min, format.getImageableY());
    double right =
        Math.min(format.getWidth() - min, format.getImageableX() + format.getImageableWidth());
    double bottom =
        Math.min(format.getHeight() - min, format.getImageableY() + format.getImageableHeight());
    if (header != null) {
      double band = Paper.mmToPoints(PageSpec.HEADER_MM);
      g.setColor(Color.BLACK);
      PageHeader.draw(
          g,
          new Rectangle2D.Double(left, top, right - left, band),
          Paper.mmToPoints(PageHeader.SPACING_MM),
          (float) TextSize.NORMAL.points(),
          header,
          0,
          1);
      top += band;
    }
    double scale = Math.min((right - left) / image.getWidth(), (bottom - top) / image.getHeight());
    if (scale <= 0) {
      return Printable.PAGE_EXISTS;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.translate(
          left + (right - left - image.getWidth() * scale) / 2,
          top + (bottom - top - image.getHeight() * scale) / 2);
      g2.scale(scale, scale);
      g2.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g2.drawImage(image, 0, 0, null);
    } finally {
      g2.dispose();
    }
    return Printable.PAGE_EXISTS;
  }

  private final transient Document document;
  private final transient List<SystemPrinterDestination> destinations =
      new ArrayList<>(SystemPrinterDestination.lookup());
  private final JComboBox<SystemPrinterDestination> destinationComboBox = new JComboBox<>();
  private final JButton settingsButton =
      new JButton(Messages.getString("PrintDialog.printer_settings"));
  private final JComboBox<Paper> paperComboBox = new JComboBox<>();
  private final JComboBox<Orientation> orientationComboBox = new JComboBox<>(Orientation.values());
  private final JSpinner copiesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
  private final MaskingProfileSelector maskingProfile = new MaskingProfileSelector();
  private final PagePreview preview = new PagePreview();
  private final JLabel pageLabel = new JLabel();
  private final JLabel messageLabel = new JLabel();
  private final JButton previousButton = new JButton("◀");
  private final JButton nextButton = new JButton("▶");
  private final JButton printButton = new JButton(Messages.common("print"));

  private transient Pageable pages;
  private int pageIndex;
  private boolean updating;

  public DocumentPrintDialog(Window parent, String title, Document document) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.document = document;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    initComponents();
    restorePreferences();
    pack();
  }

  /** Opens the dialog over the window of {@code parent}. */
  public static void show(Component parent, String title, Document document) {
    Window window =
        parent == null
            ? GuiUtils.getUICore().getApplicationWindow()
            : SwingUtilities.getWindowAncestor(parent);
    DocumentPrintDialog dialog = new DocumentPrintDialog(window, title, document);
    dialog.setLocationRelativeTo(window);
    dialog.setVisible(true);
  }

  private void initComponents() {
    JPanel settings =
        new JPanel(
            new MigLayout(
                "insets 0, wrap 4", // NON-NLS
                "[trailing]rel[grow,fill,120lp::]unrel[trailing]rel[grow,fill,120lp::]")); // NON-NLS

    destinationComboBox.setModel(
        new DefaultComboBoxModel<>(destinations.toArray(SystemPrinterDestination[]::new)));
    destinationComboBox.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Object text = value instanceof SystemPrinterDestination d ? d.title() : value;
            return super.getListCellRendererComponent(list, text, index, selected, focus);
          }
        });
    destinationComboBox.addActionListener(e -> applyDestination());
    settingsButton.addActionListener(e -> printerSettings());
    settings.add(label("PrintDialog.destination"));
    settings.add(destinationComboBox, "span 2"); // NON-NLS
    settings.add(settingsButton, "growx 0"); // NON-NLS

    orientationComboBox.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Object text =
                value instanceof Orientation o
                    ? Messages.getString(
                        o == Orientation.LANDSCAPE
                            ? "PrintDialog.landscape"
                            : "PrintDialog.portrait")
                    : value;
            return super.getListCellRendererComponent(list, text, index, selected, focus);
          }
        });
    settings.add(label("PrintDialog.paper"));
    settings.add(paperComboBox);
    settings.add(new JLabel(Messages.common("orientation") + StringUtil.COLON));
    settings.add(orientationComboBox);
    settings.add(label("PrintDialog.copies"));
    settings.add(copiesSpinner, "growx 0, width 70lp, wrap"); // NON-NLS
    for (Option option : document.options(this::layoutChanged)) {
      if (option.label() == null) {
        settings.add(option.component(), "skip 1, span 3, growx 0"); // NON-NLS
      } else {
        settings.add(option.label());
        settings.add(option.component(), "span 3, growx 0"); // NON-NLS
      }
    }
    settings.add(maskingProfile.createLabel());
    settings.add(maskingProfile, "span 3, growx 0"); // NON-NLS

    paperComboBox.addActionListener(e -> layoutChanged());
    orientationComboBox.addActionListener(e -> layoutChanged());
    maskingProfile.addActionListener(e -> layoutChanged());
    previousButton.addActionListener(e -> turnPage(-1));
    nextButton.addActionListener(e -> turnPage(1));

    JPanel side = new JPanel(new MigLayout("insets 0, wrap 1", "[grow,fill]")); // NON-NLS
    side.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING, 4, 0, pageLabel, previousButton, nextButton));
    side.add(messageLabel, "gaptop unrel"); // NON-NLS

    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 10lp 15lp 10lp 15lp, wrap 2", // NON-NLS
                "[grow,fill][170lp!]", // NON-NLS
                "[]unrel[grow,fill]unrel[]")); // NON-NLS
    panel.add(settings, "span 2, growx"); // NON-NLS
    panel.add(preview, "grow"); // NON-NLS
    panel.add(side, "aligny top, growy 0"); // NON-NLS

    JButton cancelButton = new JButton(Messages.common("cancel"));
    cancelButton.addActionListener(e -> cancel());
    printButton.addActionListener(e -> printAction());
    getRootPane().setDefaultButton(printButton);
    getRootPane()
        .registerKeyboardAction(
            e -> cancel(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    panel.add(
        GuiUtils.createHelpButton("share-export/print/"), "span 2, split 3, growx 0"); // NON-NLS
    panel.add(printButton, "gapbefore push, growx 0"); // NON-NLS
    panel.add(cancelButton, "gapbefore 15lp, growx 0"); // NON-NLS
    setContentPane(panel);

    if (destinations.isEmpty()) {
      printButton.setEnabled(false);
      settingsButton.setEnabled(false);
      previousButton.setEnabled(false);
      nextButton.setEnabled(false);
      messageLabel.setText(Messages.getString("PrintDialog.no_destination"));
    }
  }

  private static JLabel label(String key) {
    return new JLabel(Messages.getString(key) + StringUtil.COLON);
  }

  private static WProperties preferences() {
    return GuiUtils.getUICore().getLocalPersistence();
  }

  private SystemPrinterDestination selectedDestination() {
    return destinationComboBox.getSelectedItem() instanceof SystemPrinterDestination d ? d : null;
  }

  private Paper selectedPaper() {
    return paperComboBox.getSelectedItem() instanceof Paper p
        ? p
        : SystemPrinterDestination.localeDefault();
  }

  private Orientation selectedOrientation() {
    return orientationComboBox.getSelectedItem() instanceof Orientation o
        ? o
        : Orientation.PORTRAIT;
  }

  private int copies() {
    return ((Number) copiesSpinner.getValue()).intValue();
  }

  private void restorePreferences() {
    String last = preferences().getProperty(PREF_DESTINATION, "");
    for (SystemPrinterDestination destination : destinations) {
      if (destination.id().equals(last)) {
        destinationComboBox.setSelectedItem(destination);
        return;
      }
    }
    applyDestination();
  }

  private void savePreferences() {
    SystemPrinterDestination destination = selectedDestination();
    if (destination == null) {
      return;
    }
    WProperties prefs = preferences();
    prefs.setProperty(PREF_DESTINATION, destination.id());
    String key = PREF + destination.id();
    prefs.setProperty(key + KEY_PAPER, selectedPaper().id());
    prefs.setProperty(key + KEY_ORIENTATION, selectedOrientation().name());
  }

  /** Fills the paper list and the remembered settings of the selected printer. */
  private void applyDestination() {
    SystemPrinterDestination destination = selectedDestination();
    if (destination == null) {
      return;
    }
    WProperties prefs = preferences();
    String key = PREF + destination.id();
    updating = true;
    try {
      List<Paper> papers = destination.papers();
      paperComboBox.setModel(new DefaultComboBoxModel<>(papers.toArray(Paper[]::new)));
      String paperId = prefs.getProperty(key + KEY_PAPER);
      selectPaper(
          papers.stream()
              .filter(p -> p.id().equals(paperId))
              .findFirst()
              .orElseGet(destination::defaultPaper));
      orientationComboBox.setSelectedItem(orientationOf(prefs.getProperty(key + KEY_ORIENTATION)));
    } finally {
      updating = false;
    }
    layoutChanged();
  }

  private Orientation orientationOf(String name) {
    try {
      return name == null ? document.defaultOrientation() : Orientation.valueOf(name);
    } catch (IllegalArgumentException e) {
      return document.defaultOrientation();
    }
  }

  private void selectPaper(Paper paper) {
    DefaultComboBoxModel<Paper> model = (DefaultComboBoxModel<Paper>) paperComboBox.getModel();
    if (model.getIndexOf(paper) < 0) {
      model.addElement(paper);
    }
    paperComboBox.setSelectedItem(paper);
  }

  /** Opens the native dialog of the printer and reads its choices back. */
  private void printerSettings() {
    SystemPrinterDestination printer = selectedDestination();
    if (printer == null
        || !printer.showNativeDialog(this, selectedPaper(), selectedOrientation(), copies())) {
      return;
    }
    // The user may have picked another printer there: keep one entry per service
    DefaultComboBoxModel<SystemPrinterDestination> model =
        (DefaultComboBoxModel<SystemPrinterDestination>) destinationComboBox.getModel();
    String name = printer.getService().getName();
    for (SystemPrinterDestination other : List.copyOf(destinations)) {
      if (other != printer && other.getService().getName().equals(name)) {
        destinations.remove(other);
        model.removeElement(other);
      }
    }
    updating = true;
    try {
      printer.paper().ifPresent(this::selectPaper);
      printer.orientation().ifPresent(orientationComboBox::setSelectedItem);
      printer.copies().ifPresent(copiesSpinner::setValue);
    } finally {
      updating = false;
    }
    destinationComboBox.repaint();
    layoutChanged();
  }

  /** Another printer, paper or orientation: the document is laid out again. */
  private void layoutChanged() {
    if (updating) {
      return;
    }
    SystemPrinterDestination destination = selectedDestination();
    if (destination == null) {
      return;
    }
    pages = null;
    try {
      PageFormat format = destination.pageFormat(selectedPaper(), selectedOrientation());
      pages = maskingProfile.runMasked(() -> document.paginate(format));
    } catch (RuntimeException e) {
      LOGGER.error("Laying out the pages to print", e);
    }
    pageIndex = 0;
    updatePreview();
  }

  private void turnPage(int by) {
    if (pages != null) {
      pageIndex = Math.clamp(pageIndex + by, 0, Math.max(0, pages.getNumberOfPages() - 1));
      updatePreview();
    }
  }

  private void updatePreview() {
    int count = pages == null ? 0 : pages.getNumberOfPages();
    printButton.setEnabled(count > 0);
    previousButton.setEnabled(pageIndex > 0);
    nextButton.setEnabled(pageIndex < count - 1);
    if (count <= 0) {
      pageLabel.setText("");
      messageLabel.setText(Messages.getString("PrintDialog.preview_failed"));
      return;
    }
    messageLabel.setText("");
    pageLabel.setText(Messages.format("PrintDialog.page_of", pageIndex + 1, count));
    PageSpec spec = PageSpec.of(selectedPaper(), selectedOrientation(), Color.WHITE);
    // The document paints Swing components: rendered here, on the event dispatch thread
    double pixelsPerMm = preview.pixelsPerMm(spec);
    BufferedImage page = maskingProfile.runMasked(() -> render(pixelsPerMm));
    preview.request(spec, _ -> page);
  }

  private BufferedImage render(double pixelsPerMm) {
    PageFormat format = pages.getPageFormat(pageIndex);
    double scale = pixelsPerMm * Paper.MM_PER_INCH / Paper.POINTS_PER_INCH;
    BufferedImage image =
        new BufferedImage(
            Math.max(1, (int) Math.round(format.getWidth() * scale)),
            Math.max(1, (int) Math.round(format.getHeight() * scale)),
            BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.scale(scale, scale);
      g.setColor(Color.BLACK);
      pages.getPrintable(pageIndex).print(g, format, pageIndex);
    } catch (PrinterException | RuntimeException e) {
      LOGGER.error("Print preview", e);
      messageLabel.setText(Messages.getString("PrintDialog.preview_failed"));
    } finally {
      g.dispose();
    }
    return image;
  }

  private void printAction() {
    SystemPrinterDestination printer = selectedDestination();
    if (printer == null) {
      return;
    }
    Paper paper = selectedPaper();
    Orientation orientation = selectedOrientation();
    int copies = copies();
    savePreferences();
    closeDialog();
    try {
      PrinterJob job = PrinterJob.getPrinterJob();
      job.setPrintService(printer.getService());
      if (StringUtil.hasText(document.jobName())) {
        job.setJobName(document.jobName());
      }
      PrintRequestAttributeSet attributes = printer.attributes(paper, orientation, copies);
      // The job lays out and renders the pages on this thread: the mask is bound for all of it
      maskingProfile.runMaskedAction(
          () -> {
            try {
              // Laid out for the job itself, so the pages are those of the format it prints
              job.setPageable(document.paginate(job.getPageFormat(attributes)));
              PagePrintable.print(job, attributes);
            } catch (PrinterException e) {
              throw new IllegalStateException(e);
            }
          });
    } catch (PrinterException | RuntimeException e) {
      LOGGER.error("Print to {}", printer.title(), e);
      JOptionPane.showMessageDialog(
          GuiUtils.getUICore().getApplicationWindow(),
          Messages.getString("PrintDialog.print_failed"),
          getTitle(),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void cancel() {
    savePreferences();
    closeDialog();
  }

  private void closeDialog() {
    preview.close();
    dispose();
  }
}
