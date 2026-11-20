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

import java.util.Map;
import org.pf4j.ExtensionPoint;
import org.weasis.core.api.gui.Insertable.Type;

public interface InsertableFactory extends ExtensionPoint {

  Insertable createInstance(Map<String, Object> properties);

  void dispose(Insertable component);

  boolean isComponentCreatedByThisFactory(Insertable component);

  Type getType();

  /**
   * Whether the components of this factory are made for that type of viewer. Every viewer registers
   * the factories of all the plugins, so a factory whose tool reads the state of one viewer names
   * it here; by default a factory serves every viewer.
   *
   * @param viewerClass the class of the viewer that registers the factory
   */
  default boolean supportsViewer(Class<?> viewerClass) {
    return true;
  }
}
