/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.core.bean;

import java.util.Iterator;
import java.util.Map.Entry;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.dicom.codec.TagD;

public class Global extends DefaultTaggable {

  public static final Integer PATIENT_DICOM_GROUP_NUMBER = Integer.parseInt("0010", 16);

  protected boolean allowFullEdition = true;

  public Global() {
    init(null);
  }

  public void init(Taggable taggable) {
    clear();
    tags.put(TagD.get(Tag.StudyInstanceUID), UIDUtils.createUID());

    if (taggable != null) {
      taggable
          .getTagEntrySetIterator()
          .forEachRemaining(
              i -> {
                TagW tag = i.getKey();
                if (tag != null) {
                  tags.put(tag, i.getValue());
                }
              });
    }

    allowFullEdition =
        getTagValue(TagD.get(Tag.PatientID)) == null
            || getTagValue(TagD.get(Tag.PatientName)) == null;
  }

  /**
   * Updates all Dicom Tags from the given document except Patient Dicom Group Tags
   *
   * @param tagList list of tags
   */
  public void updateAllButPatient(TagReadable tagList) {
    if (tagList != null) {
      tagList
          .getTagEntrySetIterator()
          .forEachRemaining(
              i -> {
                TagW tag = i.getKey();
                if (tag != null
                    && TagUtils.groupNumber(tag.getId()) != PATIENT_DICOM_GROUP_NUMBER) {
                  tags.put(tag, i.getValue());
                }
              });
    }
  }

  public boolean containsSameTagValues(Taggable taggable, Integer dicomGroupNumber) {
    if (taggable != null) {
      Iterator<Entry<TagW, Object>> iter = taggable.getTagEntrySetIterator();
      while (iter.hasNext()) {
        Entry<TagW, Object> entry = iter.next();
        TagW tag = entry.getKey();
        if (tag != null
            && (dicomGroupNumber == null
                || TagUtils.groupNumber(tag.getId()) == dicomGroupNumber)) {
          if (this.containTagKey(tag)) {
            if (!TagUtil.isEquals(this.getTagValue(tag), entry.getValue())) {
              return false;
            }
          } else if (entry.getValue() != null) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Override
  public String toString() {
    TagW name = TagD.get(Tag.PatientName);
    return name.getFormattedTagValue(getTagValue(name), null);
  }

  public boolean isAllowFullEdition() {
    return allowFullEdition;
  }
}
