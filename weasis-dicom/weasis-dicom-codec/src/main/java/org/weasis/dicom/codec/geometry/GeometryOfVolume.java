/* Copyright (c) 2001-2004, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import org.weasis.dicom.codec.Messages;

/**
 * <p>
 * An abstract class to describe the spatial geometry of an entire volume of contiguous cross-sectional image slices.
 * </p>
 * 
 * <p>
 * The 3D coordinate space used is the DICOM coordinate space, which is LPH+, that is, the x-axis is increasing to the
 * left hand side of the patient, the y-axis is increasing to the posterior side of the patient, and the z-axis is
 * increasing toward the head of the patient.
 * </p>
 * 
 * @author dclunie
 */
public abstract class GeometryOfVolume {

    protected GeometryOfSlice[] frames;
    protected boolean isVolume;

    /**
     * <p>
     * Get the geometry of the slices.
     * </p>
     * 
     * @return an array of the geometry of the slices
     */
    public final GeometryOfSlice[] getGeometryOfSlices() {
        return frames;
    }

    /**
     * <p>
     * Given the present geometry, look up the location of a point specified in image coordinates (column and row and
     * frame offset) and return the x,y and z coordinates of the point in the DICOM 3D coordinate space.
     * </p>
     * 
     * @param column
     *            the offset along the column from the top left hand corner, zero being no offset
     * @param row
     *            the offset along the row from the top left hand corner, zero being no offset
     * @param frame
     *            the offset along the frames from first frame, zero being no offset
     * @return the x, y and z location in 3D space
     */
    public final double[] lookupImageCoordinate(int column, int row, int frame) {
        double[] location = null;
        if (frames != null && frame < frames.length && frames[frame] != null) {
            location = frames[frame].lookupImageCoordinate(column, row);
        }
        return location;
    }

    /**
     * <p>
     * Given the present geometry, look up the location of a point specified in image coordinates (column and row and
     * frame offset) and return the x,y and z coordinates of the point in the DICOM 3D coordinate space.
     * </p>
     * 
     * @param location
     *            an array in which to return the x, y and z location in 3D space
     * @param column
     *            the offset along the column from the top left hand corner, zero being no offset
     * @param row
     *            the offset along the row from the top left hand corner, zero being no offset
     * @param frame
     *            the offset along the frames from first frame, zero being no offset
     */
    public final void lookupImageCoordinate(double[] location, int column, int row, int frame) {
        if (frames != null && frame < frames.length && frames[frame] != null) {
            frames[frame].lookupImageCoordinate(location, column, row);
        } else {
            location[0] = 0;
            location[1] = 0;
            location[2] = 0;
        }
    }

    /**
     * <p>
     * Given the present geometry, look up the location of a point specified in x,y and z coordinates of the point in
     * the DICOM 3D coordinate space, and return the volume coordinates (column and row and frame offset).
     * </p>
     * 
     * @param location
     *            the x, y and z location in 3D space
     * @return the column and row and frame offsets from the top left hand corner of the volume
     */
    public final double[] lookupImageCoordinate(double[] location) {
        double[] offsets = new double[3];
        lookupImageCoordinate(offsets, location);
        return offsets;
    }

