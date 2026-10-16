/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.hp.HPImageSet;
import org.weasis.dicom.hp.HPSelector;
import org.weasis.dicom.hp.HangingProtocol;

/**
 * The Weasis JSON of a hanging protocol, one entry of the {@code protocols} document: {@code {"id":
 * ..., "schema": 1, "dicom": {...}, "weasis": {...}}}. The {@code dicom} block is the Hanging
 * Protocol IOD as {@link DicomJson} writes it, so it maps one-to-one to a DICOM HP object and
 * {@code org.weasis.dicom.hp} reads it back; the {@code weasis} block is {@link WeasisExtensions}.
 * A document of a newer schema is refused rather than half read.
 */
public final class ProtocolJson {

  public static final int SCHEMA = 1;
  public static final String SCHEMA_KEY = "schema"; // NON-NLS
  public static final String DICOM = "dicom"; // NON-NLS
  public static final String WEASIS = "weasis"; // NON-NLS

  /** A protocol with its id and its Weasis extensions. */
  public record Document(String id, HangingProtocol protocol, WeasisExtensions weasis) {
    public Document {
      Objects.requireNonNull(id);
      Objects.requireNonNull(protocol);
      weasis = weasis == null ? WeasisExtensions.NONE : weasis;
    }
  }

  private ProtocolJson() {}

  public static JsonObject toJson(String id, HangingProtocol protocol, WeasisExtensions weasis) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add(ListDocument.ID, id)
            .add(SCHEMA_KEY, SCHEMA)
            .add(DICOM, DicomJson.toJson(dataset(protocol)));
    JsonObject extensions = (weasis == null ? WeasisExtensions.NONE : weasis).toJson();
    if (!extensions.isEmpty()) {
      json.add(WEASIS, extensions);
    }
    return json.build();
  }

  /**
   * The dataset of the protocol, completed with the Image Sets Sequence when the model keeps the
   * image sets apart from it ({@code HangingProtocol.addNewImageSet} does not attach the item).
   */
  static Attributes dataset(HangingProtocol protocol) {
    Attributes dcm = new Attributes(protocol.getAttributes());
    if (!dcm.containsValue(Tag.ImageSetsSequence) && !protocol.getImageSets().isEmpty()) {
      Sequence seq = dcm.newSequence(Tag.ImageSetsSequence, protocol.getImageSets().size());
      for (HPImageSet imageSet : protocol.getImageSets()) {
        Attributes item = new Attributes(imageSet.getAttributes());
        if (!item.containsValue(Tag.ImageSetSelectorSequence)) {
          // an image set sharing the selectors of another keeps them in memory only
          Sequence selectors =
              item.newSequence(
                  Tag.ImageSetSelectorSequence, imageSet.getImageSetSelectors().size());
          for (HPSelector selector : imageSet.getImageSetSelectors()) {
            selectors.add(new Attributes(selector.getAttributes()));
          }
        }
        Sequence timeBased = item.getSequence(Tag.TimeBasedImageSetsSequence);
        if (timeBased != null) {
          // the model numbers the time-based sets only when they exist at numbering time
          for (Attributes tb : timeBased) {
            if (!tb.containsValue(Tag.ImageSetNumber)) {
              tb.setInt(Tag.ImageSetNumber, VR.US, imageSet.getImageSetNumber());
            }
          }
        }
        seq.add(item);
      }
    }
    return dcm;
  }

  public static JsonObject toJson(Document document) {
    return toJson(document.id(), document.protocol(), document.weasis());
  }

  /**
   * @throws IllegalArgumentException when the schema is newer than this version reads, or the entry
   *     has no id or no {@code dicom} block
   */
  /**
   * weasis-dicom-tools 5.35.1 builds each image set of a dataset on its time-based item and resets
   * its Image Set Number to 0 before the number is read, so the numbers the display sets and the
   * extensions refer to are lost; the sets are read in number order, so the index gives them back.
   */
  static void renumberImageSets(HangingProtocol protocol) {
    List<HPImageSet> sets = protocol.getImageSets();
    for (int i = 0; i < sets.size(); i++) {
      if (sets.get(i).getImageSetNumber() != i + 1) {
        sets.get(i).setImageSetNumber(i + 1);
      }
    }
  }

  public static Document fromJson(JsonObject json) {
    int schema = json.getInt(SCHEMA_KEY, SCHEMA);
    if (schema > SCHEMA) {
      throw new IllegalArgumentException("Protocol schema " + schema + " is newer than " + SCHEMA);
    }
    String id = json.getString(ListDocument.ID, null);
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("A protocol entry needs an id");
    }
    if (!(json.get(DICOM) instanceof JsonObject dicom)) {
      throw new IllegalArgumentException("Protocol " + id + " has no dicom block");
    }
    HangingProtocol protocol = new HangingProtocol(DicomJson.toAttributes(dicom));
    renumberImageSets(protocol);
    return new Document(
        id,
        protocol,
        json.get(WEASIS) instanceof JsonObject w
            ? WeasisExtensions.fromJson(w)
            : WeasisExtensions.NONE);
  }
}
