/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import org.opencv.core.CvType;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/**
 * Colorizes the windowed image through the selected {@link ByteLut}. An 8-bit source goes through
 * the 256-entry table; a 16-bit source (a {@link WindowOp} with {@link WindowOp#P_OUTPUT_BITS}
 * above 8) is indexed over {@code [0, 2^bits - 1]} through the map behind the table, so wide
 * windows keep smooth gradients.
 */
public final class PseudoColorOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("PseudoColorOperation.title");

  /** Parameter name for the lookup table (LUT) used in pseudo-coloring. (ByteLut, Required) */
  public static final String P_LUT = ActionW.LUT.cmd();

  /** Parameter name for inverting the LUT in pseudo-coloring. (Boolean, Optional) */
  public static final String P_LUT_INVERSE = ActionW.INVERT_LUT.cmd();

  /**
   * Index width of a 16-bit source, matching {@link WindowOp#P_OUTPUT_BITS}. (Integer, Optional)
   */
  public static final String P_INPUT_BITS = "lut.input.bits"; // NON-NLS

  private static final int DEFAULT_BITS = 8;

  private record WideKey(ColorMap map, int bits) {}

  private WideKey wideKey;
  private LookupTableCV wideTable;

  public PseudoColorOp() {
    setName(OP_NAME);
  }

  public PseudoColorOp(PseudoColorOp op) {
    super(op);
  }

  @Override
  public PseudoColorOp copy() {
    return new PseudoColorOp(this);
  }

  /** A presentation state carrying a palette (pseudo-color) selects it as the LUT. */
  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    if (event.eventType() == ImageOpEvent.OpEvent.APPLY_PR
        && event.params() != null
        && event.params().get(P_LUT) instanceof ByteLut lut) {
      setParam(P_LUT, lut);
      setParam(P_INPUT_BITS, lut.source() != null ? lut.source().bits() : DEFAULT_BITS);
    }
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    ByteLut lutTable = (ByteLut) params.get(P_LUT);
    boolean invertLut = LangUtil.nullToFalse((Boolean) params.get(P_LUT_INVERSE));

    PlanarImage result = applyPseudoColor(source, lutTable, invertLut);
    params.put(Param.OUTPUT_IMG, result);
  }

  private PlanarImage applyPseudoColor(PlanarImage source, ByteLut lutTable, boolean invertLut) {
    if (lutTable == null) {
      return source;
    }
    if (isWideSource(source)) {
      return applyWide(source, lutTable, invertLut);
    }
    byte[][] lut = lutTable.lutTable();
    if (lut == null) {
      return invertLut ? ImageTransformer.invertLUT(source.toImageCV()) : source;
    }
    byte[][] finalLut = invertLut ? ByteLutCollection.invert(lut) : lut;
    return ImageTransformer.applyLUT(source.toMat(), finalLut);
  }

  private static boolean isWideSource(PlanarImage source) {
    int depth = CvType.depth(source.type());
    return source.channels() == 1 && (depth == CvType.CV_16U || depth == CvType.CV_16S);
  }

  private PlanarImage applyWide(PlanarImage source, ByteLut lutTable, boolean invertLut) {
    int bits = inputBits();
    int maxIndex = (1 << bits) - 1;
    if (lutTable.lutTable() == null && lutTable.source() == null) {
      ImageCV gray = ImageTransformer.rescaleToByte(source.toMat(), 255.0 / maxIndex, 0.0);
      return invertLut ? ImageTransformer.invertLUT(gray) : gray;
    }
    ColorMap map = ColorMap.fromByteLut(lutTable);
    return wideTable(invertLut ? map.reversed() : map, bits, maxIndex).lookup(source.toMat());
  }

  private int inputBits() {
    int bits = params.get(P_INPUT_BITS) instanceof Integer value ? value : DEFAULT_BITS;
    return Math.clamp(bits, ColorMap.MIN_BITS, ColorMap.MAX_BITS);
  }

  // One compiled table is kept: a view changes map or bits far less often than it renders.
  private synchronized LookupTableCV wideTable(ColorMap map, int bits, int maxIndex) {
    WideKey key = new WideKey(map, bits);
    if (!key.equals(wideKey)) {
      wideTable =
          ColorMapCompiler.toLookupTable(
              map, 0, maxIndex, ColorMapCompiler.windowToDomain(map, 0, maxIndex), false);
      wideKey = key;
    }
    return wideTable;
  }
}
