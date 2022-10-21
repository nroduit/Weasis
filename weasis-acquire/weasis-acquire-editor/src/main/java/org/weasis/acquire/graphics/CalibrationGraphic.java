/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.graphics;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.EditionToolFactory;
import org.weasis.acquire.dockable.components.actions.calibrate.CalibrationPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

public class CalibrationGraphic extends LineGraphic {

  public CalibrationGraphic() {
    super();
    setColorPaint(Color.RED);
  }

  public CalibrationGraphic(CalibrationGraphic calibrationGraphic) {
    super(calibrationGraphic);
  }

  @Override
  public void buildShape(MouseEventDouble mouseevent) {
    super.buildShape(mouseevent);
    ViewCanvas<ImageElement> view = AcquireObject.getView();
    GraphicModel graphicManager = view.getGraphicManager();
    if (graphicManager
        .getModels()
        .removeIf(g -> g.getLayer().getType() == getLayerType() && g != this)) {
      graphicManager.fireChanged();
    }

    if (!getResizingOrMoving()) {
      CalibrationView calibrationDialog = new CalibrationView(this, view, false);
      int res =
          JOptionPane.showConfirmDialog(
              view.getJComponent(),
              calibrationDialog,
              Messages.getString("CalibrationGraphic.calib"),
              JOptionPane.OK_CANCEL_OPTION);
      if (res == JOptionPane.OK_OPTION) {
        calibrationDialog.applyNewCalibration();
        if (calibrationDialog.isApplyingToSeries()) {
          ImageElement image = view.getImage();
          if (image != null) {
            AcquireImageInfo info = AcquireManager.findByImage(image);
            if (info != null) {
              List<AcquireImageInfo> list = AcquireManager.findBySeries(info.getSeries());
              for (AcquireImageInfo acquireImageInfo : list) {
                ImageElement img = acquireImageInfo.getImage();
                if (img != image) {
                  img.setPixelSpacingUnit(image.getPixelSpacingUnit());
                  img.setPixelSize(image.getPixelSize());
                }
              }
            }
          }
        }
      }

      mouseevent.consume();
      view.getEventManager()
          .getAction(EditionToolFactory.DRAW_EDITION)
          .ifPresent(a -> a.setSelectedItem(CalibrationPanel.CALIBRATION_LINE_GRAPHIC));
    }
  }

  @Override
  public Icon getIcon() {
    return LineGraphic.ICON;
  }

  @Override
  public LayerType getLayerType() {
    return LayerType.ACQUIRE;
  }

  @Override
  public String getUIName() {
    return Messages.getString("CalibrationGraphic.calib_line");
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
    return Collections.emptyList();
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return Collections.emptyList();
  }

  @Override
  public CalibrationGraphic copy() {
    return new CalibrationGraphic(this);
  }
}
