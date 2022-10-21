/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import org.weasis.core.api.Messages;

public interface ActionState {

  enum NoneLabel {
    NONE(Messages.getString("ActionState.none")),

    NONE_SERIES(Messages.getString("ActionState.none_all"));

    private final String title;

    NoneLabel(String title) {
      this.title = title;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  void enableAction(boolean enabled);

  boolean isActionEnabled();

  Feature<? extends ActionState> getActionW();

  boolean registerActionState(Object c);

  void unregisterActionState(Object c);
}
