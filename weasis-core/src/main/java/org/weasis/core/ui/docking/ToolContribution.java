/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import org.pf4j.ExtensionPoint;
import org.weasis.core.ui.editor.ViewContext;

/**
 * A tool a plugin contributes to the shell with a relevance predicate: the tool is shown while the
 * {@link ViewContext} of the selected viewer makes it relevant (a segmentation tool when SEG
 * objects reference the series, an RT tool when RT structures do, a chart when the protocol step
 * asks for one) and hidden otherwise, without the viewer knowing the tool. The tool keeps its
 * stable {@link DockableTool#getToolId() id}, so a UI profile can name it and a session remember
 * it. A contribution with no predicate of its own is always relevant on the viewers it names.
 */
public interface ToolContribution extends ExtensionPoint {

  /** The stable id of the tool, the one its {@link DockableTool#getToolId()} returns. */
  String toolId();

  /**
   * Whether the tool belongs to that viewer: by default any viewer. A contribution for one viewer
   * type checks {@link ViewContext#viewerIs(Class)}.
   */
  default boolean appliesTo(ViewContext context) {
    return !context.isEmpty();
  }

  /** Whether the tool is relevant for what the viewer shows; called only when it applies. */
  default boolean isRelevant(ViewContext context) {
    return true;
  }

  /** The tool, created once and kept; the same instance on every call. */
  DockableTool tool(ViewContext context);

  /** Releases the tool when its viewer type is gone; the next call to {@link #tool} creates it. */
  default void dispose() {
    // nothing by default
  }
}
