/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import static org.opencv.core.Core.addWeighted;
import static org.opencv.core.Core.minMaxLoc;
import static org.opencv.core.Core.multiply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.op.ImageConversion;

public class Dose extends HashMap<Integer, Dvh> {
    private static final long serialVersionUID = -1659662753587452881L;

    private String sopInstanceUid;
    private double[] imagePositionPatient;
    private String comment;
    private String doseUnit;
    private String doseType;
    private String doseSummationType;
    private double[] gridFrameOffsetVector;
    private double doseGridScaling;
    private double doseMax;

    private double doseSlicePositionThreshold;

    private List<MediaElement> images = new ArrayList<>();
    private Map<Integer, IsoDoseLayer> isoDoseSet = new LinkedHashMap<>();
    private Map<String, ArrayList<Contour>> isoContourMap = new HashMap<>();

    // Dose LUTs
    private Pair<double[], double[]> doseMmLUT;
    private Pair<double[], double[]> dosePixLUT;

    public Dose() {
        // Default threshold in mm to determine the max difference from slicePosition to closest dose frame without interpolation
        this.doseSlicePositionThreshold = 0.5;
        this.doseMax = 0.0;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public double[] getImagePositionPatient() {
        return imagePositionPatient;
    }

    public void setImagePositionPatient(double[] imagePositionPatient) {
        this.imagePositionPatient = imagePositionPatient;
    }

    public String getComment() {
        return this.comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDoseUnit() {
        return this.doseUnit;
    }

    public void setDoseUnit(String doseUnit) {
        this.doseUnit = doseUnit;
    }

    public String getDoseType() {
        return doseType;
    }

    public void setDoseType(String doseType) {
        this.doseType = doseType;
    }

    public String getDoseSummationType() {
        return doseSummationType;
    }

    public void setDoseSummationType(String doseSummationType) {
        this.doseSummationType = doseSummationType;
    }

    public double[] getGridFrameOffsetVector() {
        return this.gridFrameOffsetVector;
    }

    public void setGridFrameOffsetVector(double[] gridFrameOffsetVector) {
        this.gridFrameOffsetVector = gridFrameOffsetVector;
    }

    public double getDoseGridScaling() {
        return doseGridScaling;
    }

    public void setDoseGridScaling(double doseGridScaling) {
        this.doseGridScaling = doseGridScaling;
    }

    public double getDoseMax() {
        // Initialise max dose once dose images are available
        if (!this.images.isEmpty() && doseMax < 0.01) {
            for (MediaElement me : this.images) {
                Core.MinMaxLocResult minMaxLoc = minMaxLoc(((ImageElement) me).getImage().toMat());
                if (doseMax < minMaxLoc.maxVal) {
                    doseMax = minMaxLoc.maxVal;
                }
            }
        }

        return doseMax;
    }

    public double getDoseSlicePositionThreshold() {
        return this.doseSlicePositionThreshold;
    }

    public void setDoseSlicePositionThreshold(double doseSlicePositionThreshold) {
        this.doseSlicePositionThreshold = doseSlicePositionThreshold;
    }

    public List<MediaElement> getImages() {
        return this.images;
    }

    public void setImages(List<MediaElement> images) {
        this.images = images;
    }

    public Map<Integer, IsoDoseLayer> getIsoDoseSet() {
        return this.isoDoseSet;
    }

    public void setIsoDoseSet(Map<Integer, IsoDoseLayer> isoDoseSet) {
        this.isoDoseSet = isoDoseSet;
    }

    public Map<String, ArrayList<Contour>> getIsoContourMap() {
        return this.isoContourMap;
    }

    public void setIsoContourMap(Map<String, ArrayList<Contour>> isoContourMap) {
        this.isoContourMap = isoContourMap;
    }

    public Pair<double[], double[]> getDoseMmLUT() {
        return this.doseMmLUT;
    }

    public void setDoseMmLUT(Pair<double[], double[]> lut) {
        this.doseMmLUT = lut;
    }

    public Pair<double[], double[]> getDosePixLUT() {
        return this.dosePixLUT;
    }

    public void setDosePixLUT(Pair<double[], double[]> lut) {
        this.dosePixLUT = lut;
    }

    public MediaElement getDosePlaneBySlice(double slicePosition) {
        MediaElement dosePlane = null;

        // If dose contains a multi-frame dose pixel array
        if (this.gridFrameOffsetVector.length > 0) {

            // Initial dose grid position Z (in patient coordinates)
            double imagePatientPositionZ = this.imagePositionPatient[2];

            // Add initial image patient position Z to the offset vector to determine the Z coordinate of each dose plane
            double[] dosePlanesZ = new double[this.gridFrameOffsetVector.length];
            for (int i = 0; i < dosePlanesZ.length; i++) {
                dosePlanesZ[i] = this.gridFrameOffsetVector[i]  + imagePatientPositionZ;
            }

            // Check whether the requested plane is within the dose grid boundaries
            if (Arrays.stream(dosePlanesZ).min().getAsDouble() <= slicePosition && slicePosition <= Arrays.stream(dosePlanesZ).max().getAsDouble()) {

                // Calculate the absolute distance vector between dose planes and requested slice position
                double[] absoluteDistance = new double[dosePlanesZ.length];
                for (int i = 0; i < absoluteDistance.length; i++) {
                    absoluteDistance[i] = Math.abs(dosePlanesZ[i] - slicePosition);
                }

                // Check to see if the requested plane exists in the array (or is close enough)
                int doseSlicePosition = -1;
                double minDistance = Arrays.stream(absoluteDistance).min().getAsDouble();
                if (minDistance < this.doseSlicePositionThreshold) {
                    doseSlicePosition = firstIndexOf(absoluteDistance, minDistance, 0.001);
                }

                // Dose slice position found return the plane
                if (doseSlicePosition != -1) {
                    dosePlane = this.images.get(doseSlicePosition);
                }
                // There is no dose plane for such slice position, so interpolate between planes
                else {

                    // First minimum distance - upper boundary
                    int upperBoundaryIndex = firstIndexOf(absoluteDistance, minDistance, 0.001);

                    // Prepare modified absolute distance vector to find the second minimum
                    double[] modifiedAbsoluteDistance = Arrays.copyOf(absoluteDistance, absoluteDistance.length);
                    modifiedAbsoluteDistance[upperBoundaryIndex] = Arrays.stream(absoluteDistance).max().getAsDouble();

                    // Second minimum distance - lower boundary
                    minDistance = Arrays.stream(modifiedAbsoluteDistance).min().getAsDouble();
                    int lowerBoundaryIndex = firstIndexOf(modifiedAbsoluteDistance, minDistance, 0.001);

                    // Fractional distance of dose plane between upper and lower boundary (from bottom to top)
                    // E.g. if = 1, the plane is at the upper plane, = 0, it is at the lower plane.
                    double fractionalDistance = (slicePosition - dosePlanesZ[lowerBoundaryIndex]) / (dosePlanesZ[upperBoundaryIndex] - dosePlanesZ[lowerBoundaryIndex]);

                    dosePlane = this.interpolateDosePlanes(upperBoundaryIndex, lowerBoundaryIndex, fractionalDistance);
                }
            }
        }

        return dosePlane;
    }

    public Mat getMaskedDosePlaneHist(double slicePosition, Mat mask, int maxDose) {

        DicomImageElement dosePlane = (DicomImageElement) this.getDosePlaneBySlice(slicePosition);

        int rows = dosePlane.getImage().toMat().rows();
        int cols = dosePlane.getImage().toMat().cols();

        // Calculate dose matrix for OpenCV
        Mat src = new Mat(rows, cols, CvType.CV_32FC1);
        dosePlane.getImage().toMat().convertTo(src, CvType.CV_32FC1);
        Scalar scalar = new Scalar(this.doseGridScaling * 100);
        Mat doseMatrix = new Mat(rows, cols, CvType.CV_32FC1);
        multiply(src, scalar, doseMatrix);
        ImageConversion.releaseMat(src);
        List<Mat> doseMatrixVector = new ArrayList<>();
        doseMatrixVector.add(doseMatrix);

        // Masked dose plan histogram
        Mat hist = new Mat();
        // Number of histogram bins
        MatOfInt histSize = new MatOfInt(maxDose);
        // Dose varies from 0 to maxDose
        MatOfFloat histRange = new MatOfFloat(0, maxDose);
        // Only one 0-th channel
        MatOfInt channels = new MatOfInt(0);

        // Ned to change the structure dose mask type vor OpenCV histogram calculation
        Mat maskSrc = new Mat(mask.rows(), mask.cols(), CvType.CV_8U);
        mask.convertTo(maskSrc, CvType.CV_8U);

        Imgproc.calcHist(doseMatrixVector, channels, maskSrc, hist, histSize, histRange);
        ImageConversion.releaseMat(maskSrc);
        ImageConversion.releaseMat(doseMatrix);
        return hist;
    }

    public List<MatOfPoint> getIsoDoseContourPoints(KeyDouble slicePosition, double isoDoseThreshold) {
        List<MatOfPoint> contours = new ArrayList<>();

        // Convert from threshold in cCy to raw pixel value threshold
        double rawThreshold = (isoDoseThreshold / 100) / this.doseGridScaling;

        DicomImageElement dosePlane = (DicomImageElement) this.getDosePlaneBySlice(slicePosition.getValue());

        int rows = dosePlane.getImage().toMat().rows();
        int cols = dosePlane.getImage().toMat().cols();

        Mat src = new Mat(rows, cols, CvType.CV_32FC1);
        Mat thr = new Mat(rows, cols, CvType.CV_32FC1);

        dosePlane.getImage().toMat().convertTo(src, CvType.CV_32FC1);

        Mat hierarchy = new Mat();
        
        Imgproc.threshold(src, thr, rawThreshold, 255, Imgproc.THRESH_BINARY);
        ImageConversion.releaseMat(src);
        Mat thrSrc = new Mat(rows, cols, CvType.CV_8U);
        thr.convertTo(thrSrc, CvType.CV_8U);
        ImageConversion.releaseMat(thr);
        
        Imgproc.findContours(thrSrc, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        ImageConversion.releaseMat(thrSrc);
        return contours;
    }

    public void initialiseDoseGridToImageGrid(Image patientImage) {

        // Transpose the dose grid LUT onto the image grid LUT
        double[] x = new double[this.doseMmLUT.getFirst().length];
        for (int i = 0; i < this.doseMmLUT.getFirst().length; i++) {
            x[i] = (this.doseMmLUT.getFirst()[i] - patientImage.getImageLUT().getFirst()[0]) * patientImage.getProne() * patientImage.getFeetFirst() / patientImage.getImageSpacing()[0];

        }
        double[] y = new double[this.doseMmLUT.getSecond().length];
        for (int j = 0; j < this.doseMmLUT.getSecond().length; j++) {
            y[j] = (this.doseMmLUT.getSecond()[j]) - patientImage.getImageLUT().getSecond()[0] * patientImage.getProne() / patientImage.getImageSpacing()[1];
        }

        this.dosePixLUT = new Pair<>(x, y);
    }

    private MediaElement interpolateDosePlanes(int upperBoundaryIndex, int lowerBoundaryIndex, double fractionalDistance) {
        MediaElement dosePlane = null;

        DicomImageElement upperPlane = (DicomImageElement) this.images.get(upperBoundaryIndex);
        DicomImageElement lowerPlane = (DicomImageElement) this.images.get(lowerBoundaryIndex);
        
        // A simple linear interpolation (lerp)
        Mat dosePlaneMat = new Mat();
        addWeighted(lowerPlane.getImage().toMat(), 1.0 - fractionalDistance, upperPlane.getImage().toMat(), fractionalDistance, 0.0, dosePlaneMat);

        //TODO: dosePlaneMat should be an image for new dosePlane MediaElement

        return dosePlane;
    }

    private static int firstIndexOf(double[] array, double valueToFind, double tolerance) {
        for(int i = 0; i < array.length; i++) {
            if (Math.abs(array[i] - valueToFind) < tolerance) {
                return i;
            }
        }
        return -1;
    }

}
