/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DateFormat;
import java.util.*;
import javax.swing.ButtonGroup;
import javax.swing.JPopupMenu;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.CIELab;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.AbstractGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.util.EscapeChars;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.lut.DefaultWlPresentation;

/**
 * Manager for DICOM Presentation State (PR) operations.
 *
 * <p>This class handles the application of DICOM Presentation States to image views, including
 * spatial transformations, graphics overlay, and UI components.
 */
public class PRManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(PRManager.class);

  // Configuration constants
  public static final String PR_APPLY = "weasis.apply.latest.pr";
  public static final String PR_PRESETS = "pr.presets";
  public static final String TAG_CHANGE_PIX_CONFIG = "change.pixel";
  public static final String TAG_PR_ZOOM = "original.zoom";
  public static final String TAG_DICOM_LAYERS = "pr.layers";

  // Internal constants
  private static final String DISPLAY_MODE = "DISPLAY"; // NON-NLS
  private static final String PIXEL_MODE = "PIXEL"; // NON-NLS
  private static final String TRUE_SIZE_MODE = "TRUE SIZE"; // NON-NLS
  private static final String SCALE_TO_FIT_MODE = "SCALE TO FIT"; // NON-NLS
  private static final String MAGNIFY_MODE = "MAGNIFY"; // NON-NLS
  private static final String DICOM_SUFFIX = " [DICOM]"; // NON-NLS
  private static final int DEFAULT_DICOM_FRAME = 1;
  private static final int XML_LAYER_BASE_LEVEL = 270;
  private static final int GRAPHIC_LAYER_BASE_LEVEL = 310;

  private PRManager() {}

  /**
   * Applies a presentation state to the given view.
   *
   * @param view The view canvas to apply the presentation state to
   * @param reader The presentation state reader
   * @param img The DICOM image element
   */
  public static void applyPresentationState(
      ViewCanvas<DicomImageElement> view, PresentationStateReader reader, DicomImageElement img) {
    if (!isValidInput(view, reader, img)) {
      return;
    }

    try {
      PresentationContext context = new PresentationContext(view, reader, img);
      applyWindowLevelSettings(context);
      applySpatialTransformations(context);
      applyGraphics(context);
    } catch (Exception e) {
      LOGGER.error("Error applying presentation state: {}", e.getMessage(), e);
    }
  }

  /**
   * Deletes DICOM layers from the graphic manager.
   *
   * @param layers The layers to delete
   * @param graphicManager The graphic manager
   */
  public static void deleteDicomLayers(List<GraphicLayer> layers, GraphicModel graphicManager) {
    if (layers != null) {
      layers.forEach(graphicManager::deleteByLayer);
    }
  }

  /**
   * Builds a presentation state selection button for the view.
   *
   * @param view The 2D view
   * @param series The media series
   * @param img The DICOM image element
   * @return The view button or null if no PR elements available
   */
  public static ViewButton buildPrSelection(
      final View2d view, MediaSeries<DicomImageElement> series, DicomImageElement img) {
    if (!isValidInput(view, series, img)) {
      return null;
    }

    List<PRSpecialElement> prList = DicomModel.getPrSpecialElements(series, img);
    if (prList.isEmpty()) {
      return null;
    }

    PrSelectionHandler selectionHandler = new PrSelectionHandler(view, series, prList);
    return selectionHandler.createButton();
  }

  /**
   * Extracts PrDicomObject from a presentation state element.
   *
   * @param prElement The PR element
   * @return The PrDicomObject or null
   */
  public static PrDicomObject getPrDicomObject(Object prElement) {
    return prElement instanceof PRSpecialElement pr ? pr.getPrDicomObject() : null;
  }

  // ========== Private Helper Methods ==========

  private static boolean isValidInput(Object... objects) {
    return Arrays.stream(objects).allMatch(Objects::nonNull);
  }

  private static void applyWindowLevelSettings(PresentationContext context) {
    Map<String, Object> actionsInView = context.view().getActionsInView();
    context.reader().applySpatialTransformationModule(actionsInView);

    boolean pixelPadding = getPixelPaddingValue(context.view());
    DefaultWlPresentation wlp =
        new DefaultWlPresentation(context.reader().getPrDicomObject(), pixelPadding);

    List<PresetWindowLevel> presetList = context.img().getPresetList(wlp, true);
    if (!presetList.isEmpty()) {
      PresetWindowLevel preset = presetList.getFirst();

      actionsInView.put(ActionW.WINDOW.cmd(), preset.getWindow());
      actionsInView.put(ActionW.LEVEL.cmd(), preset.getLevel());
      actionsInView.put(PR_PRESETS, presetList);
      actionsInView.put(ActionW.PRESET.cmd(), preset);
      actionsInView.put(ActionW.LUT_SHAPE.cmd(), preset.getLutShape());
      actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), true);
    }
  }

  private static boolean getPixelPaddingValue(ViewCanvas<DicomImageElement> view) {
    ImageOpNode node = view.getDisplayOpManager().getNode(WindowOp.OP_NAME);
    if (node == null) {
      return true;
    }
    return LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
  }

  private static void applySpatialTransformations(PresentationContext context) {
    context.reader().readDisplayArea(context.img());

    new PixelSpacingProcessor(context).process();
    new DisplayAreaProcessor(context).process();
    new ZoomProcessor(context).process();
  }

  private static void applyGraphics(PresentationContext context) {
    GraphicModel graphicModel =
        PrGraphicUtil.getPresentationModel(context.reader().getDicomObject());

    List<GraphicLayer> layers =
        (graphicModel != null)
            ? readXmlModel(context.view(), graphicModel)
            : readGraphicAnnotation(context);

    if (layers != null) {
      context.view().setActionsInView(TAG_DICOM_LAYERS, layers);
    }
  }

  private static List<GraphicLayer> readXmlModel(
      ViewCanvas<DicomImageElement> view, GraphicModel graphicModel) {
    List<GraphicLayer> layers = new ArrayList<>();

    int layerIndex = 0;
    for (GraphicLayer layer : graphicModel.getLayers()) {
      GraphicLayer processedLayer = processXmlLayer(layer, layerIndex++);
      layers.add(processedLayer);
    }
    // Add graphics to the view
    for (Graphic graphic : graphicModel.getModels()) {
      AbstractGraphicModel.addGraphicToModel(view, graphic.getLayer(), graphic);
    }

    return layers;
  }

  private static GraphicLayer processXmlLayer(GraphicLayer layer, int index) {
    String layerName =
        Optional.ofNullable(layer.getName()).orElseGet(() -> layer.getType().getDefaultName());

    layer.setName(layerName + DICOM_SUFFIX);
    layer.setLocked(true);
    layer.setSerializable(false);
    layer.setLevel(XML_LAYER_BASE_LEVEL + index);

    return layer;
  }

  private static List<GraphicLayer> readGraphicAnnotation(PresentationContext context) {
    Attributes attributes = context.reader().getDicomObject();
    if (attributes == null) {
      return null;
    }

    GraphicAnnotationReader reader = new GraphicAnnotationReader(context, attributes);
    return reader.readAnnotations();
  }

  // ========== Helper Classes ==========

  /** Context object holding all necessary data for presentation state processing. */
  private record PresentationContext(
      ViewCanvas<DicomImageElement> view, PresentationStateReader reader, DicomImageElement img) {}

  /** Processes pixel spacing settings from presentation state. */
  private static class PixelSpacingProcessor {
    private final PresentationContext context;
    private final Map<String, Object> actionsInView;

    public PixelSpacingProcessor(PresentationContext context) {
      this.context = context;
      this.actionsInView = context.view().getActionsInView();
    }

    public void process() {
      String presentationMode =
          TagD.getTagValue(context.reader(), Tag.PresentationSizeMode, String.class);
      boolean trueSize = TRUE_SIZE_MODE.equalsIgnoreCase(presentationMode);

      processPixelSpacing(trueSize);
      processAspectRatio();
    }

    private void processPixelSpacing(boolean trueSize) {
      double[] prPixSize =
          TagD.getTagValue(context.reader(), Tag.PresentationPixelSpacing, double[].class);
      if (prPixSize != null && prPixSize.length == 2 && prPixSize[0] > 0.0 && prPixSize[1] > 0.0) {
        if (trueSize) {
          changePixelSize(context.img(), actionsInView, prPixSize);
          context.img().setPixelSpacingUnit(Unit.MILLIMETER);
          EventManager.getInstance()
              .getAction(ActionW.SPATIAL_UNIT)
              .ifPresent(c -> c.setSelectedItem(Unit.MILLIMETER));
        } else {
          applyAspectRatio(context.img(), actionsInView, prPixSize);
        }
      }
    }

    private void processAspectRatio() {
      double[] prPixSize =
          TagD.getTagValue(context.reader(), Tag.PresentationPixelSpacing, double[].class);
      if (prPixSize == null) {
        int[] aspects =
            TagD.getTagValue(context.reader(), Tag.PresentationPixelAspectRatio, int[].class);
        if (aspects != null && aspects.length == 2) {
          applyAspectRatio(context.img(), actionsInView, new double[] {aspects[0], aspects[1]});
        }
      }
    }

    private static void applyAspectRatio(
        DicomImageElement img, Map<String, Object> actionsInView, double[] aspects) {
      if (MathUtil.isDifferent(aspects[0], aspects[1])) {
        double[] pixelSize =
            aspects[1] < aspects[0]
                ? new double[] {aspects[0] / aspects[1], 1.0}
                : new double[] {1.0, aspects[1] / aspects[0]};

        changePixelSize(img, actionsInView, pixelSize);
        img.setPixelSpacingUnit(Unit.PIXEL);
      }
    }
  }

  /** Processes display area settings from presentation state. */
  private static class DisplayAreaProcessor {
    private final PresentationContext context;
    private final Map<String, Object> actionsInView;

    public DisplayAreaProcessor(PresentationContext context) {
      this.context = context;
      this.actionsInView = context.view().getActionsInView();
    }

    public void process() {
      int[] tlhc =
          TagD.getTagValue(context.reader(), Tag.DisplayedAreaTopLeftHandCorner, int[].class);
      int[] brhc =
          TagD.getTagValue(context.reader(), Tag.DisplayedAreaBottomRightHandCorner, int[].class);

      if (tlhc != null && tlhc.length == 2 && brhc != null && brhc.length == 2) {
        Rectangle cropArea = calculateCropArea(tlhc, brhc);
        applyCropArea(cropArea);
      }
    }

    private Rectangle calculateCropArea(int[] tlhc, int[] brhc) {
      // Adjust for systems that encode topLeft as 1,1 instead of 0,0
      if (tlhc[0] == 1) tlhc[0] = 0;
      if (tlhc[1] == 1) tlhc[1] = 0;
      Rectangle area = new Rectangle();
      double ratioX = context.img().getRescaleX();
      double ratioY = context.img().getRescaleY();
      area.setFrameFromDiagonal(
          getDisplayLength(tlhc[0], ratioX),
          getDisplayLength(tlhc[1], ratioY),
          getDisplayLength(brhc[0], ratioX),
          getDisplayLength(brhc[1], ratioY));

      return area;
    }

    private static int getDisplayLength(int length, double ratio) {
      return (int) Math.ceil(length * ratio - 0.5);
    }

    private void applyCropArea(Rectangle area) {
      PlanarImage source = context.view().getSourceImage();
      if (source == null) {
        return;
      }
      Rectangle imgBounds = ImageConversion.getBounds(source);
      area = area.intersection(imgBounds);
      if (area.width > 1 && area.height > 1 && !area.equals(imgBounds)) {
        SimpleOpManager opManager =
            Optional.ofNullable((SimpleOpManager) actionsInView.get(ActionW.PREPROCESSING.cmd()))
                .orElseGet(SimpleOpManager::new);
        CropOp crop = new CropOp();
        crop.setParam(CropOp.P_AREA, area);
        opManager.addImageOperationAction(crop);
        actionsInView.put(ActionW.PREPROCESSING.cmd(), opManager);
      }
      actionsInView.put(ActionW.CROP.cmd(), area);
    }
  }

  /** Processes zoom settings from the Presentation State. */
  private static class ZoomProcessor {
    private final PresentationContext context;
    private final Map<String, Object> actionsInView;

    public ZoomProcessor(PresentationContext context) {
      this.context = context;
      this.actionsInView = context.view().getActionsInView();
    }

    public void process() {
      String presentationMode =
          TagD.getTagValue(context.reader(), Tag.PresentationSizeMode, String.class);

      switch (presentationMode != null ? presentationMode.toUpperCase() : "") {
        case SCALE_TO_FIT_MODE -> actionsInView.put(TAG_PR_ZOOM, -200.0);
        case MAGNIFY_MODE -> {
          Float magnification =
              TagD.getTagValue(
                  context.reader(), Tag.PresentationPixelMagnificationRatio, Float.class);
          actionsInView.put(TAG_PR_ZOOM, magnification != null ? magnification : 1.0);
        }
        // Required to calibrate the screen in Weasis preferences
        case TRUE_SIZE_MODE -> actionsInView.put(TAG_PR_ZOOM, -100.0);
      }
    }
  }

  /** Reads graphic annotations from DICOM presentation state. */
  private static class GraphicAnnotationReader {
    private final PresentationContext context;
    private final Attributes attributes;
    private final Map<String, Object> actionsInView;
    private final String imgSop;
    private final int dicomFrame;

    public GraphicAnnotationReader(PresentationContext context, Attributes attributes) {
      this.context = context;
      this.attributes = attributes;
      this.actionsInView = context.view().getActionsInView();
      this.imgSop = TagD.getTagValue(context.img(), Tag.SOPInstanceUID, String.class);
      this.dicomFrame =
          context.img().getKey() instanceof Integer intVal ? intVal + 1 : DEFAULT_DICOM_FRAME;
    }

    public List<GraphicLayer> readAnnotations() {
      Sequence graphicSequence = attributes.getSequence(Tag.GraphicAnnotationSequence);
      Sequence layerSequence = attributes.getSequence(Tag.GraphicLayerSequence);

      if (graphicSequence == null || layerSequence == null) {
        return null;
      }

      Map<String, Attributes> layerMap = createLayerMap(layerSequence);
      TransformationContext transformContext = createTransformationContext();

      List<GraphicLayer> layers = new ArrayList<>();

      for (Attributes annotation : graphicSequence) {
        processGraphicAnnotation(annotation, layerMap, transformContext, layers);
      }
      return layers;
    }

    private Map<String, Attributes> createLayerMap(Sequence layerSequence) {
      Map<String, Attributes> layerMap = HashMap.newHashMap(layerSequence.size());
      for (Attributes layer : layerSequence) {
        layerMap.put(layer.getString(Tag.GraphicLayer), layer);
      }
      return layerMap;
    }

    private TransformationContext createTransformationContext() {
      int rotation =
          (Integer) actionsInView.getOrDefault(PresentationStateReader.TAG_PR_ROTATION, 0);
      boolean flip =
          (Boolean) actionsInView.getOrDefault(PresentationStateReader.TAG_PR_FLIP, false);
      Rectangle area = (Rectangle) actionsInView.get(ActionW.CROP.cmd());
      Rectangle2D modelArea = context.view().getViewModel().getModelArea();

      double width = area != null ? area.getWidth() : modelArea.getWidth();
      double height = area != null ? area.getHeight() : modelArea.getHeight();

      AffineTransform inverse = createInverseTransform(rotation, flip, area);

      return new TransformationContext(width, height, inverse);
    }

    private AffineTransform createInverseTransform(int rotation, boolean flip, Rectangle area) {
      if (rotation == 0 && !flip) {
        return null;
      }

      double offsetX = area != null ? area.getX() / area.getWidth() : 0.0;
      double offsetY = area != null ? area.getY() / area.getHeight() : 0.0;

      AffineTransform inverse = AffineTransform.getTranslateInstance(offsetX, offsetY);
      if (flip) {
        inverse.scale(-1.0, 1.0);
        inverse.translate(-1.0, 0.0);
      }
      if (rotation != 0) {
        inverse.rotate(Math.toRadians(rotation), 0.5, 0.5);
      }
      return inverse;
    }

    private void processGraphicAnnotation(
        Attributes annotation,
        Map<String, Attributes> layerMap,
        TransformationContext transformContext,
        List<GraphicLayer> layers) {

      String layerName = annotation.getString(Tag.GraphicLayer);
      Attributes layerAttributes = layerMap.get(layerName);

      if (!isAnnotationApplicable(annotation, layerAttributes)) {
        return;
      }

      GraphicLayer layer = createGraphicLayer(layerName, layerAttributes);
      layers.add(layer);

      Color layerColor = extractLayerColor(layerAttributes);

      processGraphicObjects(annotation, layer, layerColor, transformContext);
      processTextObjects(annotation, layer, layerColor, transformContext);
    }

    private boolean isAnnotationApplicable(Attributes annotation, Attributes layerAttributes) {
      return layerAttributes != null
          && DicomObjectUtil.isImageFrameApplicableToReferencedImageSequence(
              DicomObjectUtil.getSequence(annotation, Tag.ReferencedImageSequence),
              Tag.ReferencedFrameNumber,
              imgSop,
              dicomFrame,
              false);
    }

    private GraphicLayer createGraphicLayer(String layerName, Attributes layerAttributes) {
      GraphicLayer layer = new DefaultLayer(LayerType.DICOM_PR);
      layer.setName(layerName + DICOM_SUFFIX);
      layer.setSerializable(false);
      layer.setLocked(true);
      layer.setSelectable(false);
      layer.setLevel(GRAPHIC_LAYER_BASE_LEVEL + layerAttributes.getInt(Tag.GraphicLayerOrder, 0));
      return layer;
    }

    private Color extractLayerColor(Attributes layerAttributes) {
      Integer grayValue =
          DicomUtils.getIntegerFromDicomElement(
              layerAttributes, Tag.GraphicLayerRecommendedDisplayGrayscaleValue, null);

      int[] colorRgb =
          CIELab.dicomLab2rgb(
              DicomUtils.getIntArrayFromDicomElement(
                  layerAttributes, Tag.GraphicLayerRecommendedDisplayCIELabValue, null));
      if (colorRgb.length == 0) {
        colorRgb =
            DicomUtils.getIntArrayFromDicomElement(
                layerAttributes, Tag.GraphicLayerRecommendedDisplayRGBValue, null);

        if (colorRgb == null && grayValue == null) {
          Color defaultColor =
              Optional.ofNullable(MeasureTool.viewSetting.getLineColor()).orElse(Color.YELLOW);
          colorRgb =
              new int[] {defaultColor.getRed(), defaultColor.getGreen(), defaultColor.getBlue()};
        }
      }
      return DicomObjectUtil.getRGBColor(grayValue != null ? grayValue : 255, colorRgb);
    }

    private void processGraphicObjects(
        Attributes annotation,
        GraphicLayer layer,
        Color layerColor,
        TransformationContext transformContext) {

      Sequence graphicSequence = annotation.getSequence(Tag.GraphicObjectSequence);
      if (graphicSequence == null) {
        return;
      }

      for (Attributes graphicObject : graphicSequence) {
        try {
          Graphic graphic =
              PrGraphicUtil.buildGraphic(
                  graphicObject,
                  layerColor,
                  false,
                  transformContext.width(),
                  transformContext.height(),
                  true,
                  transformContext.inverse(),
                  false);
          if (graphic != null) {
            AbstractGraphicModel.addGraphicToModel(context.view(), layer, graphic);
          }
        } catch (InvalidShapeException e) {
          LOGGER.error("Cannot create graphic: {}", e.getMessage(), e);
        }
      }
    }

    private void processTextObjects(
        Attributes annotation,
        GraphicLayer layer,
        Color layerColor,
        TransformationContext transformContext) {

      Sequence textSequence = annotation.getSequence(Tag.TextObjectSequence);
      if (textSequence == null) {
        return;
      }

      for (Attributes textObject : textSequence) {
        TextObjectProcessor processor =
            new TextObjectProcessor(textObject, layer, layerColor, transformContext);
        processor.process();
      }
    }

    /** Processes individual text objects from presentation state. */
    private class TextObjectProcessor {
      private final Attributes textObject;
      private final GraphicLayer layer;
      private final Color layerColor;
      private final TransformationContext context;

      public TextObjectProcessor(
          Attributes textObject,
          GraphicLayer layer,
          Color layerColor,
          TransformationContext transformContext) {
        this.textObject = textObject;
        this.layer = layer;
        this.layerColor = layerColor;
        this.context = transformContext;
      }

      public void process() {
        Color textColor = extractTextColor();
        Float thickness = extractThickness();
        String[] textLines = extractTextLines();

        processAnchoredText(textColor, thickness, textLines);
        processBoundedText(textColor, thickness, textLines);
      }

      private Color extractTextColor() {
        Attributes style = textObject.getNestedDataset(Tag.LineStyleSequence);
        if (style != null) {
          int[] rgb = CIELab.dicomLab2rgb(style.getInts(Tag.PatternOnColorCIELabValue));
          return DicomObjectUtil.getRGBColor(0xFFFF, rgb);
        }
        return layerColor;
      }

      private Float extractThickness() {
        Attributes style = textObject.getNestedDataset(Tag.LineStyleSequence);
        return DicomUtils.getFloatFromDicomElement(style, Tag.LineThickness, 1.0f);
      }

      private String[] extractTextLines() {
        return EscapeChars.convertToLines(textObject.getString(Tag.UnformattedTextValue));
      }

      private void processAnchoredText(Color textColor, Float thickness, String[] textLines) {
        float[] anchor = textObject.getFloats(Tag.AnchorPoint);
        if (anchor == null || anchor.length != 2) {
          return;
        }

        boolean isDisplay =
            DISPLAY_MODE.equalsIgnoreCase(textObject.getString(Tag.AnchorPointAnnotationUnits));

        double x = isDisplay ? anchor[0] * context.width() : anchor[0];
        double y = isDisplay ? anchor[1] * context.height() : anchor[1];
        Point2D.Double anchorPoint = new Point2D.Double(x, y);

        Rectangle2D boundingBox = extractBoundingBox();
        // Use the box center. This does not follow the DICOM standard.
        Point2D.Double boxPoint =
            boundingBox != null
                ? new Point2D.Double(boundingBox.getCenterX(), boundingBox.getCenterY())
                : anchorPoint;

        if (!PresentationStateReader.getBooleanValue(textObject, Tag.AnchorPointVisibility)) {
          anchorPoint = null;
        }

        if (anchorPoint != null && anchorPoint.equals(boxPoint)) {
          boxPoint = new Point2D.Double(anchorPoint.getX() + 20, anchorPoint.getY() + 50);
        }

        createAnnotationGraphic(anchorPoint, boxPoint, textColor, thickness, textLines);
      }

      private void processBoundedText(Color textColor, Float thickness, String[] textLines) {
        float[] anchor = textObject.getFloats(Tag.AnchorPoint);
        if (anchor != null && anchor.length == 2) {
          return; // Already processed as anchored text
        }

        Rectangle2D boundingBox = extractBoundingBox();
        if (boundingBox != null) {
          Point2D point = new Point2D.Double(boundingBox.getMinX(), boundingBox.getMinY());
          createPointAnnotation(point, textColor, thickness, textLines);
        }
      }

      private Rectangle2D extractBoundingBox() {
        boolean isDisplay =
            DISPLAY_MODE.equalsIgnoreCase(textObject.getString(Tag.BoundingBoxAnnotationUnits));

        float[] topLeft = textObject.getFloats(Tag.BoundingBoxTopLeftHandCorner);
        float[] bottomRight = textObject.getFloats(Tag.BoundingBoxBottomRightHandCorner);

        if (topLeft == null || bottomRight == null) {
          return null;
        }

        // Ensure correct coordinate order
        if (topLeft[0] > bottomRight[0]) {
          float temp = topLeft[0];
          topLeft[0] = bottomRight[0];
          bottomRight[0] = temp;
        }
        if (topLeft[1] > bottomRight[1]) {
          float temp = topLeft[1];
          topLeft[1] = bottomRight[1];
          bottomRight[1] = temp;
        }
        Rectangle2D rect =
            new Rectangle2D.Double(
                topLeft[0], topLeft[1], bottomRight[0] - topLeft[0], bottomRight[1] - topLeft[1]);
        if (isDisplay) {
          rect.setRect(
              rect.getX() * context.width(),
              rect.getY() * context.height(),
              rect.getWidth() * context.width(),
              rect.getHeight() * context.height());

          if (context.inverse() != null) {
            float[] dstPt1 = new float[2];
            float[] dstPt2 = new float[2];
            context.inverse().transform(topLeft, 0, dstPt1, 0, 1);
            context.inverse().transform(bottomRight, 0, dstPt2, 0, 1);
            rect.setFrameFromDiagonal(
                dstPt1[0] * context.width(),
                dstPt1[1] * context.height(),
                dstPt2[0] * context.width(),
                dstPt2[1] * context.height());
          }
        }

        return rect;
      }

      private void createAnnotationGraphic(
          Point2D.Double anchorPoint,
          Point2D.Double boxPoint,
          Color textColor,
          Float thickness,
          String[] textLines) {
        try {
          List<Point2D> points = new ArrayList<>(2);
          points.add(anchorPoint);
          points.add(boxPoint);

          Graphic graphic = new AnnotationGraphic().buildGraphic(points);
          graphic.setPaint(textColor);
          graphic.setLineThickness(thickness);
          graphic.setLabelVisible(Boolean.TRUE);
          graphic.setLabel(textLines, GraphicAnnotationReader.this.context.view());

          AbstractGraphicModel.addGraphicToModel(
              GraphicAnnotationReader.this.context.view(), layer, graphic);
        } catch (InvalidShapeException e) {
          LOGGER.error("Cannot create annotation: {}", e.getMessage(), e);
        }
      }

      private void createPointAnnotation(
          Point2D point, Color textColor, Float thickness, String[] textLines) {
        try {
          AbstractGraphic pointGraphic =
              (AbstractGraphic) new PointGraphic().buildGraphic(Collections.singletonList(point));

          pointGraphic.setLineThickness(thickness);
          pointGraphic.setLabelVisible(Boolean.TRUE);

          AbstractGraphicModel.addGraphicToModel(
              GraphicAnnotationReader.this.context.view(), layer, pointGraphic);
          pointGraphic.setShape(null, null);
          pointGraphic.setLabel(textLines, GraphicAnnotationReader.this.context.view(), point);
        } catch (InvalidShapeException e) {
          LOGGER.error("Cannot create annotation: {}", e.getMessage(), e);
        }
      }
    }
  }

  /** Handles presentation state selection UI operations. */
  private record PrSelectionHandler(
      View2d view, MediaSeries<DicomImageElement> series, List<PRSpecialElement> prList) {

    public ViewButton createButton() {
      setupInitialPrState();
      return new ViewButton(
          this::showPrSelectionMenu,
          ResourceUtil.getIcon(OtherIcon.IMAGE_PRESENTATION).derive(24, 24),
          ActionW.PR_STATE.getTitle());
    }

    private void setupInitialPrState() {
      Object currentPr = view.getActionValue(ActionW.PR_STATE.cmd());

      if (!view.getEventManager().getOptions().getBooleanProperty(PR_APPLY, false)) {
        currentPr = ActionState.NoneLabel.NONE_SERIES;
        view.setActionsInView(ActionW.PR_STATE.cmd(), currentPr);
      }

      if (currentPr == null
          || currentPr.equals(ActionState.NoneLabel.NONE)
          || currentPr instanceof PRSpecialElement) {
        // Set the previous selected value, otherwise set the more recent PR by default
        var selectedPr = prList.contains(currentPr) ? currentPr : prList.getFirst();
        view.setPresentationState(selectedPr, true);
      }
    }

    private Object[] createMenuItems() {
      int offset = series.size(null) > 1 ? 2 : 1;
      Object[] items = new Object[prList.size() + offset];
      items[0] = ActionState.NoneLabel.NONE;
      if (offset == 2) {
        items[1] = ActionState.NoneLabel.NONE_SERIES;
      }
      for (int i = 0; i < prList.size(); i++) {
        items[i + offset] = prList.get(i);
      }

      return items;
    }

    private void showPrSelectionMenu(Component invoker, int x, int y) {
      Object currentPr = view.getActionValue(ActionW.PR_STATE.cmd());
      JPopupMenu popupMenu = createPopupMenu(currentPr);
      popupMenu.show(invoker, x, y);
    }

    private JPopupMenu createPopupMenu(Object currentPr) {
      JPopupMenu popupMenu = new JPopupMenu();
      popupMenu.add(new TitleMenuItem(ActionW.PR_STATE.getTitle()));
      popupMenu.addSeparator();
      ButtonGroup buttonGroup = new ButtonGroup();
      Object[] items = createMenuItems();

      for (Object item : items) {
        String title;
        if (item instanceof PRSpecialElement pr) {
          title = buildPrTitle(pr);
        } else {
          title = item.toString();
        }
        RadioMenuItem menuItem = new RadioMenuItem(title, null, item, item == currentPr);

        menuItem.addActionListener(
            e -> {
              if (e.getSource() instanceof RadioMenuItem radioItem) {
                view.setPresentationState(radioItem.getUserObject(), false);
              }
            });
        buttonGroup.add(menuItem);
        popupMenu.add(menuItem);
      }

      return popupMenu;
    }
  }

  // ========== Utility Methods ==========

  private static String buildPrTitle(PRSpecialElement pr) {
    StringBuilder title = new StringBuilder(pr.toString());
    PrDicomObject prDicomObject = pr.getPrDicomObject();
    if (prDicomObject != null) {
      Attributes dicomObject = prDicomObject.getDicomObject();
      if (dicomObject != null) {
        Date creationDate = dicomObject.getDate(Tag.PresentationCreationDateAndTime);
        if (creationDate != null) {
          title
              .append(" (")
              .append(
                  DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                      .format(creationDate))
              .append(')');
        }
      }
    }
    return title.toString();
  }

  private static void changePixelSize(
      DicomImageElement img, Map<String, Object> actionsInView, double[] prPixSize) {
    img.setPixelSize(prPixSize[1], prPixSize[0]);
    actionsInView.put(TAG_CHANGE_PIX_CONFIG, true);

    ZoomOp zoomOp = img.getRectifyAspectRatioZoomOp();
    if (zoomOp != null) {
      SimpleOpManager process = new SimpleOpManager();
      process.addImageOperationAction(zoomOp);

      OpManager preprocessing = (OpManager) actionsInView.get(ActionW.PREPROCESSING.cmd());
      if (preprocessing != null) {
        preprocessing.getOperations().stream()
            .filter(op -> !zoomOp.getName().equals(op.getName()))
            .forEach(process::addImageOperationAction);
      }

      actionsInView.put(ActionW.PREPROCESSING.cmd(), process);
    }
  }

  // ========== Records ==========

  private record TransformationContext(double width, double height, AffineTransform inverse) {}
}
