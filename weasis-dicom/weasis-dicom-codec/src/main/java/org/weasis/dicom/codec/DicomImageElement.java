/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.awt.image.RenderedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.ImageRendering;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.ImageAdapter;
import org.dcm4che3.img.stream.ImageAdapter.AdaptTransferSyntax;
import org.dcm4che3.img.util.DicomUtils;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.Ultrasound;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlParams;
import org.weasis.opencv.op.lut.WlPresentation;

public class DicomImageElement extends ImageElement implements DicomElement {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomImageElement.class);

  private DicomImageAdapter adapter = null;
  private Collection<LutShape> lutShapeCollection = null;

  public DicomImageElement(DcmMediaReader mediaIO, Object key) {
    super(mediaIO, key);

    initPixelConfiguration();
  }

  public void initPixelConfiguration() {
    this.pixelSizeX = 1.0;
    this.pixelSizeY = 1.0;
    this.pixelSpacingUnit = Unit.PIXEL;

    double[] val = null;
    String modality = TagD.getTagValue(mediaIO, Tag.Modality, String.class);
    if (!"SC".equals(modality) && !"OT".equals(modality)) { // NON-NLS
      // Physical distance in mm between the center of each pixel (ratio in mm)
      val = TagD.getTagValue(mediaIO, Tag.PixelSpacing, double[].class);
      if (val == null || val.length != 2) {
        val = TagD.getTagValue(mediaIO, Tag.ImagerPixelSpacing, double[].class);
        // Follows D. Clunie recommendations
        pixelSizeCalibrationDescription =
            val == null ? null : Messages.getString("DicomImageElement.detector");

      } else {
        pixelSizeCalibrationDescription =
            TagD.getTagValue(mediaIO, Tag.PixelSpacingCalibrationDescription, String.class);
      }
      if (val == null || val.length != 2) {
        val = TagD.getTagValue(mediaIO, Tag.NominalScannedPixelSpacing, double[].class);
      }

      if (val != null && val.length == 2 && val[0] > 0.0 && val[1] > 0.0) {
        /*
         * Pixel Spacing = Row Spacing \ Column Spacing => (Y,X) The first value is the row spacing in mm, that
         * is the spacing between the centers of adjacent rows, or vertical spacing. Pixel Spacing must be
         * always positive, but some DICOMs have negative values
         */
        setPixelSize(val[1], val[0]);
        pixelSpacingUnit = Unit.MILLIMETER;
      }

      // DICOM $C.11.1.1.2 Modality LUT and Rescale Type
      // Specifies the units of the output of the Modality LUT or rescale operation.
      // Defined Terms:
      // OD = The number in the LUT represents thousands of optical density. That is, a value of
      // 2140 represents an optical density of 2.140.
      // HU = Hounsfield Units (CT)
      // US = Unspecified
      // Other values are permitted, but are not defined by the DICOM Standard.
      pixelValueUnit = TagD.getTagValue(this, Tag.RescaleType, String.class);
      if (pixelValueUnit == null) {
        // For some other modalities like PET
        pixelValueUnit = TagD.getTagValue(this, Tag.Units, String.class);
      }
      if (pixelValueUnit == null && "CT".equals(modality)) {
        pixelValueUnit = "HU";
      } else if (pixelSpacingUnit == Unit.PIXEL && "US".equals(modality)) {
        Attributes spatialCalib =
            Ultrasound.getUniqueSpatialRegion(getMediaReader().getDicomObject());
        if (spatialCalib != null) {
          Double calibX =
              DicomMediaUtils.getDoubleFromDicomElement(spatialCalib, Tag.PhysicalDeltaX, null);
          Double calibY =
              DicomMediaUtils.getDoubleFromDicomElement(spatialCalib, Tag.PhysicalDeltaY, null);
          if (calibX != null && calibY != null) {
            calibX = Math.abs(calibX);
            calibY = Math.abs(calibY);
            // Do not apply when value X and Y are different, otherwise the image will be stretched
            if (MathUtil.isEqual(calibX, calibY)) {
              setPixelSize(calibX, calibY);
              pixelSpacingUnit = Unit.CENTIMETER;
            }
          }
        }
      }
    }

    if (val == null) {
      int[] aspects = TagD.getTagValue(mediaIO, Tag.PixelAspectRatio, int[].class);
      if (aspects != null && aspects.length == 2 && aspects[0] != aspects[1]) {
        /*
         * Set the Pixel Aspect Ratio to the pixel size of the image to stretch the rendered image (for having
         * square pixel on the display image)
         */
        if (aspects[1] < aspects[0]) {
          setPixelSize(1.0, (double) aspects[0] / (double) aspects[1]);
        } else {
          setPixelSize((double) aspects[1] / (double) aspects[0], 1.0);
        }
      }
    }
  }

  /**
   * @return return the min value after modality pixel transformation and after pixel padding
   *     operation if padding exists.
   */
  @Override
  public double getMinValue(WlPresentation wlp) {
    if (adapter != null) {
      return adapter.getMinValue(wlp);
    }
    return 0.0;
  }

  /**
   * @return return the max value after modality pixel transformation and after pixel padding
   *     operation if padding exists.
   */
  @Override
  public double getMaxValue(WlPresentation wlp) {
    if (adapter != null) {
      return adapter.getMaxValue(wlp);
    }
    return 0.0;
  }

  @Override
  protected boolean isGrayImage(RenderedImage source) {
    Boolean val = (Boolean) getTagValue(TagW.MonoChrome);
    return val == null || val;
  }

  /**
   * Data representation of the pixel samples. Each sample shall have the same pixel representation.
   * Enumerated Values: 0000H = unsigned integer. 0001H = 2's complement
   *
   * @return true if Tag exist and if explicitly defined a signed
   * @see "DICOM standard PS 3.3 - §C.7.6.3 - Image Pixel Module"
   */
  public boolean isPixelRepresentationSigned() {
    Integer pixelRepresentation = TagD.getTagValue(this, Tag.PixelRepresentation, Integer.class);
    return (pixelRepresentation != null) && (pixelRepresentation != 0);
  }

  public int getBitsStored() {
    return TagD.getTagValue(this, Tag.BitsStored, Integer.class);
  }

  public int getBitsAllocated() {
    return TagD.getTagValue(this, Tag.BitsAllocated, Integer.class);
  }

  @Override
  public String toString() {
    return TagD.getTagValue(this, Tag.SOPInstanceUID, String.class);
  }

  @Override
  public DcmMediaReader getMediaReader() {
    return (DcmMediaReader) super.getMediaReader();
  }

  @Override
  public Number pixelToRealValue(Number pixelValue, WlPresentation wlp) {
    if (pixelValue != null && adapter != null) {
      return adapter.pixelToRealValue(pixelValue, wlp);
    }
    return pixelValue;
  }

  /**
   * The value of Photometric Interpretation specifies the intended interpretation of the image
   * pixel data.
   *
   * @return following values (MONOCHROME1 , MONOCHROME2 , PALETTE COLOR ....) Other values are
   *     permitted but the meaning is not defined by this Standard.
   */
  public String getPhotometricInterpretation() {
    return TagD.getTagValue(this, Tag.PhotometricInterpretation, String.class);
  }

  public boolean isPhotometricInterpretationMonochrome() {
    String photometricInterpretation = getPhotometricInterpretation();

    return ("MONOCHROME1".equalsIgnoreCase(photometricInterpretation)
        || "MONOCHROME2".equalsIgnoreCase(photometricInterpretation));
  }

  /**
   * Pixel Padding Value is used to pad grayscale images (those with a Photometric Interpretation of
   * MONOCHROME1 or MONOCHROME2)<br>
   * Pixel Padding Value specifies either a single value of this padding value, or when combined
   * with Pixel Padding Range Limit, a range of values (inclusive) that are padding.<br>
   * <br>
   * <b>Note :</b> It is explicitly described in order to prevent display applications from taking
   * it into account when determining the dynamic range of an image, since the Pixel Padding Value
   * will be outside the range between the minimum and maximum values of the pixels in the native
   * image
   *
   * @see "DICOM standard PS 3.3 - §C.7.5.1.1.2 - Pixel Padding Value and Pixel Padding Range Limit"
   */
  public Integer getPaddingValue() {
    return TagD.getTagValue(this, Tag.PixelPaddingValue, Integer.class);
  }

  public Integer getPaddingLimit() {
    return TagD.getTagValue(this, Tag.PixelPaddingRangeLimit, Integer.class);
  }

  public PlanarImage getModalityLutImage(OpManager manager, DicomImageReadParam params) {
    PlanarImage image = getImage(manager, adapter == null);
    return ImageRendering.getModalityLutImage(image, adapter, params);
  }

  /**
   * @param wl the WlParams value
   * @return 8 bits unsigned Lookup Table
   */
  @Override
  public LookupTableCV getVOILookup(WlParams wl) {
    if (adapter == null) {
      return null;
    }
    return adapter.getVOILookup(wl);
  }

  /**
   * @return default as first element of preset List <br>
   *     Note : null should never be returned since auto is at least one preset
   */
  public PresetWindowLevel getDefaultPreset(WlPresentation wlp) {
    if (adapter != null) {
      return adapter.getDefaultPreset(wlp);
    }
    return null;
  }

  public boolean containsPreset(PresetWindowLevel preset) {
    if (preset != null && adapter != null && adapter.getPresetCollectionSize() > 0) {
      List<PresetWindowLevel> collection = adapter.getPresetList(null);
      if (collection != null) {
        return collection.contains(preset);
      }
    }
    return false;
  }

  public synchronized Collection<LutShape> getLutShapeCollection(WlPresentation wlp) {
    if (lutShapeCollection != null) {
      return lutShapeCollection;
    }

    lutShapeCollection = new LinkedHashSet<>();
    if (adapter != null) {
      List<PresetWindowLevel> presetList = adapter.getPresetList(wlp);
      if (presetList != null) {
        for (PresetWindowLevel preset : presetList) {
          lutShapeCollection.add(preset.getLutShape());
        }
      }
    }
    lutShapeCollection.addAll(LutShape.DEFAULT_FACTORY_FUNCTIONS);

    return lutShapeCollection;
  }

  public List<PresetWindowLevel> getPresetList(WlPresentation wl) {
    return getPresetList(wl, false);
  }

  public List<PresetWindowLevel> getPresetList(WlPresentation wl, boolean reload) {
    if (adapter != null) {
      return adapter.getPresetList(wl, reload);
    }
    return Collections.emptyList();
  }

  @Override
  protected void findMinMaxValues(PlanarImage img, boolean exclude8bitImage) {
    /*
     * This function can be called several times from the inner class Load. min and max will be computed only once.
     */

    if (img != null && !isImageAvailable()) {
      DicomMetaData meta = getMediaReader().getDicomMetaData();
      if (meta != null) {
        adapter = new DicomImageAdapter(img, meta.getImageDescriptor());
        MinMaxLocResult val = adapter.getMinMax();
        if (val != null) {
          this.minPixelValue = val.minVal;
          this.maxPixelValue = val.maxVal;
        }
      }
    }
  }

  public double[] getDisplayPixelSize() {
    return new double[] {pixelSizeX, pixelSizeY};
  }

  @Override
  public LutShape getDefaultShape(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    return (defaultPreset != null) ? defaultPreset.getLutShape() : super.getDefaultShape(null);
  }

  @Override
  public double getDefaultWindow(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    return (defaultPreset != null) ? defaultPreset.getWindow() : super.getDefaultWindow(null);
  }

  @Override
  public double getDefaultLevel(WlPresentation wlp) {
    PresetWindowLevel defaultPreset = getDefaultPreset(wlp);
    return (defaultPreset != null) ? defaultPreset.getLevel() : super.getDefaultLevel(null);
  }

  @Override
  public PlanarImage getRenderedImage(PlanarImage imageSource, Map<String, Object> params) {
    if (imageSource == null) {
      return null;
    }
    DicomMetaData meta = getMediaReader().getDicomMetaData();
    if (meta != null) {
      DicomImageReadParam readParams = new DicomImageReadParam();
      if (params != null) {
        readParams.setPresentationState(
            (PrDicomObject) params.get(WindowAndPresetsOp.P_PR_ELEMENT));

        readParams.setWindowCenter((Double) params.get(ActionW.LEVEL.cmd()));
        readParams.setWindowWidth((Double) params.get(ActionW.WINDOW.cmd()));
        readParams.setLevelMin((Double) params.get(ActionW.LEVEL_MIN.cmd()));
        readParams.setLevelMax((Double) params.get(ActionW.LEVEL_MAX.cmd()));
        readParams.setVoiLutShape((LutShape) params.get(ActionW.LUT_SHAPE.cmd()));

        readParams.setApplyPixelPadding((Boolean) params.get(ActionW.IMAGE_PIX_PADDING.cmd()));
        readParams.setApplyWindowLevelToColorImage((Boolean) params.get(WindowOp.P_APPLY_WL_COLOR));
        readParams.setInverseLut((Boolean) params.get(WindowOp.P_INVERSE_LEVEL));
        readParams.setFillOutsideLutRange((Boolean) params.get(WindowOp.P_FILL_OUTSIDE_LUT));
      }
      return ImageRendering.getVoiLutImage(imageSource, adapter, readParams);
    }
    return null;
  }

  public GeometryOfSlice getDispSliceGeometry() {
    // The geometry is adapted to get square pixel as all the images are displayed with square
    // pixel.
    double[] imgOr = TagD.getTagValue(this, Tag.ImageOrientationPatient, double[].class);
    if (imgOr != null && imgOr.length == 6) {
      double[] pos = TagD.getTagValue(this, Tag.ImagePositionPatient, double[].class);
      if (pos != null && pos.length == 3) {
        Double sliceTickness = TagD.getTagValue(this, Tag.SliceThickness, Double.class);
        if (sliceTickness == null) {
          sliceTickness = getPixelSize();
        }
        double[] spacing = {getPixelSize(), getPixelSize(), sliceTickness};
        Integer rows = TagD.getTagValue(this, Tag.Rows, Integer.class);
        Integer columns = TagD.getTagValue(this, Tag.Columns, Integer.class);
        if (rows != null && columns != null && rows > 0 && columns > 0) {
          // SliceTickness is only use in IntersectVolume
          // Multiply rows and columns by getZoomScale() to have square pixel image size
          return new GeometryOfSlice(
              new double[] {imgOr[0], imgOr[1], imgOr[2]},
              new double[] {imgOr[3], imgOr[4], imgOr[5]},
              pos,
              spacing,
              sliceTickness,
              new double[] {rows * getRescaleY(), columns * getRescaleX(), 1});
        }
      }
    }
    return null;
  }

  public GeometryOfSlice getSliceGeometry() {
    double[] imgOr = TagD.getTagValue(this, Tag.ImageOrientationPatient, double[].class);
    if (imgOr != null && imgOr.length == 6) {
      double[] pos = TagD.getTagValue(this, Tag.ImagePositionPatient, double[].class);
      if (pos != null && pos.length == 3) {
        Double sliceTickness = TagD.getTagValue(this, Tag.SliceThickness, Double.class);
        if (sliceTickness == null) {
          sliceTickness = getPixelSize();
        }
        double[] pixSize = getDisplayPixelSize();
        double[] spacing = {pixSize[0], pixSize[1], sliceTickness};
        Integer rows = TagD.getTagValue(this, Tag.Rows, Integer.class);
        Integer columns = TagD.getTagValue(this, Tag.Columns, Integer.class);
        if (rows != null && columns != null && rows > 0 && columns > 0) {
          return new GeometryOfSlice(
              new double[] {imgOr[0], imgOr[1], imgOr[2]},
              new double[] {imgOr[3], imgOr[4], imgOr[5]},
              pos,
              spacing,
              sliceTickness,
              new double[] {rows, columns, 1});
        }
      }
    }
    return null;
  }

  @Override
  public Attributes saveToFile(File output, DicomExportParameters params) {
    boolean hasTransformation = params.dicomEditors() != null && !params.dicomEditors().isEmpty();
    if (!hasTransformation && params.syntax() == null) {
      super.saveToFile(output);
      return new Attributes();
    }

    DicomMetaData metaData = getMediaReader().getDicomMetaData();
    String outputTsuid =
        params.syntax() == null
            ? metaData.getTransferSyntaxUID()
            : params.syntax().getTransferSyntaxUID();
    outputTsuid = getOutputTransferSyntax(true, metaData.getTransferSyntaxUID(), outputTsuid);
    var adaptTransferSyntax = new AdaptTransferSyntax(metaData.getTransferSyntaxUID(), outputTsuid);
    adaptTransferSyntax.setJpegQuality(params.compressionQuality());
    adaptTransferSyntax.setCompressionRatioFactor(params.compressionRatioFactor());
    Attributes attributes = new Attributes(metaData.getDicomObject());
    AttributeEditorContext context =
        new AttributeEditorContext(adaptTransferSyntax.getOriginal(), null, null);
    if (hasTransformation) {
      params.dicomEditors().forEach(e -> e.apply(attributes, context));
    }

    BytesWithImageDescriptor desc =
        ImageAdapter.imageTranscode(attributes, adaptTransferSyntax, context);
    if (ImageAdapter.writeDicomFile(
        attributes, adaptTransferSyntax, context.getEditable(), desc, output)) {
      return attributes;
    } else {
      LOGGER.error("Cannot export DICOM file: {}", getFileCache().getOriginalFile().orElse(null));
      return null;
    }
  }

  @Override
  public SimpleOpManager buildSimpleOpManager(
      boolean img16, boolean padding, boolean shutter, boolean overlay, double ratio) {
    SimpleOpManager manager = new SimpleOpManager();
    PlanarImage image = getImage(null);
    if (image != null) {
      if (img16) {
        DicomImageReadParam params = new DicomImageReadParam();
        params.setApplyPixelPadding(padding);

        image = getModalityLutImage(null, params);
        if (CvType.depth(image.type()) == CvType.CV_16S) {
          ImageCV dstImg = new ImageCV();
          image.toImageCV().convertTo(dstImg, CvType.CV_16UC(image.channels()), 1.0, 32768);
          image = dstImg;
        }
      } else {
        manager.addImageOperationAction(new WindowAndPresetsOp());
        manager.setParamValue(WindowOp.OP_NAME, WindowOp.P_IMAGE_ELEMENT, this);
        manager.setParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), padding);
        manager.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), true);
      }

      if (shutter) {
        manager.addImageOperationAction(new ShutterOp());
        manager.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_IMAGE_ELEMENT, this);
        manager.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, true);
        manager.setParamValue(
            ShutterOp.OP_NAME, ShutterOp.P_SHAPE, getTagValue(TagW.ShutterFinalShape));
        manager.setParamValue(
            ShutterOp.OP_NAME, ShutterOp.P_PS_VALUE, getTagValue(TagW.ShutterPSValue));
        manager.setParamValue(
            ShutterOp.OP_NAME, ShutterOp.P_RGB_COLOR, getTagValue(TagW.ShutterRGBColor));
      }
      if (overlay) {
        manager.addImageOperationAction(new OverlayOp());
        manager.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_IMAGE_ELEMENT, this);
        manager.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, true);
      }

      ZoomOp node = new ZoomOp();
      node.setParam(ZoomOp.P_RATIO_X, getRescaleX() * ratio);
      node.setParam(ZoomOp.P_RATIO_Y, getRescaleY() * ratio);
      node.setParam(ZoomOp.P_INTERPOLATION, ZoomOp.Interpolation.BICUBIC);
      manager.addImageOperationAction(node);

      manager.setFirstNode(image);
    }
    return manager;
  }

  private static String getOutputTransferSyntax(
      boolean onlyRaw, String originalTsuid, String outputTsuid) {
    if (outputTsuid == null) {
      return originalTsuid;
    }
    if (onlyRaw && !DicomUtils.isNative(originalTsuid) && !UID.RLELossless.equals(originalTsuid)) {
      return originalTsuid;
    }
    if (DicomOutputData.isSupportedSyntax(outputTsuid)
        && DicomImageReader.isSupportedSyntax(originalTsuid)) {
      return outputTsuid;
    }
    if (UID.RLELossless.equals(originalTsuid)
        || UID.ImplicitVRLittleEndian.equals(originalTsuid)
        || UID.ExplicitVRBigEndian.equals(originalTsuid)) {
      return UID.ExplicitVRLittleEndian;
    }
    return originalTsuid;
  }
}
