/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HiddenSeriesManager {
  private static final HiddenSeriesManager instance = new HiddenSeriesManager();
  public final Map<String, List<HiddenSpecialElement>> series2Elements = new ConcurrentHashMap<>();
  public final Map<String, List<String>> patient2Series = new ConcurrentHashMap<>();
  public final Map<String, List<String>> reference2Series = new ConcurrentHashMap<>();

  public static HiddenSeriesManager getInstance() {
    return instance;
  }
}
