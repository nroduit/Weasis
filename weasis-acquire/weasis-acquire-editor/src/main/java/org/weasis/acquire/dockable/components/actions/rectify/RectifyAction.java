/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.rectify;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.graphics.CropRectangleGraphic;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.MaskOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;

/**
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class RectifyAction extends AbstractAcquireAction {
  private static final Logger LOGGER = LoggerFactory.getLogger(RectifyAction.class);

  private static final CropRectangleGraphic CROP_RECTANGLE_GRAPHIC = new CropRectangleGraphic();

  private RectangleGraphic currentCropArea;
  private final List<Graphic> graphics;

  public RectifyAction(AcquireActionButtonsPanel panel) {
    super(panel);
    this.graphics = new ArrayList<>();
  }

  public void init(GraphicModel model, AcquireImageInfo imageInfo) {
    graphics.clear();

    AffineTransform transform = getAffineTransform(imageInfo, true);
    model
        .getModels()
        .forEach(
            g -> {
              Graphic copy = g.copy();
              copy.getPts().forEach(p -> transform.transform(p, p));
              graphics.add(copy);
            });

    for (GraphicLayer layer : new ArrayList<>(model.getLayers())) {
      if (LangUtil.getNULLtoFalse(layer.getSerializable())) {
        model.deleteByLayer(layer);
      }
    }
  }

  protected void updateCropGraphic() {
    AcquireImageInfo imageInfo = getImageInfo();
    ViewCanvas<ImageElement> view = getView();

    CropRectangleGraphic.updateCropDisplay(imageInfo);
    PlanarImage img = view.getSourceImage();
    if (img != null) {
      view.setSelected(false);
      Rectangle2D modelArea = view.getViewModel().getModelArea();
      Rectangle2D area =
          Optional.ofNullable((Rectangle2D) imageInfo.getNextValues().getCropZone())
              .orElse(modelArea);
      try {
        if (currentCropArea == null) {
          currentCropArea = CROP_RECTANGLE_GRAPHIC.copy().buildGraphic(area);
        } else {
          currentCropArea.buildGraphic(area);
        }
        if (!view.getGraphicManager().getModels().contains(currentCropArea)) {
          AbstractGraphicModel.addGraphicToModel(view, currentCropArea);
        }
        currentCropArea.setSelected(true);

        GeomUtil.growRectangle(modelArea, 15);
        double viewportWidth = view.getJComponent().getWidth() - 1.0;
        double viewportHeight = view.getJComponent().getHeight() - 1.0;
        view.zoom(
            Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight()));
      } catch (InvalidShapeException e) {
        LOGGER.error("Build crop graphic", e);
      }
    }
  }

  @Override
  public void cancel() {
    AcquireImageInfo imageInfo = getImageInfo();
    ViewCanvas<ImageElement> view = getView();
    boolean dirty = imageInfo.isDirty();

    if (dirty) {
      if (view != null) {
        imageInfo.removeLayer(view);
        view.getGraphicManager().deleteByLayerType(LayerType.DICOM_PR);
        AffineTransform transform =
            imageInfo.getAffineTransform(imageInfo.getCurrentValues().getFullRotation(), false);
        applyGraphicsTransformation(view, null, transform);
      }
      centralPanel.initValues(imageInfo, imageInfo.getCurrentValues());
      updateCropGraphic();
    }
  }

  @Override
  public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
    imageInfo.removeLayer(view);
    this.centralPanel.restoreLastAction();

    if (view.getImageLayer() instanceof RenderedImageLayer && currentCropArea != null) {
      view.getGraphicManager().deleteByLayerType(LayerType.DICOM_PR);
      applyGraphicsTransformation(view, null, getAffineTransform(imageInfo, false));

      // Force dirty value, rotation is always apply in post process
      imageInfo.getCurrentValues().setCropZone(null);
      imageInfo.getNextValues().setCropZone(currentCropArea.getShape().getBounds());
      view.setActionsInView(ActionW.ROTATION.cmd(), 0);
      view.setActionsInView(ActionW.FLIP.cmd(), false);
      imageInfo.getPostProcessOpManager().setParamValue(MaskOp.OP_NAME, MaskOp.P_SHOW, false);
      imageInfo.applyFinalProcessing(view);
      view.getImage().setTag(TagW.ThumbnailPath, null);
      Panner<?> panner = view.getPanner();
      if (panner != null) {
        panner.updateImage();
      }
    }
  }

  @Override
  public void reset(ActionEvent e) {
    AcquireImageInfo imageInfo = getImageInfo();
    ViewCanvas<ImageElement> view = getView();
    boolean dirty = imageInfo.isDirtyFromDefault();

    if (dirty) {
      int confirm =
          JOptionPane.showConfirmDialog(
              (Component) e.getSource(),
              Messages.getString("AbstractAcquireAction.reset_msg"),
              "",
              JOptionPane.YES_NO_OPTION);
      if (confirm == 0) {
        if (view != null) {
          imageInfo.removeLayer(view);
          view.getGraphicManager().deleteByLayerType(LayerType.DICOM_PR);
          applyGraphicsTransformation(view, null, imageInfo.getAffineTransform(0, false));
        }
        centralPanel.initValues(imageInfo, imageInfo.getDefaultValues());
        updateCropGraphic();
      }
    }
  }

  public RectangleGraphic getCurrentCropArea() {
    return currentCropArea;
  }

  public void updateCropDisplay() {
    Optional.ofNullable(currentCropArea)
        .map(CropRectangleGraphic.class::cast)
        .ifPresent(c -> updateCropGraphic());
  }

  @Override
  public AcquireActionPanel newCentralPanel() {
    return new RectifyPanel(this);
  }

  private AffineTransform getAffineTransform(AcquireImageInfo imageInfo, boolean inverse) {
    return imageInfo.getAffineTransform(
        (imageInfo.getNextValues().getFullRotation() + 720) % 360, inverse);
  }

  private void applyGraphicsTransformation(
      ViewCanvas<ImageElement> view, GraphicLayer layer, AffineTransform transform) {
    for (Graphic g : graphics) {
      Graphic copy = g.copy();
      copy.getPts().forEach(p -> transform.transform(p, p));
      copy.buildShape();
      AbstractGraphicModel.addGraphicToModel(view, layer, copy);
    }
  }

  public void updateGraphics(AcquireImageInfo imageInfo) {
    ViewCanvas<ImageElement> view = getView();
    if (view != null) {
      GraphicModel graphicManager = view.getGraphicManager();
      RectangleGraphic cropGraphic = getCurrentCropArea();
      if (cropGraphic != null) {
        graphicManager
            .getModels()
            .removeIf(g -> g.getLayer().getType() == cropGraphic.getLayerType());
      }

      view.getGraphicManager().deleteByLayerType(LayerType.DICOM_PR);

      GraphicLayer layer = new DefaultLayer(LayerType.DICOM_PR);
      layer.setName("Temp"); // NON-NLS
      layer.setSerializable(true);
      layer.setLocked(true);
      layer.setSelectable(false);
      layer.setLevel(380);

      applyGraphicsTransformation(view, layer, getAffineTransform(imageInfo, false));
    }
  }
}
