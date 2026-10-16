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
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

/**
 * One row or card of the DICOM explorer: an image series, or a non-image object of a study. Modes
 * decide how an item is drawn; the item itself is headless and carries what every mode shows.
 */
public sealed interface ExplorerItem permits SeriesItem, ObjectItem {

  /** The study the item belongs to. */
  MediaSeriesGroup study();

  /** The Series Instance UID of the item (of the hidden series for an object). */
  String seriesUID();

  /** The text of the row: series description, KO title, PR content label... */
  String label();

  /** The DICOM modality. */
  String modality();

  /** When the content was created, null when unknown. */
  LocalDateTime date();

  /** The icon of the item, null for an image series (its thumbnail stands for it). */
  ResourceIconPath icon();
}
