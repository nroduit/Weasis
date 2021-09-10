/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.serialize;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.awt.Color;
import java.awt.Paint;
import org.weasis.core.api.service.WProperties;

public class ColorModelAdapter {

  private ColorModelAdapter() {}

  static class ColorModel {
    @XmlAttribute(required = true)
    String rgb;
  }

  public static class ColorAdapter extends XmlAdapter<ColorModel, Color> {

    @Override
    public ColorModel marshal(Color color) throws Exception {
      ColorModel m = new ColorModel();
      m.rgb = WProperties.color2Hexadecimal(color, true);
      return m;
    }

    @Override
    public Color unmarshal(ColorModel c) throws Exception {
      return WProperties.hexadecimal2Color(c.rgb);
    }
  }

  public static class PaintAdapter extends XmlAdapter<ColorModel, Paint> {

    @Override
    public ColorModel marshal(Paint color) throws Exception {
      ColorModel m = new ColorModel();
      if (color instanceof Color) {
        m.rgb = WProperties.color2Hexadecimal((Color) color, true);
      }
      return m;
    }

    @Override
    public Color unmarshal(ColorModel c) throws Exception {
      return WProperties.hexadecimal2Color(c.rgb);
    }
  }
}
