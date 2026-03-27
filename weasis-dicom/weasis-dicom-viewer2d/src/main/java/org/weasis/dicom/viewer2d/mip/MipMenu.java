/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mip;

import static org.weasis.dicom.explorer.DicomModel.LOADING_EXECUTOR;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;

/**
 * Builds the MIP dropdown popup menu.
 *
 * <p>Mode radio buttons are embedded in a {@link JPanel} added directly to the {@link JPopupMenu}.
 * Because Swing only auto-dismisses the popup when a {@link JMenuItem} is activated, placing the
 * buttons in a plain panel keeps the menu open when the user switches the mode from <em>None</em>
 * to an active projection – letting them also pick a thickness without reopening the menu.
 */
public final class MipMenu {

  private MipMenu() {}

  public static void showPopup(Component invoker, int x, int y) {
    EventManager em = EventManager.getInstance();
    ImageViewerPlugin<DicomImageElement> container = em.getSelectedView2dContainer();
    if (!(container instanceof View2dContainer)) {
      return;
    }
    ViewCanvas<DicomImageElement> selView = container.getSelectedImagePane();
    if (selView == null) {
      return;
    }

    // Determine whether we are already inside a MipView
    AtomicReference<MipView> mipRef = new AtomicReference<>();
    if (selView instanceof MipView mv) {
      mipRef.set(mv);
    }

    JPopupMenu popup = buildPopup(em, container, selView, mipRef);
    popup.show(invoker, x, y);
  }

  public static JPopupMenu buildViewButtonPopup(MipView view) {
    AtomicReference<MipView> mipRef = new AtomicReference<>(view);
    EventManager em = EventManager.getInstance();
    ImageViewerPlugin<DicomImageElement> container = em.getSelectedView2dContainer();
    return buildPopup(em, container, view, mipRef);
  }

  private static JPopupMenu buildPopup(
      EventManager em,
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> selView,
      AtomicReference<MipView> mipRef) {

    JPopupMenu popup = new JPopupMenu();

    MipView.Type currentType = getCurrentType(mipRef.get());
    boolean isActive = currentType != MipView.Type.NONE;

    JMenu thicknessMenu = buildThicknessMenu(mipRef, isActive);

    JMenuItem rebuildItem = new JMenuItem(Messages.getString("build.series"));
    rebuildItem.setEnabled(isActive);
    rebuildItem.addActionListener(_ -> rebuildSeries(mipRef.get()));

    JPanel modePanel =
        buildModePanel(em, container, selView, mipRef, currentType, thicknessMenu, rebuildItem);

    popup.add(modePanel);
    popup.addSeparator();
    popup.add(thicknessMenu);
    popup.addSeparator();
    popup.add(rebuildItem);

    return popup;
  }

  private static JPanel buildModePanel(
      EventManager em,
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> selView,
      AtomicReference<MipView> mipRef,
      MipView.Type initialType,
      JMenu thicknessMenu,
      JMenuItem rebuildItem) {

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    panel.setBorder(BorderFactory.createTitledBorder(Messages.getString("MipPopup.projection")));

    ButtonGroup group = new ButtonGroup();
    for (MipView.Type type : MipView.Type.values()) {
      JRadioButton radio = new JRadioButton(type.getTitle(), type == initialType);
      group.add(radio);
      radio.addActionListener(
          _ -> onModeSelected(type, em, container, selView, mipRef, thicknessMenu, rebuildItem));
      panel.add(radio);
    }

    return panel;
  }

  private static void onModeSelected(
      MipView.Type type,
      EventManager em,
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> selView,
      AtomicReference<MipView> mipRef,
      JMenu thicknessMenu,
      JMenuItem rebuildItem) {

    if (type == MipView.Type.NONE) {
      MipView v = mipRef.getAndSet(null);
      if (v != null) {
        v.exitMipMode(v.getSeries(), null);
      }
      thicknessMenu.setEnabled(false);
      rebuildItem.setEnabled(false);
      // Close the popup – nothing left to configure when mode is None
      JPopupMenu popup =
          (JPopupMenu) SwingUtilities.getAncestorOfClass(JPopupMenu.class, thicknessMenu);
      if (popup != null) {
        popup.setVisible(false);
      }
    } else {
      MipView v = mipRef.get();
      if (v == null) {
        v = createMipView(em, container, selView);
        if (v == null) return;
        mipRef.set(v);
      }
      v.setActionsInView(MipView.MIP.cmd(), type);
      MipView.buildMip(v);
      thicknessMenu.setEnabled(true);
      rebuildItem.setEnabled(true);
    }
  }

  private static JMenu buildThicknessMenu(AtomicReference<MipView> mipRef, boolean enabled) {
    JMenu menu = new JMenu(Messages.getString("mip.thickness"));
    menu.setEnabled(enabled);

    MipView view = mipRef.get();
    Integer thickness =
        view == null ? null : (Integer) view.getActionValue(MipView.MIP_THICKNESS.cmd());
    int currentThickness = thickness == null ? 2 : thickness;

    MediaSeries<DicomImageElement> series = view != null ? view.getSeries() : null;
    String unit = getUnitAbbreviation(series);

    ButtonGroup group = new ButtonGroup();
    for (int size : List.of(1, 2, 3, 5, 7, 10, 15, 20)) {
      var label = buildSizeLabel(size, series, unit);
      var item = new JRadioButtonMenuItem(label, size == currentThickness);
      group.add(item);
      item.addActionListener(_ -> applyThickness(mipRef, size));
      menu.add(item);
    }

    menu.addSeparator();

    var customItem = new JMenuItem(Messages.getString("custom.thickness"));
    customItem.addActionListener(_ -> showCustomThicknessDialog(mipRef, currentThickness, unit));
    menu.add(customItem);

    return menu;
  }

