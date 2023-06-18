/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.function.Predicate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;

public class TagSeq extends TagD {

  public TagSeq(
      int tagID,
      String keyword,
      String displayedName,
      String privateCreatorID,
      VR vr,
      int vmMin,
      int vmMax,
      Object defaultValue,
      boolean retired) {
    super(tagID, keyword, displayedName, privateCreatorID, vr, vmMin, vmMax, defaultValue, retired);
  }

  @Override
  public void readValue(Object data, Taggable taggable) {
    if (data instanceof MacroSeqData macro) {
      Object val = getValue(macro.getAttributes());
      if (val instanceof Sequence seq && !seq.isEmpty()) {
        val = seq.get(0);
      }

      if (val instanceof Attributes dataset) {
        Predicate<? super Attributes> predicate = macro.getApplicable();
        if (predicate == null || predicate.test(dataset)) {
          for (TagW tag : macro.getTags()) {
            if (tag != null) {
              tag.readValue(dataset, taggable);
            }
          }
        }
      }
    }
  }

  public static class MacroSeqData {
    private final Attributes attributes;
    private final TagW[] tags;
    private final Predicate<? super Attributes> applicable;

    public MacroSeqData(Attributes attributes, TagW[] tags) {
      this(attributes, tags, null);
    }

    public MacroSeqData(
        Attributes attributes, TagW[] tags, Predicate<? super Attributes> applicable) {
      this.attributes = attributes;
      this.tags = tags;
      this.applicable = applicable;
    }

    public Attributes getAttributes() {
      return attributes;
    }

    public TagW[] getTags() {
      return tags;
    }

    public Predicate<? super Attributes> getApplicable() {
      return applicable;
    }
  }
}
