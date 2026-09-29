/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;

/** A content item of a Content Sequence: a content module plus its relationship to the parent. */
public class SRDocumentContent extends SRDocumentContentModule {

  public SRDocumentContent(Attributes attributes) {
    super(attributes);
  }

  public String getRelationshipType() {
    return dcmItems.getString(Tag.RelationshipType);
  }

  public int[] getReferencedContentItemIdentifier() {
    return DicomUtils.getIntArrayFromDicomElement(
        dcmItems, Tag.ReferencedContentItemIdentifier, null);
  }

  /**
   * Referenced Content Item Identifier (0040,DB73) as a dotted node identifier, e.g. "1.4.1", or
   * null when the attribute is absent.
   */
  public String getReferencedContentItemNodeId() {
    return toNodeId(getReferencedContentItemIdentifier());
  }

  /**
   * A by-reference content item carries only a Relationship Type and a Referenced Content Item
   * Identifier: it has no Value Type of its own (PS3.3 C.17.3.2.5). An item holding both a Value
   * Type and an identifier is a by-value item with a non-conformant extra attribute.
   */
  public boolean isByReference() {
    return getValueType() == null && getReferencedContentItemIdentifier() != null;
  }

  static String toNodeId(int[] refs) {
    if (refs == null || refs.length == 0) {
      return null;
    }
    StringBuilder r = new StringBuilder();
    for (int j = 0; j < refs.length; j++) {
      if (j > 0) {
        r.append('.');
      }
      r.append(refs[j]);
    }
    return r.toString();
  }
}
