/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.imp.suite;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.core.ui.test.utils.ModelListHelper;

class ConstructorNoArgumentsTest extends ModelListHelper {

  @Test
  void testXmlModelList() {
    GraphicModel actual = new XmlGraphicModel();

    assertInstanceOf(DefaultUUID.class, actual);
    assertInstanceOf(GraphicModel.class, actual);
    assertInstanceOf(AbstractGraphicModel.class, actual);
    assertInstanceOf(XmlGraphicModel.class, actual);

    assertTrue(actual.getReferencedSeries().isEmpty());
    assertTrue(actual.getLayers().isEmpty());
    assertTrue(actual.getModels().isEmpty());
    assertTrue(actual.getAllGraphics().isEmpty());
    assertTrue(actual.groupLayerByType().isEmpty());
    assertTrue(actual.getSelectedDraggableGraphics().isEmpty());
    assertTrue(actual.getSelectedGraphics().isEmpty());
    assertTrue(actual.getGraphicSelectionListeners().isEmpty());

    assertEquals(0, actual.getLayerCount());
  }
}
