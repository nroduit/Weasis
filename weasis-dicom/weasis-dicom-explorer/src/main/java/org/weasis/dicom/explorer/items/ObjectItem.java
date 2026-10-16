/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.time.LocalDateTime;
import java.util.Objects;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.ObjectDescriptor;
import org.weasis.dicom.codec.ObjectKind;

/**
 * A non-image object of a study (KO, PR, SEG, RT...) as the explorer lists it: the hidden element
 * and its description.
 *
 * @param element the element
 * @param descriptor what the explorer shows for it
 * @param study the study the item is listed under: the object's own study, or the study of the
 *     series it references when the object lives in another one
 */
public record ObjectItem(
    HiddenSpecialElement element, ObjectDescriptor descriptor, MediaSeriesGroup study)
    implements ExplorerItem {

  public ObjectItem {
    Objects.requireNonNull(element);
    Objects.requireNonNull(descriptor);
  }

  public ObjectKind kind() {
    return descriptor.kind();
  }

  /** True when the object references the given image series. */
  public boolean references(String seriesInstanceUID) {
    return descriptor.references(seriesInstanceUID);
  }

  @Override
  public String seriesUID() {
    return descriptor.seriesUID();
  }

  @Override
  public String label() {
    return descriptor.label();
  }

  @Override
  public String modality() {
    return descriptor.modality();
  }

  @Override
  public LocalDateTime date() {
    return descriptor.date();
  }

  @Override
  public ResourceIconPath icon() {
    return descriptor.icon();
  }
}
