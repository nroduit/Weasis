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
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Point2D;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.AbstractGraphic;
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
    assertThat(serializationGraphic).isEqualTo(TPL_XML_PREFIX + expectedGraphic);
  }

  protected void checkDeserialization() throws Exception {
    deserializedGraphic = deserialize(serializationGraphic, getGraphicClass());
    assertThat(deserializedGraphic)
        .isInstanceOfAny(Graphic.class, AbstractGraphic.class, getGraphicClass());
    assertThat(deserializedGraphic.getUuid()).isEqualTo(graphic.getUuid());
    assertThat(deserializedGraphic).usingRecursiveComparison().isEqualTo(graphic);
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
      Fail.fail("Cannot create instance");
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

    assertThat(xml).isNotNull();

    E result = deserialize(xml, getGraphicClass());
    E expected = getExpectedDeserializeBasicGraphic();

    assertThat(result).isNotNull();
    assertThat(expected).isNotNull();

    assertThat(result.getUuid()).isNotEmpty().isEqualTo(expected.getUuid());
    assertThat(result).usingRecursiveComparison().isEqualTo(expected);

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

    assertThat(xml).isNotNull();

    E result = deserialize(xml, getGraphicClass());
    E expected = getExpectedDeserializeCompleteGraphicCopy();

    assertThat(result).isNotNull();
    assertThat(expected).isNotNull();

    assertThat(result.getUuid()).isNotEmpty().isEqualTo(expected.getUuid());

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
    E copy0 = (E) object0.copy();

    testCopy(object0, copy0);

    InputStream xml1 = getClass().getResourceAsStream(getXmlFilePathCase1());

    E object1 = deserialize(xml1, getGraphicClass());
    E copy1 = (E) object1.copy();

    testCopy(copy1, object1);
  }

  protected void testCopy(E actual, E expected) throws Exception {
    checkGraphicInterfaceFields(actual, expected);
  }

  protected final void checkGraphicInterfaceFields(Graphic result, Graphic expected) {
    assertThat(result.getPts()).isEqualTo(expected.getPts());
    assertThat(result.getColorPaint()).isNotNull().isEqualTo(expected.getColorPaint());
    assertThat(result.getLineThickness()).isNotNull().isEqualTo(expected.getLineThickness());
    assertThat(result.getLabelVisible()).isNotNull().isEqualTo(expected.getLabelVisible());
    assertThat(result.getFilled()).isNotNull().isEqualTo(expected.getFilled());
    assertThat(result.getLayerType()).isNotNull().isEqualTo(expected.getLayerType());
    assertThat(result.getVariablePointsNumber())
        .isNotNull()
        .isEqualTo(expected.getVariablePointsNumber());

    // Values that are always null when creating new instance
    assertThat(result.getGraphicLabel()).isEqualTo(expected.getGraphicLabel());
    assertThat(result.getLayer()).isEqualTo(expected.getLayer());

    checkDefaultValues(result);
  }

  /** Check values that never change during serialization */
  protected void checkDefaultValues(Graphic result) {
    assertThat(result.getSelected()).isNotNull().isEqualTo(Graphic.DEFAULT_SELECTED);
  }

  protected void checkDragGraphicInterface(DragGraphic result, DragGraphic expected) {
    assertThat(result.getResizingOrMoving())
        .isNotNull()
        .isEqualTo(DragGraphic.DEFAULT_RESIZE_OR_MOVING);
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