  private static void applyThickness(AtomicReference<MipView> mipRef, int thickness) {
    MipView v = mipRef.get();
    if (v != null) {
      updateSize(v, thickness);
    }
  }

  /**
   * Returns the pixel-spacing unit abbreviation from the middle image of the series, or {@code ""}.
   */
  private static String getUnitAbbreviation(MediaSeries<DicomImageElement> series) {
    if (series == null) return "";
    var img = series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
    return img != null ? img.getPixelSpacingUnit().getAbbreviation() : "";
  }

  private static String buildSizeLabel(
      int size, MediaSeries<DicomImageElement> series, String unit) {
    if (series == null || unit.isEmpty()) return String.valueOf(size);
    try {
      var first = series.getMedia(0, null, null);
      var second = series.size(null) > 1 ? series.getMedia(1, null, null) : null;
      if (first != null && second != null) {
        double sliceThickness = Math.abs(DicomMediaUtils.getThickness(first, second));
        if (sliceThickness > 0) {
          double mm = (size * 2 + 1) * sliceThickness;
          return size + " (" + DecFormatter.allNumber(mm) + " " + unit + ")";
        }
      }
    } catch (Exception ignored) {
      // fall through to plain label
    }
    return String.valueOf(size);
  }

  private static void showCustomThicknessDialog(
      AtomicReference<MipView> mipRef, int currentThickness, String unit) {
    MipView v = mipRef.get();
    MediaSeries<DicomImageElement> series = v != null ? v.getSeries() : null;
    int maxVal = series != null ? Math.max(series.size(null) / 2, 1) : 50;

    var model = new SpinnerNumberModel(1, 1, maxVal, 1);
    var spinner = new JSpinner(model);
    var convLabel = new JLabel();

    // Live mm conversion
    if (v != null && !unit.isEmpty() && series != null) {
      var first = series.getMedia(0, null, null);
      var second = series.getMedia(1, null, null);
      if (first != null && second != null) {
        double sliceThickness = Math.abs(DicomMediaUtils.getThickness(first, second));
        if (sliceThickness > 0) {
          spinner.addChangeListener(
              _ -> {
                int val = (Integer) spinner.getValue();
                convLabel.setText(
                    " = " + DecFormatter.allNumber((val * 2 + 1) * sliceThickness) + " " + unit);
              });
        }
      }
    }
    spinner.setValue(Math.min(currentThickness, maxVal));

    var fm = convLabel.getFontMetrics(convLabel.getFont());
    convLabel.setPreferredSize(new Dimension(fm.stringWidth(" = 9999.9 mm"), fm.getHeight()));

    var panel = new JPanel(new FlowLayout(FlowLayout.LEADING));
    panel.add(new JLabel(Messages.getString("MipView.img_extend") + StringUtil.COLON));
    panel.add(spinner);
    panel.add(convLabel);

    int result =
        JOptionPane.showConfirmDialog(
            null,
            panel,
            Messages.getString("custom.thickness"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    if (result == JOptionPane.OK_OPTION && v != null) {
      updateSize(v, (Integer) spinner.getValue());
    }
  }

  private static void updateSize(MipView v, int thickness) {
    Integer oldVal = (Integer) v.getActionValue(MipView.MIP_THICKNESS.cmd());
    v.setActionsInView(MipView.MIP_THICKNESS.cmd(), thickness);
    if (!Objects.equals(oldVal, thickness)) {
      v.activateCrosslinesUpdate();
      MipView.buildMip(v);
    }
  }

  private static void rebuildSeries(MipView view) {
    if (view == null) return;
    if (GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
        instanceof DicomExplorer explorer) {
      DataExplorerModel dicomModel = explorer.getDataExplorerModel();
      Runnable runnable = MipView.buildMipRunnable(view, true);
      if (runnable == null) return;

      ExplorerTask<Boolean, String> task =
          new ExplorerTask<>(Messages.getString("build.series"), false) {
            @Override
            protected Boolean doInBackground() {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
              runnable.run();
              return true;
            }

            @Override
            protected void done() {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
            }
          };
      LOADING_EXECUTOR.execute(task);
    }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Returns the current {@link MipView.Type}, defaulting to {@code NONE} when unknown. */
  private static MipView.Type getCurrentType(MipView view) {
    if (view == null) return MipView.Type.NONE;
    return view.getActionValue(MipView.MIP.cmd()) instanceof MipView.Type t ? t : MipView.Type.NONE;
  }

  /**
   * Creates a fresh {@link MipView}, replaces {@code selView} in the container, and immediately
   * triggers the initial build.
   */
  static MipView createMipView(
      EventManager em,
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> selView) {

    if (selView == null) return null;
    MediaSeries<DicomImageElement> s = selView.getSeries();
    if (s == null || s.size(null) < DefaultView2d.MINIMAL_IMAGES_FOR_3D) return null;

    s = LoadLocalDicom.confirmSplittingMultiPhaseSeries(s);
    if (s == null) return null;

    container.setSelectedAndGetFocus();
    MipView newView = new MipView(em);
    newView.registerDefaultListeners();
    newView.initMIPSeries(selView);
    container.replaceView(selView, newView);

    // Restore focus after the replacement is rendered
    SwingUtilities.invokeLater(() -> container.setSelectedImagePaneFromFocus(newView));
    return newView;
  }
}
