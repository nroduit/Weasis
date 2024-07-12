/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.launcher;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.util.StringUtil;

public class Placeholder {
  private static final Logger LOGGER = LoggerFactory.getLogger(Placeholder.class);

  public static final String DICOM_WADO_FOLDER = "wado.folder";
  public static final String DICOM_QR_FOLDER = "qr.folder";
  public static final String DICOM_LAST_FOLDER = "last.folder";
  public static final String DICOM_COPY_FOLDER = "selection.folder";

  public static final Placeholder PREFERENCES_PLACEHOLDER =
      new Placeholder(
          "\\{pref:(\\S+)}", // NON-NLS
          (pref, _) -> {
            String val = UICore.getInstance().getSystemPreferences().getProperty(pref);
            if (!StringUtil.hasText(val)) {
              val = UICore.getInstance().getLocalPersistence().getProperty(pref);
            }
            return val;
          });

  private final String patternString;
  private final BiFunction<String, ImageViewerEventManager<?>, String> biFunction;

  public Placeholder(
      String patternString, BiFunction<String, ImageViewerEventManager<?>, String> biFunction) {
    this.patternString = Objects.requireNonNull(patternString);
    this.biFunction = Objects.requireNonNull(biFunction);
  }

  public String resolvePlaceholders(String template, ImageViewerEventManager<?> eventManager) {
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(template);
    StringBuilder sb = new StringBuilder();

    while (matcher.find()) {
      String placeholder = matcher.group(1);
      String replacement = biFunction.apply(placeholder, eventManager);
      if (replacement == null) {
        replacement = StringUtil.EMPTY_STRING;
        LOGGER.warn("Placeholder '{}' not found", placeholder);
      }
      matcher.appendReplacement(sb, replacement);
    }
    matcher.appendTail(sb);

    return sb.toString();
  }
}
