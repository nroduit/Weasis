/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.print.PageFigures;
import org.weasis.core.ui.print.PagePreview;
import org.weasis.core.ui.print.PageRenderer;
import org.weasis.core.ui.print.PageSource;
import org.weasis.core.ui.print.PageSpec;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.ui.print.PdfFileDestination;
import org.weasis.core.ui.print.PrintDestination;
import org.weasis.core.ui.print.PrintDestinations;
import org.weasis.core.ui.print.PrintJob;
import org.weasis.core.ui.print.PrintResolution;
import org.weasis.core.ui.print.SeriesPages;
import org.weasis.core.ui.print.SystemPrinterDestination;
import org.weasis.core.ui.print.TextSize;
import org.weasis.core.util.StringUtil;

/**
 * The print dialog: a destination, its paper and orientation, the content (the layout, one pane, or
 * the frames of a series on a grid of pages) and the look of the cells, with a preview of the pages
 * as the destination will receive them. The native dialog of the system is a secondary step,
 * reached through <i>Printer settings</i>.
 *
 * @author Marcelo Porto (marcelo@animati.com.br)
 * @author Nicolas Roduit
 */
public class PrintDialog<I extends ImageElement> extends JDialog {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrintDialog.class);

  private static final String PREF_DESTINATION = "print.destination"; // NON-NLS
  private static final String PREF_ANNOTATIONS = "print.annotations"; // NON-NLS
  private static final String PREF_CENTER = "print.center"; // NON-NLS
  private static final String PREF_HEADER = "print.header"; // NON-NLS
  private static final String PREF_GRID = "print.series.grid"; // NON-NLS
  private static final String KEY_PAPER = ".paper"; // NON-NLS
  private static final String KEY_ORIENTATION = ".orientation"; // NON-NLS
  private static final String KEY_TEXT_SIZE = ".text_size"; // NON-NLS
  private static final String KEY_BACKGROUND = ".background"; // NON-NLS

  /** The grids offered for the frames of a series, columns by rows. */
  private static final List<String> GRIDS =
      List.of("1x1", "2x1", "2x2", "3x2", "3x3", "4x3", "4x4", "5x4", "6x5"); // NON-NLS

  private final ImageViewerEventManager<I> eventManager;
  private final List<PrintDestination> destinations = PrintDestinations.all();
  private final JComboBox<PrintDestination> destinationComboBox = new JComboBox<>();
  private final JButton settingsButton =
      new JButton(Messages.getString("PrintDialog.printer_settings"));
  private final JComboBox<Paper> paperComboBox = new JComboBox<>();
  private final JComboBox<Orientation> orientationComboBox = new JComboBox<>(Orientation.values());
  private final JSpinner copiesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
  private final JComboBox<String> backgroundComboBox =
      new JComboBox<>(
          new String[] {
            Messages.getString("PrintDialog.white"), Messages.getString("PrintDialog.black")
          });
  private final JRadioButton currentLayoutRadio =
      new JRadioButton(Messages.getString("PrintDialog.current_layout"));
  private final JRadioButton selectedViewRadio =
      new JRadioButton(Messages.getString("PrintDialog.selected_view"));
  private final JRadioButton seriesRadio =
      new JRadioButton(Messages.getString("PrintDialog.series"));
  private final JComboBox<String> gridComboBox = new JComboBox<>(GRIDS.toArray(String[]::new));
  private final JSpinner stepSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
  private final JSpinner firstSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
  private final JSpinner lastSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
  private final JPanel seriesRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
  private final JComboBox<Object> textSizeComboBox = new JComboBox<>();
  private final JComboBox<String> positionComboBox =
      new JComboBox<>(
          new String[] {
            Messages.getString("PrintDialog.center"), Messages.getString("PrintDialog.top")
          });
  private final JCheckBox annotationsCheckBox =
      new JCheckBox(Messages.getString("PrintDialog.annotate"));
  private final JCheckBox headerCheckBox = new JCheckBox(Messages.getString("PrintDialog.header"));
  private final MaskingProfileSelector maskingProfile = new MaskingProfileSelector();
  private final PagePreview preview = new PagePreview();
  private final JLabel figuresLabel = new JLabel();
  private final JLabel resolutionLabel = new JLabel();
  private final JLabel pageLabel = new JLabel();
  private final JLabel framesLabel = new JLabel();
  private final JButton previousButton = new JButton("◀");
  private final JButton nextButton = new JButton("▶");
  private final JButton printButton = new JButton(Messages.common("print"));

  private final JPanel optionsHolder = new JPanel(new BorderLayout());
  private final String destinationPrefix;

  private ExportLayout<I> layoutAll;
  private ExportLayout<I> layoutSelected;
  private final ViewCanvas<I> selectedView;
  private final int frameCount;
  private SeriesPages<I> seriesPages;
  private int pageIndex;
  private boolean updating;

  public PrintDialog(Window parent, String title, ImageViewerEventManager<I> eventManager) {
    this(parent, title, eventManager, null);
  }

  /**
   * @param destinationPrefix when not {@code null}, the dialog opens on the destination last used
   *     among those whose id starts with it, else on the first of them: the DICOM printers, say
   */
  public PrintDialog(
      Window parent,
      String title,
      ImageViewerEventManager<I> eventManager,
      String destinationPrefix) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.eventManager = eventManager;
    this.destinationPrefix = destinationPrefix;
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            cancel();
          }
        });

    ImageViewerPlugin<I> container = eventManager.getSelectedView2dContainer();
    selectedView = eventManager.getSelectedViewPane();
    if (container != null && !container.getImagePanels().isEmpty()) {
      layoutAll = new ExportLayout<>(container);
    }
    if (selectedView != null) {
      layoutSelected = new ExportLayout<>(selectedView);
    }
    frameCount = selectedView == null ? 0 : SeriesPages.frames(selectedView).size();

    initComponents();
    restorePreferences();
    pack();
  }

  private void initComponents() {
    JPanel settings =
        new JPanel(
            new MigLayout(
                "insets 0, wrap 4, hidemode 3", // NON-NLS
                "[trailing]rel[grow,fill,120lp::]unrel[trailing]rel[grow,fill,120lp::]")); // NON-NLS

    destinationComboBox.setModel(
        new DefaultComboBoxModel<>(destinations.toArray(PrintDestination[]::new)));
    destinationComboBox.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Object text = value instanceof PrintDestination d ? d.title() : value;
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
    settings.add(copiesSpinner, "growx 0, width 70lp"); // NON-NLS
    settings.add(label("PrintDialog.background"));
    settings.add(backgroundComboBox);

    DefaultComboBoxModel<Object> sizes = new DefaultComboBoxModel<>();
    sizes.addElement(Messages.common("automatic"));
    for (TextSize size : TextSize.values()) {
      sizes.addElement(size);
    }
    textSizeComboBox.setModel(sizes);
    settings.add(label("PrintDialog.text_size"));
    settings.add(textSizeComboBox);
    settings.add(label("PrintDialog.pos"));
    settings.add(positionComboBox);

    ButtonGroup content = new ButtonGroup();
    content.add(currentLayoutRadio);
    content.add(selectedViewRadio);
    content.add(seriesRadio);
    currentLayoutRadio.setSelected(true);
    boolean severalPanes = layoutAll != null && layoutAll.getLayoutModel().getCellCount() > 1;
    selectedViewRadio.setEnabled(layoutSelected != null);
    selectedViewRadio.setVisible(severalPanes);
    seriesRadio.setEnabled(frameCount > 1);
    settings.add(label("PrintDialog.content"));
    settings.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING, 0, 0, currentLayoutRadio, selectedViewRadio, seriesRadio),
        "span 3, growx 0"); // NON-NLS

    ((SpinnerNumberModel) firstSpinner.getModel()).setMaximum(Math.max(1, frameCount));
    ((SpinnerNumberModel) lastSpinner.getModel()).setMaximum(Math.max(1, frameCount));
    lastSpinner.setValue(Math.max(1, frameCount));
    seriesRow.add(new JLabel(Messages.getString("PrintDialog.grid") + StringUtil.COLON));
    seriesRow.add(gridComboBox);
    seriesRow.add(GuiUtils.boxHorizontalStrut(10));
    seriesRow.add(new JLabel(Messages.getString("PrintDialog.frames") + StringUtil.COLON));
    seriesRow.add(firstSpinner);
    seriesRow.add(new JLabel(Messages.getString("PrintDialog.frames_to")));
    seriesRow.add(lastSpinner);
    seriesRow.add(GuiUtils.boxHorizontalStrut(10));
    seriesRow.add(new JLabel(Messages.getString("PrintDialog.every") + StringUtil.COLON));
    seriesRow.add(stepSpinner);
    seriesRow.setVisible(false);
    settings.add(seriesRow, "skip 1, span 3, growx 0"); // NON-NLS

    annotationsCheckBox.setSelected(true);
    settings.add(annotationsCheckBox, "skip 1, span 3, growx 0"); // NON-NLS
    settings.add(headerCheckBox, "skip 1, span 3, growx 0"); // NON-NLS
    settings.add(maskingProfile.createLabel());
    settings.add(maskingProfile, "span 3, growx 0"); // NON-NLS
    settings.add(
        MaskingProfileSelector.createReviewWarning(
            selectedView == null ? null : selectedView.getImage(),
            selectedView == null ? null : selectedView.getSeries()),
        "skip 1, span 3"); // NON-NLS

    for (JComboBox<?> combo :
        List.of(
            paperComboBox,
            orientationComboBox,
            backgroundComboBox,
            textSizeComboBox,
            positionComboBox,
            maskingProfile)) {
      combo.addActionListener(e -> updatePreview());
    }
    copiesSpinner.addChangeListener(e -> updatePreview());
    annotationsCheckBox.addActionListener(e -> updatePreview());
    headerCheckBox.addActionListener(e -> updatePreview());
    currentLayoutRadio.addActionListener(e -> contentChanged());
    selectedViewRadio.addActionListener(e -> contentChanged());
    seriesRadio.addActionListener(e -> contentChanged());
    gridComboBox.addActionListener(e -> seriesChanged());
    stepSpinner.addChangeListener(e -> seriesChanged());
    firstSpinner.addChangeListener(e -> seriesChanged());
    lastSpinner.addChangeListener(e -> seriesChanged());
    previousButton.addActionListener(e -> turnPage(-1));
    nextButton.addActionListener(e -> turnPage(1));

    preview.setErrorHandler(
        t -> {
          LOGGER.error("Print preview", t);
          figuresLabel.setText(Messages.getString("PrintDialog.preview_failed"));
        });

    JPanel side = new JPanel(new MigLayout("insets 0, wrap 1", "[grow,fill]")); // NON-NLS
    side.add(figuresLabel);
    side.add(resolutionLabel, "gaptop rel"); // NON-NLS
    side.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.LEADING, 4, 0, pageLabel, previousButton, nextButton),
        "gaptop unrel"); // NON-NLS
    side.add(framesLabel);
    side.setPreferredSize(new Dimension(GuiUtils.getScaleLength(250), 0));

    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 10lp 15lp 10lp 15lp, wrap 2", // NON-NLS
                "[grow,fill][250lp!]", // NON-NLS
                "[]rel[]unrel[grow,fill]unrel[]")); // NON-NLS
    panel.add(settings, "span 2, growx"); // NON-NLS
    optionsHolder.setVisible(false);
    panel.add(optionsHolder, "span 2, growx, hidemode 3"); // NON-NLS
    panel.add(preview, "grow"); // NON-NLS
    panel.add(side, "aligny top, growy 0"); // NON-NLS

    JButton helpButton = GuiUtils.createHelpButton("print"); // NON-NLS
    JButton cancelButton = new JButton(Messages.common("cancel"));
    cancelButton.addActionListener(e -> cancel());
    printButton.addActionListener(e -> printAction());
    getRootPane().setDefaultButton(printButton);
    getRootPane()
        .registerKeyboardAction(
            e -> cancel(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    panel.add(helpButton, "span 2, split 3, growx 0"); // NON-NLS
    panel.add(printButton, "gapbefore push, growx 0"); // NON-NLS
    panel.add(cancelButton, "gapbefore 15lp, growx 0"); // NON-NLS
    setContentPane(panel);

    if (destinations.isEmpty() || currentPages() == null) {
      printButton.setEnabled(false);
      settingsButton.setEnabled(false);
      figuresLabel.setText(
          Messages.getString(
              destinations.isEmpty() ? "PrintDialog.no_destination" : "PrintDialog.no_print"));
    }
  }

  private static JLabel label(String key) {
    return new JLabel(Messages.getString(key) + StringUtil.COLON);
  }

  private static WProperties preferences() {
    return GuiUtils.getUICore().getLocalPersistence();
  }

  private PrintDestination selectedDestination() {
    return destinationComboBox.getSelectedItem() instanceof PrintDestination d ? d : null;
  }

  private Paper selectedPaper() {
    return paperComboBox.getSelectedItem() instanceof Paper p ? p : Paper.A4;
  }

  private Orientation selectedOrientation() {
    return orientationComboBox.getSelectedItem() instanceof Orientation o
        ? o
        : Orientation.PORTRAIT;
  }

  private Color selectedBackground() {
    return backgroundComboBox.getSelectedIndex() == 1 ? Color.BLACK : Color.WHITE;
  }

  private int copies() {
    return ((Number) copiesSpinner.getValue()).intValue();
  }

  private int spinner(JSpinner spinner) {
    return ((Number) spinner.getValue()).intValue();
  }

  /** The pages of the chosen content, or {@code null} when nothing is printable. */
  private PageSource currentPages() {
    if (seriesRadio.isSelected() && selectedView != null) {
      if (seriesPages == null) {
        String[] grid = String.valueOf(gridComboBox.getSelectedItem()).split("x"); // NON-NLS
        seriesPages =
            new SeriesPages<>(
                selectedView,
                Integer.parseInt(grid[0]),
                Integer.parseInt(grid[1]),
                spinner(firstSpinner),
                spinner(lastSpinner),
                spinner(stepSpinner));
      }
      return seriesPages;
    }
    ExportLayout<I> layout =
        selectedViewRadio.isSelected() && layoutSelected != null
            ? layoutSelected
            : layoutAll != null ? layoutAll : layoutSelected;
    // A layout without an image to draw is not printable
    return layout == null || !layout.hasContent() ? null : PageSource.of(layout);
  }

  private ExportLayout<? extends ImageElement> firstPage() {
    PageSource pages = currentPages();
    return pages == null ? null : pages.page(0);
  }

  private PageSpec currentSpec(PrintDestination destination) {
    return destination
        .pageSpec(selectedPaper(), selectedOrientation(), selectedBackground(), null)
        .withHeader(headerCheckBox.isSelected());
  }

  private PrintOptions currentOptions() {
    PrintOptions options = new PrintOptions();
    options.setShowingAnnotations(annotationsCheckBox.isSelected());
    options.setTextSize(textSizeComboBox.getSelectedItem() instanceof TextSize s ? s : null);
    options.setBackground(selectedBackground());
    options.setCenter(positionComboBox.getSelectedIndex() == 0);
    options.setHeader(headerCheckBox.isSelected());
    return options;
  }

  private Orientation defaultOrientation() {
    ExportLayout<? extends ImageElement> layout = firstPage();
    return layout == null ? Orientation.PORTRAIT : PrintJob.defaultOrientation(layout);
  }

  private void restorePreferences() {
    WProperties prefs = preferences();
    annotationsCheckBox.setSelected(prefs.getBooleanProperty(PREF_ANNOTATIONS, true));
    headerCheckBox.setSelected(prefs.getBooleanProperty(PREF_HEADER, false));
    positionComboBox.setSelectedIndex(prefs.getBooleanProperty(PREF_CENTER, true) ? 0 : 1);
    String grid = prefs.getProperty(PREF_GRID);
    if (grid != null && GRIDS.contains(grid)) {
      gridComboBox.setSelectedItem(grid);
    } else {
      gridComboBox.setSelectedItem("4x3"); // NON-NLS
    }
    Optional<PrintDestination> last =
        PrintDestinations.find(destinations, prefs.getProperty(PREF_DESTINATION, ""));
    if (destinationPrefix != null) {
      last =
          last.filter(d -> d.id().startsWith(destinationPrefix))
              .or(
                  () ->
                      destinations.stream()
                          .filter(d -> d.id().startsWith(destinationPrefix))
                          .findFirst());
    }
    if (last.isPresent()) {
      destinationComboBox.setSelectedItem(last.get());
    } else {
      applyDestination();
    }
  }

  private void savePreferences() {
    PrintDestination destination = selectedDestination();
    WProperties prefs = preferences();
    prefs.putBooleanProperty(PREF_ANNOTATIONS, annotationsCheckBox.isSelected());
    prefs.putBooleanProperty(PREF_HEADER, headerCheckBox.isSelected());
    prefs.putBooleanProperty(PREF_CENTER, positionComboBox.getSelectedIndex() == 0);
    prefs.setProperty(PREF_GRID, String.valueOf(gridComboBox.getSelectedItem()));
    if (destination == null) {
      return;
    }
    String key = "print." + destination.id(); // NON-NLS
    prefs.setProperty(PREF_DESTINATION, destination.id());
    prefs.setProperty(key + KEY_PAPER, selectedPaper().id());
    prefs.setProperty(key + KEY_ORIENTATION, selectedOrientation().name());
    if (textSizeComboBox.getSelectedItem() instanceof TextSize size) {
      prefs.setProperty(key + KEY_TEXT_SIZE, size.name());
    } else {
      prefs.remove(key + KEY_TEXT_SIZE);
    }
    prefs.putColorProperty(key + KEY_BACKGROUND, selectedBackground());
  }

  /** Fills the paper list and the remembered settings of the selected destination. */
  private void applyDestination() {
    PrintDestination destination = selectedDestination();
    if (destination == null) {
      return;
    }
    WProperties prefs = preferences();
    String key = "print." + destination.id(); // NON-NLS
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
      orientationComboBox.setSelectedItem(
          orientationOf(prefs.getProperty(key + KEY_ORIENTATION))
              .or(destination::preferredOrientation)
              .orElseGet(this::defaultOrientation));
      textSizeComboBox.setSelectedIndex(0);
      textSizeOf(prefs.getProperty(key + KEY_TEXT_SIZE))
          .ifPresent(textSizeComboBox::setSelectedItem);
      Color background =
          prefs.getColorProperty(key + KEY_BACKGROUND, destination.defaultBackground());
      backgroundComboBox.setSelectedIndex(Color.BLACK.equals(background) ? 1 : 0);
      settingsButton.setEnabled(destination instanceof SystemPrinterDestination);
      showOptions(destination);
    } finally {
      updating = false;
    }
    updatePreview();
  }

  /** Swaps in the settings panel of the destination, if it has one. */
  private void showOptions(PrintDestination destination) {
    optionsHolder.removeAll();
    Optional<JComponent> options = destination.optionsComponent(this::updatePreview);
    options.ifPresent(c -> optionsHolder.add(c, BorderLayout.CENTER));
    optionsHolder.setVisible(options.isPresent());
    optionsHolder.revalidate();
    if (isShowing()) {
      pack();
    }
  }

  private void selectPaper(Paper paper) {
    DefaultComboBoxModel<Paper> model = (DefaultComboBoxModel<Paper>) paperComboBox.getModel();
    if (model.getIndexOf(paper) < 0) {
      model.addElement(paper);
    }
    paperComboBox.setSelectedItem(paper);
  }

  private static Optional<Orientation> orientationOf(String name) {
    try {
      return name == null ? Optional.empty() : Optional.of(Orientation.valueOf(name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<TextSize> textSizeOf(String name) {
    try {
      return name == null ? Optional.empty() : Optional.of(TextSize.valueOf(name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Opens the native dialog of the system printer and reads its choices back. */
  private void printerSettings() {
    if (!(selectedDestination() instanceof SystemPrinterDestination printer)) {
      return;
    }
    if (!printer.showNativeDialog(this, selectedPaper(), selectedOrientation(), copies())) {
      return;
    }
    // The user may have picked another printer there: keep one entry per service
    DefaultComboBoxModel<PrintDestination> model =
        (DefaultComboBoxModel<PrintDestination>) destinationComboBox.getModel();
    String name = printer.getService().getName();
    for (PrintDestination other : List.copyOf(destinations)) {
      if (other != printer
          && other instanceof SystemPrinterDestination sys
          && sys.getService().getName().equals(name)) {
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
    updatePreview();
  }

  private void contentChanged() {
    seriesRow.setVisible(seriesRadio.isSelected());
    pageIndex = 0;
    if (isShowing()) {
      pack();
    }
    updatePreview();
  }

  /** A change of grid, step or range: the series pages are built again. */
  private void seriesChanged() {
    if (updating) {
      return;
    }
    int first = spinner(firstSpinner);
    int last = spinner(lastSpinner);
    if (last < first) {
      updating = true;
      try {
        lastSpinner.setValue(first);
      } finally {
        updating = false;
      }
    }
    disposeSeries();
    pageIndex = 0;
    updatePreview();
  }

  private void turnPage(int by) {
    PageSource pages = currentPages();
    if (pages == null) {
      return;
    }
    pageIndex = Math.clamp(pageIndex + by, 0, pages.pageCount() - 1);
    updatePreview();
  }

  private void updatePreview() {
    if (updating) {
      return;
    }
    PrintDestination destination = selectedDestination();
    PageSource pages = currentPages();
    if (destination == null || pages == null) {
      return;
    }
    int count = pages.pageCount();
    pageIndex = Math.clamp(pageIndex, 0, count - 1);
    PageSpec spec = currentSpec(destination);
    PrintOptions options = currentOptions();
    OptionalInt known = destination.resolutionDpi();
    int dpi =
        Math.clamp(
            known.orElse(PrintResolution.DEFAULT_DPI),
            PrintResolution.MIN_DPI,
            PrintResolution.maxDpi());
    // Built here, on the EDT, so that the worker finds the page ready
    ExportLayout<? extends ImageElement> page = pages.page(pageIndex);
    figuresLabel.setText(figures(page, spec, options, dpi));
    resolutionLabel.setText(
        MessageFormat.format(
            Messages.getString(
                known.isPresent() ? "PrintDialog.resolution" : "PrintDialog.resolution_assumed"),
            dpi));
    pageLabel.setText(Messages.format("PrintDialog.page_of", pageIndex + 1, count));
    framesLabel.setText(
        pages instanceof SeriesPages<?> series
            ? Messages.format("PrintDialog.pages_frames", count, series.frameCount())
            : "");
    previousButton.setEnabled(pageIndex > 0);
    nextButton.setEnabled(pageIndex < count - 1);

    MaskingProfile profile = maskingProfile.getSelectedProfile();
    boolean gray = !destination.isColor();
    int index = pageIndex;
    preview.request(
        spec,
        pixelsPerMm -> {
          Supplier<BufferedImage> task =
              () -> PageRenderer.renderPage(page, spec, pixelsPerMm, options, gray, index, count);
          return profile == null
              ? task.get()
              : IdentityMask.forProfile(profile).callMasked(task::get);
        });
  }

  private static String figures(
      ExportLayout<?> layout, PageSpec spec, PrintOptions options, int dpi) {
    Optional<PageFigures> figures = PageFigures.of(layout, spec, options, dpi);
    if (figures.isEmpty()) {
      return Messages.getString("PrintDialog.no_print");
    }
    PageFigures f = figures.get();
    String cell =
        Messages.format(
            "PrintDialog.figures_cell",
            Math.round(f.cellWidthMm()),
            Math.round(f.cellHeightMm()),
            f.textSize());
    String image =
        Messages.format(
            "PrintDialog.figures_image",
            Math.round(f.imagePixelsPerInch()),
            Messages.getString(f.magnified() ? "PrintDialog.magnified" : "PrintDialog.reduced"));
    return "<html>" + cell + "<br>" + image + "</html>"; // NON-NLS
  }

  private void printAction() {
    PrintDestination destination = selectedDestination();
    PageSource pages = currentPages();
    if (destination == null || pages == null) {
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          Messages.getString("PrintDialog.no_print"),
          null,
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (destination instanceof PdfFileDestination pdf
        && pdf.file().isEmpty()
        && !pdf.chooseFile(this)) {
      return;
    }
    PageSpec spec = currentSpec(destination);
    PrintOptions options = currentOptions();
    int copies = copies();
    savePreferences();
    closeDialog();
    try {
      // The job renders the pages on this thread, so the mask is bound for the whole job
      maskingProfile.runMaskedAction(
          () -> {
            try {
              destination.print(pages, spec, options, copies);
            } catch (PrinterException e) {
              throw new IllegalStateException(e);
            }
          });
    } catch (RuntimeException e) {
      LOGGER.error("Print to {}", destination.title(), e);
      JOptionPane.showMessageDialog(
          GuiUtils.getUICore().getApplicationWindow(),
          Messages.getString("PrintDialog.print_failed"),
          getTitle(),
          JOptionPane.ERROR_MESSAGE);
    } finally {
      disposeLayouts();
    }
  }

  private void cancel() {
    savePreferences();
    closeDialog();
    disposeLayouts();
  }

  /** Closes the window once the preview worker is idle, so the layouts may be used or disposed. */
  private void closeDialog() {
    preview.close();
    dispose();
  }

  private void disposeSeries() {
    if (seriesPages != null) {
      seriesPages.dispose();
      seriesPages = null;
    }
  }

  private void disposeLayouts() {
    disposeSeries();
    if (layoutAll != null) {
      layoutAll.dispose();
      layoutAll = null;
    }
    if (layoutSelected != null) {
      layoutSelected.dispose();
      layoutSelected = null;
    }
  }
}
