/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.io.File;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.util.UIDUtils;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Image I/O handler for curved MPR panoramic images.
 * 
 * <p>This class generates the "straightened" panoramic view by sampling the volume along
 * the curved path defined in CurvedMprAxis.
 */
public class CurvedMprImageIO implements DcmMediaReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(CurvedMprImageIO.class);

  private static final String MIME_TYPE = "image/cmpr";
  
  // Debug visualization data - stores the last computed curve info for overlay drawing
  private static volatile DebugCurveData lastDebugData = null;
  
  /**
   * Debug data for visualizing the computed curve and perpendicular directions.
   */
  public static class DebugCurveData {
    public final List<Vector3d> originalPoints;
    public final List<Vector3d> smoothedPoints;
    public final List<Vector3d> sampledPoints;
    public final List<Vector3d> perpDirections;
    public final double slabThicknessMm;
    
    public DebugCurveData(List<Vector3d> original, List<Vector3d> smoothed, 
        List<Vector3d> sampled, List<Vector3d> perps, double slabMm) {
      this.originalPoints = new ArrayList<>(original);
      this.smoothedPoints = new ArrayList<>(smoothed);
      this.sampledPoints = new ArrayList<>(sampled);
      this.perpDirections = new ArrayList<>(perps);
      this.slabThicknessMm = slabMm;
    }
  }
  
  public static DebugCurveData getLastDebugData() {
    return lastDebugData;
  }
  private static final SoftHashMap<CurvedMprImageIO, DicomMetaData> HEADER_CACHE =
      new SoftHashMap<>() {
        @Override
        public void removeElement(Reference<? extends DicomMetaData> soft) {
          CurvedMprImageIO key = reverseLookup.remove(soft);
          if (key != null) {
            hash.remove(key);
          }
        }
      };

  private final FileCache fileCache;
  private final HashMap<TagW, Object> tags;
  private final URI uri;
  private final CurvedMprAxis axis;
  private final Volume<?> volume;
  private Attributes attributes;

  public CurvedMprImageIO(CurvedMprAxis axis) {
    this.axis = Objects.requireNonNull(axis);
    this.volume = axis.getVolume();
    this.fileCache = new FileCache(this);
    this.tags = new HashMap<>();
    try {
      this.uri = new URI("data:" + MIME_TYPE);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public void setBaseAttributes(Attributes attributes) {
    this.attributes = attributes;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    return generatePanoramicImage();
  }

  /**
   * Generate the panoramic image using Curved Planar Reformation (CPR).
   * 
   * <p>For a dental arch curve drawn on the axial plane (XY plane), this uses a
   * straightforward approach:
   * <ul>
   *   <li>The curve lies in the XY plane at a fixed Z (the axial slice level)</li>
   *   <li>At each curve point, the tangent is computed in XY</li>
   *   <li>The in-plane perpendicular (Z × tangent) is used for MIP slab (depth)</li>
   *   <li>The vertical (Z) direction is used for height sampling</li>
   *   <li>Output X = arc-length position along the curve</li>
   *   <li>Output Y = vertical (Z) position</li>
   * </ul>
   */
  private PlanarImage generatePanoramicImage() {
    List<Vector3d> curvePoints = axis.getCurvePoints3D();
    if (curvePoints.size() < 2) {
      return null;
    }

    double stepMm = axis.getStepMm();
    double widthMm = axis.getWidthMm();
    double pixelMm = volume.getMinPixelRatio();
    Vector3d voxelRatio = volume.getVoxelRatio();

    LOGGER.info("=== generatePanoramicImage CPR ===");
    LOGGER.info("curvePoints: {}, stepMm: {}, widthMm: {}, pixelMm: {}", 
        curvePoints.size(), stepMm, widthMm, pixelMm);
    LOGGER.info("volume size: {}x{}x{}, voxelRatio: ({},{},{})",
        volume.getSize().x, volume.getSize().y, volume.getSize().z,
        voxelRatio.x, voxelRatio.y, voxelRatio.z);

    // Smooth the curve using Catmull-Rom spline interpolation
    List<Vector3d> smoothedPoints = smoothCurveWithSpline(curvePoints);
    
    // Resample to uniform arc-length spacing along the curve
    List<Vector3d> sampledPoints = resampleCurve(smoothedPoints, stepMm, pixelMm);
    if (sampledPoints.isEmpty()) {
      return null;
    }
    
    // Reverse for dentist view (patient's right on viewer's left)
    Collections.reverse(sampledPoints);

    // Compute in-plane perpendicular directions for MIP slab
    // For a curve in the XY plane: perp = Z × tangent (gives in-plane perpendicular)
    List<Vector3d> perpDirs = computePerpendicularDirections(
        sampledPoints, axis.getPlaneNormal());
    
    // Slab thickness (in mm) for MIP along the perpendicular direction
    double slabThicknessMm = 10.0;
    int slabSamples = (int) Math.max(1, Math.round(slabThicknessMm / pixelMm));
    
    // Vertical extent: height in Z direction (in mm)
    double sliceSizeMm = widthMm;
    int heightPx = (int) Math.round(sliceSizeMm / pixelMm);
    if (heightPx < 1) heightPx = 1;
    
    int widthPx = sampledPoints.size();
    
    // Store debug data for visualization
    lastDebugData = new DebugCurveData(curvePoints, smoothedPoints, sampledPoints, perpDirs, slabThicknessMm);

    LOGGER.info("Output: {}x{} px, slab={}mm ({} samples), height={}mm", 
        widthPx, heightPx, slabThicknessMm, slabSamples, sliceSizeMm);

    int cvType = volume.getCVType();
    ImageCV dst = new ImageCV(heightPx, widthPx, cvType);

    // The Z coordinate of the curve (axial slice level)
    double curveZ = sampledPoints.get(0).z;

    // Diagnostic logging
    int midI = widthPx / 2;
    Vector3d midPt = sampledPoints.get(midI);
    Vector3d midPerp = perpDirs.get(midI);
    LOGGER.info("Middle curve point[{}]: ({},{},{})", midI, 
        String.format("%.1f", midPt.x), String.format("%.1f", midPt.y), String.format("%.1f", midPt.z));
    LOGGER.info("Middle perp: ({},{},{})",
        String.format("%.3f", midPerp.x), String.format("%.3f", midPerp.y), String.format("%.3f", midPerp.z));
    
    // For each point along the curve (horizontal axis of panoramic)
    for (int i = 0; i < widthPx; i++) {
      Vector3d curvePoint = sampledPoints.get(i);
      Vector3d perp = perpDirs.get(i); // in-plane perpendicular for MIP slab

      // For each pixel in the vertical direction (Z axis)
      for (int j = 0; j < heightPx; j++) {
        // Vertical offset in voxels along Z, centered on the curve's Z level
        // Account for voxel ratio: convert pixel offset to voxel offset
        double zOffsetVoxels = (j - heightPx / 2.0) / voxelRatio.z;
        double sampleZ = curveZ + zOffsetVoxels;
        
        // MIP along the in-plane perpendicular direction (slab thickness)
        double maxValue = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < slabSamples; k++) {
          double perpOffset = (k - slabSamples / 2.0);
          double sampleX = curvePoint.x + perp.x * perpOffset;
          double sampleY = curvePoint.y + perp.y * perpOffset;
          
          Number value = volume.getInterpolatedValueFromSource(sampleX, sampleY, sampleZ);
          if (value != null && value.doubleValue() > maxValue) {
            maxValue = value.doubleValue();
          }
        }
        
        if (maxValue > Double.NEGATIVE_INFINITY) {
          setPixelValue(dst, j, i, maxValue, cvType);
        }
      }
    }
    
    LOGGER.info("Generated CPR panoramic image");

    setDicomTags(widthPx, heightPx, pixelMm, stepMm);
    return dst;
  }
  
  /**
   * Compute a parallel transport frame along the curve.
   * 
   * <p>This creates a consistent coordinate system at each curve point:
   * <ul>
   *   <li>Tangent: direction along the curve</li>
   *   <li>Normal: perpendicular to tangent, smoothly transported along curve</li>
   *   <li>Binormal: perpendicular to both tangent and normal</li>
   * </ul>
   * 
   * <p>The parallel transport frame avoids the twisting that occurs with 
   * Frenet-Serret frames at inflection points.
   */
  private void computeParallelTransportFrame(
      List<Vector3d> points,
      List<Vector3d> tangents,
      List<Vector3d> normals,
      List<Vector3d> binormals) {
    
    int n = points.size();
    if (n < 2) return;
    
    // Compute tangent vectors
    for (int i = 0; i < n; i++) {
      Vector3d tangent;
      if (i == 0) {
        tangent = new Vector3d(points.get(1)).sub(points.get(0));
      } else if (i == n - 1) {
        tangent = new Vector3d(points.get(n - 1)).sub(points.get(n - 2));
      } else {
        tangent = new Vector3d(points.get(i + 1)).sub(points.get(i - 1));
      }
      tangent.normalize();
      tangents.add(tangent);
    }
    
    // Initialize the first normal using a reference direction
    // For dental (curve in XY plane), use Z as the initial binormal reference
    Vector3d refUp = new Vector3d(0, 0, 1);
    Vector3d firstTangent = tangents.get(0);
    
    // First normal = refUp × tangent (gives a vector in XY plane, perpendicular to tangent)
    Vector3d firstNormal = new Vector3d(refUp).cross(firstTangent);
    if (firstNormal.lengthSquared() < 1e-10) {
      // Tangent is parallel to Z, use X as reference
      firstNormal = new Vector3d(1, 0, 0).cross(firstTangent);
    }
    firstNormal.normalize();
    
    // First binormal = tangent × normal
    Vector3d firstBinormal = new Vector3d(firstTangent).cross(firstNormal);
    firstBinormal.normalize();
    
    normals.add(firstNormal);
    binormals.add(firstBinormal);
    
    // Propagate the frame along the curve using parallel transport
    for (int i = 1; i < n; i++) {
      Vector3d prevNormal = normals.get(i - 1);
      Vector3d prevBinormal = binormals.get(i - 1);
      Vector3d prevTangent = tangents.get(i - 1);
      Vector3d currTangent = tangents.get(i);
      
      // Compute the rotation axis and angle between consecutive tangents
      Vector3d rotAxis = new Vector3d(prevTangent).cross(currTangent);
      double sinAngle = rotAxis.length();
      double cosAngle = prevTangent.dot(currTangent);
      
      Vector3d newNormal, newBinormal;
      
      if (sinAngle > 1e-10) {
        // Rotate the previous normal and binormal to align with new tangent
        rotAxis.normalize();
        double angle = Math.atan2(sinAngle, cosAngle);
        
        // Rodrigues' rotation formula
        newNormal = rotateVector(prevNormal, rotAxis, angle);
        newBinormal = rotateVector(prevBinormal, rotAxis, angle);
      } else {
        // Tangents are parallel, just copy
        newNormal = new Vector3d(prevNormal);
        newBinormal = new Vector3d(prevBinormal);
      }
      
      // Re-orthogonalize to prevent drift
      newBinormal = new Vector3d(currTangent).cross(newNormal);
      newBinormal.normalize();
      newNormal = new Vector3d(newBinormal).cross(currTangent);
      newNormal.normalize();
      
      normals.add(newNormal);
      binormals.add(newBinormal);
    }
    
    // Ensure normals point outward from the dental arch
    // Check if the middle normal points toward or away from the curve centroid
    Vector3d centroid = new Vector3d(0, 0, 0);
    for (Vector3d p : points) {
      centroid.add(p);
    }
    centroid.div(n);
    
    int midIdx = n / 2;
    Vector3d toMid = new Vector3d(points.get(midIdx)).sub(centroid);
    if (normals.get(midIdx).dot(toMid) < 0) {
      // Flip all normals to point outward
      for (int i = 0; i < n; i++) {
        normals.get(i).negate();
        binormals.get(i).negate();
      }
    }
    
    LOGGER.info("Computed parallel transport frame for {} points", n);
    // Log some sample frame values for debugging
    int[] sampleIdxs = {0, n/2, n-1};
    for (int idx : sampleIdxs) {
      Vector3d t = tangents.get(idx);
      Vector3d nn = normals.get(idx);
      Vector3d b = binormals.get(idx);
      LOGGER.info("Frame[{}]: T=({},{},{}), N=({},{},{}), B=({},{},{})",
          idx, 
          String.format("%.2f", t.x), String.format("%.2f", t.y), String.format("%.2f", t.z),
          String.format("%.2f", nn.x), String.format("%.2f", nn.y), String.format("%.2f", nn.z),
          String.format("%.2f", b.x), String.format("%.2f", b.y), String.format("%.2f", b.z));
    }
  }
  
  /**
   * Rotate a vector around an axis using Rodrigues' rotation formula.
   */
  private Vector3d rotateVector(Vector3d v, Vector3d axis, double angle) {
    double cos = Math.cos(angle);
    double sin = Math.sin(angle);
    
    // v_rot = v*cos + (axis × v)*sin + axis*(axis·v)*(1-cos)
    Vector3d cross = new Vector3d(axis).cross(v);
    double dot = axis.dot(v);
    
    return new Vector3d(v).mul(cos)
        .add(new Vector3d(cross).mul(sin))
        .add(new Vector3d(axis).mul(dot * (1 - cos)));
  }

  /**
   * Compute the perpendicular direction to the curve tangent at each point.
   * The perpendicular is computed in the plane defined by planeNormal (typically XY plane).
   * This direction is used for the slab thickness in MIP.
   * 
   * <p>Perpendicular directions are kept consistent along the curve by ensuring
   * each direction doesn't flip relative to its predecessor. This prevents
   * sudden direction changes that would cause sampling artifacts.
   * 
   * @param sampledPoints the resampled curve points
   * @param planeNormal the normal of the source plane (e.g., Z for axial)
   * @return list of unit perpendicular directions at each point
   */
  private List<Vector3d> computePerpendicularDirections(
      List<Vector3d> sampledPoints, Vector3d planeNormal) {
    List<Vector3d> perpDirs = new ArrayList<>();
    int n = sampledPoints.size();
    
    Vector3d prevPerp = null;
    
    for (int i = 0; i < n; i++) {
      Vector3d tangent;
      if (i == 0) {
        tangent = new Vector3d(sampledPoints.get(1)).sub(sampledPoints.get(0));
      } else if (i == n - 1) {
        tangent = new Vector3d(sampledPoints.get(n - 1)).sub(sampledPoints.get(n - 2));
      } else {
        tangent = new Vector3d(sampledPoints.get(i + 1)).sub(sampledPoints.get(i - 1));
      }
      
      // Compute perpendicular in the plane: perp = planeNormal × tangent
      Vector3d perp = new Vector3d(planeNormal).cross(tangent);
      
      if (perp.lengthSquared() > 1e-10) {
        perp.normalize();
      } else {
        perp = (prevPerp != null) ? new Vector3d(prevPerp) : new Vector3d(1, 0, 0);
      }
      
      // Ensure consistency with previous perpendicular (no sudden flips)
      if (prevPerp != null && perp.dot(prevPerp) < 0) {
        perp.negate();
      }
      
      perpDirs.add(perp);
      prevPerp = perp;
    }
    
    // Now determine if we need to flip ALL directions to point "outward"
    // Use the curve's overall shape: for a dental arch, the middle of the curve
    // should have perpendiculars pointing "forward" (away from the throat)
    // We check by looking at the middle point's perpendicular relative to curve center
    if (n >= 3) {
      Vector3d centroid = new Vector3d(0, 0, 0);
      for (Vector3d p : sampledPoints) {
        centroid.add(p);
      }
      centroid.div(n);
      
      int midIdx = n / 2;
      Vector3d midPoint = sampledPoints.get(midIdx);
      Vector3d midPerp = perpDirs.get(midIdx);
      Vector3d toMid = new Vector3d(midPoint).sub(centroid);
      
      // If middle perpendicular points inward (toward centroid), flip all
      if (midPerp.dot(toMid) < 0) {
        LOGGER.info("Flipping all perpendiculars to point outward");
        for (Vector3d p : perpDirs) {
          p.negate();
        }
      }
    }
    
    return perpDirs;
  }

  /**
   * Smooth the curve using Catmull-Rom spline interpolation.
   * This converts rough user-drawn polylines into smooth curves.
   * 
   * <p>The number of samples per segment is proportional to the segment length
   * to ensure uniform sampling density along the entire curve.
   * 
   * @param points the original control points
   * @return smoothed curve with many more points
   */
  private List<Vector3d> smoothCurveWithSpline(List<Vector3d> points) {
    if (points.size() < 2) return new ArrayList<>(points);
    if (points.size() == 2) return new ArrayList<>(points);
    
    List<Vector3d> result = new ArrayList<>();
    
    // Calculate segment lengths to determine proportional sampling
    double[] segmentLengths = new double[points.size() - 1];
    double totalLength = 0;
    for (int i = 0; i < points.size() - 1; i++) {
      segmentLengths[i] = points.get(i).distance(points.get(i + 1));
      totalLength += segmentLengths[i];
    }
    
    // Target: approximately 1 sample per voxel along the curve
    // Use a base density that gives good smoothing
    double samplesPerVoxel = 2.0;
    
    for (int i = 0; i < points.size() - 1; i++) {
      // Get 4 control points for Catmull-Rom (with clamping at endpoints)
      Vector3d p0 = points.get(Math.max(0, i - 1));
      Vector3d p1 = points.get(i);
      Vector3d p2 = points.get(i + 1);
      Vector3d p3 = points.get(Math.min(points.size() - 1, i + 2));
      
      // Number of samples proportional to segment length
      int segmentSamples = Math.max(2, (int) Math.round(segmentLengths[i] * samplesPerVoxel));
      
      // Generate points along this segment
      for (int j = 0; j < segmentSamples; j++) {
        double t = (double) j / segmentSamples;
        Vector3d interpolated = catmullRom(p0, p1, p2, p3, t);
        result.add(interpolated);
      }
    }
    
    // Add the last point
    result.add(new Vector3d(points.get(points.size() - 1)));
    
    LOGGER.info("Smoothed curve: {} input points -> {} output points (total length: {} voxels)", 
        points.size(), result.size(), totalLength);
    
    return result;
  }
  
  /**
   * Catmull-Rom spline interpolation between p1 and p2.
   * 
   * @param p0 control point before p1
   * @param p1 start point of segment
   * @param p2 end point of segment  
   * @param p3 control point after p2
   * @param t interpolation parameter [0, 1]
   * @return interpolated point
   */
  private Vector3d catmullRom(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
    double t2 = t * t;
    double t3 = t2 * t;
    
    // Catmull-Rom basis functions
    double b0 = -0.5 * t3 + t2 - 0.5 * t;
    double b1 = 1.5 * t3 - 2.5 * t2 + 1.0;
    double b2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
    double b3 = 0.5 * t3 - 0.5 * t2;
    
    return new Vector3d(
        b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
        b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y,
        b0 * p0.z + b1 * p1.z + b2 * p2.z + b3 * p3.z
    );
  }

  /**
   * Resample the curve to have evenly-spaced points.
   * Points are in voxel coordinates. We resample at 1-voxel intervals.
   */
  private List<Vector3d> resampleCurve(List<Vector3d> points, double stepMm, double pixelMm) {
    List<Vector3d> result = new ArrayList<>();
    if (points.size() < 2) return result;

    // Calculate total length in voxels
    double totalLengthVoxels = 0;
    for (int i = 1; i < points.size(); i++) {
      totalLengthVoxels += points.get(i).distance(points.get(i - 1));
    }

    if (totalLengthVoxels <= 0) return result;

    // Resample at 1-voxel step intervals for smooth output
    double stepVoxels = 1.0;
    int numSamples = (int) Math.ceil(totalLengthVoxels / stepVoxels);
    
    LOGGER.info("Resampling: totalLength={} voxels, numSamples={}", totalLengthVoxels, numSamples);
    
    for (int i = 0; i <= numSamples; i++) {
      double targetDist = i * stepVoxels;
      Vector3d point = interpolateAlongCurve(points, targetDist);
      if (point != null) {
        result.add(point);
      }
    }
    return result;
  }

  /**
   * Interpolate along the curve to find the point at a given distance (in voxels).
   */
  private Vector3d interpolateAlongCurve(List<Vector3d> points, double targetDistVoxels) {
    double accumulated = 0;
    for (int i = 1; i < points.size(); i++) {
      Vector3d p0 = points.get(i - 1);
      Vector3d p1 = points.get(i);
      double segmentLength = p0.distance(p1);
      if (accumulated + segmentLength >= targetDistVoxels) {
        double remaining = targetDistVoxels - accumulated;
        double t = segmentLength > 0 ? remaining / segmentLength : 0;
        return new Vector3d(p0).lerp(p1, t);
      }
      accumulated += segmentLength;
    }
    return points.isEmpty() ? null : new Vector3d(points.get(points.size() - 1));
  }

  private void setPixelValue(ImageCV dst, int row, int col, Number value, int cvType) {
    int depth = CvType.depth(cvType);
    switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> dst.put(row, col, value.byteValue());
      case CvType.CV_16U, CvType.CV_16S -> dst.put(row, col, value.shortValue());
      case CvType.CV_32S -> dst.put(row, col, value.intValue());
      case CvType.CV_32F -> dst.put(row, col, value.floatValue());
      case CvType.CV_64F -> dst.put(row, col, value.doubleValue());
    }
  }

  private void setDicomTags(int widthPx, int heightPx, double pixelMm, double stepMm) {
    HEADER_CACHE.remove(this);
    tags.put(TagD.get(Tag.Columns), widthPx);
    tags.put(TagD.get(Tag.Rows), heightPx);
    tags.put(TagD.get(Tag.SliceThickness), pixelMm);
    tags.put(TagD.get(Tag.PixelSpacing), new double[]{pixelMm, stepMm});
    tags.put(TagD.get(Tag.SOPInstanceUID), UIDUtils.createUID());
    tags.put(TagD.get(Tag.InstanceNumber), 1);
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public MediaElement getPreview() {
    return null;
  }

  @Override
  public boolean delegate(DataExplorerModel explorerModel) {
    return false;
  }

  @Override
  public DicomImageElement[] getMediaElement() {
    return null;
  }

  @Override
  public DicomSeries getMediaSeries() {
    return null;
  }

  @Override
  public int getMediaElementNumber() {
    return 1;
  }

  @Override
  public String getMediaFragmentMimeType() {
    return MIME_TYPE;
  }

  @Override
  public Map<TagW, Object> getMediaFragmentTags(Object key) {
    return tags;
  }

  @Override
  public void close() {
    HEADER_CACHE.remove(this);
  }

  @Override
  public Codec getCodec() {
    return null;
  }

  @Override
  public String[] getReaderDescription() {
    return new String[]{"Curved MPR Image Decoder"};
  }

  @Override
  public Object getTagValue(TagW tag) {
    return tag == null ? null : tags.get(tag);
  }

  @Override
  public void setTag(TagW tag, Object value) {
    DicomMediaUtils.setTag(tags, tag, value);
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    if (value != null) {
      setTag(tag, value);
    }
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return tags.containsKey(tag);
  }

  @Override
  public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
    return tags.entrySet().iterator();
  }

  public void copyTags(TagW[] tagList, MediaElement media, boolean allowNullValue) {
    if (tagList != null && media != null) {
      for (TagW tag : tagList) {
        Object value = media.getTagValue(tag);
        if (allowNullValue || value != null) {
          tags.put(tag, value);
        }
      }
    }
  }

  @Override
  public void replaceURI(URI uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Attributes getDicomObject() {
    Attributes dcm = new Attributes(tags.size() + (attributes != null ? attributes.size() : 0));
    if (attributes != null) {
      SpecificCharacterSet cs = attributes.getSpecificCharacterSet();
      dcm.setSpecificCharacterSet(cs.toCodes());
      dcm.addAll(attributes);
    }
    DicomMediaUtils.fillAttributes(tags, dcm);
    return dcm;
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean buildFile(File output) {
    return false;
  }

  @Override
  public DicomMetaData getDicomMetaData() {
    return readMetaData();
  }

  @Override
  public boolean isEditableDicom() {
    return false;
  }

  @Override
  public void writeMetaData(MediaSeriesGroup group) {
    DcmMediaReader.super.writeMetaData(group);
  }

  private synchronized DicomMetaData readMetaData() {
    DicomMetaData header = HEADER_CACHE.get(this);
    if (header != null) {
      return header;
    }
    Attributes dcm = getDicomObject();
    header = new DicomMetaData(dcm, UID.ImplicitVRLittleEndian);
    org.dcm4che3.img.stream.ImageDescriptor desc = header.getImageDescriptor();
    if (desc != null) {
      org.opencv.core.Core.MinMaxLocResult minMax = new org.opencv.core.Core.MinMaxLocResult();
      minMax.minVal = volume.getMinimum();
      minMax.maxVal = volume.getMaximum();
      desc.setMinMaxPixelValue(0, minMax);
    }
    HEADER_CACHE.put(this, header);
    return header;
  }
}
