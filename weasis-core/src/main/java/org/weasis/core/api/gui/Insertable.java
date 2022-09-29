/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui;

public interface Insertable {

  int LAST_FILLER_HEIGHT = 5;
  int BLOCK_SEPARATOR = 15;
  int ITEM_SEPARATOR_SMALL = 2;
  int ITEM_SEPARATOR = 5;
  int ITEM_SEPARATOR_LARGE = 10;

  enum Type {
    EXPLORER,
    TOOL,
    TOOLBAR,
    EMPTY,
    PREFERENCES
  }

  String getComponentName();

  Type getType();

  boolean isComponentEnabled();

  void setComponentEnabled(boolean enabled);

  int getComponentPosition();

  void setComponentPosition(int position);
}
