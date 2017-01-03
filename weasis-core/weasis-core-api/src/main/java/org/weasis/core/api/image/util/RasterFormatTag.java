/*
 * $RCSfile: RasterFormatTag.java,v $
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
 * $Revision: 1.1 $
 * $Date: 2005/02/11 04:57:18 $
 * $State: Exp $
 */
package org.weasis.core.api.image.util;
import java.awt.image.ComponentSampleModel;
import java.awt.image.SampleModel;


/**
 *  This class encapsulates the information needed for 
 *  RasterAccessor to understand how a Raster is laid out.  It's
 *  designed so that one RasterFormatTag can be constructed per
 *  source and that RasterFormatTag can cache information that
 *  the RasterAccessor would otherwise have to extract from the
 *  Raster each time it's constructed (generally each time 
 *  OpImage.computeRect() is called.)  Additionally, it can 
 *  cache various arrays (i.e. bankIndices[] and bandOffsets[])
 *  that would otherwise be cloned everytime they were requested.
 *
 *  Because of the way SampleModel.createCompatibleSampleModel()
 *  is designed not all fields of a particular SampleModel will
 *  match those of the SampleModel returned by 
 *  SampleModel.createCompatibleSampleModel().  Values like
 *  pixelStride and numBands won't change, but values like
 *  bankIndicies[] and bandOffsets[] might if the underlying
 *  Raster is not pixelSequential.  Rasters which are pixelSequential
 *  meet the following conditions 1) The SampleModel is a 
 *  ComponentSampleModel.  2) The pixelStride is equal to the number 
 *  of bands.  3) All the bankIndices[] are equal.  4) All the 
 *  bandOffsets[] values are less than pixelStride  5) No two 
 *  bandOffsets[] values are equal.  For that reason, 
 *  RasterFormatTags representing non pixelSequential rasters don't
 *  attempt to cache the bandOffsets[] or bankIndices[].  For such
 *  rasters, this information should be taken directly from the 
 *  raster itself.   Note that any RasterFormatTag that will cause
 *  data to be copied from the Raster will be pixelSequential as that
 *  is the format in which data is returned from  Raster.getPixels() 
 *  returns.
 *
 */
public final class RasterFormatTag {

    private static final int COPY_MASK = RasterAccessor.COPY_MASK;
    private static final int UNCOPIED = RasterAccessor.UNCOPIED;
    private static final int COPIED = RasterAccessor.COPIED;

    private int formatTagID;
    private int bankIndices[];
    private int numBands;
    private int bandOffsets[];
    private int pixelStride;

    private boolean isPixelSequential;

    /**
     *  Constructs a RasterFormatTag given a sampleModel and a
     *  formatTagID.  Generally, this constructor is called by 
     *  RasterAccessor.findCompatibleTags(RenderedImage[] srcs, 
     *  RenderedImage dst) and it takes care of setting the values
     *  correctly.  In special cases, OpImages need to construct
     *  a RasterFormatTag without creating a RenderedImage.  In this
     *  case a RasterFormatTag can be created using a formatTagID 
     *  returned from
     *  RasterAccessor.findCompatibleTag(SampleModel[] srcs, SampleModel dst)
     *  and a sampleModel that was either passed in to the 
     *  findCompatibleTag() call or one that was created using 
     *  createCompatibleSampleModel() on one of the passed in 
     *  SampleModels.  Attempting to use arbitrary SampleModels 
     *  with arbitrary formatTagIDs has undefined results.
     *  
     *  param sampleModel A <code>SampleModel</code> for the RasterFormagTag
     *  param formatTagID An <code>int</code> to indicate format tag id
     *
     */
    public RasterFormatTag(SampleModel sampleModel, int formatTagID) {
        this.formatTagID = formatTagID;
        if ((formatTagID & COPY_MASK) == UNCOPIED) {
            ComponentSampleModel csm =
                   (ComponentSampleModel)sampleModel;
            this.bankIndices = csm.getBankIndices();
            this.numBands = csm.getNumDataElements();
            this.bandOffsets = csm.getBandOffsets();
            this.pixelStride = csm.getPixelStride();

            if (pixelStride != bandOffsets.length) {
                isPixelSequential = false;
            } else {
                isPixelSequential = true;
                for (int i = 0; i < bandOffsets.length; i++) {
                    if (bandOffsets[i] >= pixelStride ||
                        bankIndices[i] != bankIndices[0]) {
                        isPixelSequential = false;
                    }
                    for (int j = i+1; j < bandOffsets.length; j++) {
                       if (bandOffsets[i] == bandOffsets[j]) {
                           isPixelSequential = false;
                       }
                    }
                    if (!isPixelSequential) break;
                }
            }
        } else if ((formatTagID & COPY_MASK) == COPIED) {
            numBands = sampleModel.getNumBands();
            bandOffsets = new int[numBands];
            pixelStride = numBands;
            bankIndices = new int[numBands];

            for (int i = 0; i < numBands; i++) {
                bandOffsets[i] = i;
                bankIndices[i] = 0;
            }
            isPixelSequential = true;
        }
    }

    /** 
     *  Returns whether or not the SampleModel represented by the 
     *  RasterFormatTag is PixelSequential.  
     *  Note that RasterFormatTag's that indicate
     *  data should be copied out of the Raster by the RasterAccessor
     *  will always return true for isPixelSequential().  
     *  RasterFormatTags that indicate no copying is needed will only
     *  return true, if 1) The SampleModel is a ComponentSampleModel.
     *  2) The pixelStride is equal to the number of bands.
     *  3) All the bankIndices[] are equal.  4) All the bandOffsets[]
     *  values are less than pixelStride  5) No two bandOffset values
     *  are equal.
     */
    public final boolean isPixelSequential() {
        return isPixelSequential;
    }

    /**
     *  Returns the FormatTagID used to construct this RasterFormatTag.
     *  Valid values are defined in javax.media.jai.RasterAccessor.
     */
    public final int getFormatTagID() {
        return formatTagID;
    }

    /**
     *  Returns the bankIndices for the Raster if isPixelSequential()
     *  is true.  Returns null otherwise. In the COPIED case, the 
     *  bankIndices will all be 0.
     */
    public final int[] getBankIndices() {
        if (isPixelSequential) {
            return bankIndices;
        } else {
            return null;
        }
    }

    /** Returns the number of bands in the underlying Raster */
    public final int getNumBands() {
        return numBands;
    }

    /** 
     *  Returns the bandOffsets for the Raster if isPixelSequential() is
     *  true.  Returns null otherwise.  In the COPIED case, bankIndices
     *  will be numBands sequential integers starting with 0.
     */ 
    public final int[] getBandOffsets() {
        if (isPixelSequential) {
            return bandOffsets;
        } else {
            return null;
        }
    }

    /** Returns the pixelStride of the underlying Raster */
    public final int getPixelStride() {
        return pixelStride;
    }
}
