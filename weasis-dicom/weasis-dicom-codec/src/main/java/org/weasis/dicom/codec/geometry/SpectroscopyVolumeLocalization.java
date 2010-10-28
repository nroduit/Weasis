/* Copyright (c) 2001-2003, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.Messages;

//import javax.vecmath.*;
//import com.pixelmed.geometry.*;

/**
 * <p>
 * A class to extract and describe the spatial geometry of an MR spectroscopy localization volume, given a list of DICOM
 * attributes.
 * </p>
 * 
 * @author dclunie
 */
public class SpectroscopyVolumeLocalization {

    private static final String identString =
        "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/dicom/SpectroscopyVolumeLocalization.java,v 1.5 2004/05/01 20:19:51 dclunie Exp $"; //$NON-NLS-1$

    private int numberOfSlabs;
    private double[] slabThickness;
    private double[][] slabOrientation;
    private double[][] midSlabPosition;

    /**
     * <p>
     * Construct the localization volume geometry from the Per-frame and Shared Functional Group Sequences of a
     * multi-frame MR spectroscopy object.
     * </p>
     * 
     * @param list
     *            the list of DICOM attributes
     */
    public SpectroscopyVolumeLocalization(DicomImageElement image) throws Exception {
        // System.err.println("SpectroscopyVolumeLocalization:");

        // SequenceAttribute aVolumeLocalizationSequence =
        // (SequenceAttribute) (list.get(TagFromName.VolumeLocalizationSequence));
        // if (aVolumeLocalizationSequence != null) {
        // numberOfSlabs = aVolumeLocalizationSequence.getNumberOfItems();
        // slabThickness = new double[numberOfSlabs];
        // slabOrientation = new double[numberOfSlabs][];
        // midSlabPosition = new double[numberOfSlabs][];
        // int i = 0;
        // Iterator sitems = aVolumeLocalizationSequence.iterator();
        // while (sitems.hasNext()) {
        // SequenceItem sitem = (SequenceItem) sitems.next();
        // AttributeList slist = sitem.getAttributeList();
        // if (slist != null) {
        // slabThickness[i] = Attribute.getSingleDoubleValueOrDefault(slist, TagFromName.SlabThickness, 0);
        // slabOrientation[i] = Attribute.getDoubleValues(slist, TagFromName.SlabOrientation);
        // midSlabPosition[i] = Attribute.getDoubleValues(slist, TagFromName.MidSlabPosition);
        // // System.err.println("SpectroscopyVolumeLocalization: slabThickness  ["+i+"] = "+slabThickness[i]);
        // //
        // System.err.println("SpectroscopyVolumeLocalization: slabOrientation["+i+"] = "+slabOrientation[i][0]+","+slabOrientation[i][1]+","+slabOrientation[i][2]);
        // //
        // System.err.println("SpectroscopyVolumeLocalization: midSlabPosition["+i+"] = "+midSlabPosition[i][0]+","+midSlabPosition[i][1]+","+midSlabPosition[i][2]);
        // }
        // ++i;
        // }
        // }
    }

    /**
     * <p>
     * Get the number of localization slabs.
     * </p>
     * 
     * @return the number of localization slabs
     */
    public final int getNumberOfSlabs() {
        return numberOfSlabs;
    }

    /**
     * <p>
     * Get the localization slab thickness.
     * </p>
     * 
     * @return localization slab thickness in mm.
     */
    public final double getSlabThickness(int i) {
        return slabThickness[i];
    }

    /**
     * <p>
     * Get the localization slab orientation.
     * </p>
     * 
     * @return the direction cosines of a normal vector perpendicular to the selection plane with respect to the patient
     *         as X, Y and Z components (LPH+)
     */
    public final double[] getSlabOrientation(int i) {
        return slabOrientation[i];
    }

    /**
     * <p>
     * Get the mid-slab position.
     * </p>
     * 
     * @return the x, y, and z coordinates of the mid-point of the slab in mm
     */
    public final double[] getMidSlabPosition(int i) {
        return midSlabPosition[i];
    }
}
