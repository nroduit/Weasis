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
 * Physical size of a pixel in mm, in the order of Pixel Spacing (0028,0030).
 *
 * @param row the distance between adjacent rows (vertical size)
 * @param column the distance between adjacent columns (horizontal size)
 */
public record PixelSpacing(double row, double column) {}
