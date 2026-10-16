/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import java.util.Map;
import java.util.Optional;
import org.weasis.core.api.util.Copyable;

/**
 * Represents a node in an image processing pipeline. Each node can perform operations on images and
 * can be enabled or disabled.
 */
public interface ImageOpNode extends Copyable<ImageOpNode> {

  /** Parameter keys for image operations. */
  final class Param {

    public static final String NAME = "op.display.name";
    public static final String ENABLE = "op.enable";

    public static final String INPUT_IMG = "op.input.img";
    public static final String OUTPUT_IMG = "op.output.img";

    private Param() {}
  }

  /**
   * Processes the image operation.
   *
   * @throws Exception if processing fails
   */
  void process() throws Exception;

  boolean isEnabled();

  void setEnabled(boolean enabled);

  String getName();

  void setName(String name);

  /**
   * Gets a parameter value.
   *
   * @param key the parameter key
   * @return the parameter value wrapped in Optional
   */
  default Optional<Object> getParamOptional(String key) {
    return Optional.ofNullable(getParam(key));
  }

  Object getParam(String key);

  void setParam(String key, Object value);

  /**
   * Gets a parameter value with type casting.
   *
   * @param key the parameter key
   * @param ignored the class type to cast to (not used)
   * @param <T> the expected type of the parameter value
   * @return the parameter value cast to type T
   */
  <T> T getParam(String key, Class<T> ignored);

  /**
   * Sets all parameters from the provided map.
   *
   * @param params the parameters map
   */
  void setAllParameters(Map<String, Object> params);

  void removeParam(String key);

  void clearParams();

  /** Clears cached input and output images to force reprocessing. */
  void clearIOCache();

  /**
   * Handles image operation events.
   *
   * @param event the event to handle
   */
  void handleImageOpEvent(ImageOpEvent event);

  /**
   * A number that changes each time something that can alter the output changes. The {@link
   * SimpleOpManager} skips a node whose revision and input are those of its last run and reuses its
   * output.
   *
   * @return the revision, or -1 when unknown: the node then runs on every pass
   */
  default long getRevision() {
    return -1;
  }

  /**
   * Tells that something the operation reads outside its parameters changed (a preference, a cache,
   * a document), so that it runs on the next pass.
   */
  default void invalidate() {
    // No revision to change: the node runs on every pass
  }
}
