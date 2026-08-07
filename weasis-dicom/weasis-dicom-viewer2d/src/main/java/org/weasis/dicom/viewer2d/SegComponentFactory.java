/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;

/** Resolves the segmentations displayable on a view and toggles their visibility. */
public final class SegComponentFactory {

  private SegComponentFactory() {}

  /** Returns every SEG element that can be rendered on the given view. */
  public static List<SegSpecialElement> getRelatedSegments(ViewCanvas<?> viewCanvas) {
    return getRelatedSegments(SegSpecialElement.class, viewCanvas);
  }

  /**
   * Returns every region element of the given type that can be rendered on the given view: the
   * elements referencing the displayed series plus the patient's elements that can be overlaid
   * spatially on it (same frame of reference) although they reference another series.
   */
  public static <E extends SpecialElementRegion> List<E> getRelatedSegments(
      Class<E> clazz, ViewCanvas<?> viewCanvas) {
    if (viewCanvas == null) {
      return List.of();
    }
    if (viewCanvas instanceof MprView mprView) {
      return getMprRelatedSegments(clazz, mprView);
    }
    return getRelatedSegments(clazz, viewCanvas.getSeries());
  }

  /**
   * Returns every region element of the given type that can be rendered on images of the given
   * series — see {@link #getRelatedSegments(Class, ViewCanvas)}.
   */
  public static <E extends SpecialElementRegion> List<E> getRelatedSegments(
      Class<E> clazz, MediaSeries<?> dcmSeries) {
    String seriesUID = TagD.getTagValue(dcmSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(seriesUID)) {
      return List.of();
    }
    Set<E> segs = new LinkedHashSet<>();
    Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
    if (list != null && !list.isEmpty()) {
      segs.addAll(
          HiddenSeriesManager.getHiddenElementsFromSeries(clazz, list.toArray(new String[0])));
    }
    Object media = dcmSeries.getMedia(MEDIA_POSITION.FIRST, null, null);
    if (media instanceof DicomImageElement img) {
      String patientPseudoUID = (String) img.getTagValue(TagW.PatientPseudoUID);
      if (StringUtil.hasText(patientPseudoUID)) {
        HiddenSeriesManager.getHiddenElementsFromPatient(clazz, patientPseudoUID).stream()
            .filter(seg -> seg.matchesSeries(dcmSeries))
            .forEach(segs::add);
      }
    }
    return List.copyOf(segs);
  }

  /**
   * For MPR views the current series is a synthetic MPR series whose UID is never registered in
   * reference2Series, so the SEG list comes from the controller's related-series resolution ({@link
   * MprController#getRelatedSegs()}). It returns every related SEG (visible or hidden) regardless
   * of which volumes have been built, so the tool can still list and toggle SEGs whose volume is
   * built lazily on demand.
   */
  private static <E extends SpecialElementRegion> List<E> getMprRelatedSegments(
      Class<E> clazz, MprView mprView) {
    MprController ctrl = mprView.getMprController();
    if (ctrl == null) {
      return List.of();
    }
    return ctrl.getRelatedSegs().stream().filter(clazz::isInstance).map(clazz::cast).toList();
  }

  /**
   * Toggles the display of every segmentation available on the selected view: hides them all when
   * at least one is visible, shows them all otherwise. Bound to {@link
   * ShortcutManager#ID_DICOM_TOGGLE_SEG}; a per-segmentation selection remains available in the
   * Segmentation tool.
   */
  public static void toggleSegmentationsVisibility(ImageViewerPlugin<DicomImageElement> container) {
    if (container == null) {
      return;
    }
    ViewCanvas<DicomImageElement> view = container.getSelectedViewCanvas();
    List<SegSpecialElement> segs = getRelatedSegments(view);
    if (segs.isEmpty()) {
      return;
    }
    boolean visible = segs.stream().noneMatch(SegSpecialElement::isVisible);
    segs.forEach(seg -> seg.setVisible(visible));

    for (ViewCanvas<DicomImageElement> v : container.getImagePanels()) {
      if (v instanceof View2d view2d) {
        view2d.updateSegmentation();
        view2d.repaint();
      }
    }
    // Keep the Segmentation tool tree in sync with the new visibility states
    EventManager.getInstance()
        .fireSeriesViewerListeners(
            new SeriesViewerEvent(container, view.getSeries(), view.getImage(), EVENT.SELECT_VIEW));
  }
}
