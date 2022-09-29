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
import org.weasis.core.api.util.Copyable;

public interface ImageOpNode extends Copyable<ImageOpNode> {

  final class Param {

    public static final String NAME = "op.display.name";
    public static final String ENABLE = "op.enable";

    public static final String INPUT_IMG = "op.input.img";
    public static final String OUTPUT_IMG = "op.output.img";

    private Param() {}
  }

  void process() throws Exception;

  boolean isEnabled();

  void setEnabled(boolean enabled);

  String getName();

  void setName(String name);

  Object getParam(String key);

  void setParam(String key, Object value);

  void setAllParameters(Map<String, Object> map);

  void removeParam(String key);

  void clearParams();

  /** Clear all the parameter values starting by "op.input" or "op.output" */
  void clearIOCache();

  void handleImageOpEvent(ImageOpEvent event);
}
