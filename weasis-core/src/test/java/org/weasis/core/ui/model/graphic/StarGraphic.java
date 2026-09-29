/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;

/** A tool that only a test plugin knows, to exercise registration and XML round trips. */
@XmlType(name = "star")
@XmlRootElement(name = "star")
@XmlAccessorType(XmlAccessType.NONE)
public class StarGraphic extends LineGraphic {

  public StarGraphic() {
    super();
  }

  public StarGraphic(StarGraphic graphic) {
    super(graphic);
  }

  @Override
  public StarGraphic copy() {
    return new StarGraphic(this);
  }
}
