/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.List;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class CardiothoracicRatioGraphicTest extends GraphicTester<CardiothoracicRatioGraphic> {
  private static final String XML_0 = "/graphic/ctr/ctr.graphic.0.xml"; // NON-NLS
  private static final String XML_1 = "/graphic/ctr/ctr.graphic.1.xml"; // NON-NLS

  public static final String BASIC_TPL =
      "<ctr fillOpacity=\"%s\" fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" // NON-NLS
          + "<paint rgb=\"%s\"/>" // NON-NLS
          + "<pts/>" // NON-NLS
          + "</ctr>"; // NON-NLS

  public static final CardiothoracicRatioGraphic COMPLETE_OBJECT = new CardiothoracicRatioGraphic();

  static {
    COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
    COMPLETE_OBJECT.setColorPaint(Color.BLACK);
    COMPLETE_OBJECT.setPts(
        List.of(
            new Point2D.Double(80.0, 100.0),
            new Point2D.Double(180.0, 130.0),
            new Point2D.Double(20.0, 160.0),
            new Point2D.Double(220.0, 150.0),
            new Point2D.Double(120.0, 70.0),
            new Point2D.Double(120.0, 190.0)));
  }

  @Override
  public String getTemplate() {
    return BASIC_TPL;
  }

  @Override
  public Object[] getParameters() {
    return new Object[] {
      Graphic.DEFAULT_FILL_OPACITY,
      Graphic.DEFAULT_FILLED,
      Graphic.DEFAULT_LABEL_VISIBLE,
      Graphic.DEFAULT_LINE_THICKNESS,
      getGraphicUuid(),
      WProperties.color2Hexadecimal(Graphic.DEFAULT_COLOR, true)
    };
  }

  @Override
  public String getXmlFilePathCase0() {
    return XML_0;
  }

  @Override
  public String getXmlFilePathCase1() {
    return XML_1;
  }

  @Override
  public CardiothoracicRatioGraphic getExpectedDeserializeCompleteGraphic() {
    return COMPLETE_OBJECT;
  }
}
