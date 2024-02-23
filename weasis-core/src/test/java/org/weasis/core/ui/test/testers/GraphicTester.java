/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.test.testers;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Point2D;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.test.utils.XmlSerialisationHelper;

/**
 * Graphic helper for general testing. Test serialization/deserialization of basic and complete
 * objects Test copy() of objects It is possible to add more test with methods <code>
 * additionalTestsFor<<b>name of the test</b>></code>
 *
 * @author ylar - Yannick LARVOR (ylarvor@smarwavesa.com)
 * @param <E> Class implementing {@link Graphic}
 * @since v2.5.0 - 2016-07-12 - Creation
 */
public abstract class GraphicTester<E extends Graphic> extends XmlSerialisationHelper {
  protected Class<E> clazz;
  protected E graphic;
  protected E deserializedGraphic;

  String serializationGraphic;

  protected List<Point2D> pts;

  public abstract String getTemplate();

  public abstract Object[] getParameters();

  public abstract String getXmlFilePathCase0();

  public abstract String getXmlFilePathCase1();

  public abstract E getExpectedDeserializeCompleteGraphic();

  @BeforeEach
  public void setUp() {
    pts = new ArrayList<>();
  }

  protected void checkSerialization(String expectedGraphic) {
    assertEquals(TPL_XML_PREFIX + expectedGraphic, serializationGraphic);
  }

  protected void checkDeserialization() throws Exception {
    deserializedGraphic = deserialize(serializationGraphic, getGraphicClass());
    assertTrue(deserializedGraphic != null || getGraphicClass().isInstance(deserializedGraphic));
    assertEquals(graphic.getUuid(), deserializedGraphic.getUuid());
    assertEquals(graphic, deserializedGraphic);
  }

  @SuppressWarnings("unchecked")
  public Class<E> getGraphicClass() {
    if (Objects.isNull(clazz)) {
      clazz =
          (Class<E>)
              ((ParameterizedType) this.getClass().getGenericSuperclass())
                  .getActualTypeArguments()[0];
    }
    return clazz;
  }

  public E createNewInstance() {
    try {
      return getGraphicClass().getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      fail("Cannot create instance"); // NON-NLS
    }
    return null;
  }

  public String getGraphicUuid() {
    return graphic.getUuid();
  }

  public E getExpectedDeserializeBasicGraphic() {
    E object = createNewInstance();
    object.setUuid(GRAPHIC_UUID_0);
    return object;
  }

  /**
   * Check object has a default constructor.
   *
   * @since v2.5.0 - ylar - Creation
   */
  @Test
  public void testBasicObject() throws Exception {
    graphic = createNewInstance();
    serializationGraphic = serialize(graphic);

    String expected = format(getTemplate(), getParameters());

    checkSerialization(expected);
    checkDeserialization();
  }

  /**
   * Check an empty object can be reconstructed from an XML correctly.
   *
   * @since v2.5.0 - ylar - Creation
   */
  @Test
  public void testDeserializeBasicGraphic() throws Exception {
    InputStream xml = getClass().getResourceAsStream(getXmlFilePathCase0());

    assertNotNull(xml);

    E result = deserialize(xml, getGraphicClass());
    E expected = getExpectedDeserializeBasicGraphic();

    assertNotNull(result);
    assertNotNull(expected);

    assertEquals(result.getUuid(), expected.getUuid());
    assertEquals(result, expected);

    checkGraphicInterfaceFields(result, expected);

    if (result instanceof DragGraphic) {
      checkDragGraphicInterface((DragGraphic) result, (DragGraphic) expected);
    }
    if (result instanceof GraphicArea) {
      checkDragGraphicAreaInterface((GraphicArea) result, (GraphicArea) expected);
    }

    // Additional tests
    additionalTestsForDeserializeBasicGraphic(result, expected);
  }

  /**
   * Check an object with points can be reconstructed from an XML correctly.
   *
   * @since v2.5.0 - ylar - Creation
   */
  @Test
  public void testCompleteGraphic() throws Exception {
    InputStream xml = getClass().getResourceAsStream(getXmlFilePathCase1());

    assertNotNull(xml);

    E result = deserialize(xml, getGraphicClass());
    E expected = getExpectedDeserializeCompleteGraphicCopy();

    assertNotNull(result);
    assertNotNull(expected);

    assertEquals(expected.getUuid(), result.getUuid());

    checkGraphicInterfaceFields(result, expected);

    if (result instanceof DragGraphic) {
      checkDragGraphicInterface((DragGraphic) result, (DragGraphic) expected);
    }
    if (result instanceof GraphicArea) {
      checkDragGraphicAreaInterface((GraphicArea) result, (GraphicArea) expected);
    }

    // Additional tests
    additionalTestsForDeserializeCompleteGraphic(result, expected);
  }

  private E getExpectedDeserializeCompleteGraphicCopy() {
    E original = getExpectedDeserializeCompleteGraphic();
    return getCopy(original);
  }

  @SuppressWarnings("unchecked")
  private E getCopy(E original) {
    E copy = (E) original.copy();
    copy.setUuid(original.getUuid());
    return copy;
  }

  /**
   * Check object copy works correctly. The copied object Must be exactly the same has the given one
   * except for the <b>UUID</b>
   *
   * @since v2.5.0 - ylar - Creation
   */
  @Test
  public void testCopy() throws Exception {
    InputStream xml0 = getClass().getResourceAsStream(getXmlFilePathCase0());
    E object0 = deserialize(xml0, getGraphicClass());
    checkGraphicInterfaceFields(object0, object0.copy());

    InputStream xml1 = getClass().getResourceAsStream(getXmlFilePathCase1());
    E object1 = deserialize(xml1, getGraphicClass());
    checkGraphicInterfaceFields(object1.copy(), object1);
  }

  protected final void checkGraphicInterfaceFields(Graphic result, Graphic expected) {
    assertEquals(expected.getPts(), result.getPts());
    assertEquals(expected.getColorPaint(), result.getColorPaint());
    assertEquals(expected.getLineThickness(), result.getLineThickness());
    assertEquals(expected.getLabelVisible(), result.getLabelVisible());
    assertEquals(expected.getFilled(), result.getFilled());
    assertEquals(expected.getLayerType(), result.getLayerType());
    assertEquals(expected.getVariablePointsNumber(), result.getVariablePointsNumber());

    // Values that are always null when creating new instance
    assertEquals(expected.getGraphicLabel(), result.getGraphicLabel());
    assertEquals(expected.getLayer(), result.getLayer());

    checkDefaultValues(result);
  }

  /** Check values that never change during serialization */
  protected void checkDefaultValues(Graphic result) {
    assertEquals(Graphic.DEFAULT_SELECTED, result.getSelected());
  }

  protected void checkDragGraphicInterface(DragGraphic result, DragGraphic expected) {
    assertEquals(DragGraphic.DEFAULT_RESIZE_OR_MOVING, result.getResizingOrMoving());
  }

  private void checkDragGraphicAreaInterface(GraphicArea result, GraphicArea expected) {}

  /**
   * Override this method in case you want to make more test on the result and expected object
   *
   * @param result Object result of the XML deserialization
   * @param expected Expected object that should be exactly the same of the result object
   */
  public void additionalTestsForDeserializeBasicGraphic(E result, E expected) {}

  public void additionalTestsForDeserializeCompleteGraphic(E result, E expected) {}
}
