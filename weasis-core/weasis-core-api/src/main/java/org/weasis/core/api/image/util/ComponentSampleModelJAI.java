/*
 * $RCSfile: ComponentSampleModelJAI.java,v $
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
 * $Revision: 1.1 $
 * $Date: 2005/02/11 04:57:07 $
 * $State: Exp $
 */ 
package org.weasis.core.api.image.util;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.SampleModel;

/**
 *  This class represents image data which is stored such that each sample
 *  of a pixel occupies one data element of the <code>DataBuffer</code>.  It stores the
 *  N samples which make up a pixel in N separate data array elements.
 *  Different bands may be in different banks of the <code>DataBuffer</code>.
 *  Accessor methods are provided so that image data can be manipulated
 *  directly. This class can support different kinds of interleaving, e.g.
 *  band interleaving, scanline interleaving, and pixel interleaving.
 *  Pixel stride is the number of data array elements between two samples
 *  for the same band on the same scanline. Scanline stride is the number
 *  of data array elements between a given sample and the corresponding sample
 *  in the same column of the next scanline.  Band offsets denote the number
 *  of data array elements from the first data array element of the bank
 *  of the <code>DataBuffer</code> holding each band to the first sample of the band.
 *  The bands are numbered from 0 to N-1.  This class can represent image
 *  data for the dataTypes enumerated in java.awt.image.DataBuffer (all
 *  samples of a given <code>ComponentSampleModel</code> are stored with the same precision)
 *  . This class adds support for <code>Double</code> and <code>Float</code> data types in addition
 * to those supported by the <code>ComponentSampleModel</code> class in Java 2D.
 * All strides and offsets must be non-negative.
 *  @see java.awt.image.ComponentSampleModel
 */

public class ComponentSampleModelJAI extends ComponentSampleModel {

    /**
     * Constructs a <code>ComponentSampleModel</code> with the specified
     * parameters.  The number of bands will be given by the length of
     * the bandOffsets array.  All bands will be stored in the first
     * bank of the <code>DataBuffer</code>.
     *
     * @param dataType 	The data type for storing samples.
     * @param w 	The width (in pixels) of the region of
     * image data described.
     * @param h 	The height (in pixels) of the region of
     * image data described.
     * @param pixelStride The pixel stride of the region of image
     * data described.
     * @param scanlineStride The line stride of the region of image
     * data described.
     * @param bandOffsets The offsets of all bands.
     */
    public ComponentSampleModelJAI(int dataType,
                                   int w, int h,
                                   int pixelStride,
                                   int scanlineStride,
                                   int bandOffsets[]) {
        super(dataType, w, h, pixelStride, scanlineStride, bandOffsets);
    }


    /**
     * Constructs a <code>ComponentSampleModel</code> with the specified
     * parameters.  The number of bands will be given by the length of
     * the bandOffsets array.  Different bands may be stored in
     * different banks of the <code>DataBuffer</code>.
     *
     * @param dataType 	The data type for storing samples.
     * @param w 	The width (in pixels) of the region of
     * image data described. 
     * @param h 	The height (in pixels) of the region of
     * image data described.
     * @param pixelStride The pixel stride of the region of image
     * data described.	
     * @param scanlineStride The line stride of the region of image
     * data described. 
     * @param bankIndices The bank indices of all bands. 
     * @param bandOffsets The band offsets of all bands.
     */
    public ComponentSampleModelJAI(int dataType,
                                   int w, int h,
                                   int pixelStride,
                                   int scanlineStride,
                                   int bankIndices[],
                                   int bandOffsets[]) {
        super(dataType, w, h, pixelStride, scanlineStride, 
              bankIndices, bandOffsets);
    }

    /**
     * Returns the size of the data buffer (in data elements) needed
     * for a data buffer that matches this <code>ComponentSampleModel</code>.
     */
     private long getBufferSize() {
         int maxBandOff=bandOffsets[0];
         for (int i=1; i<bandOffsets.length; i++)
             maxBandOff = Math.max(maxBandOff,bandOffsets[i]);

         long size = 0;
         if (maxBandOff >= 0)
             size += maxBandOff+1;
         if (pixelStride > 0)
             size += pixelStride * (width-1);
         if (scanlineStride > 0)
             size += scanlineStride*(height-1);
         return size;
     }

