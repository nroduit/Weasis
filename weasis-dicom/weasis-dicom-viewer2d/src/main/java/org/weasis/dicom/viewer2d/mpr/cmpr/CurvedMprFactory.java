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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Factory for creating Curved MPR viewer containers.
 * 
 * <p>This factory is registered as an OSGi component and provides the ability to create
 * CurvedMprContainer instances programmatically from an MprView.
 */
@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class CurvedMprFactory implements SeriesViewerFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(CurvedMprFactory.class);

  public static final String NAME = "Curved MPR";

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(OtherIcon.VIEW_3D);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Curved Multi-Planar Reconstruction viewer";
  }

  @Override
  public SeriesViewer<?> createSeriesViewer(Map<String, Object> properties) {
    LOGGER.info("createSeriesViewer called");
    LayoutModel layout =
        ImageViewerPlugin.getLayoutModel(properties, ImageViewerPlugin.VIEWS_1x1, null);
    CurvedMprContainer instance = new CurvedMprContainer(layout.model(), layout.uid());
    LOGGER.info("Created CurvedMprContainer instance");
    
    // Retrieve the axis from properties if available
    if (properties != null) {
      Object axisObj = properties.get("curvedMprAxis");
      LOGGER.info("curvedMprAxis in properties: {}", axisObj != null ? axisObj.getClass().getName() : "null");
      if (axisObj instanceof CurvedMprAxis axis) {
        LOGGER.info("Setting CurvedMprAxis on container");
        instance.setCurvedMprAxis(axis);
      }
    }
    
    ImageViewerPlugin.registerInDataExplorerModel(properties, instance);
    LOGGER.info("Registered container, returning instance");
    return instance;
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return false;
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return viewer instanceof CurvedMprContainer;
  }

  @Override
  public int getLevel() {
    return 16;
  }

  @Override
  public boolean canAddSeries() {
    return false;
  }

  @Override
  public boolean canExternalizeSeries() {
    return true;
  }

  @Override
  public boolean canReadSeries(MediaSeries<?> series) {
    return false;
  }

  public static void closeSeriesViewer(CurvedMprContainer container) {
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(container);
    }
  }

  /**
   * Open a Curved MPR viewer from an MprView with the given curve points.
   *
   * @param sourceView the source MprView
   * @param curvePoints3D the 3D curve points in voxel coordinates
   */
  public static void openCurvedMpr(MprView sourceView, List<Vector3d> curvePoints3D) {
    LOGGER.info("openCurvedMpr called with {} points", curvePoints3D != null ? curvePoints3D.size() : 0);
    
    if (sourceView == null || curvePoints3D == null || curvePoints3D.size() < 2) {
      LOGGER.warn("Cannot open curved MPR: invalid source view or curve points");
      return;
    }

    MprController controller = sourceView.getMprController();
    if (controller == null) {
      LOGGER.warn("Cannot open curved MPR: no controller available");
      return;
    }

    Volume<?> volume = controller.getVolume();
    if (volume == null) {
      LOGGER.warn("Cannot open curved MPR: no volume available");
      return;
    }
    LOGGER.info("Volume available: {}", volume.getClass().getSimpleName());

    Plane plane = sourceView.getPlane();
    Vector3d planeNormal = plane.getDirection();
    LOGGER.info("Plane: {}, normal: {}", plane, planeNormal);

    Quaterniond rotation = controller.getRotation(plane);
    if (rotation != null) {
      rotation.transform(planeNormal);
    }
    LOGGER.info("Transformed normal: {}", planeNormal);

    CurvedMprAxis axis = new CurvedMprAxis(volume, curvePoints3D, planeNormal);
    LOGGER.info("Created CurvedMprAxis with arc length: {} mm", axis.getTotalArcLengthMm());

    CurvedMprImageIO io = new CurvedMprImageIO(axis);
    axis.setIo(io);
    LOGGER.info("Created CurvedMprImageIO");

    DicomImageElement refImg = sourceView.getImage();
    if (refImg != null) {
      copyBaseTags(io, refImg);
      Attributes attrs = getBaseAttributes(refImg);
      io.setBaseAttributes(attrs);
      LOGGER.info("Copied tags from reference image");
    }

    DicomImageElement imageElement = new DicomImageElement(io, 0);
    axis.setImageElement(imageElement);
    LOGGER.info("Created DicomImageElement");

    LOGGER.info("Scheduling openCurvedMprViewer on GUI thread");
    GuiExecutor.execute(() -> openCurvedMprViewer(axis));
  }

  private static void copyBaseTags(CurvedMprImageIO io, DicomImageElement refImg) {
    io.copyTags(TagD.getTagFromIDs(
        Tag.PatientID,
        Tag.PatientName,
        Tag.PatientBirthDate,
        Tag.PatientSex,
        Tag.StudyInstanceUID,
        Tag.StudyID,
        Tag.StudyDate,
        Tag.StudyTime,
        Tag.AccessionNumber,
        Tag.ReferringPhysicianName,
        Tag.Modality,
        Tag.BodyPartExamined,
        Tag.PhotometricInterpretation,
        Tag.SamplesPerPixel,
        Tag.BitsAllocated,
        Tag.BitsStored,
        Tag.HighBit,
        Tag.PixelRepresentation,
        Tag.RescaleSlope,
        Tag.RescaleIntercept,
        Tag.RescaleType,
        Tag.WindowCenter,
        Tag.WindowWidth,
        Tag.WindowCenterWidthExplanation,
        Tag.VOILUTFunction
    ), refImg, false);
  }

  private static Attributes getBaseAttributes(DicomImageElement refImg) {
    Attributes attrs = new Attributes();
    Object val = refImg.getTagValue(TagD.get(Tag.PatientID));
    if (val != null) attrs.setString(Tag.PatientID, org.dcm4che3.data.VR.LO, val.toString());
    val = refImg.getTagValue(TagD.get(Tag.PatientName));
    if (val != null) attrs.setString(Tag.PatientName, org.dcm4che3.data.VR.PN, val.toString());
    val = refImg.getTagValue(TagD.get(Tag.StudyInstanceUID));
    if (val != null) attrs.setString(Tag.StudyInstanceUID, org.dcm4che3.data.VR.UI, val.toString());
    val = refImg.getTagValue(TagD.get(Tag.Modality));
    if (val != null) attrs.setString(Tag.Modality, org.dcm4che3.data.VR.CS, val.toString());
    return attrs;
  }

  private static void openCurvedMprViewer(CurvedMprAxis axis) {
    LOGGER.info("openCurvedMprViewer called");
    
    SeriesViewerFactory factory = GuiUtils.getUICore().getViewerFactory(CurvedMprFactory.class);
    if (factory == null) {
      LOGGER.error("CurvedMprFactory not found in registered factories!");
      for (SeriesViewerFactory f : GuiUtils.getUICore().getSeriesViewerFactories()) {
        LOGGER.info("  Available factory: {}", f.getClass().getName());
      }
      return;
    }
    LOGGER.info("Found CurvedMprFactory: {}", factory.getClass().getName());

    // Create the viewer directly instead of using ViewerPluginBuilder
    // since we don't have a traditional series
    Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
    props.put("curvedMprAxis", axis);
    
    SeriesViewer<?> viewer = factory.createSeriesViewer(props);
    if (viewer instanceof ViewerPlugin<?> plugin) {
      LOGGER.info("Created viewer plugin, registering...");
      GuiUtils.getUICore().getViewerPlugins().add(plugin);
      plugin.showDockable();
      plugin.setSelectedAndGetFocus();
      LOGGER.info("Viewer registered and shown");
    } else {
      LOGGER.error("Created viewer is not a ViewerPlugin: {}", 
          viewer != null ? viewer.getClass().getName() : "null");
    }
  }
}
