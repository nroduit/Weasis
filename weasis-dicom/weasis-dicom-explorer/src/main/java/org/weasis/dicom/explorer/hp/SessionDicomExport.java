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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.Node;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.SplitNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.HangingLayout.Cell;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;

/**
 * A session as DICOM: one Basic Structured Display object (PS3.3 A.33.5) laying out the panes of
 * the 2D viewers of the main window as image boxes, each box referencing the images of its series
 * and a Grayscale Softcopy Presentation State that carries the pane's window, rotation, flip,
 * inversion, magnification and graphics. What the IOD cannot carry (MPR and 3D views, other
 * windows, hidden tabs, blends) is reported as a warning. The studies of the session must be
 * loaded.
 */
public final class SessionDicomExport {

  private static final Pattern GRID = Pattern.compile("(\\d+)x(\\d+)"); // NON-NLS
  private static final String DISPLAY_FILE = "structured-display.dcm"; // NON-NLS
  private static final String PR_FILE = "presentation-%d.dcm"; // NON-NLS
  private static final String CONTENT_LABEL = "WEASIS_SESSION"; // NON-NLS

  /** What was written: the display object, one presentation state per box, the warnings. */
  public record Report(Path display, List<Path> presentations, int boxes, List<String> warnings) {
    public JsonObject toJson() {
      JsonArrayBuilder files = Json.createArrayBuilder().add(display.toString());
      presentations.forEach(p -> files.add(p.toString()));
      JsonArrayBuilder warn = Json.createArrayBuilder();
      warnings.forEach(warn::add);
      return Json.createObjectBuilder()
          .add("files", files) // NON-NLS
          .add("boxes", boxes) // NON-NLS
          .add("warnings", warn) // NON-NLS
          .build();
    }
  }

  /** One image box to write: its pane, its rectangle and what it shows. */
  private record Box(
      int number,
      Rectangle2D rect,
      ViewState state,
      MediaSeries<? extends MediaElement> series,
      List<DicomImageElement> images,
      DicomImageElement shown,
      String synch) {}

  private final DicomModel model;
  private final List<String> warnings = new ArrayList<>();

  private SessionDicomExport(DicomModel model) {
    this.model = model;
  }

  /**
   * Writes the display object and its presentation states into a directory.
   *
   * @throws IllegalArgumentException when no pane of the session shows a loaded image
   */
  public static Report write(Session session, DicomModel model, Path dir) throws IOException {
    Files.createDirectories(dir);
    SessionDicomExport export = new SessionDicomExport(model);
    List<Box> boxes = export.boxes(session.workspace());
    if (boxes.isEmpty()) {
      throw new IllegalArgumentException(
          "No pane of the session shows a loaded image: " + export.warnings);
    }
    Attributes first = attributesOf(boxes.getFirst().shown());
    String prSeriesUid = UIDUtils.createUID();
    List<Path> files = new ArrayList<>();
    Map<Integer, Attributes> presentations = new LinkedHashMap<>();
    for (Box box : boxes) {
      Path file = dir.resolve(String.format(PR_FILE, box.number()));
      Attributes pr = export.presentationState(box, prSeriesUid, file);
      if (pr != null) {
        presentations.put(box.number(), pr);
        files.add(file);
      }
    }
    Attributes display = export.display(session, boxes, presentations, first);
    Path displayFile = dir.resolve(DISPLAY_FILE);
    try (DicomOutputStream out =
        new DicomOutputStream(Files.newOutputStream(displayFile), UID.ExplicitVRLittleEndian)) {
      out.writeDataset(display.createFileMetaInformation(UID.ExplicitVRLittleEndian), display);
    }
    return new Report(displayFile, files, boxes.size(), List.copyOf(export.warnings));
  }

  // ---- the boxes of the workspace

  private List<Box> boxes(WorkspaceSnapshot snapshot) {
    List<Box> boxes = new ArrayList<>();
    if (snapshot == null || snapshot.windows().isEmpty()) {
      return boxes;
    }
    List<WindowSnapshot> windows = snapshot.windows();
    WindowSnapshot window =
        windows.stream().filter(WindowSnapshot::main).findFirst().orElse(windows.getFirst());
    if (windows.size() > 1) {
      warnings.add("Only the main window is exported: a Basic Structured Display has one screen");
    }
    Map<ViewDescriptor, Rectangle2D> placed = new LinkedHashMap<>();
    place(window.root(), new Rectangle2D.Double(0, 0, 1, 1), placed);
    placed.forEach((view, rect) -> boxes(view, rect, boxes));
    return boxes;
  }

