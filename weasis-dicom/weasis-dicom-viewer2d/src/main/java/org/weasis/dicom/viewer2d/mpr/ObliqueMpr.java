/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation.AXIAL;
import static org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation.CORONAL;
import static org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation.SAGITTAL;

import java.awt.Dimension;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class ObliqueMpr extends OriginalStack {
  static final String[] imageTypes = {"DERIVED", "SECONDARY", "MPR"};
  private final Volume<?> volume;

  public ObliqueMpr(
      SliceOrientation sliceOrientation, MediaSeries<DicomImageElement> series, MprView view) {
    super(sliceOrientation, series, (Filter) view.getActionValue(ActionW.FILTERED_SERIES.cmd()));
    JProgressBar bar = createProgressBar(view, getSourceStack().size());
    GuiExecutor.invokeAndWait(
        () -> {
          bar.setValue(0);
          view.repaint();
        });
    bar.addChangeListener(
        e -> {
          if (bar.getValue() == bar.getMaximum()) {
            view.setProgressBar(null);
          }
          view.repaint();
        });
    volume = Volume.createVolume(this, bar);
  }

  public static JProgressBar createProgressBar(MprView view, int maxSize) {
    JProgressBar bar = new JProgressBar(0, maxSize);
    Dimension dim = new Dimension(view.getWidth() / 2, GuiUtils.getScaleLength(30));
    bar.setSize(dim);
    bar.setPreferredSize(dim);
    bar.setMaximumSize(dim);
    bar.setValue(0);
    bar.setStringPainted(true);
    view.setProgressBar(bar);
    return bar;
  }

  public Volume<?> getVolume() {
    return volume;
  }

  protected void fillMprView(
      BuildContext context, MprView view, MediaSeries<DicomImageElement> series) {
    GuiExecutor.execute(
        () -> {
          view.setSeries(series);
          // Copy the synch values from the main view
          MprView mainView = context.getMainView();
          if (mainView != view) {
            for (String action : MprContainer.defaultMpr.getSynchData().getActions().keySet()) {
              view.setActionsInView(action, mainView.getActionValue(action));
            }
            view.zoom(mainView.getViewModel().getViewScale());
          }
          view.center();
          view.repaint();
        });
  }

  public void generate(BuildContext context) {
    MprController controller = context.getMprContainer().getMprController();
    controller.setVolume(volume);

    String[] uidsRef = setUIDsRef();
    context.getMainView().zoom(0.0);

    try (ForkJoinPool pool = new ForkJoinPool()) {
      pool.invoke(new CreateSeriesTask(context, uidsRef, AXIAL, SAGITTAL, CORONAL));
    }

    //    ArcBallController arcBall =
    //        new ArcBallController(controller) {
    //          @Override
    //          public void stateChanged(BoundedRangeModel model) {
    //            Quaterniond rotation = new Quaterniond();
    //            rotation.rotateZ(Math.toRadians(model.getValue()));
    //            controller.initRotation(rotation);
    //          }
    //        };
    //    arcBall.enableAction(true);
    //    controller.setArcBall(arcBall);
    //    controller.getAxial().getMprView().addMouseListener(arcBall);
    //    controller.getAxial().getMprView().addMouseMotionListener(arcBall);
    //    controller.getCoronal().getMprView().addMouseListener(arcBall);
    //    controller.getCoronal().getMprView().addMouseMotionListener(arcBall);
    //    controller.getSagittal().getMprView().addMouseListener(arcBall);
    //    controller.getSagittal().getMprView().addMouseMotionListener(arcBall);
  }

  private class CreateSeriesTask extends RecursiveAction {
    private final BuildContext context;
    private final String[] uidsRef;
    private final SliceOrientation[] orientations;

    CreateSeriesTask(BuildContext context, String[] uidsRef, SliceOrientation... orientations) {
      this.context = context;
      this.uidsRef = uidsRef;
      this.orientations = orientations;
    }

    @Override
    protected void compute() {
      for (SliceOrientation orientation : orientations) {
        createSeries(context, uidsRef[orientation.ordinal()], orientation);
      }
    }
  }

  private void createSeries(BuildContext context, String uid, SliceOrientation orientation) {
    DicomSeries series = new DicomSeries(uid, null, DicomModel.series.tagView());
    series.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
    getMiddleImage().getMediaReader().writeMetaData(series);
    DataExplorerModel model = (DataExplorerModel) this.series.getTagValue(TagW.ExplorerModel);
    series.setTag(TagW.ExplorerModel, model);

    MprContainer mprContainer = context.getMprContainer();
    MprController controller = mprContainer.getMprController();
    MprView mprView = mprContainer.getMprView(orientation);
    MprAxis axis = controller.getMprAxis(orientation);
    axis.setMprView(mprView);
    axis.setRawIO(buildRawIO(series, axis));
    DicomImageElement dcm = axis.getImageElement();
    if (dcm != null) {
      series.addMedia(dcm);
    }
    fillMprView(context, mprView, series);
  }

  private String[] setUIDsRef() {
    String[] uidsRef = TagW.getTagValue(series, OriginalStack.seriesReferences, String[].class);
    if (uidsRef == null || uidsRef.length != 3) {
      uidsRef = new String[] {UIDUtils.createUID(), UIDUtils.createUID(), UIDUtils.createUID()};
      series.setTag(OriginalStack.seriesReferences, uidsRef);
    }
    return uidsRef;
  }

  public VolImageIO buildRawIO(MediaSeries<DicomImageElement> series, MprAxis axis) {
    DicomImageElement img = volume.stack.getMiddleImage();
    VolImageIO rawIO = new VolImageIO(axis, volume);

    final Attributes cpTags = getCommonTags(this, axis.getViewOrientation());
    rawIO.setBaseAttributes(cpTags);

    rawIO.setTag(
        TagD.get(Tag.SeriesInstanceUID), series.getTagValue(TagD.get(Tag.SeriesInstanceUID)));

    rawIO.setTag(TagD.get(Tag.BitsAllocated), img.getBitsAllocated());
    rawIO.setTag(TagD.get(Tag.BitsStored), img.getBitsStored());

    // Mandatory tags
    DerivedStack.copyMandatoryTags(img, rawIO);
    TagW[] tagList2;

    tagList2 =
        TagD.getTagFromIDs(
            Tag.PixelPaddingValue,
            Tag.PixelPaddingRangeLimit,
            Tag.PixelSpacingCalibrationDescription);
    rawIO.copyTags(tagList2, img, false);
    return rawIO;
  }

  private String setFrameOfReferenceUID(OriginalStack stack) {
    String frUID = TagD.getTagValue(stack.series, Tag.FrameOfReferenceUID, String.class);
    if (frUID == null) {
      frUID = UIDUtils.createUID();
      stack.series.setTag(TagD.get(Tag.FrameOfReferenceUID), frUID);
    }
    return frUID;
  }

  private Attributes getCommonTags(OriginalStack stack, SliceOrientation orientation) {
    String frUID = setFrameOfReferenceUID(stack);
    String desc = TagD.getTagValue(stack.series, Tag.SeriesDescription, String.class);
    String mprDesc = "MPR " + orientation;
    desc = desc == null ? mprDesc : desc + " [%s]".formatted(mprDesc);
    return stack.getCommonAttributes(frUID, desc, imageTypes);
  }
}
