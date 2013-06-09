package org.weasis.core.ui.editor.image;

import java.util.HashMap;

public class SynchEvent {

    private final DefaultView2d<?> view;
    private final HashMap<String, Object> events;

    public SynchEvent(DefaultView2d<?> view) {
        this.view = view;
        this.events = new HashMap<String, Object>();
    }

    public SynchEvent(DefaultView2d<?> view, String command, Object value) {
        this.view = view;
        if (command != null) {
            this.events = new HashMap<String, Object>(2);
            this.getEvents().put(command, value);
        } else {
            this.events = new HashMap<String, Object>(8);
        }
    }

    public void put(String key, Object value) {
        events.put(key, value);
    }

    public DefaultView2d<?> getView() {
        return view;
    }

    public HashMap<String, Object> getEvents() {
        return events;
    }
}