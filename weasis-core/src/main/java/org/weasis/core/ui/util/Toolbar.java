/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.util.Locale;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.ui.editor.Relevant;

/**
 * A toolbar of a viewer; {@link Relevant#isRelevant} decides whether it is shown for the selected
 * view (always, unless the toolbar overrides it).
 */
public interface Toolbar extends Insertable, Relevant {

  @Override
  Type getPanelType();

  WtoolBar getComponent();

  /**
   * A stable lowercase token naming the toolbar across sessions and locales, the one a UI profile
   * lists: by default the class name without its {@code ToolBar} suffix in kebab case ({@code
   * ViewerToolBar} gives {@code viewer}, {@code KeyObjectToolBar} gives {@code key-object}).
   */
  default String getToolbarId() {
    return idOf(getClass());
  }

  /** The default toolbar id of a class, see {@link #getToolbarId()}. */
  static String idOf(Class<?> type) {
    String name = type.getSimpleName();
    if (name.isEmpty()) {
      name = type.getSuperclass().getSimpleName();
    }
    name = name.replaceFirst("(?i)toolbar$|bar$", ""); // NON-NLS
    return name.replaceAll("(?<=[a-z])(?=[A-Z0-9])|(?<=[A-Z])(?=[A-Z][a-z])", "-")
        .toLowerCase(Locale.ROOT);
  }
}
