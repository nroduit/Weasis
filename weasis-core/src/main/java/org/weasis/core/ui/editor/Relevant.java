/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

/**
 * A part of the workspace that decides whether it is relevant for what the selected view shows: a
 * toolbar, a tool panel, a contributed tool, a menu entry. The decision reads the {@link
 * ViewContext} only (the facts the plugins publish about the series and the viewer), so it is
 * headless and the command surface can ask the same question. An item that does not override it is
 * always relevant, which is the behaviour of the 4.x era; an irrelevant item leaves the toolbar or
 * the menu while the view is selected and comes back with the next view, the user's own choice to
 * hide or show it ({@code weasis.tool.<id>.visible}) is untouched.
 */
public interface Relevant {

  /** Whether the item is relevant for the view the context describes; true by default. */
  default boolean isRelevant(ViewContext context) {
    return true;
  }
}
