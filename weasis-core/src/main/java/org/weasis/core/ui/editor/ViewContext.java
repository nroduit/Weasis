/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * What is displayed and why, for the parts of the workspace that decide whether they are relevant:
 * the selected viewer and its type, the selected series, and the facts the plugins contribute about
 * them ({@link ViewContextContributor}: the hanging protocol and its step, the series features, the
 * objects of the series). The facts are a plain map keyed by dotted names ({@code hp.protocol},
 * {@code hp.step}), so a predicate written in one plugin can read what another one knows without a
 * dependency; the two keys {@link #SERIES_KEY} and {@link #CONTAINER_KEY} are the ones the
 * factories of the 4.x era already read.
 */
public record ViewContext(
    SeriesViewer<?> viewer, String viewerTypeId, MediaSeries<?> series, Map<String, Object> facts) {

  /** The fact under which the selected series travels, as the 4.x tool factories expect it. */
  public static final String SERIES_KEY = MediaSeries.class.getName();

  /** The fact under which the class name of the viewer travels, as the 4.x factories expect it. */
  public static final String CONTAINER_KEY = "class.container"; // NON-NLS

  private static final ViewContext EMPTY = new ViewContext(null, null, null, Map.of());

  public ViewContext {
    facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts == null ? Map.of() : facts));
  }

  /** No viewer selected. */
  public static ViewContext empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return viewer == null;
  }

  /** A fact by name, empty when the plugins said nothing under it. */
  public Optional<Object> fact(String name) {
    return Optional.ofNullable(facts.get(name));
  }

  /** A fact by name and type. */
  public <T> Optional<T> fact(String name, Class<T> type) {
    return fact(name).filter(type::isInstance).map(type::cast);
  }

  /** Whether the viewer is an instance of that class (a viewer type a contribution belongs to). */
  public boolean viewerIs(Class<?> viewerClass) {
    return viewer != null && viewerClass.isInstance(viewer);
  }

  /** The facts and the 4.x keys together, for a predicate written against the property map. */
  public Map<String, Object> properties() {
    Map<String, Object> properties = new LinkedHashMap<>(facts);
    if (series != null) {
      properties.put(SERIES_KEY, series);
    }
    if (viewer != null) {
      properties.put(CONTAINER_KEY, viewer.getClass().getName());
    }
    return properties;
  }

  /** The context as the commands report it: type, series id, the facts (scalars as they are). */
  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (viewerTypeId != null) {
      json.add("viewerType", viewerTypeId); // NON-NLS
    }
    if (series != null) {
      Object id = series.getTagValue(series.getTagID());
      if (id != null) {
        json.add("series", String.valueOf(id)); // NON-NLS
      }
    }
    JsonObjectBuilder factJson = Json.createObjectBuilder();
    facts.forEach(
        (name, value) -> {
          switch (value) {
            case null -> factJson.addNull(name);
            case Integer integer -> factJson.add(name, integer);
            case Long longValue -> factJson.add(name, longValue);
            case Number number -> factJson.add(name, number.doubleValue());
            case Boolean bool -> factJson.add(name, bool);
            case String text -> factJson.add(name, text);
            case JsonObject object -> factJson.add(name, object);
            default -> factJson.add(name, value.getClass().getSimpleName());
          }
        });
    json.add("facts", factJson); // NON-NLS
    return json.build();
  }
}