  private void place(Node node, Rectangle2D rect, Map<ViewDescriptor, Rectangle2D> placed) {
    switch (node) {
      case null -> {}
      case SplitNode split -> {
        double ratio = split.ratio() <= 0 || split.ratio() >= 1 ? 0.5 : split.ratio();
        Rectangle2D first;
        Rectangle2D second;
        if (split.vertical()) { // top over bottom
          double h = rect.getHeight() * ratio;
          first = new Rectangle2D.Double(rect.getX(), rect.getY(), rect.getWidth(), h);
          second =
              new Rectangle2D.Double(
                  rect.getX(), rect.getY() + h, rect.getWidth(), rect.getHeight() - h);
        } else {
          double w = rect.getWidth() * ratio;
          first = new Rectangle2D.Double(rect.getX(), rect.getY(), w, rect.getHeight());
          second =
              new Rectangle2D.Double(
                  rect.getX() + w, rect.getY(), rect.getWidth() - w, rect.getHeight());
        }
        place(split.first(), first, placed);
        place(split.second(), second, placed);
      }
      case GroupNode group -> {
        List<ViewDescriptor> views = group.views();
        if (views.isEmpty()) {
          return;
        }
        int selected = Math.max(0, Math.min(group.selectedIndex(), views.size() - 1));
        placed.put(views.get(selected), rect);
        for (int i = 0; i < views.size(); i++) {
          if (i != selected) {
            warnings.add(
                "View " + views.get(i).viewId() + " is a tab behind another: not exported");
          }
        }
      }
    }
  }

  private void boxes(ViewDescriptor view, Rectangle2D rect, List<Box> boxes) {
    JsonObject state = view.state();
    if (state == null || !state.containsKey("cells")) { // NON-NLS
      warnings.add(
          "View " + view.viewId() + " (" + view.typeId() + ") has no 2D cells: not exported");
      return;
    }
    HangingLayout layout = HangingLayout.fromJson(state);
    List<Rectangle2D> cells = cellRectangles(layout, rect);
    for (Cell cell : layout.cells()) {
      if (cell.position() < 0 || cell.position() >= cells.size()) {
        continue;
      }
      Rectangle2D cellRect = cells.get(cell.position());
      List<ViewState> panes = cell.panes();
      int cols = cell.tiling() == null ? 1 : Math.max(1, cell.tiling().cols());
      int rows = cell.tiling() == null ? 1 : Math.max(1, cell.tiling().rows());
      for (int i = 0; i < panes.size(); i++) {
        Rectangle2D paneRect =
            new Rectangle2D.Double(
                cellRect.getX() + (i % cols) * cellRect.getWidth() / cols,
                cellRect.getY() + (i / cols) * cellRect.getHeight() / rows,
                cellRect.getWidth() / cols,
                cellRect.getHeight() / rows);
        box(view, panes.get(i), paneRect, layout.synch(), boxes);
      }
    }
  }

  private void box(
      ViewDescriptor view, ViewState state, Rectangle2D rect, String synch, List<Box> boxes) {
    String seriesUid = state.data().series();
    if (seriesUid == null) {
      return; // an empty pane
    }
    MediaSeries<? extends MediaElement> series = model.findSeries(seriesUid).orElse(null);
    if (series == null) {
      warnings.add("Series " + seriesUid + " of view " + view.viewId() + " is not loaded");
      return;
    }
    List<DicomImageElement> images = new ArrayList<>();
    for (MediaElement media : series.getMedias(null, null)) {
      if (media instanceof DicomImageElement image) {
        images.add(image);
      }
    }
    if (images.isEmpty()) {
      warnings.add("Series " + seriesUid + " has no image: not exported");
      return;
    }
    String instance = state.data().instance();
    DicomImageElement shown =
        images.stream()
            .filter(img -> instance != null && instance.equals(sopInstanceUid(img)))
            .findFirst()
            .orElse(images.get(Math.max(0, Math.min(state.data().image() - 1, images.size() - 1))));
    if (state.blend() != null && state.blend().overlay() != null) {
      warnings.add("The blended series of view " + view.viewId() + " is not carried by the object");
    }
    boxes.add(new Box(boxes.size() + 1, rect, state, series, images, shown, synch));
  }

