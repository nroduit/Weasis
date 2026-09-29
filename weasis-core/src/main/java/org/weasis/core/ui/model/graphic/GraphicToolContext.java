/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * What a tool panel works on: the event manager of the viewer type, the selected view and the
 * selected graphic of the tool, when there is one.
 */
public record GraphicToolContext(
    ImageViewerEventManager<?> eventManager, ViewCanvas<?> view, Graphic graphic) {}
