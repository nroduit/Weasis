/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import org.weasis.core.ui.model.graphic.imp.seg.ByteLutAlpha;

/**
 * The value-to-color mapping a {@link FusionOp} is currently painting with. Carrying the actual
 * {@link ByteLutAlpha} (rather than the parameters it was built from) keeps {@link FusionColorBar}
 * showing exactly what the overlay shows, transparency included.
 *
 * @param window maps the overlay values to the LUT entries
 * @param lut the ABGR LUT indexed by the windowed 8-bit intensity
 */
public record FusionColorScale(FusionWindow window, ByteLutAlpha lut) {}
