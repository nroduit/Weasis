/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;

/**
 * A tool of the 4.x era created on demand from a property map; also a {@link ToolContribution},
 * with {@link #isCompatible} as its relevance predicate on the properties of the {@link
 * ViewContext}, so the two registrations behave the same.
 */
public abstract class ExtToolFactory<E extends ImageElement>
    implements InsertableFactory, ToolContribution {

  protected final String name;
  protected Insertable toolPane = null;

  protected ExtToolFactory(String name) {
    this.name = Objects.requireNonNull(name);
  }

  protected abstract ImageViewerEventManager<E> getImageViewerEventManager();

  protected abstract boolean isCompatible(Map<String, Object> properties);

  protected abstract Insertable getInstance(Map<String, Object> properties);

  @Override
  public String toolId() {
    return toolPane instanceof DockableTool dockableTool ? dockableTool.getToolId() : name;
  }

  /** A factory belongs to the viewers whose UI lists it among their dynamic tools. */
  @Override
  public boolean appliesTo(ViewContext context) {
    return context.viewer() != null
        && context.viewer().getSeriesViewerUI().dynamicTools.contains(this);
  }

  @Override
  public boolean isRelevant(ViewContext context) {
    return isCompatible(context.properties());
  }

  @Override
  public DockableTool tool(ViewContext context) {
    Insertable instance = toolPane != null ? toolPane : createInstance(context.properties());
    return instance instanceof DockableTool dockableTool ? dockableTool : null;
  }

  @Override
  public void dispose() {
    if (toolPane != null) {
      dispose(toolPane);
    }
  }

  public void hideTool() {
    if (toolPane instanceof DockableTool dockableTool) {
      dockableTool.closeDockable();
    }
  }

  @Override
  public Insertable createInstance(Map<String, Object> properties) {
    boolean compatible = isCompatible(properties);
    if (!compatible) {
      return null;
    }
    if (toolPane == null) {
      toolPane = getInstance(properties);
      if (toolPane instanceof SeriesViewerListener listener) {
        ImageViewerEventManager<E> manager = getImageViewerEventManager();
        if (manager != null) {
          manager.addSeriesViewerListener(listener);
        }
      }
    }
    return toolPane;
  }

  @Override
  public void dispose(Insertable tool) {
    if (tool instanceof SeriesViewerListener listener) {
      ImageViewerEventManager<E> manager = getImageViewerEventManager();
      if (manager != null) {
        manager.removeSeriesViewerListener(listener);
      }
    }
    this.toolPane = null;
  }

  @Override
  public Type getType() {
    return Type.TOOL_EXT;
  }
}
