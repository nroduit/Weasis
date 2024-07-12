/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.FileIcon;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.pref.LauncherToolBar;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.ForcedAcceptPrintService;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomExportAction;
import org.weasis.dicom.explorer.DicomFieldsView;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomViewerPlugin;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;

public class SRContainer extends DicomViewerPlugin implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(SRContainer.class);

  public static final GridBagLayoutModel VIEWS_SR =
      new GridBagLayoutModel(
          "1x1", // NON-NLS
          "1x1", // NON-NLS
          1,
          1,
          SRView.class.getName());

  public static final List<GridBagLayoutModel> LAYOUT_LIST = List.of(VIEWS_SR);

  public static final List<SynchView> SYNCH_LIST = List.of(SynchView.NONE);

  public static final SeriesViewerUI UI = new SeriesViewerUI(SRContainer.class);
  static final ImageViewerEventManager<DicomImageElement> SR_EVENT_MANAGER =
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

  protected SRView srview;

  public SRContainer() {
    this(VIEWS_SR, null);
  }

  public SRContainer(GridBagLayoutModel layoutModel, String uid) {
    super(
        SR_EVENT_MANAGER,
        layoutModel,
        uid,
        SRFactory.NAME,
        ResourceUtil.getIcon(FileIcon.TEXT),
        null);
    setSynchView(SynchView.NONE);

    if (!UI.init.getAndSet(true)) {
      List<Toolbar> toolBars = UI.toolBars;
      // Add standard toolbars
      final BundleContext context = AppProperties.getBundleContext(this.getClass());
      if (context == null) {
        LOGGER.error("Cannot get BundleContext");
        return;
      }
      String bundleName = context.getBundle().getSymbolicName();
      String componentName = InsertableUtil.getCName(this.getClass());
      String key = "enable"; // NON-NLS
      WProperties preferences = GuiUtils.getUICore().getSystemPreferences();

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ImportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            GuiUtils.getUICore().getExplorerPluginToolbars().stream()
                .filter(t -> t instanceof ImportToolBar)
                .findFirst();
        b.ifPresent(toolBars::add);
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(ExportToolBar.class),
          key,
          true)) {
        Optional<Toolbar> b =
            GuiUtils.getUICore().getExplorerPluginToolbars().stream()
                .filter(t -> t instanceof ExportToolBar)
                .findFirst();
        b.ifPresent(toolBars::add);
      }

      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(SrToolBar.class),
          key,
          true)) {
        toolBars.add(new SrToolBar(10));
      }
      if (InsertableUtil.getBooleanProperty(
          preferences,
          bundleName,
          componentName,
          InsertableUtil.getCName(LauncherToolBar.class),
          key,
          true)) {
        toolBars.add(new LauncherToolBar(getEventManager(), 130));
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

      List<Action> actions = getPrintActions();
      if (actions != null) {
        JMenu printMenu = new JMenu(Messages.getString("SRContainer.print"));
        for (Action action : actions) {
          JMenuItem item = new JMenuItem(action);
          printMenu.add(item);
        }
        menuRoot.add(printMenu);
      }
    }
    return menuRoot;
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return UI;
  }

  @Override
  public void close() {
    SRFactory.closeSeriesViewer(this);
    super.close();

    GuiExecutor.execute(
        () -> {
          if (srview != null) {
            srview.dispose();
          }
        });
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt instanceof ObservableEvent event) {
      ObservableEvent.BasicAction action = event.getActionCommand();
      Object newVal = event.getNewValue();

      if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
        if (newVal instanceof MediaSeriesGroup group) {
          // Patient Group
          if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
            if (group.equals(getGroupID())) {
              // Close the content of the plug-in
              close();
              handleFocusAfterClosing();
            }
          }
          // Study Group
          else if (TagD.getUID(Level.STUDY).equals(group.getTagID())) {
            if (event.getSource() instanceof DicomModel model) {
              if (srview != null
                  && group.equals(model.getParent(srview.getSeries(), DicomModel.study))) {
                close();
                handleFocusAfterClosing();
              }
            }
          }
          // Series Group
          else if (TagD.getUID(Level.SERIES).equals(group.getTagID())) {
            if (srview != null && srview.getSeries() == newVal) {
              close();
              handleFocusAfterClosing();
            }
          }
        }
      }
    }
  }

  @Override
  public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
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
  public ViewCanvas<DicomImageElement> createDefaultView(String classType) {
    return null;
  }

  @Override
  public JComponent createComponent(String clazz) {
    try {
      // FIXME use classloader.loadClass or injection
      JComponent component = buildInstance(Class.forName(clazz));
      if (component instanceof SRView) {
        srview = (SRView) component;
      }
      return component;
    } catch (Exception e) {
      LOGGER.error("Cannot create {}", clazz, e);
    }
    return null;
  }

  @Override
  public Class<?> getSeriesViewerClass() {
    return SRView.class;
  }

  @Override
  public GridBagLayoutModel getDefaultLayoutModel() {
    return VIEWS_SR;
  }

  @Override
  public List<Action> getPrintActions() {
    final String title = Messages.getString("SRContainer.print_layout");
    return Collections.singletonList(
        new DefaultAction(
            title, ResourceUtil.getIcon(ActionIcon.PRINT), event -> printCurrentView()));
  }

  void displayHeader() {
    if (srview != null) {
      DicomSpecialElement dcm =
          DicomModel.getFirstSpecialElement(srview.getSeries(), DicomSpecialElement.class);
      DicomFieldsView.showHeaderDialog(this, srview.getSeries(), dcm);
    }
  }

  void printCurrentView() {
    if (srview != null) {
      PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
      PrinterJob pj = PrinterJob.getPrinterJob();

      // Get page format from the printer
      if (pj.printDialog(aset)) {
        PageFormat pageFormat = pj.defaultPage();
        // Force printing in black and white
        EditorPanePrinter pnlPreview =
            new EditorPanePrinter(srview.getHtmlPanel(), pageFormat, new Insets(18, 18, 18, 18));
        pj.setPageable(pnlPreview);
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

  public Series<?> getSeries() {
    if (srview != null) {
      return srview.getSeries();
    }
    return null;
  }

  @Override
  public void addSeries(MediaSeries<DicomImageElement> sequence) {
    if (srview != null && sequence instanceof Series && srview.getSeries() != sequence) {
      srview.setSeries((Series<DicomImageElement>) sequence);
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
  public List<SynchView> getSynchList() {
    return SYNCH_LIST;
  }

  @Override
  public List<GridBagLayoutModel> getLayoutList() {
    return LAYOUT_LIST;
  }
}
