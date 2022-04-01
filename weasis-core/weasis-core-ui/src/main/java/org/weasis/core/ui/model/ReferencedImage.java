/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;

public class ReferencedImage extends DefaultUUID {

  private List<Integer> frames;

  public ReferencedImage() {
    this(null, null);
  }

  public ReferencedImage(String uuid) {
    this(uuid, null);
  }

  public ReferencedImage(String uuid, List<Integer> frames) {
    super(uuid);
    setFrames(frames);
  }

  @XmlList
  @XmlAttribute(name = "frames")
  public List<Integer> getFrames() {
    return frames;
  }

  /**
   * Set a list of frames. Each value matches to the position of a MediaElement (media.getKey()).
   *
   * <p>Note: to match with DICOM Instance Frame the value must be increment of one (DicomFrame =
   * frame + 1).
   *
   * @param frames the image frame list
   */
  public void setFrames(List<Integer> frames) {
    this.frames = Optional.ofNullable(frames).orElseGet(ArrayList::new);
  }
}