    /**
     * <p>
     * Given the present geometry, look up the location of a point specified in x,y and z coordinates of the point in
     * the DICOM 3D coordinate space, and return the volume coordinates (column and row and frame offset).
     * </p>
     * 
     * @param offsets
     *            an array in which to return the column and row and frame offsets from the top left hand corner of the
     *            volume
     * @param location
     *            the x, y and z location in 3D space
     */
    public final void lookupImageCoordinate(double offsets[], double[] location) {
        double[] rowArray = frames[0].getRowArray();
        double[] columnArray = frames[0].getColumnArray();
        double[] normalArray = frames[0].getNormalArray();
        double[] tlhcArray = frames[0].getTLHCArray();
        double[] voxelSpacingArray = frames[0].getVoxelSpacingArray();

        double frame =
            (((location[0] - tlhcArray[0]) * columnArray[1] * voxelSpacingArray[0] - (location[1] - tlhcArray[1])
                * columnArray[0] * voxelSpacingArray[0])
                * (rowArray[2] * voxelSpacingArray[1] * columnArray[1] * voxelSpacingArray[0] - rowArray[1]
                    * voxelSpacingArray[1] * columnArray[2] * voxelSpacingArray[0]) - ((location[2] - tlhcArray[2])
                * columnArray[1] * voxelSpacingArray[0] - (location[1] - tlhcArray[1]) * columnArray[2]
                * voxelSpacingArray[0])
                * (rowArray[0] * voxelSpacingArray[1] * columnArray[1] * voxelSpacingArray[0] - rowArray[1]
                    * voxelSpacingArray[1] * columnArray[0] * voxelSpacingArray[0]))
                / ((normalArray[0] * voxelSpacingArray[2] * columnArray[1] * voxelSpacingArray[0] + normalArray[1]
                    * voxelSpacingArray[2] * columnArray[0] * voxelSpacingArray[0])
                    * (rowArray[2] * voxelSpacingArray[1] * columnArray[1] * voxelSpacingArray[0] - rowArray[1]
                        * voxelSpacingArray[1] * columnArray[2] * voxelSpacingArray[0]) - (normalArray[2]
                    * voxelSpacingArray[2] * columnArray[1] * voxelSpacingArray[0] + normalArray[1]
                    * voxelSpacingArray[2] * columnArray[2] * voxelSpacingArray[0])
                    * (rowArray[0] * voxelSpacingArray[1] * columnArray[1] * voxelSpacingArray[0] - rowArray[1]
                        * voxelSpacingArray[1] * columnArray[0] * voxelSpacingArray[0]));

        double column =
            ((location[0] - tlhcArray[0] - frame * normalArray[0] * voxelSpacingArray[2]) * columnArray[1]
                * voxelSpacingArray[0] - (location[1] - tlhcArray[1] - frame * normalArray[1] * voxelSpacingArray[2])
                * columnArray[0] * voxelSpacingArray[0])
                / (rowArray[0] * voxelSpacingArray[1] * columnArray[1] * voxelSpacingArray[0] - rowArray[1]
                    * voxelSpacingArray[1] * columnArray[0] * voxelSpacingArray[0]);

        double row =
            (location[1] - tlhcArray[1] - column * rowArray[1] * voxelSpacingArray[1] - frame * normalArray[1]
                * voxelSpacingArray[2])
                / (columnArray[1] * voxelSpacingArray[0]);
        offsets[0] = column;
        offsets[1] = row;
        offsets[2] = frame;
    }

    /**
     * <p>
     * Find the slice in the our geometry that is closest to the supplied slice geometry.
     * </p>
     * 
     * <p>
     * Specifically, the shortest distance along the normal to the plane of the common orientation is chosen.
     * </p>
     * 
     * @param otherSlice
     *            the geometry of the slice to match
     * @return the index of the closest frame in this volume, or -1 if something goes wrong
     */

    public final int findClosestSliceInSamePlane(GeometryOfSlice otherSlice) {
        // System.err.println("GeometryOfVolume.findClosestSliceInSamePlane():");
        double otherDistance = otherSlice.getDistanceAlongNormalFromOrigin();
        int found = -1;
        double closest = 999999999;
        for (int i = 0; i < frames.length; ++i) {
            // System.err.println("GeometryOfVolume.findClosestSliceInSamePlane(): testing "+i);
            double distance = Math.abs(frames[i].getDistanceAlongNormalFromOrigin() - otherDistance);
            if (distance < closest) {
                // System.err.println("GeometryOfVolume.findClosestSliceInSamePlane(): closer");
                closest = distance;
                found = i;
            }
        }
        // System.err.println("GeometryOfVolume.findClosestSliceInSamePlane(): found = "+found);
        return found;
    }

    /**
     * <p>
     * Given the present geometry, determine the distances along the normal to the plane of the slices of the TLHC of
     * each slice from the origin of the coordinate space (0,0,0).
     * </p>
     * 
     * @return an array of the distances of the TLHCs from the origin along the normal axis
     */
    public final double[] getDistanceAlongNormalFromOrigin() {
        double[] distances = new double[frames.length];
        for (int i = 0; i < frames.length; ++i) {
            distances[i] = frames[i].getDistanceAlongNormalFromOrigin();
        }
        return distances;
    }

