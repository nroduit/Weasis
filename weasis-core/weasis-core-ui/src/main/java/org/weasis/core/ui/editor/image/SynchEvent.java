/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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