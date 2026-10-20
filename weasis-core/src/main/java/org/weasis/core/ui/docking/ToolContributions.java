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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.core.ui.editor.ViewContexts;

/**
 * Applies the {@link ToolContribution tool contributions} to a {@link ViewContext}: a contribution
 * that applies and is relevant shows its tool, one that does not hides it. The contributions of the
 * plugins (pf4j extensions) follow every published context; a viewer's own dynamic tools go through
 * {@link #apply(List, ViewContext)} with the context of its selected series.
 */
public final class ToolContributions {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolContributions.class);

  private static final Set<ToolContribution> SHOWN =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static volatile Supplier<List<ToolContribution>> extensions =
      () -> GuiUtils.getUICore().getPluginManager().getExtensions(ToolContribution.class);
  private static volatile boolean following;

  private ToolContributions() {}

  /** Replaces the extension lookup, for tests. */
  public static void setExtensions(Supplier<List<ToolContribution>> supplier) {
    extensions = Objects.requireNonNull(supplier);
  }

  /** The contributions of the plugins, as pf4j lists them. */
  public static List<ToolContribution> extensions() {
    try {
      return extensions.get();
    } catch (RuntimeException e) {
      LOGGER.warn("No tool contributions", e);
      return List.of();
    }
  }

  /** Makes the plugin contributions follow every published context; called once by the shell. */
  public static synchronized void follow() {
    if (!following) {
      following = true;
      ViewContexts.addListener(context -> apply(extensions(), context));
    }
  }

  /**
   * Shows the tools of the contributions that apply and are relevant to the context, hides the
   * others.
   *
   * @return the ids of the tools shown
   */
  public static List<String> apply(List<ToolContribution> contributions, ViewContext context) {
    List<String> shown = new ArrayList<>();
    for (ToolContribution contribution : contributions) {
      boolean relevant;
      try {
        relevant =
            context != null && contribution.appliesTo(context) && contribution.isRelevant(context);
      } catch (RuntimeException e) {
        LOGGER.warn("Tool contribution {} failed", contribution.toolId(), e);
        relevant = false;
      }
      if (relevant) {
        DockableTool tool = contribution.tool(context);
        if (tool != null) {
          tool.showDockable();
          shown.add(tool.getToolId());
          synchronized (SHOWN) {
            SHOWN.add(contribution);
          }
        }
      } else {
        hide(contribution, context);
      }
    }
    return shown;
  }

  private static void hide(ToolContribution contribution, ViewContext context) {
    boolean wasShown;
    synchronized (SHOWN) {
      wasShown = SHOWN.remove(contribution);
    }
    if (wasShown) {
      DockableTool tool = contribution.tool(context);
      if (tool != null) {
        tool.closeDockable();
      }
    }
  }
}
