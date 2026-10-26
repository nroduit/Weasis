/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.SplitNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.HangingLayout.Cell;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewState.Context;
import org.weasis.core.ui.editor.image.ViewState.Position;
import org.weasis.core.ui.editor.image.ViewState.ViewData;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;

class SessionDicomExportTest {

  private static final String CT = "1.2.3.1"; // NON-NLS
  private static final String CR = "1.2.3.2"; // NON-NLS

  private static ViewState state(String series, int image, String instance, boolean inverted) {
    return new ViewState(
        new ViewData(series, image, instance, null),
        new ViewPresentation(
            Position.START,
            Context.UNKNOWN,
            ZoomType.BEST_FIT,
            0,
            0,
            0,
            90,
            true,
            400.0,
            40.0,
            null,
            null,
            null,
            inverted,
            null,
            false));
  }

  private static JsonObject viewer(String grid, String synch, ViewState... panes) {
    HangingLayout.Builder layout = HangingLayout.builder().grid(grid).synch(synch).select(1);
    for (int i = 0; i < panes.length; i++) {
      layout.cell(new Cell(i, null, List.of(panes[i])));
    }
    return layout.build().writeTo(Json.createObjectBuilder()).build();
  }

  private static Session session(WorkspaceSnapshot snapshot) {
    Instant now = Instant.now();
    return new Session(
        "s1", "my reading", false, now, now, List.of(), "weasis.ct", 1, Map.of(), snapshot);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static DicomModel model(DicomSeries... all) {
    DicomModel model = mock(DicomModel.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    when(study.getTagValue(TagD.get(Tag.StudyInstanceUID))).thenReturn("1.2.3"); // NON-NLS
    when(model.findSeries(any())).thenReturn(Optional.empty());
    for (DicomSeries s : all) {
      String uid = TagD.getTagValue(s, Tag.SeriesInstanceUID, String.class);
      when(model.findSeries(uid)).thenReturn((Optional) Optional.of(s));
      when(model.getParent(s, DicomModel.study)).thenReturn(study);
    }
    return model;
  }

  private static Attributes read(Path file) throws IOException {
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      return in.readDataset();
    }
  }

  @Test
  void aTwoPaneViewerBecomesTwoImageBoxesWithTheirPresentationStates(@TempDir Path dir)
      throws IOException {
    DicomSeries ct = ExplorerFixtures.ctSeries(CT, "B30f", 3, "CT"); // NON-NLS
    DicomSeries cr = ExplorerFixtures.crSeries(CR);
    JsonObject viewer =
        viewer("1x2", "synch_stack", state(CT, 2, CT + ".2", true), state(CR, 1, null, false));
    GroupNode group = new GroupNode(List.of(new ViewDescriptor("v1", "2d", viewer)), 0);
    WorkspaceSnapshot snapshot =
        new WorkspaceSnapshot(
            WorkspaceSnapshot.CURRENT_VERSION,
            List.of(new WindowSnapshot("screen0", null, true, group, null)));

    SessionDicomExport.Report report =
        SessionDicomExport.write(session(snapshot), model(ct, cr), dir);
    Attributes sd = read(report.display());
    Sequence boxes = sd.getSequence(Tag.StructuredDisplayImageBoxSequence);
    Attributes box1 = boxes.get(0);
    Attributes box2 = boxes.get(1);
    Attributes pr1 = read(report.presentations().getFirst());
    assertAll(
        () -> assertEquals(2, report.boxes()),
        () -> assertEquals(2, report.presentations().size()),
        () -> assertTrue(report.warnings().isEmpty(), report.warnings().toString()),
        () -> assertEquals(UID.BasicStructuredDisplayStorage, sd.getString(Tag.SOPClassUID)),
        () -> assertEquals("PR", sd.getString(Tag.Modality)),
        () -> assertEquals(1, sd.getInt(Tag.NumberOfScreens, 0)),
        () -> assertEquals(1, sd.getSequence(Tag.NominalScreenDefinitionSequence).size()),
        () -> assertEquals("weasis.ct", sd.getString(Tag.HangingProtocolName)),
        () -> assertEquals("my reading", sd.getString(Tag.ContentDescription)),
        () -> assertEquals(1, box1.getInt(Tag.ImageBoxNumber, 0)),
        () ->
            assertArrayEquals(
                new float[] {0f, 1f, 0.5f, 0f},
                box1.getFloats(Tag.DisplayEnvironmentSpatialPosition),
                1e-6f),
        () ->
            assertArrayEquals(
                new float[] {0.5f, 1f, 1f, 0f},
                box2.getFloats(Tag.DisplayEnvironmentSpatialPosition),
                1e-6f),
        () -> assertEquals("STACK", box1.getString(Tag.ImageBoxLayoutType)),
        () -> assertEquals(3, box1.getSequence(Tag.ReferencedImageSequence).size()),
        () ->
            assertEquals(
                CT + ".2",
                box1.getSequence(Tag.ReferencedFirstFrameSequence)
                    .getFirst()
                    .getString(Tag.ReferencedSOPInstanceUID)),
        () ->
            assertEquals(
                pr1.getString(Tag.SOPInstanceUID),
                box1.getSequence(Tag.ReferencedImageSequence)
                    .getFirst()
                    .getSequence(Tag.ReferencedPresentationStateSequence)
                    .getFirst()
                    .getString(Tag.ReferencedSOPInstanceUID)),
        () -> assertEquals("SINGLE", box2.getString(Tag.ImageBoxLayoutType)),
        () -> assertNull(sd.getSequence(Tag.ImageBoxSynchronizationSequence), "one stack only"),
        () ->
            assertEquals(
                3,
                sd.getSequence(Tag.ReferencedSeriesSequence).size(),
                "two image series and the presentation states"),
        () -> assertNull(sd.getSequence(Tag.StudiesContainingOtherReferencedInstancesSequence)),
        () ->
            assertEquals(
                UID.GrayscaleSoftcopyPresentationStateStorage, pr1.getString(Tag.SOPClassUID)),
        () ->
            assertEquals(
                40.0,
                pr1.getSequence(Tag.SoftcopyVOILUTSequence)
                    .getFirst()
                    .getDouble(Tag.WindowCenter, 0),
                1e-9),
        () -> assertEquals(90, pr1.getInt(Tag.ImageRotation, -1)),
        () -> assertEquals("Y", pr1.getString(Tag.ImageHorizontalFlip)),
        () -> assertEquals("INVERSE", pr1.getString(Tag.PresentationLUTShape)),
        () -> assertEquals("WEASIS_SESSION", pr1.getString(Tag.ContentLabel)),
        () ->
            assertEquals(
                "IDENTITY",
                read(report.presentations().get(1)).getString(Tag.PresentationLUTShape)),
        () -> assertEquals(1, pr1.getSequence(Tag.ReferencedSeriesSequence).size()));
    assertTrue(report.toJson().getJsonArray("files").size() == 3);
  }

  @Test
  void theSplitTreeTabsAndNonImageViewsAreReportedAndTheGridsAreKnown(@TempDir Path dir)
      throws IOException {
    DicomSeries ct = ExplorerFixtures.ctSeries(CT, "B30f", 2, "CT"); // NON-NLS
    JsonObject viewer = viewer("1x1", null, state(CT, 1, CT + ".1", false));
    JsonObject mpr = Json.createObjectBuilder().add("name", "mpr").build(); // NON-NLS
    SplitNode root =
        new SplitNode(
            true,
            0.25,
            new GroupNode(List.of(new ViewDescriptor("m1", "mpr", mpr)), 0),
            new GroupNode(
                List.of(
                    new ViewDescriptor("v1", "2d", viewer), new ViewDescriptor("v2", "2d", viewer)),
                1));
    WorkspaceSnapshot snapshot =
        new WorkspaceSnapshot(
            WorkspaceSnapshot.CURRENT_VERSION,
            List.of(
                new WindowSnapshot("screen0", null, true, root, null),
                new WindowSnapshot("screen1", null, false, root, null)));

    SessionDicomExport.Report report = SessionDicomExport.write(session(snapshot), model(ct), dir);
    Attributes box =
        read(report.display()).getSequence(Tag.StructuredDisplayImageBoxSequence).getFirst();
    assertAll(
        () -> assertEquals(1, report.boxes()),
        () ->
            assertArrayEquals(
                new float[] {0f, 0.75f, 1f, 0f},
                box.getFloats(Tag.DisplayEnvironmentSpatialPosition),
                1e-6f,
                "the lower three quarters"),
        () -> assertEquals(3, report.warnings().size(), report.warnings().toString()),
        () -> assertTrue(report.warnings().stream().anyMatch(w -> w.contains("main window"))),
        () -> assertTrue(report.warnings().stream().anyMatch(w -> w.contains("m1"))),
        () -> assertTrue(report.warnings().stream().anyMatch(w -> w.contains("tab"))));

    Rectangle2D whole = new Rectangle2D.Double(0, 0, 1, 1);
    List<Rectangle2D> merged =
        SessionDicomExport.cellRectangles(
            HangingLayout.builder().grid("layout_c2x1").build(), whole);
    List<Rectangle2D> grid =
        SessionDicomExport.cellRectangles(HangingLayout.builder().grid("2x2").build(), whole);
    assertAll(
        () -> assertEquals(3, merged.size()),
        () -> assertEquals(new Rectangle2D.Double(0.5, 0, 0.5, 1), merged.get(1)),
        () -> assertEquals(4, grid.size()),
        () -> assertEquals(new Rectangle2D.Double(0.5, 0.5, 0.5, 0.5), grid.get(3)));

    assertThrows(
        IllegalArgumentException.class,
        () -> SessionDicomExport.write(session(snapshot), model(), dir.resolve("none")));
    assertFalse(((Object) ct.getTagValue(TagW.PresentationModel)) instanceof String);
  }
}