  /** The rectangles of the cells of a grid, by cell position, within the view's rectangle. */
  static List<Rectangle2D> cellRectangles(HangingLayout layout, Rectangle2D rect) {
    String grid = layout.grid() == null ? "" : layout.grid();
    int count = Math.max(1, layout.cells().stream().mapToInt(Cell::position).max().orElse(0) + 1);
    List<Rectangle2D> cells = new ArrayList<>();
    if ("layout_c2x1".equals(grid)) { // left column of two, right merged
      cells.add(part(rect, 0, 0, 0.5, 0.5));
      cells.add(part(rect, 0.5, 0, 0.5, 1));
      cells.add(part(rect, 0, 0.5, 0.5, 0.5));
      return cells;
    }
    if ("layout_r1x2".equals(grid)) { // top merged, bottom row of two
      cells.add(part(rect, 0, 0, 1, 0.5));
      cells.add(part(rect, 0, 0.5, 0.5, 0.5));
      cells.add(part(rect, 0.5, 0.5, 0.5, 0.5));
      return cells;
    }
    Matcher m = GRID.matcher(grid);
    int rows;
    int cols;
    if (m.find()) {
      rows = Integer.parseInt(m.group(1));
      cols = Integer.parseInt(m.group(2));
    } else {
      cols = (int) Math.ceil(Math.sqrt(count));
      rows = (int) Math.ceil(count / (double) cols);
    }
    rows = Math.max(1, rows);
    cols = Math.max(1, cols);
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        cells.add(part(rect, c / (double) cols, r / (double) rows, 1.0 / cols, 1.0 / rows));
      }
    }
    return cells;
  }

  private static Rectangle2D part(Rectangle2D rect, double x, double y, double w, double h) {
    return new Rectangle2D.Double(
        rect.getX() + x * rect.getWidth(),
        rect.getY() + y * rect.getHeight(),
        w * rect.getWidth(),
        h * rect.getHeight());
  }

  // ---- the presentation state of a box

  private Attributes presentationState(Box box, String seriesUid, Path file) {
    GraphicModel graphics =
        box.shown().getTagValue(TagW.PresentationModel) instanceof GraphicModel g
            ? g
            : new XmlGraphicModel(box.shown());
    Attributes pr =
        DicomPrSerializer.writePresentation(
            graphics, box.shown(), file.toFile(), seriesUid, UIDUtils.createUID());
    if (pr == null) {
      warnings.add("No presentation state for box " + box.number());
      return null;
    }
    applyPresentation(pr, box.state().view(), box.number());
    try (DicomOutputStream out =
        new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
      out.writeDataset(pr.createFileMetaInformation(UID.ExplicitVRLittleEndian), pr);
    } catch (IOException e) {
      warnings.add("Cannot write the presentation state of box " + box.number() + ": " + e);
      return null;
    }
    return pr;
  }

  /**
   * Lays the pane's presentation over a presentation state: Softcopy VOI LUT (C.11.8), Spatial
   * Transformation (C.10.6), Softcopy Presentation LUT (C.11.6) and the magnification of the
   * Displayed Area (C.10.4).
   */
  static void applyPresentation(Attributes pr, ViewPresentation view, int instanceNumber) {
    pr.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);
    pr.setString(Tag.ContentLabel, VR.CS, CONTENT_LABEL);
    if (view.window() != null && view.level() != null) {
      Attributes voi = new Attributes(2);
      voi.setDouble(Tag.WindowCenter, VR.DS, view.level());
      voi.setDouble(Tag.WindowWidth, VR.DS, view.window());
      pr.newSequence(Tag.SoftcopyVOILUTSequence, 1).add(voi);
    }
    int rotation = Math.floorMod(view.rotation(), 360);
    if (rotation % 90 != 0) {
      rotation =
          Math.floorMod(
              Math.round(rotation / 90f) * 90, 360); // the module allows 0/90/180/270 only
    }
    pr.setInt(Tag.ImageRotation, VR.US, rotation);
    pr.setString(Tag.ImageHorizontalFlip, VR.CS, view.flip() ? "Y" : "N"); // NON-NLS
    pr.setString(
        Tag.PresentationLUTShape, VR.CS, view.invert() ? "INVERSE" : "IDENTITY"); // NON-NLS
    Sequence displayed = pr.getSequence(Tag.DisplayedAreaSelectionSequence);
    if (displayed != null && !displayed.isEmpty() && view.zoom() > 0) {
      Attributes area = displayed.getFirst();
      area.setString(Tag.PresentationSizeMode, VR.CS, "MAGNIFY"); // NON-NLS
      area.setFloat(Tag.PresentationPixelMagnificationRatio, VR.FL, (float) view.zoom());
    }
  }

  // ---- the display object

  private Attributes display(
      Session session, List<Box> boxes, Map<Integer, Attributes> presentations, Attributes first) {
    Attributes sd =
        DicomPrSerializer.createDicomPR(first, UIDUtils.createUID(), UIDUtils.createUID());
    sd.setString(Tag.SOPClassUID, VR.UI, UID.BasicStructuredDisplayStorage);
    sd.remove(Tag.ICCProfile);
    sd.setInt(Tag.SeriesNumber, VR.IS, 1);
    sd.setString(Tag.SeriesDescription, VR.LO, AppProperties.WEASIS_NAME + " session"); // NON-NLS
    sd.setString(Tag.Manufacturer, VR.LO, AppProperties.WEASIS_NAME);
    sd.setString(Tag.SoftwareVersions, VR.LO, AppProperties.WEASIS_VERSION);
    sd.setInt(Tag.InstanceNumber, VR.IS, 1);
    sd.setString(Tag.ContentLabel, VR.CS, CONTENT_LABEL);
    sd.setString(
        Tag.ContentDescription, VR.LO, session.label() == null ? session.id() : session.label());
    sd.setString(Tag.ContentCreatorName, VR.PN, AppProperties.WEASIS_USER);
    if (session.protocol() != null) {
      sd.setString(Tag.HangingProtocolName, VR.SH, session.protocol());
      sd.setString(Tag.HangingProtocolCreator, VR.LO, AppProperties.WEASIS_NAME);
    }
    // Structured Display module: one screen for the Basic SOP class
    sd.setInt(Tag.NumberOfScreens, VR.US, 1);
    Attributes screen = new Attributes(4);
    screen.setInt(Tag.NumberOfVerticalPixels, VR.US, 1080);
    screen.setInt(Tag.NumberOfHorizontalPixels, VR.US, 1920);
    screen.setFloat(Tag.DisplayEnvironmentSpatialPosition, VR.FD, 0f, 1f, 1f, 0f);
    screen.setInt(Tag.ScreenMinimumGrayscaleBitDepth, VR.US, 8);
    sd.newSequence(Tag.NominalScreenDefinitionSequence, 1).add(screen);

    Sequence boxSeq = sd.newSequence(Tag.StructuredDisplayImageBoxSequence, boxes.size());
    for (Box box : boxes) {
      boxSeq.add(imageBox(box, presentations.get(box.number())));
    }
    synchronization(sd, boxes);
    String ownStudy = studyUidOf(boxes.getFirst().series());
    if (ownStudy == null) {
      ownStudy = first.getString(Tag.StudyInstanceUID);
    }
    if (ownStudy != null && !sd.containsValue(Tag.StudyInstanceUID)) {
      sd.setString(Tag.StudyInstanceUID, VR.UI, ownStudy);
    }
    commonInstanceReferences(sd, boxes, presentations, ownStudy);
    return sd;
  }

  private Attributes imageBox(Box box, Attributes pr) {
    Attributes item = new Attributes(8);
    item.setInt(Tag.ImageBoxNumber, VR.US, box.number());
    Rectangle2D r = box.rect();
    // x1, y1 of the top left corner and x2, y2 of the bottom right one, origin at the bottom left
    item.setFloat(
        Tag.DisplayEnvironmentSpatialPosition,
        VR.FD,
        (float) r.getX(),
        (float) (1 - r.getY()),
        (float) (r.getX() + r.getWidth()),
        (float) (1 - r.getY() - r.getHeight()));
    boolean stack = box.images().size() > 1;
    item.setString(Tag.ImageBoxLayoutType, VR.CS, stack ? "STACK" : "SINGLE"); // NON-NLS
    Sequence refs = item.newSequence(Tag.ReferencedImageSequence, box.images().size());
    for (DicomImageElement image : box.images()) {
      refs.add(reference(image, pr));
    }
    if (stack) {
      item.newSequence(Tag.ReferencedFirstFrameSequence, 1).add(reference(box.shown(), null));
    }
    ViewPresentation view = box.state().view();
    if (view.justify() != null) {
      item.setString(
          Tag.DisplaySetHorizontalJustification, VR.CS, view.justify().horizontal().name());
      item.setString(Tag.DisplaySetVerticalJustification, VR.CS, view.justify().vertical().name());
    }
    return item;
  }

  /** An Image SOP Instance Reference, with the presentation state applied to it when given. */
  private static Attributes reference(DicomImageElement image, Attributes pr) {
    Attributes ref = new Attributes(3);
    ref.setString(
        Tag.ReferencedSOPClassUID, VR.UI, TagD.getTagValue(image, Tag.SOPClassUID, String.class));
    ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUid(image));
    if (pr != null) {
      Attributes prRef = new Attributes(2);
      prRef.setString(Tag.ReferencedSOPClassUID, VR.UI, pr.getString(Tag.SOPClassUID));
      prRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, pr.getString(Tag.SOPInstanceUID));
      ref.newSequence(Tag.ReferencedPresentationStateSequence, 1).add(prRef);
    }
    return ref;
  }

  /** The stacks of a synchronized viewer scroll together (Type of Synchronization POSITION). */
  private static void synchronization(Attributes sd, List<Box> boxes) {
    Map<String, List<Integer>> groups = new LinkedHashMap<>();
    for (Box box : boxes) {
      String synch = box.synch();
      if (synch != null
          && !synch.isBlank()
          && !"None".equalsIgnoreCase(synch)
          && box.images().size() > 1) {
        groups.computeIfAbsent(synch, k -> new ArrayList<>()).add(box.number());
      }
    }
    List<List<Integer>> lists = groups.values().stream().filter(l -> l.size() > 1).toList();
    if (lists.isEmpty()) {
      return;
    }
    Sequence seq = sd.newSequence(Tag.ImageBoxSynchronizationSequence, lists.size());
    for (List<Integer> list : lists) {
      Attributes item = new Attributes(2);
      item.setInt(
          Tag.SynchronizedImageBoxList, VR.US, list.stream().mapToInt(Integer::intValue).toArray());
      item.setString(Tag.TypeOfSynchronization, VR.CS, "POSITION"); // NON-NLS
      seq.add(item);
    }
  }

  /** Common Instance Reference module: the referenced images and presentation states by study. */
  private void commonInstanceReferences(
      Attributes sd, List<Box> boxes, Map<Integer, Attributes> presentations, String ownStudy) {
    Map<String, Map<String, List<Attributes>>> byStudy = new LinkedHashMap<>();
    for (Box box : boxes) {
      String study = studyUidOf(box.series());
      String seriesUid = TagD.getTagValue(box.shown(), Tag.SeriesInstanceUID, String.class);
      List<Attributes> instances =
          byStudy
              .computeIfAbsent(study == null ? ownStudy : study, k -> new LinkedHashMap<>())
              .computeIfAbsent(seriesUid == null ? "" : seriesUid, k -> new ArrayList<>());
      for (DicomImageElement image : box.images()) {
        instances.add(reference(image, null));
      }
      Attributes pr = presentations.get(box.number());
      if (pr != null) {
        Attributes ref = new Attributes(2);
        ref.setString(Tag.ReferencedSOPClassUID, VR.UI, pr.getString(Tag.SOPClassUID));
        ref.setString(Tag.ReferencedSOPInstanceUID, VR.UI, pr.getString(Tag.SOPInstanceUID));
        byStudy
            .computeIfAbsent(ownStudy, k -> new LinkedHashMap<>())
            .computeIfAbsent(pr.getString(Tag.SeriesInstanceUID), k -> new ArrayList<>())
            .add(ref);
      }
    }
    Map<String, List<Attributes>> own = byStudy.remove(ownStudy);
    if (own != null) {
      referencedSeries(sd, own);
    }
    if (!byStudy.isEmpty()) {
      Sequence others =
          sd.newSequence(Tag.StudiesContainingOtherReferencedInstancesSequence, byStudy.size());
      byStudy.forEach(
          (study, series) -> {
            Attributes item = new Attributes(2);
            item.setString(Tag.StudyInstanceUID, VR.UI, study);
            referencedSeries(item, series);
            others.add(item);
          });
    }
  }

  private static void referencedSeries(Attributes target, Map<String, List<Attributes>> series) {
    Sequence seq = target.newSequence(Tag.ReferencedSeriesSequence, series.size());
    series.forEach(
        (uid, instances) -> {
          Attributes item = new Attributes(2);
          item.setString(Tag.SeriesInstanceUID, VR.UI, uid);
          Sequence refs = item.newSequence(Tag.ReferencedInstanceSequence, instances.size());
          instances.forEach(refs::add);
          seq.add(item);
        });
  }

  private String studyUidOf(MediaSeries<? extends MediaElement> series) {
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    return study == null ? null : TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
  }

  private static String sopInstanceUid(DicomImageElement image) {
    return TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
  }

  private static Attributes attributesOf(DicomImageElement image) {
    Attributes dcm =
        image.getMediaReader() instanceof DcmMediaReader reader ? reader.getDicomObject() : null;
    return dcm == null ? new Attributes() : dcm;
  }
}
