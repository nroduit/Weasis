/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.HashMap;
import java.util.Map;

public class SynchEvent {

  private final ViewCanvas<?> view;
  private final Map<String, Object> events;

  private final boolean valueIsAdjusting;

  public SynchEvent(ViewCanvas<?> view) {
    this(view, null, null, false);
  }

  public SynchEvent(ViewCanvas<?> view, String command, Object value) {
    this(view, command, value, false);
  }

  public SynchEvent(ViewCanvas<?> view, String command, Object value, boolean valueIsAdjusting) {
    this.view = view;
    this.valueIsAdjusting = valueIsAdjusting;
    this.events = new HashMap<>();
    if (command != null) {
      this.getEvents().put(command, value);
    }
  }

  public void put(String key, Object value) {
    events.put(key, value);
  }

  public ViewCanvas<?> getView() {
    return view;
  }

  public Map<String, Object> getEvents() {
    return events;
  }

  public boolean isValueIsAdjusting() {
    return valueIsAdjusting;
  }
}
