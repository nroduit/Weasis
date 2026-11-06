/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

/**
 * Size of a level and of its tiles, as read from the header of its first instance.
 *
 * @param pixelSpacing null when the header gives no physical size
 */
record LevelGeometry(
    int totalColumns, int totalRows, int tileWidth, int tileHeight, PixelSpacing pixelSpacing) {}
