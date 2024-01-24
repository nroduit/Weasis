/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class Release {
  @JsonProperty(value = "date")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date date;

  @JsonProperty(value = "version", required = true)
  private String version;

  @JsonProperty(value = "url", required = true)
  private String url;

  public Date getDate() {
    return date;
  }

  public String getVersion() {
    return version;
  }

  public String getUrl() {
    return url;
  }
}
