/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

/**
 * The kind of a non-image DICOM object the explorer lists as an item. The kinds are those of the
 * standard composite objects Weasis reads without a viewer of their own; {@link #OTHER} covers any
 * other hidden modality a plugin registers.
 */
public enum ObjectKind {
  /** Key Object Selection Document, except a rejection note. */
  KEY_OBJECT("KO"), // NON-NLS
  /** Key Object Selection Document whose title is an IOCM rejection reason. */
  REJECTION_NOTE("KO"), // NON-NLS
  /** Grayscale, color or blending Softcopy Presentation State. */
  PRESENTATION_STATE("PR"), // NON-NLS
  /** Segmentation. */
  SEGMENTATION("SEG"), // NON-NLS
  /** RT Structure Set. */
  RT_STRUCTURE_SET("RTSTRUCT"), // NON-NLS
  /** RT Plan. */
  RT_PLAN("RTPLAN"), // NON-NLS
  /** RT Dose. */
  RT_DOSE("RTDOSE"), // NON-NLS
  /** Microscopy Bulk Simple Annotations. */
  ANNOTATION("ANN"), // NON-NLS
  /** Any other hidden special element. */
  OTHER(null);

  private final String modality;

  ObjectKind(String modality) {
    this.modality = modality;
  }

  /** The DICOM modality of the objects of this kind, null for {@link #OTHER}. */
  public String getModality() {
    return modality;
  }

  /** The stable lower-case identifier used by the commands ({@code key-object}, {@code seg}...). */
  public String getId() {
    return switch (this) {
      case KEY_OBJECT -> "ko"; // NON-NLS
      case REJECTION_NOTE -> "rejection"; // NON-NLS
      case PRESENTATION_STATE -> "pr"; // NON-NLS
      case SEGMENTATION -> "seg"; // NON-NLS
      case RT_STRUCTURE_SET -> "rtstruct"; // NON-NLS
      case RT_PLAN -> "rtplan"; // NON-NLS
      case RT_DOSE -> "rtdose"; // NON-NLS
      case ANNOTATION -> "ann"; // NON-NLS
      case OTHER -> "other"; // NON-NLS
    };
  }

  /** The kind matching a command identifier (case-insensitive), or null. */
  public static ObjectKind fromId(String id) {
    if (id == null) {
      return null;
    }
    for (ObjectKind kind : values()) {
      if (kind.getId().equalsIgnoreCase(id.trim())) {
        return kind;
      }
    }
    return null;
  }

  /** The kind of a modality other than KO (which needs the element to tell a rejection note). */
  static ObjectKind fromModality(String modality) {
    if (modality == null) {
      return OTHER;
    }
    for (ObjectKind kind : values()) {
      if (kind != REJECTION_NOTE && modality.equals(kind.modality)) {
        return kind;
      }
    }
    return OTHER;
  }
}
