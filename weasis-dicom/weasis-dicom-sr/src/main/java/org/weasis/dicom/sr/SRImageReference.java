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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.dicom.macro.SOPInstanceReference;

/**
 * The target of a clickable link in the rendered report: a referenced SOP instance (image, waveform
 * or any composite object) and, for an SCOORD item, the graphics to draw on that image.
 */
public class SRImageReference {
  private SOPInstanceReference sopInstanceReference;
  private List<Graphic> graphics;
  private final String nodeLevel;

  public SRImageReference(String nodeLevel) {
    super();
    this.nodeLevel = nodeLevel;
  }

  public void addGraphic(Graphic g) {
    if (g != null) {
      if (graphics == null) {
        graphics = new ArrayList<>();
      }
      graphics.add(g);
    }
  }

  public boolean hasGraphics() {
    return graphics != null && !graphics.isEmpty();
  }

  public SOPInstanceReference getSopInstanceReference() {
    return sopInstanceReference;
  }

  public void setSopInstanceReference(SOPInstanceReference sopInstanceReference) {
    this.sopInstanceReference = sopInstanceReference;
  }

  /** The graphics to overlay, never null. */
  public List<Graphic> getGraphics() {
    return graphics == null ? Collections.emptyList() : graphics;
  }

  /** The node identifier (e.g. "1.4.1") of the content item that produced this link. */
  public String getNodeLevel() {
    return nodeLevel;
  }
}
