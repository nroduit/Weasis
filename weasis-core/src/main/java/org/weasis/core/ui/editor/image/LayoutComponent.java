/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import javax.swing.JComponent;
import org.weasis.core.ui.editor.SeriesViewerListener;

/**
 * A component that takes a cell of the layout of a viewer next to the viewports: a histogram, the
 * attributes of the image, a chart of a plugin. It is implemented by a Swing component and follows
 * the selected view through {@link SeriesViewerListener#changingViewContentEvent}: the viewer
 * registers it as a listener when it enters the layout and removes it when it leaves.
 *
 * <p>A kind of component is declared once with {@link LayoutComponents#register}, which is what
 * makes it available in the layouts of the user and what creates it without reflection.
 */
public interface LayoutComponent extends SeriesViewerListener {

  /** The Swing component put in the cell; by default this object. */
  default JComponent asComponent() {
    return (JComponent) this;
  }

  /** Called when the component leaves the layout for good, to release what it holds. */
  default void disposeComponent() {}
}
