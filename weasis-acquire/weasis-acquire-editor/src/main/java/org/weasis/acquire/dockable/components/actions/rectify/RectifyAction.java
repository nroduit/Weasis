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

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
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
import org.weasis.opencv.data.PlanarImage;

/**
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 */
public class RectifyAction extends AbstractAcquireAction {
  private static final Logger LOGGER = LoggerFactory.getLogger(RectifyAction.class);

  private static final CropRectangleGraphic CROP_RECTANGLE_GRAPHIC = new CropRectangleGraphic();

  private RectangleGraphic currentCropArea;
  private final List<Graphic> graphics;
  private int prevRotation;

  public RectifyAction(AcquireActionButtonsPanel panel) {
    super(panel);
    this.graphics = new ArrayList<>();
  }

  public void init(GraphicModel model, int rotation) {
    this.prevRotation = rotation;
    graphics.clear();
    model.getModels().forEach(g -> graphics.add(g.copy()));

    for (GraphicLayer layer : new ArrayList<>(model.getLayers())) {
      if (layer.getSerializable()) {
        model.deleteByLayer(layer);
      }
    }
  }

  public List<Graphic> getGraphics() {
    return graphics;
  }

  public int getPrevRotation() {
    return prevRotation;
  }

  protected void updateCropGraphic() {
    AcquireImageInfo imageInfo = getImageInfo();
    ViewCanvas<ImageElement> view = getView();

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

  private static void buildAffineTransform(
      AffineTransform transform,
      AcquireImageValues imageValues,
      Rectangle2D modelArea,
      Point offset) {
    int rotationAngle = imageValues.getFullRotation();

    if (rotationAngle > 0) {
      rotationAngle = (rotationAngle + 720) % 360;
      transform.rotate(
          Math.toRadians(rotationAngle), modelArea.getWidth() / 2.0, modelArea.getHeight() / 2.0);
    }

    if (offset != null) {
      // TODO not consistent with image coordinates after crop
      transform.translate(-offset.getX(), -offset.getY());
    }
  }

  @Override
  public boolean cancel() {
    boolean doCancel = super.cancel();
    updateCropGraphic();
    return doCancel;
  }

  @Override
  public void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view) {
    imageInfo.removeLayer(view);
    this.centralPanel.restoreLastAction();

    if (view.getImageLayer() instanceof RenderedImageLayer && currentCropArea != null) {
      view.getGraphicManager().deleteByLayerType(LayerType.DICOM_PR);

      Rectangle2D modelArea = view.getViewModel().getModelArea();
      AffineTransform transform = getAffineTransform(imageInfo, modelArea);

      for (Graphic g : graphics) {
        Graphic copy = g.copy();
        copy.getPts().forEach(p -> transform.transform(p, p));
        copy.buildShape();
        AbstractGraphicModel.addGraphicToModel(view, copy);
      }

      imageInfo
          .getCurrentValues()
          .setCropZone(null); // Force dirty value, rotation is always apply in post process
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
  public boolean reset(ActionEvent e) {
    boolean doReset = super.reset(e);
    updateCropGraphic();
    return doReset;
  }

  public RectangleGraphic getCurrentCropArea() {
    return currentCropArea;
  }

  public void updateCropDisplay() {
    Optional.ofNullable(currentCropArea)
        .map(CropRectangleGraphic.class::cast)
        .ifPresent(
            c -> {
              c.updateCropDisplay(getImageInfo());
              updateCropGraphic();
            });
  }

  @Override
  public AcquireActionPanel newCentralPanel() {
    return new RectifyPanel(this);
  }

  public AffineTransform getAffineTransform(AcquireImageInfo imageInfo, Rectangle2D modelArea) {
    AffineTransform transform = new AffineTransform();
    int rotation = (imageInfo.getNextValues().getFullRotation() - getPrevRotation() + 720) % 360;
    if (rotation != 0) {
      transform.rotate(
          Math.toRadians(rotation), modelArea.getWidth() / 2.0, modelArea.getHeight() / 2.0);
    }
    return transform;
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

      Rectangle2D modelArea = view.getViewModel().getModelArea();
      AffineTransform transform = getAffineTransform(imageInfo, modelArea);
      GraphicLayer layer = new DefaultLayer(LayerType.DICOM_PR);
      layer.setName("Temp"); // NON-NLS
      layer.setSerializable(true);
      layer.setLocked(true);
      layer.setSelectable(false);
      layer.setLevel(380);
      List<Graphic> graphics = getGraphics();
      for (Graphic g : graphics) {
        Graphic copy = g.copy();
        copy.getPts().forEach(p -> transform.transform(p, p));
        // copy.setShape(transform.createTransformedShape(g.getShape()), null);
        AbstractGraphicModel.addGraphicToModel(view, layer, copy);
      }
    }
  }
}
