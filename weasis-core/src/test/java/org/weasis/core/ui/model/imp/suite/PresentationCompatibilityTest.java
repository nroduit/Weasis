/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.imp.suite;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.GraphicToolDescriptor;
import org.weasis.core.ui.model.graphic.GraphicToolProvider;
import org.weasis.core.ui.model.graphic.StarGraphic;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;

/** The presentation XML must stay readable across versions and across installed plugins. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PresentationCompatibilityTest {

  private static final String LAYER = "layer-1"; // NON-NLS

  private static String presentation(String versionAttribute, String graphics) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" // NON-NLS
        + "<presentation"
        + versionAttribute
        + " uuid=\"p-1\"><references/><layers>" // NON-NLS
        + "<layer level=\"60\" locked=\"false\" selectable=\"true\" type=\"MEASURE\" visible=\"true\" uuid=\"" // NON-NLS
        + LAYER
        + "\"/></layers><graphics>" // NON-NLS
        + graphics
        + "</graphics></presentation>"; // NON-NLS
  }

  private static String graphic(String element, String uuid) {
    return "<"
        + element
        + " fillOpacity=\"1.0\" fill=\"false\" showLabel=\"true\" thickness=\"1.0\" uuid=\"" // NON-NLS
        + uuid
        + "\"><paint rgb=\"ffffff00\"/><layer>" // NON-NLS
        + LAYER
        + "</layer><pts><pt x=\"0.0\" y=\"0.0\"/><pt x=\"30.0\" y=\"40.0\"/></pts></" // NON-NLS
        + element
        + ">";
  }

  private static GraphicModel read(String xml) throws Exception {
    return XmlSerializer.readPresentationModel(new StringReader(xml));
  }

  @Test
  void file_without_version_is_read_as_legacy_and_written_with_the_current_version()
      throws Exception {
    GraphicModel model = read(presentation("", graphic("line", "g-1")));
    assertAll(
        () ->
            assertEquals(
                AbstractGraphicModel.LEGACY_VERSION, ((AbstractGraphicModel) model).getVersion()),
        () -> assertEquals(1, model.getModels().size()),
        () -> assertInstanceOf(LineGraphic.class, model.getModels().getFirst()),
        () ->
            assertEquals(
                30.0, model.getModels().getFirst().getShape().getBounds2D().getWidth(), 1e-6));
    String written = XmlSerializer.toXml(model);
    assertTrue(
        written.contains("<presentation version=\"" + AbstractGraphicModel.CURRENT_VERSION + "\""));
  }

  @Test
  void new_model_declares_the_current_version() throws Exception {
    XmlGraphicModel model = new XmlGraphicModel();
    assertEquals(AbstractGraphicModel.CURRENT_VERSION, model.getVersion());
    assertTrue(XmlSerializer.toXml(model).contains("version=\"2.7\""));
  }

  @Test
  void unknown_tool_element_is_preserved_and_written_back() throws Exception {
    String xml = presentation(" version=\"9.0\"", graphic("line", "g-1") + graphic("star", "g-2"));
    GraphicModel model = read(xml);
    AbstractGraphicModel abstractModel = (AbstractGraphicModel) model;
    assertAll(
        () -> assertEquals("9.0", abstractModel.getVersion()),
        () -> assertEquals(1, model.getModels().size()),
        () -> assertEquals(1, abstractModel.getUnknownGraphics().size()),
        () -> assertEquals("star", abstractModel.getUnknownGraphics().getFirst().getTagName()));

    String written = XmlSerializer.toXml(model);
    assertAll(
        () -> assertTrue(written.contains("<star ")),
        () -> assertTrue(written.contains("uuid=\"g-2\"")),
        () -> assertTrue(written.contains("<layer>" + LAYER + "</layer><pts>")),
        () -> assertTrue(written.indexOf("<line ") < written.indexOf("<star ")));
    GraphicModel again = read(written);
    assertEquals(1, ((AbstractGraphicModel) again).getUnknownGraphics().size());
  }

  @Test
  void registered_plugin_tool_round_trips_as_a_typed_graphic() throws Exception {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    GraphicToolProvider provider =
        () ->
            List.of(GraphicToolDescriptor.of("test.star", ToolCategory.ADVANCED, StarGraphic::new));
    registry.register(provider);
    try {
      GraphicModel model = read(presentation("", graphic("star", "g-2")));
      assertAll(
          () -> assertEquals(1, model.getModels().size()),
          () -> assertInstanceOf(StarGraphic.class, model.getModels().getFirst()),
          () -> assertTrue(((AbstractGraphicModel) model).getUnknownGraphics().isEmpty()));

      XmlGraphicModel fresh = new XmlGraphicModel();
      Graphic star = new StarGraphic();
      star.buildGraphic(model.getModels().getFirst().getPts());
      fresh.addGraphic(star);
      String written = XmlSerializer.toXml(fresh);
      assertTrue(written.contains("<star "));
      assertFalse(written.contains("<line "));
    } finally {
      registry.unregister(provider);
    }
  }
}