     /**
      * Preserves band ordering with new step factor...
      */
    private int[] JAIorderBands(int orig[], int step) {
        int map[] = new int[orig.length];
        int ret[] = new int[orig.length];

        for (int i=0; i<map.length; i++) map[i] = i;

        for (int i = 0; i < ret.length; i++) {
            int index = i;
            for (int j = i+1; j < ret.length; j++) {
                if (orig[map[index]] > orig[map[j]]) {
                    index = j;
                }
            }
            ret[map[index]] = i*step;
            map[index]  = map[i];
        }
        return ret;
    }

    /**
     * Creates a new <code>ComponentSampleModel</code> with the specified
     * width and height.  The new <code>SampleModel</code> will have the same
     * number of bands, storage data type, interleaving scheme, and
     * pixel stride as this <code>SampleModel</code>.
     *
     * @param w The width in pixels.
     * @param h The height in pixels
     */
    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        SampleModel ret=null;
        long size;
        int minBandOff=bandOffsets[0];
        int maxBandOff=bandOffsets[0];
        for (int i=1; i<bandOffsets.length; i++) {
            minBandOff = Math.min(minBandOff,bandOffsets[i]);
            maxBandOff = Math.max(maxBandOff,bandOffsets[i]);
        }
        maxBandOff -= minBandOff;

        int bands   = bandOffsets.length;
        int bandOff[];
        int pStride = Math.abs(pixelStride);
        int lStride = Math.abs(scanlineStride);
        int bStride = Math.abs(maxBandOff);

        if (pStride > lStride) {
            if (pStride > bStride) {
                if (lStride > bStride) { // pix > line > band
                    bandOff = new int[bandOffsets.length];
                    for (int i=0; i<bands; i++)
                        bandOff[i] = bandOffsets[i]-minBandOff;
                    lStride = bStride+1;
                    pStride = lStride*h;
                } else { // pix > band > line
                    bandOff = JAIorderBands(bandOffsets,lStride*h);
                    pStride = bands*lStride*h;
                }
            } else { // band > pix > line
                pStride = lStride*h;
                bandOff = JAIorderBands(bandOffsets,pStride*w);
            }
        } else {
            if (pStride > bStride) { // line > pix > band
                bandOff = new int[bandOffsets.length];
                for (int i=0; i<bands; i++)
                    bandOff[i] = bandOffsets[i]-minBandOff;
                pStride = bStride+1;
                lStride = pStride*w;
            } else {
                if (lStride > bStride) { // line > band > pix
                    bandOff = JAIorderBands(bandOffsets,pStride*w);
                    lStride = bands*pStride*w;
                } else { // band > line > pix
                    lStride = pStride*w;
                    bandOff = JAIorderBands(bandOffsets,lStride*h);
                }
            }
        }

        // make sure we make room for negative offsets...
        int base = 0;
        if (scanlineStride < 0) {
            base += lStride*h;
            lStride *= -1;
        }
        if (pixelStride    < 0) {
            base += pStride*w;
            pStride *= -1;
        }