    /**
     * <p>
     * Is the set of frames regularly sampled along the frame dimension ?
     * </p>
     * 
     * @return true if same spacing between centers of frames and position monotonically increasing
     */
    public final boolean isVolumeSampledRegularlyAlongFrameDimension() {
        return isVolume;
    }

    /**
     * <p>
     * Check if the set of frames regularly sampled along the frame dimension.
     * </p>
     * 
     * <p>
     * Method is public only to make it accessible from constructors in other packages.
     * </p>
     * 
     */
    public final void checkAndSetVolumeSampledRegularlyAlongFrameDimension() {
        if (frames != null && frames.length > 1) {
            // check to see if we are actually a volume
            // - more than one slice
            // - slices all parallel
            // - spacing between slices the same
            // - distance along normal to plane of slice monotonically increasing

            GeometryOfSlice lastGeometry = frames[0];
            GeometryOfSlice currentGeometry = frames[1];
            double lastDistanceAlongNormal = lastGeometry.getDistanceAlongNormalFromOrigin();
            if (GeometryOfSlice.areSlicesParallel(lastGeometry, currentGeometry)) {
                double currentDistanceAlongNormal = currentGeometry.getDistanceAlongNormalFromOrigin();
                double wantIntervalAlongNormal = currentDistanceAlongNormal - lastDistanceAlongNormal;
                lastDistanceAlongNormal = currentDistanceAlongNormal;
                boolean success = true;
                for (int f = 2; f < frames.length && success; ++f) {
                    currentGeometry = frames[f];
                    if (GeometryOfSlice.areSlicesParallel(lastGeometry, currentGeometry)) {
                        currentDistanceAlongNormal = currentGeometry.getDistanceAlongNormalFromOrigin();
                        double currentIntervalAlongNormal = currentDistanceAlongNormal - lastDistanceAlongNormal;
                        if (Math.abs(currentIntervalAlongNormal - wantIntervalAlongNormal) >= .001) {
                            success = false; // different spacing
                        }
                        lastDistanceAlongNormal = currentDistanceAlongNormal;
                    } else {
                        success = false; // not parallel
                    }
                }
                if (success) {
                    // System.err.println("GeometryOfVolume.checkAndSetVolumeSampledRegularlyAlongFrameDimension(): spacing="+wantIntervalAlongNormal);
                    isVolume = true;
                    wantIntervalAlongNormal = Math.abs(wantIntervalAlongNormal); // since sign may be negative and we
                    // don't want that
                    for (int f = 0; f < frames.length; ++f) {
                        frames[f].setVoxelSpacingBetweenSlices(wantIntervalAlongNormal);
                    }
                }
            }
            // else not parallel
        }
        // System.err.println("GeometryOfVolume.checkAndSetVolumeSampledRegularlyAlongFrameDimension(): isVolume="+isVolume);
        // System.err.println(toString());
    }

    /**
     * <p>
     * Get a human-readable rendering of the geometry.
     * </p>
     * 
     * @return the string rendering of the geometry
     */
    @Override
    public final String toString() {
        StringBuffer str = new StringBuffer();
        for (int f = 0; f < frames.length; ++f) {
            str.append("["); //$NON-NLS-1$
            str.append(f);
            str.append("] "); //$NON-NLS-1$
            str.append(frames[f].toString());
            str.append("\n"); //$NON-NLS-1$
        }
        return str.toString();
    }

    /**
     * <p>
     * Get the letter representation of the orientation of the rows of this slice.
     * </p>
     * 
     * @param frame
     *            the offset along the frames from first frame, zero being no offset
     * @return a string rendering of the row orientation, L or R, A or P, H or F, more than one letter if oblique to the
     *         orthogonal axes, or empty string (not null) if fails
     */
    public final String getRowOrientation(int frame) {
        return frames != null && frame < frames.length ? frames[frame].getRowOrientation() : ""; //$NON-NLS-1$
    }

    /**
     * <p>
     * Get the letter representation of the orientation of the columns of this slice.
     * </p>
     * 
     * @param frame
     *            the offset along the frames from first frame, zero being no offset
     * @return a string rendering of the column orientation, L or R, A or P, H or F, more than one letter if oblique to
     *         the orthogonal axes, or empty string (not null) if fails
     */
    public final String getColumnOrientation(int frame) {
        return frames != null && frame < frames.length ? frames[frame].getColumnOrientation() : ""; //$NON-NLS-1$
    }

}
