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

import java.util.Hashtable;
import java.util.Objects;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;

public abstract class ExtToolFactory<E extends ImageElement> implements InsertableFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtToolFactory.class);

  protected final String name;
  protected Insertable toolPane = null;

  protected ExtToolFactory(String name) {
    this.name = Objects.requireNonNull(name);
  }

  protected abstract ImageViewerEventManager<E> getImageViewerEventManager();

  protected abstract boolean isCompatible(Hashtable<String, Object> properties);

  protected abstract Insertable getInstance(Hashtable<String, Object> properties);

  public void hideTool() {
    if (toolPane instanceof DockableTool dockableTool) {
      dockableTool.closeDockable();
    }
  }

  @Override
  public Insertable createInstance(Hashtable<String, Object> properties) {
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

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) {
    LOGGER.info("Activate the {} panel", name);
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("Deactivate the {} panel", name);
  }
}