        for (int i=0; i<bands; i++)
            bandOff[i] += base;
        return new ComponentSampleModelJAI(dataType, w, h, pStride,
                                           lStride, bankIndices, bandOff);
    }

    /**
     * This creates a new <code>ComponentSampleModel</code> with a subset of the bands
     * of this <code>ComponentSampleModel</code>.  The new <code>ComponentSampleModel</code> can be
     * used with any <code>DataBuffer</code> that the existing <code>ComponentSampleModel</code>
     * can be used with.  The new <code>ComponentSampleModel</code>/<code>DataBuffer</code>
     * combination will represent an image with a subset of the bands
     * of the original <code>ComponentSampleModel</code>/<code>DataBuffer</code> combination.
     *
     * @param bands subset of bands of this <code>ComponentSampleModel</code>
     */
    @Override
    public SampleModel createSubsetSampleModel(int bands[]) {
        int newBankIndices[] = new int[bands.length];
        int newBandOffsets[] = new int[bands.length];
        for (int i=0; i<bands.length; i++) {
            int b = bands[i];
            newBankIndices[i] = bankIndices[b];
            newBandOffsets[i] = bandOffsets[b];
        }
        return new ComponentSampleModelJAI(this.dataType, width, height,
                                           this.pixelStride,
                                           this.scanlineStride,
                                           newBankIndices,
                                           newBandOffsets);
    }

    /**
     * Creates a <code>DataBuffer</code> that corresponds to this <code>ComponentSampleModel</code>.
     * The <code>DataBuffer</code>'s data type, number of banks, and size
     * will be consistent with this <code>ComponentSampleModel</code>.
     */
    @Override
    public DataBuffer createDataBuffer() {
        DataBuffer dataBuffer = null;

        int size = (int)getBufferSize();
        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            dataBuffer = new DataBufferByte(size, numBanks);
            break;
        case DataBuffer.TYPE_USHORT:
            dataBuffer = new DataBufferUShort(size, numBanks);
            break;
        case DataBuffer.TYPE_INT:
            dataBuffer = new DataBufferInt(size, numBanks);
            break;
        case DataBuffer.TYPE_SHORT:
            dataBuffer = new DataBufferShort(size, numBanks);
            break;
        case DataBuffer.TYPE_FLOAT:
            dataBuffer = DataBufferUtils.createDataBufferFloat(size, numBanks);
            break;
        case DataBuffer.TYPE_DOUBLE:
            dataBuffer = DataBufferUtils.createDataBufferDouble(size, numBanks);
            break;
	default:
            throw new RuntimeException("Unsupported data type");
        }

        return dataBuffer;
    }


    /** 
     * Returns data for a single pixel in a primitive array of type
     * TransferType.  For a <code>ComponentSampleModel</code>, this will be the same
     * as the data type, and samples will be returned one per array
     * element.  Generally, obj
     * should be passed in as null, so that the <code>Object</code> will be created
     * automatically and will be of the right primitive data type.
     * <p>
     * The following code illustrates transferring data for one pixel from
     * <code>DataBuffer</code> <code>db1</code>, whose storage layout is described by
     * <code>ComponentSampleModel</code> <code>csm1</code>, to <code>DataBuffer</code> <code>db2</code>,
     * whose storage layout is described by
     * <code>ComponentSampleModel</code> <code>csm2</code>.
     * The transfer will generally be more efficient than using
     * getPixel/setPixel.
     * <pre>
     * 	     ComponentSampleModel csm1, csm2;
     *	     DataBufferInt db1, db2;
     * 	     csm2.setDataElements(x, y,
     *                            csm1.getDataElements(x, y, null, db1), db2);
     * </pre>
     * Using getDataElements/setDataElements to transfer between two
     * <code>DataBuffer</code>/SampleModel pairs is legitimate if the <code>SampleModel</code>s have
     * the same number of bands, corresponding bands have the same number of
     * bits per sample, and the TransferTypes are the same.
     * <p>
     * @param x 	The X coordinate of the pixel location.
     * @param y 	The Y coordinate of the pixel location.
     * @param obj       If non-null, a primitive array in which to return
     *                  the pixel data.
     * @param data      The <code>DataBuffer</code> containing the image data.
     * @throws <code>ClassCastException</code> if obj is non-null and is not
     *          a primitive array of type TransferType.
     * @throws <code>ArrayIndexOutOfBoundsException</code> if the coordinates
     *         are not in bounds, or if obj is non-null and is not large 
     *         enough to hold the pixel data. 
     */
    @Override
    public Object getDataElements(int x, int y, Object obj, DataBuffer data) {

	int type = getTransferType();
	int numDataElems = getNumDataElements();
	int pixelOffset = y*scanlineStride + x*pixelStride;

	switch(type) {

	case DataBuffer.TYPE_BYTE:

	    byte[] bdata;

	    if (obj == null)
		bdata = new byte[numDataElems];
	    else
		bdata = (byte[])obj;

	    for (int i=0; i<numDataElems; i++) {
		bdata[i] = (byte)data.getElem(bankIndices[i],
                                              pixelOffset + bandOffsets[i]);
	    }

	    obj = bdata;
	    break;

	case DataBuffer.TYPE_USHORT:

	    short[] usdata;

	    if (obj == null)
		usdata = new short[numDataElems];
	    else
		usdata = (short[])obj;

	    for (int i=0; i<numDataElems; i++) {
		usdata[i] = (short)data.getElem(bankIndices[i],
                                               pixelOffset + bandOffsets[i]);
	    }

	    obj = usdata;
	    break;

	case DataBuffer.TYPE_INT:

	    int[] idata;

	    if (obj == null)
		idata = new int[numDataElems];
	    else
		idata = (int[])obj;

	    for (int i=0; i<numDataElems; i++) {
		idata[i] = data.getElem(bankIndices[i],
                                        pixelOffset + bandOffsets[i]);
	    }

	    obj = idata;
	    break;

        case DataBuffer.TYPE_SHORT:
 
            short[] sdata;
 
            if (obj == null)
                sdata = new short[numDataElems];
            else
                sdata = (short[])obj;
 
            for (int i=0; i<numDataElems; i++) {
                sdata[i] = (short)data.getElem(bankIndices[i],
                                               pixelOffset + bandOffsets[i]);
            }
 
            obj = sdata;
            break;
 
        case DataBuffer.TYPE_FLOAT:
 
            float[] fdata;
 
            if (obj == null)
                fdata = new float[numDataElems];
            else
                fdata = (float[])obj;
 
            for (int i=0; i<numDataElems; i++) {
                fdata[i] = data.getElemFloat(bankIndices[i],
                                             pixelOffset + bandOffsets[i]);
            }
 
            obj = fdata;
            break;

        case DataBuffer.TYPE_DOUBLE:
 
            double[] ddata;
 
            if (obj == null)
                ddata = new double[numDataElems];
            else
                ddata = (double[])obj;
 
            for (int i=0; i<numDataElems; i++) {
                ddata[i] = data.getElemDouble(bankIndices[i],
                                              pixelOffset + bandOffsets[i]);
            }
 
            obj = ddata;
            break;
	
	default:
            throw new RuntimeException("Unsupported data type");

	}

	return obj;
    }


    /**
     * Returns the pixel data for the specified rectangle of pixels in a
     * primitive array of type TransferType.
     * For image data supported by the Java 2D API, this
     * will be one of the dataTypes supported by java.awt.image.DataBuffer.
     * Data may be returned in a packed format, thus increasing efficiency
     * for data transfers. Generally, obj should be passed in as null, so
     * that the <code>Object</code> will be created automatically and will be of the right
     * primitive data type.
     * <p>
     * The following code illustrates transferring data for a rectangular
     * region of pixels from
     * <code>DataBuffer</code> <code>db1</code>, whose storage layout is described by
     * <code>SampleModel</code> <code>sm1</code>, to <code>DataBuffer</code> <code>db2</code>, whose
     * storage layout is described by <code>SampleModel</code> <code>sm2</code>.
     * The transfer will generally be more efficient than using
     * getPixels/setPixels.
     * <pre>
     *       SampleModel sm1, sm2;
     *       DataBuffer db1, db2;
     *       sm2.setDataElements(x, y, w, h, sm1.getDataElements(x, y, w,
     *                           h, null, db1), db2);
     * </pre>
     * Using getDataElements/setDataElements to transfer between two
     * <code>DataBuffer</code>/SampleModel pairs is legitimate if the <code>SampleModel</code>s have
     * the same number of bands, corresponding bands have the same number of
     * bits per sample, and the TransferTypes are the same.
     * <p>
     * @param x         The minimum X coordinate of the pixel rectangle.
     * @param y         The minimum Y coordinate of the pixel rectangle.
     * @param w         The width of the pixel rectangle.
     * @param h         The height of the pixel rectangle.
     * @param obj       If non-null, a primitive array in which to return
     *                  the pixel data.
     * @param data      The <code>DataBuffer</code> containing the image data.
     * @see #getNumDataElements
     * @see #getTransferType
     * @see java.awt.image.DataBuffer
     * @throws <code>ClassCastException</code> if obj is non-null and is not
     *          a primitive array of type TransferType.
     * @throws <code>ArrayIndexOutOfBoundsException</code> if the coordinates
     *         are not in bounds, or if obj is non-null and is not large 
     *         enough to hold the pixel data. 
     */
    @Override
    public Object getDataElements(int x, int y, int w, int h,
                                  Object obj, DataBuffer data) {
 
        int type = getTransferType();
        int numDataElems = getNumDataElements();
        int cnt = 0;
        Object o = null;
 
        switch(type) {

        case DataBuffer.TYPE_BYTE: {
            byte[] btemp;
            byte[] bdata;
 
            if (obj == null)
                bdata = new byte[numDataElems*w*h];
            else
                bdata = (byte[])obj;
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    o = getDataElements(j, i, o, data);
                    btemp = (byte[])o;
                    for (int k=0; k<numDataElems; k++) {
                        bdata[cnt++] = btemp[k];
                    }
                }
            }
            obj = bdata;
            break;
        }
 
        case DataBuffer.TYPE_USHORT: {
 
            short[] usdata;
            short[] ustemp;
 
            if (obj == null)
                usdata = new short[numDataElems*w*h];
            else
                usdata = (short[])obj;
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    o = getDataElements(j, i, o, data);
                    ustemp = (short[])o;
                    for (int k=0; k<numDataElems; k++) {
                        usdata[cnt++] = ustemp[k];
                    }
                }
            }
 
            obj = usdata;
            break;
        }

        case DataBuffer.TYPE_INT: {
 
            int[] idata;
            int[] itemp;
 
            if (obj == null)
                idata = new int[numDataElems*w*h];
            else
                idata = (int[])obj;
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    o = getDataElements(j, i, o, data);
                    itemp = (int[])o;
                    for (int k=0; k<numDataElems; k++) {
                        idata[cnt++] = itemp[k];
                    }
                }
            }
 
            obj = idata;
            break;
        }

        case DataBuffer.TYPE_SHORT: {
 
            short[] sdata;
            short[] stemp;
 
            if (obj == null)
                sdata = new short[numDataElems*w*h];
            else
                sdata = (short[])obj;
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    o = getDataElements(j, i, o, data);
                    stemp = (short[])o;
                    for (int k=0; k<numDataElems; k++) {
                        sdata[cnt++] = stemp[k];
                    }
                }
            }
 
            obj = sdata;
            break;
        }

        case DataBuffer.TYPE_FLOAT: {
 
            float[] fdata;
            float[] ftemp;
 
            if (obj == null)
                fdata = new float[numDataElems*w*h];
            else
                fdata = (float[])obj;
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    o = getDataElements(j, i, o, data);
                    ftemp = (float[])o;
                    for (int k=0; k<numDataElems; k++) {
                        fdata[cnt++] = ftemp[k];
                    }
                }
            }
 
            obj = fdata;
            break;
        }

        case DataBuffer.TYPE_DOUBLE: {
 
            double[] ddata;
            double[] dtemp;
 
            if (obj == null)
                ddata = new double[numDataElems*w*h];
            else
                ddata = (double[])obj;
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    o = getDataElements(j, i, o, data);
                    dtemp = (double[])o;
                    for (int k=0; k<numDataElems; k++) {
                        ddata[cnt++] = dtemp[k];
                    }
                }
            }
 
            obj = ddata;
            break;
        }

	default:
            throw new RuntimeException("Unsupported data type");
        }
 
        return obj;
    }






    /** 
     * Sets the data for a single pixel in the specified <code>DataBuffer</code> from a
     * primitive array of type TransferType.  For a <code>ComponentSampleModel</code>,
     * this will be the same as the data type, and samples are transferred
     * one per array element.
     * <p>
     * The following code illustrates transferring data for one pixel from
     * <code>DataBuffer</code> <code>db1</code>, whose storage layout is described by
     * <code>ComponentSampleModel</code> <code>csm1</code>, to <code>DataBuffer</code> <code>db2</code>,
     * whose storage layout is described by
     * <code>ComponentSampleModel</code> <code>csm2</code>.
     * The transfer will generally be more efficient than using
     * getPixel/setPixel.
     * <pre>
     * 	     ComponentSampleModel csm1, csm2;
     *	     DataBufferInt db1, db2;
     * 	     csm2.setDataElements(x, y, csm1.getDataElements(x, y, null, db1),
     *                            db2);
     * </pre>
     * Using getDataElements/setDataElements to transfer between two
     * <code>DataBuffer</code>/SampleModel pairs is legitimate if the <code>SampleModel</code>s have
     * the same number of bands, corresponding bands have the same number of
     * bits per sample, and the TransferTypes are the same.
     * <p>
     * @param x 	The X coordinate of the pixel location.
     * @param y 	The Y coordinate of the pixel location.
     * @param obj       A primitive array containing pixel data.
     * @param data      The <code>DataBuffer</code> containing the image data.
     * @throws <code>ClassCastException</code> if obj is non-null and is not
     *          a primitive array of type TransferType.
     * @throws <code>ArrayIndexOutOfBoundsException</code> if the coordinates
     *         are not in bounds, or if obj is non-null and is not large 
     *         enough to hold the pixel data. 
     */
    @Override
    public void setDataElements(int x, int y, Object obj, DataBuffer data) {

	int type = getTransferType();
	int numDataElems = getNumDataElements();
	int pixelOffset = y*scanlineStride + x*pixelStride;

	switch(type) {

	case DataBuffer.TYPE_BYTE:

	    byte[] barray = (byte[])obj;

	    for (int i=0; i<numDataElems; i++) {
		data.setElem(bankIndices[i], pixelOffset + bandOffsets[i],
			   (barray[i])&0xff);
	    }
	    break;

	case DataBuffer.TYPE_USHORT:

	    short[] usarray = (short[])obj;

	    for (int i=0; i<numDataElems; i++) {
		data.setElem(bankIndices[i], pixelOffset + bandOffsets[i],
			   (usarray[i])&0xffff);
	    }
	    break;

	case DataBuffer.TYPE_INT:

	    int[] iarray = (int[])obj;

	    for (int i=0; i<numDataElems; i++) {
		data.setElem(bankIndices[i],
                             pixelOffset + bandOffsets[i], iarray[i]);
	    }
	    break;

        case DataBuffer.TYPE_SHORT:
 
            short[] sarray = (short[])obj;
 
            for (int i=0; i<numDataElems; i++) {
                data.setElem(bankIndices[i],
                             pixelOffset + bandOffsets[i], sarray[i]);
            }
            break;

        case DataBuffer.TYPE_FLOAT:
 
            float[] farray = (float[])obj;
 
            for (int i=0; i<numDataElems; i++) {
                data.setElemFloat(bankIndices[i],
                                  pixelOffset + bandOffsets[i], farray[i]);
            }
            break;

        case DataBuffer.TYPE_DOUBLE:
 
            double[] darray = (double[])obj;
 
            for (int i=0; i<numDataElems; i++) {
                data.setElemDouble(bankIndices[i],
                                   pixelOffset + bandOffsets[i], darray[i]);
            }
            break;
	
	default:
            throw new RuntimeException("Unsupported data type");
	}
    }

    /**
     * Sets the data for a rectangle of pixels in the specified <code>DataBuffer</code>
     * from a primitive array of type TransferType.  For image data supported
     * by the Java 2D API, this will be one of the dataTypes supported by
     * java.awt.image.DataBuffer.  Data in the array may be in a packed
     * format, thus increasing efficiency for data transfers.
     * <p>
     * The following code illustrates transferring data for a rectangular
     * region of pixels from
     * <code>DataBuffer</code> <code>db1</code>, whose storage layout is described by
     * <code>SampleModel</code> <code>sm1</code>, to <code>DataBuffer</code> <code>db2</code>, whose
     * storage layout is described by <code>SampleModel</code> <code>sm2</code>.
     * The transfer will generally be more efficient than using
     * getPixels/setPixels.
     * <pre>
     *       SampleModel sm1, sm2;
     *       DataBuffer db1, db2;
     *       sm2.setDataElements(x, y, w, h, sm1.getDataElements(x, y, w, h,
     *                           null, db1), db2);
     * </pre>
     * Using getDataElements/setDataElements to transfer between two
     * <code>DataBuffer</code>/SampleModel pairs is legitimate if the <code>SampleModel</code>s have
     * the same number of bands, corresponding bands have the same number of
     * bits per sample, and the TransferTypes are the same.
     * <p>
     * @param x         The minimum X coordinate of the pixel rectangle.
     * @param y         The minimum Y coordinate of the pixel rectangle.
     * @param w         The width of the pixel rectangle.
     * @param h         The height of the pixel rectangle.
     * @param obj       A primitive array containing pixel data.
     * @param data      The <code>DataBuffer</code> containing the image data.
     * @throws <code>ClassCastException</code> if obj is non-null and is not
     *          a primitive array of type TransferType.
     * @throws <code>ArrayIndexOutOfBoundsException</code> if the coordinates
     *         are not in bounds, or if obj is non-null and is not large 
     *         enough to hold the pixel data. 
     * @see #getNumDataElements
     * @see #getTransferType
     * @see java.awt.image.DataBuffer
     */
    @Override
    public void setDataElements(int x, int y, int w, int h,
                                Object obj, DataBuffer data) {
        int cnt = 0;
        Object o = null;
        int type = getTransferType();
        int numDataElems = getNumDataElements();
 
        switch(type) {
 
        case DataBuffer.TYPE_BYTE: {
 
            byte[] barray = (byte[])obj;
            byte[] btemp = new byte[numDataElems];
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    for (int k=0; k<numDataElems; k++) {
                        btemp[k] = barray[cnt++];
                    }
 
                    setDataElements(j, i, btemp, data);
                }
            }
            break;
        }

        case DataBuffer.TYPE_USHORT: {
 
            short[] usarray = (short[])obj;
            short[] ustemp = new short[numDataElems];
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    for (int k=0; k<numDataElems; k++) {
                        ustemp[k] = usarray[cnt++];
                    }
                    setDataElements(j, i, ustemp, data);
                }
            }
            break;
        }

        case DataBuffer.TYPE_INT: {
 
            int[] iArray = (int[])obj;
            int[] itemp = new int[numDataElems];
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    for (int k=0; k<numDataElems; k++) {
                        itemp[k] = iArray[cnt++];
                    }
 
                    setDataElements(j, i, itemp, data);
                }
            }
            break;
        }

        case DataBuffer.TYPE_SHORT: {
 
            short[] sArray = (short[])obj;
            short[] stemp = new short[numDataElems];
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    for (int k=0; k<numDataElems; k++) {
                        stemp[k] = sArray[cnt++];
                    }
 
                    setDataElements(j, i, stemp, data);
                }
            }
            break;
        }

        case DataBuffer.TYPE_FLOAT: {
 
            float[] fArray = (float[])obj;
            float[] ftemp = new float[numDataElems];
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    for (int k=0; k<numDataElems; k++) {
                        ftemp[k] = fArray[cnt++];
                    }
 
                    setDataElements(j, i, ftemp, data);
                }
            }
            break;
        }

        case DataBuffer.TYPE_DOUBLE: {
 
            double[] dArray = (double[])obj;
            double[] dtemp = new double[numDataElems];
 
            for (int i=y; i<y+h; i++) {
                for (int j=x; j<x+w; j++) {
                    for (int k=0; k<numDataElems; k++) {
                        dtemp[k] = dArray[cnt++];
                    }
 
                    setDataElements(j, i, dtemp, data);
                }
            }
            break;
        }

	default:
            throw new RuntimeException("Unsupported data type");
        }
    }

    /**
     * Sets a sample in the specified band for the pixel located at (x,y)
     * in the <code>DataBuffer</code> using a <code>float</code> for input.
     * <code>ArrayIndexOutOfBoundsException</code> may be thrown if the coordinates are
     * not in bounds.
     * @param x 	The X coordinate of the pixel location.
     * @param y 	The Y coordinate of the pixel location.
     * @param b 	The band to set.
     * @param s 	The input sample as a <code>float</code>.
     * @param data 	The <code>DataBuffer</code> containing the image data.
     *
     * @throws <code>ArrayIndexOutOfBoundsException</code> if coordinates are not in bounds
     */
    @Override
    public void setSample(int x, int y, int b,
                          float s,
                          DataBuffer data) {
        data.setElemFloat(bankIndices[b],
                          y*scanlineStride + x*pixelStride + bandOffsets[b],
                          s);
    }

    /**
     * Returns the sample in a specified band
     * for the pixel located at (x,y) as a <code>float</code>.
     * <code>ArrayIndexOutOfBoundsException</code> may be thrown if the coordinates are
     * not in bounds.
     * @param x 	The X coordinate of the pixel location.
     * @param y 	The Y coordinate of the pixel location.
     * @param b 	The band to return.
     * @param data 	The <code>DataBuffer</code> containing the image data.
     * @return sample   The floating point sample value
     * @throws <code>ArrayIndexOutOfBoundsException</code> if coordinates are not in bounds
     */
    @Override
    public float getSampleFloat(int x, int y, int b,
                                DataBuffer data) {
        float sample =
            data.getElemFloat(bankIndices[b],
                              y*scanlineStride + x*pixelStride +
                              bandOffsets[b]);
        return sample;
    }

    /**
     * Sets a sample in the specified band for the pixel located at (x,y)
     * in the <code>DataBuffer</code> using a <code>double</code> for input.
     * <code>ArrayIndexOutOfBoundsException</code> may be thrown if the coordinates are
     * not in bounds.
     * @param x 	The X coordinate of the pixel location.
     * @param y 	The Y coordinate of the pixel location.
     * @param b 	The band to set.
     * @param s 	The input sample as a <code>double</code>.
     * @param data 	The <code>DataBuffer</code> containing the image data.
     *
     * @throws <code>ArrayIndexOutOfBoundsException</code> if coordinates are not in bounds
     */
    @Override
    public void setSample(int x, int y, int b,
                          double s,
                          DataBuffer data) {
        data.setElemDouble(bankIndices[b],
                           y*scanlineStride + x*pixelStride + bandOffsets[b],
                           s);
    }

    /**
     * Returns the sample in a specified band
     * for a pixel located at (x,y) as a <code>double</code>.
     * <code>ArrayIndexOutOfBoundsException</code> may be thrown if the coordinates are
     * not in bounds.
     * @param x 	The X coordinate of the pixel location.
     * @param y 	The Y coordinate of the pixel location.
     * @param b 	The band to return.
     * @param data 	The <code>DataBuffer</code> containing the image data.
     * @return sample   The <code>double</code> sample value
     * @throws <code>ArrayIndexOutOfBoundsException</code> if coordinates are not in bounds
     */
    @Override
    public double getSampleDouble(int x, int y, int b,
                                  DataBuffer data) {
        double sample =
            data.getElemDouble(bankIndices[b],
                               y*scanlineStride + x*pixelStride +
                               bandOffsets[b]);
        return sample;
    }

    /**
     * Returns all samples for a rectangle of pixels in a <code>double</code>
     * array, one sample per array element.
     * <code>ArrayIndexOutOfBoundsException</code> may be thrown if the coordinates are
     * not in bounds.
     * @param x         The X coordinate of the upper left pixel location.
     * @param y         The Y coordinate of the upper left pixel location.
     * @param w         The width of the pixel rectangle.
     * @param h         The height of the pixel rectangle.
     * @param dArray    If non-null, returns the samples in this array.
     * @param data      The <code>DataBuffer</code> containing the image data.
     * @throws <code>ArrayIndexOutOfBoundsException</code> if coordinates are not in bounds
     */
    @Override
    public double[] getPixels(int x, int y, int w, int h,
                              double dArray[], DataBuffer data) {
        double pixels[];
        int    Offset = 0;
 
        if (dArray != null)
            pixels = dArray;
        else
            pixels = new double[numBands * w * h];
 
        for (int i=y; i<(h+y); i++) {
            for (int j=x; j<(w+x); j++) {
                for (int k=0; k<numBands; k++) {
                    pixels[Offset++] = getSampleDouble(j, i, k, data);
                }
            }
        }
 
        return pixels;
    }

    /** Returns a <code>String</code> containing the values of all valid fields. */
    @Override
    public String toString() {
        String ret =  "ComponentSampleModelJAI: " +
                       "  dataType=" + this.getDataType() +
                       "  numBands=" + this.getNumBands() +
                       "  width=" +this.getWidth() +
                       "  height=" +this.getHeight() +
                       "  bandOffsets=[ ";
        for (int i = 0; i < numBands; i++) {
            ret += this.getBandOffsets()[i] + " ";
        }
        ret += "]";
        return ret;
    }
}

