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
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature.BasicActionStateValue;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewSynchData;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;

/**
 * The MIP of a stack: whether a pane can show one, the replacement of a pane by a {@link MipView}
 * and the series built from it. The projection and the thickness are edited in the {@link
 * SlabEditor}.
 */
public final class MipMenu {

  /**
   * Whether a projection can be built from the selected view. Unlike MPR, it replaces the canvas of
   * its pane, and a tile cannot be replaced.
   */
  public static final BasicActionStateValue AVAILABLE =
      new BasicActionStateValue(
          Messages.getString("MipView.mip"), "mip.enable", 0, 0, null); // NON-NLS

  private MipMenu() {}

  /** The MIP button of the toolbar: the slab editor of the selected pane. */
  public static void showPopup(Component invoker, int x, int y) {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view != null) {
      SlabEditor.show(view, invoker, x, y);
    }
  }

  static void rebuildSeries(MipView view) {
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

  /**
   * Creates a fresh {@link MipView}, replaces {@code selView} in the container, and immediately
   * triggers the initial build.
   */
  /** Replaces the selected pane by a MIP view of its series, keeping its slice and its links. */
  public static MipView createMipView(
      EventManager em,
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> selView) {

    if (selView == null) return null;
    MediaSeries<DicomImageElement> s = selView.getSeries();
    if (s == null || s.size(null) < DefaultView2d.MINIMAL_IMAGES_FOR_3D) return null;

    s = LoadLocalDicom.confirmSplittingMultiPhaseSeries(s);
    if (s == null) return null;

    // Capture manual sync targets BEFORE the old view is disposed. The replacement will call
    // disposeView() → resetSynchState(), which clears the per-view sync wiring.
    List<ViewCanvas<DicomImageElement>> manualSyncTargets = collectManualSyncTargets(selView);

    container.setSelectedAndGetFocus();
    MipView newView = MipView.swapIn(em, container, selView);
    if (newView == null) {
      return null;
    }

    // Re-establish manual sync between the new MIP view and the previously linked views.
    // At this point MipView.getImage() still holds the original series image (the async MIP
    // build has not started yet), so SlicePosition tags are available for offset recording.
    if (!manualSyncTargets.isEmpty()) {
      for (ViewCanvas<DicomImageElement> target : manualSyncTargets) {
        newView.syncManuallyWith(target);
      }
      em.updateAllListeners(container, container.getSynchView());
    }

    // Restore focus after the replacement is rendered
    SwingUtilities.invokeLater(() -> container.setSelectedImagePaneFromFocus(newView));
    return newView;
  }

  /**
   * Returns the list of views that {@code selView} is currently manually synchronized with. Called
   * before the view is replaced so that the wiring can be transferred to its successor.
   */
  @SuppressWarnings("unchecked")
  private static List<ViewCanvas<DicomImageElement>> collectManualSyncTargets(
      ViewCanvas<DicomImageElement> selView) {
    ViewSynchData synchData =
        (ViewSynchData) selView.getActionsInView().get(ActionW.SYNCH_LINK.cmd());
    if (synchData == null || !synchData.isManualSynchActivated()) {
      return List.of();
    }
    List<ViewCanvas<DicomImageElement>> targets = new ArrayList<>();
    for (ViewSynchData.ManualSyncData msd : synchData.getManualSyncDataSet()) {
      targets.add((ViewCanvas<DicomImageElement>) msd.getTargetPane());
    }
    return targets;
  }
}
