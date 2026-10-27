/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.util.concurrent.ExecutionException;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesMenuContributor;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * The <i>Reformat</i> entry of the series context menu of the explorer: the two planes the series
 * does not lie in, each building the reformat as a series of the study and opening it.
 */
@Extension
public class ReformatSeriesMenu implements SeriesMenuContributor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReformatSeriesMenu.class);

  @Override
  public void contribute(MediaSeries<? extends MediaElement> series, JPopupMenu menu) {
    if (!(series instanceof DicomSeries dicom)
        || dicom.getTagValue(TagW.Derivation) != null
        || !ReformatService.canReformat(dicom)) {
      return;
    }
    Plane acquired = ReformatService.planeOf(dicom);
    JMenu reformat = new JMenu(Messages.getString("ReformatMenu.title"));
    for (Plane plane : Plane.values()) {
      if (plane == acquired) {
        continue;
      }
      JMenuItem item = new JMenuItem(ReformatMenu.label(plane));
      item.addActionListener(e -> build(dicom, plane));
      reformat.add(item);
    }
    menu.add(reformat);
  }

  /** Builds the reformat in the background, with the explorer's loading indicator, and opens it. */
  static void build(MediaSeries<DicomImageElement> series, Plane plane) {
    String uid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    DataExplorerModel model =
        series.getTagValue(TagW.ExplorerModel) instanceof DataExplorerModel m ? m : null;
    ExplorerTask<ReformatService.Derived, Void> task =
        new ExplorerTask<>(Messages.getString("ReformatMenu.build"), false) {
          @Override
          protected ReformatService.Derived doInBackground() throws Exception {
            fire(ObservableEvent.BasicAction.LOADING_START);
            return ReformatService.create(uid, plane);
          }

          @Override
          protected void done() {
            fire(ObservableEvent.BasicAction.LOADING_STOP);
            try {
              ReformatService.open(get());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
              LOGGER.error("Reformat failed", e.getCause());
            }
          }

          private void fire(ObservableEvent.BasicAction action) {
            if (model != null) {
              GuiExecutor.execute(
                  () -> model.firePropertyChange(new ObservableEvent(action, model, null, this)));
            }
          }
        };
    task.execute();
  }
}
