/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.util.LayeredEntries;
import org.weasis.core.api.util.LayeredEntries.Layer;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomResource;

/**
 * The annotations overlay of each modality: which DICOM attributes are written in the corners of a
 * view, and how. The built-in default of the code is replaced, modality by modality, by the site
 * document {@value #SITE_FILE} of the resources package (see {@link ModalityViewJson}), or until
 * 5.2 by the legacy {@code attributes-view.xml} of the package root when the site ships no JSON.
 * Read once at class load, and again by {@link #reload()} when the site package is refreshed.
 */
public class ModalityView {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityView.class);

  /** The site document, in the {@code config} folder of the resources package. */
  public static final String SITE_FILE = "attributesView.json"; // NON-NLS

  /** The name of the array of entries in the site document. */
  public static final String ENTRIES = ModalityViewJson.ENTRIES;

  /** Replaced as a whole by {@link #reload}: a reader never sees a half-filled map. */
  static volatile Map<Modality, ModalityInfoData> MODALITY_VIEW_MAP = Map.of(); // NOSONAR

  public static final ModalityInfoData DEFAULT_MODALITY_VIEW =
      new ModalityInfoData(Modality.DEFAULT, null);

  private static final List<ModalityInfoData> BUILT_IN = List.of(DEFAULT_MODALITY_VIEW);

  static {
    // Format associated to DICOM field:
    // $V => the value
    // $V:l$25$ => the value is limited to 25 characters followed by "..."
    // $V:f$#,##0.##$ => java pattern to display decimal number

    /*
     * See IHE BIR RAD TF-­‐2: 4.16.4.2.2.5.8
     */
    // Default profile of tag formats
    TagView[] disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.TOP_LEFT).getInfos();
    disElements[0] = new TagView(TagD.get(Tag.PatientName));
    disElements[1] = new TagView(TagD.get(Tag.PatientBirthDate));
    disElements[2] = new TagView(Messages.getString("ModalityView.id"), TagD.get(Tag.PatientID));
    disElements[3] = new TagView(Messages.getString("ModalityView.sex"), TagD.get(Tag.PatientSex));
    disElements[4] = new TagView(TagD.get(Tag.PatientAge));

    disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.TOP_RIGHT).getInfos();
    disElements[0] = new TagView(TagD.get(Tag.InstitutionName));
    disElements[1] =
        new TagView(Messages.getString("ModalityView.desc25"), TagD.get(Tag.StudyDescription));
    disElements[2] = new TagView(Messages.getString("ModalityView.study"), TagD.get(Tag.StudyID));
    disElements[3] =
        new TagView(Messages.getString("ModalityView.ac_nb"), TagD.get(Tag.AccessionNumber));
    // else content date, else Series date, else Study date
    disElements[4] =
        new TagView(
            Messages.getString("ModalityView.acq"),
            TagD.getTagFromIDs(
                Tag.AcquisitionDate,
                Tag.ContentDate,
                Tag.DateOfSecondaryCapture,
                Tag.SeriesDate,
                Tag.StudyDate));
    // else content time, else Series time, else Study time
    disElements[5] =
        new TagView(
            Messages.getString("ModalityView.acq"),
            TagD.getTagFromIDs(
                Tag.AcquisitionTime,
                Tag.ContentTime,
                Tag.TimeOfSecondaryCapture,
                Tag.SeriesTime,
                Tag.StudyTime));

    disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.BOTTOM_RIGHT).getInfos();
    disElements[1] =
        new TagView(Messages.getString("ModalityView.series_nb"), TagD.get(Tag.SeriesNumber));
    disElements[2] =
        new TagView(
            Messages.getString("ModalityView.laterality"),
            TagD.getTagFromIDs(Tag.FrameLaterality, Tag.ImageLaterality, Tag.Laterality));

    // TODO add sequence
    // derived from Contrast/Bolus Agent Sequence (0018,0012), if
    // present, else Contrast/Bolus Agent (0018,0010)
    // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.4b.html
    disElements[3] =
        new TagView(Messages.getString("ModalityView.desc25"), TagD.get(Tag.ContrastBolusAgent));
    disElements[4] =
        new TagView(Messages.getString("ModalityView.desc25"), TagD.get(Tag.SeriesDescription));
    disElements[5] =
        new TagView(Messages.getString("ModalityView.thick"), TagD.get(Tag.SliceThickness));
    disElements[6] =
        new TagView(Messages.getString("ModalityView.location"), TagD.get(Tag.SliceLocation));
    /*
     * Spacing Between Slices (0018,0088), if present, else a value derived from successive values of Image Position
     * (Patient) (0020,0032) perpendicular to the Image Orientation (Patient) (0020,0037)
     */
    reload();
  }

  private ModalityView() {}

  public static ModalityInfoData getModlatityInfos(Modality mod) {
    Map<Modality, ModalityInfoData> map = MODALITY_VIEW_MAP;
    ModalityInfoData mdata = map.get(mod);
    if (mdata == null) {
      mdata = map.get(Modality.DEFAULT);
    }
    if (mdata == null) {
      mdata = DEFAULT_MODALITY_VIEW;
    }
    return mdata;
  }

  public static Set<Entry<Modality, ModalityInfoData>> getModalityViewEntries() {
    return MODALITY_VIEW_MAP.entrySet();
  }

  /** The built-in defaults of the code, as the base of an {@code extends} in a site document. */
  public static Function<String, Optional<JsonObject>> builtInBases() {
    return id -> {
      Modality modality = getModality(id);
      return BUILT_IN.stream()
          .filter(data -> data.getModality() == modality)
          .findFirst()
          .map(ModalityViewJson::toJson);
    };
  }

  /** Re-reads the site document, or the legacy XML, of the resources package. */
  public static void reload() {
    reload(
        SiteDocuments.find(SITE_FILE).orElse(null),
        ResourceUtil.getResource(DicomResource.ATTRIBUTES_VIEW).toPath());
  }

  /**
   * Re-reads from another resources package: {@code config/attributesView.json} under {@code root}
   * when it exists, else {@code attributes-view.xml} in {@code root}.
   */
  static void reload(Path root) {
    reload(
        root.resolve(SiteDocuments.FOLDER).resolve(SITE_FILE),
        root.resolve(DicomResource.ATTRIBUTES_VIEW.getPath()));
  }

  private static synchronized void reload(Path json, Path xml) {
    ModalityViewJson.Document site = loadSite(json, xml);
    LayeredEntries.Merged<ModalityInfoData> merged =
        LayeredEntries.merge(
            List.of(
                Layer.of(Origin.BUILT_IN, BUILT_IN, Set.of()),
                Layer.of(Origin.SITE, site.entries(), site.locked())),
            data -> data.getModality().name(),
            ModalityViewJson.WHAT);
    Map<Modality, ModalityInfoData> map = new EnumMap<>(Modality.class);
    merged.entries().forEach(data -> map.put(data.getModality(), data));
    MODALITY_VIEW_MAP = Collections.unmodifiableMap(map);
  }

  // The JSON site document first, else the legacy XML, else nothing: the built-in only
  private static ModalityViewJson.Document loadSite(Path json, Path xml) {
    if (json != null && Files.isRegularFile(json)) {
      try {
        ModalityViewJson.Document site = ModalityViewJson.read(json, builtInBases());
        LOGGER.debug("Attributes view read from the site document {}", json);
        return site;
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot read the site attributes view: {}", json, e);
        return ModalityViewJson.Document.EMPTY;
      }
    }
    if (xml != null && Files.isRegularFile(xml)) {
      try {
        ModalityViewJson.Document site =
            ModalityViewJson.parse(ModalityViewJson.convert(xml), builtInBases());
        LOGGER.debug("Attributes view read from the legacy document {}", xml);
        return site;
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot read attributes-view.xml: {}", xml, e);
        return ModalityViewJson.Document.EMPTY;
      }
    }
    LOGGER.debug("Attributes view: no site document, the built-in default only");
    return ModalityViewJson.Document.EMPTY;
  }

  /** The modality of that name, with the retired names mapped to their successor. */
  static Modality getModality(String name) {
    try {
      return Modality.valueOf(name);
    } catch (Exception e) {
      if ("DS".equals(name)) {
        return Modality.XA;
      } else if ("CF".equals(name) || "DF".equals(name) || "VF".equals(name)) {
        return Modality.RF;
      } else if ("MA".equals(name) || "MS".equals(name)) {
        return Modality.MR;
      } else if ("EC".equals(name) || "CD".equals(name) || "DD".equals(name)) {
        return Modality.US;
      } else if ("ST".equals(name)) {
        return Modality.NM;
      }
      LOGGER.error("Modality reference of {} is missing", name, e);
    }
    return null;
  }

  static CornerDisplay getCornerDisplay(String name) {
    try {
      return CornerDisplay.valueOf(name);
    } catch (Exception e) {
      LOGGER.error("CornerDisplay reference of {} doesn't exist", name, e);
    }
    return null;
  }
}
