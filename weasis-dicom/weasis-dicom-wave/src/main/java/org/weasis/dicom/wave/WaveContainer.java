/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.FileIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.ws.DockSide;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.SplitLayout;
import org.weasis.core.ui.editor.SplitPosition;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.pref.LauncherToolBar;
import org.weasis.core.ui.util.ForcedAcceptPrintService;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.WaveformAnnotationSource;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.exp.DicomExportAction;
import org.weasis.dicom.explorer.tag.DicomFieldsView;
import org.weasis.dicom.wave.dockable.MeasureAnnotationTool;

public class WaveContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(WaveContainer.class);

  public static final MigLayoutModel DEFAULT_VIEW =
      new MigLayoutModel(
          "1x1", // NON-NLS
          "1x1", // NON-NLS
          1,
          1,
          WaveView.class.getName()); // NON-NLS

  public static final List<MigLayoutModel> LAYOUT_LIST = List.of(DEFAULT_VIEW);

  public static final SeriesViewerUI UI = new SeriesViewerUI(WaveContainer.class);
  static final ImageViewerEventManager<DicomImageElement> ECG_EVENT_MANAGER =
      new ImageViewerEventManager<>() {

        @Override
        public boolean updateComponentsListener(ViewCanvas<DicomImageElement> defaultView2d) {
          // Do nothing
          return true;
        }

        @Override
        public void resetDisplay() {
          // Do nothing
        }

        @Override
        public void setSelectedView2dContainer(
            ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
          this.selectedView2dContainer = selectedView2dContainer;
        }

        @Override
        public void keyTyped(KeyEvent e) {
          // Do nothing
        }

        @Override
        public void keyPressed(KeyEvent e) {
          // Do nothing
        }

        @Override
        public void keyReleased(KeyEvent e) {
          // Do nothing
        }

        @Override
        public String resolvePlaceholders(String template) {
          return DicomExportAction.resolvePlaceholders(template, this);
        }
      };
  protected WaveView ecgView;

  public WaveContainer() {
    this(DEFAULT_VIEW, null);
  }

  public WaveContainer(MigLayoutModel layoutModel, String uid) {
    super(
        ECG_EVENT_MANAGER,
        layoutModel,
        uid,
        WaveFactory.NAME,
        ResourceUtil.getIcon(FileIcon.ECG),
        null);

    if (!UI.init.getAndSet(true)) {
      List<Toolbar> toolBars = UI.toolBars;
      // Add standard toolbars
      String plugInId = GuiUtils.getUICore().getPluginManager().getPluginId(this.getClass());
      if (plugInId == null) {
        LOGGER.error("Cannot get BundleContext");
        return;
      }
      String componentName = InsertableUtil.getCName(this.getClass());
      String key = "enable"; // NON-NLS
      WProperties preferences = GuiUtils.getUICore().getSystemPreferences();

      if (InsertableUtil.getBooleanProperty(
          preferences,
          plugInId,
          componentName,
          InsertableUtil.getCName(WaveformToolBar.class),
          key,
          true)) {
        toolBars.add(new WaveformToolBar(20));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          plugInId,
          componentName,
          InsertableUtil.getCName(LauncherToolBar.class),
          key,
          true)) {
        toolBars.add(new LauncherToolBar(getEventManager(), 130));
      }

      PluginTool tool;
      if (InsertableUtil.getBooleanProperty(
          preferences,
          plugInId,
          componentName,
          InsertableUtil.getCName(MeasureTool.class),
          key,
          true)) {
        tool = new MeasureAnnotationTool();
        eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
        UI.tools.add(tool);
      }
    }
  }

  @Override
  public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> defaultView2d) {
    setSelectedImagePane(defaultView2d);
  }

  @Override
  public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
    if (menuRoot != null) {
      menuRoot.removeAll();
      menuRoot.setText(WaveFactory.NAME);

      List<Action> actions = getPrintActions();
      if (actions != null) {
        JMenu printMenu = new JMenu(Messages.common("print"));
        for (Action action : actions) {
          JMenuItem item = new JMenuItem(action);
          printMenu.add(item);
        }
        menuRoot.add(printMenu);
      }
      JMenuItem export =
          new JMenuItem(
              Messages.getString("WaveContainer.export"),
              ResourceUtil.getIcon(ActionIcon.EXPORT_IMAGE));
      export.addActionListener(e -> exportPage());
      menuRoot.add(export);
      JMenuItem compare =
          new JMenuItem(
              Messages.getString("WaveformToolBar.compare"),
              ResourceUtil.getDirectionIcon(ActionIcon.PREVIOUS));
      compare.addActionListener(e -> compareWithPrevious());
      menuRoot.add(compare);
    }
    return menuRoot;
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return UI;
  }

  @Override
  public void setSelected(boolean selected) {
    super.setSelected(true);
    if (selected) {
      if (ecgView != null
          && !UI.tools.isEmpty()
          && UI.tools.getFirst() instanceof MeasureAnnotationTool tool) {
        ecgView.setAnnotationTool(tool);
        tool.setSeries(ecgView.getSeries());
        ecgView.updateMarkersTable();
      }
    }
  }

  @Override
  public void close() {
    super.close();
    WaveFactory.closeSeriesViewer(this);

    GuiExecutor.execute(
        () -> {
          if (ecgView != null) {
            ecgView.dispose();
          }
        });
  }

  private static boolean isAnnotationSource(Series<?> series) {
    return DicomModel.getFirstSpecialElement(series, WaveformAnnotationSource.class) != null;
  }

  private void refreshAnnotations(Series<?> series) {
    if (isAnnotationSource(series)) {
      refreshAnnotations();
    }
  }

  private void refreshAnnotations() {
    if (ecgView != null) {
      ecgView.refreshAnnotations();
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();

      if (ObservableEvent.BasicAction.ADD.equals(action) && newVal instanceof Series<?> added) {
        // The special element joins its series right after this event
        SwingUtilities.invokeLater(() -> refreshAnnotations(added));
      } else if (ObservableEvent.BasicAction.UPDATE.equals(action)
          && (newVal instanceof WaveformAnnotationSource
              || (newVal instanceof Series<?> updated && isAnnotationSource(updated)))) {
        refreshAnnotations();
      } else if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        if (newVal instanceof DicomSeries) {
          if (ecgView != null && ecgView.getSeries() == newVal) {
            close();
          } else if (isAnnotationSource((Series<?>) newVal)) {
            SwingUtilities.invokeLater(this::refreshAnnotations);
          }
        } else if (newVal instanceof MediaSeriesGroup group) {
          // Patient Group
          if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
            if (group.equals(getGroupID())) {
              // Close the content of the plug-in
              close();
            }
          }
          // Study Group
          else if (TagD.getUID(Level.STUDY).equals(group.getTagID())
              && event.getSource() instanceof DicomModel model) {
            for (MediaSeriesGroup s : model.getChildren(group)) {
              if (ecgView != null && ecgView.getSeries() == s) {
                close();
                break;
              }
            }
          }
        }
      }
    }
  }

  @Override
  public int getViewTypeNumber(MigLayoutModel layout, Class<?> defaultClass) {
    return 0;
  }

  @Override
  public boolean isViewType(Class<?> defaultClass, String type) {
    if (defaultClass != null) {
      try {
        Class<?> clazz = Class.forName(type);
        return defaultClass.isAssignableFrom(clazz);
      } catch (Exception e) {
        LOGGER.error("Checking view type", e);
      }
    }
    return false;
  }

  @Override
  public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
    return null;
  }

  @Override
  public JComponent createComponent(String clazz) {
    try {
      Class<?> cl = Class.forName(clazz);
      JComponent component = (JComponent) cl.newInstance();
      if (component instanceof SeriesViewerListener viewerListener) {
        eventManager.addSeriesViewerListener(viewerListener);
      }
      if (component instanceof WaveView waveView) {
        ecgView = waveView;
      }
      return component;
    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return WaveView.class;
  }

  @Override
  public MigLayoutModel getDefaultLayoutModel() {
    return DEFAULT_VIEW;
  }

  @Override
  public List<Action> getPrintActions() {
    ArrayList<Action> actions = new ArrayList<>(1);
    final String title = Messages.getString("WaveContainer.print_layout");

    AbstractAction printStd =
        new AbstractAction(title, ResourceUtil.getIcon(ActionIcon.PRINT)) {

          @Override
          public void actionPerformed(ActionEvent e) {
            printCurrentView();
          }
        };
    actions.add(printStd);

    return actions;
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> sequence) {
    if (ecgView != null
        && sequence instanceof Series<?> series
        && ecgView.getSeries() != sequence) {
      ecgView.setSeries(series);
    }
  }

  @Override
  public void addSeriesList(
      List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
    if (seriesList != null && !seriesList.isEmpty()) {
      addSeries(seriesList.get(0));
    }
  }

  @Override
  public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
    // Do it in addSeries()
  }

  @Override
  public List<MigLayoutModel> getLayoutList() {
    return LAYOUT_LIST;
  }

  public void setZoomRatio(double ratio) {
    if (ecgView != null) {
      ecgView.setZoomRatio(ratio);
      ecgView.setFormat(ecgView.getCurrentFormat());
      ecgView.repaint();
    }
  }

  public void clearMeasurements() {
    if (ecgView != null) {
      ecgView.clearMeasurements();
    }
  }

  public void displayHeader() {
    if (ecgView != null) {
      DicomSpecialElement dcm =
          DicomModel.getFirstSpecialElement(ecgView.getSeries(), DicomSpecialElement.class);
      DicomFieldsView.showHeaderDialog(this, ecgView.getSeries(), dcm);
    }
  }

  private static final String P_EXPORT_DIR = "weasis.wave.export.dir"; // NON-NLS

  /** Asks for a file and writes the displayed page to it as PNG, JPEG or PDF. */
  public void exportPage() {
    if (ecgView == null || ecgView.getPageWidth() <= 0) {
      return;
    }
    WProperties persistence = GuiUtils.getUICore().getLocalPersistence();
    SystemFileChooser chooser = new SystemFileChooser(persistence.getProperty(P_EXPORT_DIR, ""));
    chooser.setFileSelectionMode(SystemFileChooser.FILES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    FileNameExtensionFilter pdf = new FileNameExtensionFilter("PDF", WaveExport.PDF); // NON-NLS
    FileNameExtensionFilter png = new FileNameExtensionFilter("PNG", WaveExport.PNG); // NON-NLS
    FileNameExtensionFilter jpg = new FileNameExtensionFilter("JPEG", WaveExport.JPEG); // NON-NLS
    chooser.addChoosableFileFilter(pdf);
    chooser.addChoosableFileFilter(png);
    chooser.addChoosableFileFilter(jpg);
    chooser.setFileFilter(pdf);
    if (chooser.showSaveDialog(this) != SystemFileChooser.APPROVE_OPTION
        || chooser.getSelectedFile() == null) {
      return;
    }
    File file = chooser.getSelectedFile();
    String extension =
        chooser.getFileFilter() instanceof FileNameExtensionFilter f
            ? f.getExtensions()[0]
            : WaveExport.PDF;
    if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
      file = new File(file.getPath() + "." + extension);
    }
    persistence.setProperty(P_EXPORT_DIR, file.getParent());
    try {
      WaveExport.write(ecgView, file);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Exporting the ECG page", e);
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("WaveContainer.export_error") + "\n" + e.getMessage(),
          Messages.getString("WaveContainer.export"),
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Opens the previous waveform of the same patient (the latest one acquired before this one, or
   * the latest other one) in a viewer split beside this one.
   */
  public void compareWithPrevious() {
    if (ecgView == null || ecgView.getSeries() == null) {
      return;
    }
    Series<?> current = ecgView.getSeries();
    if (!(current.getTagValue(TagW.ExplorerModel) instanceof DicomModel model)) {
      return;
    }
    MediaSeriesGroup patient = model.getParent(current, DicomModel.patient);
    if (patient == null) {
      return;
    }
    String currentKey = acquisitionKey(current);
    List<Series<?>> candidates = new ArrayList<>();
    for (MediaSeriesGroup study : model.getChildren(patient)) {
      for (MediaSeriesGroup s : model.getChildren(study)) {
        if (s instanceof Series<?> series
            && series != current
            && WaveElementFactory.SERIES_WAVEFORM_MIMETYPE.equals(series.getMimeType())) {
          candidates.add(series);
        }
      }
    }
    if (candidates.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          Messages.getString("WaveContainer.no_previous"),
          WaveFactory.NAME,
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    Series<?> previous =
        WaveComparison.previousOf(candidates, currentKey, WaveContainer::acquisitionKey);
    if (previous == null) {
      return;
    }
    // Already open in a tab: move that viewer beside this one
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    List<ViewerPlugin<?>> plugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (plugins) {
      for (ViewerPlugin<?> plugin : plugins) {
        if (plugin instanceof WaveContainer other
            && other != this
            && other.ecgView != null
            && other.ecgView.getSeries() == previous) {
          if (manager != null) {
            manager.split(other, this, DockSide.RIGHT, 0.5);
          }
          return;
        }
      }
    }
    ViewerPluginBuilder.openInDefaultViewer(
        previous,
        model,
        ViewerOpenOptions.builder()
            .placement(ViewerPlacement.split(SplitLayout.of(SplitPosition.RIGHT)))
            .build());
  }

  /** A sortable acquisition date-time of a waveform series (DICOM DT, or DA + TM). */
  private static String acquisitionKey(Series<?> series) {
    DicomSpecialElement element =
        DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
    if (element == null || element.getMediaReader() == null) {
      return "";
    }
    org.dcm4che3.data.Attributes dcm = element.getMediaReader().getDicomObject();
    if (dcm == null) {
      return "";
    }
    String dt = dcm.getString(org.dcm4che3.data.Tag.AcquisitionDateTime);
    if (dt != null && !dt.isEmpty()) {
      return dt;
    }
    String date = dcm.getString(org.dcm4che3.data.Tag.ContentDate, "");
    String time = dcm.getString(org.dcm4che3.data.Tag.ContentTime, "");
    if (date.isEmpty()) {
      date = dcm.getString(org.dcm4che3.data.Tag.StudyDate, "");
      time = dcm.getString(org.dcm4che3.data.Tag.StudyTime, "");
    }
    return date + time;
  }

  void printCurrentView() {
    if (ecgView != null) {
      PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
      PrinterJob pj = PrinterJob.getPrinterJob();
      pj.setJobName(ecgView.getSeries().toString());

      // Get page format from the printer
      if (pj.printDialog(aset)) {
        PageFormat pageFormat = pj.getPageFormat(aset);
        Paper paper = pageFormat.getPaper();
        double margin = 12;
        paper.setImageableArea(
            margin, margin, paper.getWidth() - margin * 2, paper.getHeight() - margin * 2);
        pageFormat.setPaper(paper);
        DefaultPrinter pnlPreview = new DefaultPrinter(ecgView, pageFormat);
        pj.setPrintable(pnlPreview, pageFormat);
        try {
          pj.print();
        } catch (PrinterException e) {
          // check for the annoying 'Printer is not accepting job' error.
          if (e.getMessage().contains("accepting job")) { // NON-NLS
            // recommend prompting the user at this point if they want to force it,
            // so they'll know there may be a problem.
            int response =
                JOptionPane.showConfirmDialog(
                    GuiUtils.getUICore().getApplicationWindow(),
                    org.weasis.core.Messages.getString("ImagePrint.issue_desc"),
                    org.weasis.core.Messages.getString("ImagePrint.status"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (response == 0) {
              try {
                // try printing again but ignore the not-accepting-jobs attribute
                ForcedAcceptPrintService.setupPrintJob(pj); // add secret ingredient
                pj.print(aset);
                LOGGER.info("Bypass Printer is not accepting job");
              } catch (PrinterException ex) {
                LOGGER.error("Printer exception", ex);
              }
            }
          } else {
            LOGGER.error("Print exception", e);
          }
        }
      }
    }
  }
}
