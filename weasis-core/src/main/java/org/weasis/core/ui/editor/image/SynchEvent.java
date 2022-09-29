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

  public SynchEvent(ViewCanvas<?> view) {
    this.view = view;
    this.events = new HashMap<>();
  }

  public SynchEvent(ViewCanvas<?> view, String command, Object value) {
    this.view = view;
    if (command != null) {
      this.events = new HashMap<>(2);
      this.getEvents().put(command, value);
    } else {
      this.events = new HashMap<>(8);
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
}
