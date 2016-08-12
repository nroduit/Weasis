/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ${user} - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.weasis.core.api.gui.util.AppProperties;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AppProperties.class)
public class WPropertiesTest {
    private static final String MAGENTA = "ff00ff";
    private static final String MAGENTA_ALPHA = "ffff00ff";
    private static final String GREY = "808080";
    private static final String GREY_ALPHA = "80808080";
    private static final Color COLOR_ALPHA = new Color(128, 128, 128, 128);

    @Mock
    BundleContext context;
    
    @Before
    public void setUp() {
        PowerMockito.mockStatic(AppProperties.class);
        PowerMockito.when(AppProperties.getBundleContext(Mockito.any())).thenReturn(context);
    }
    
    @Test
    public void testContext() {
        WProperties prop = new WProperties();
        
        PowerMockito.when(context.getProperty(Mockito.eq("unknown2"))).thenReturn("test1");
        
        String result = prop.getProperty("unknown");
        assertThat(result).isNull();
        
        String result2 = prop.getProperty("unknown2");
        assertThat(result2).isEqualTo("test1");
    }
    
    @Test
    public void testSetPropertyString() {
        WProperties prop = new WProperties();
        prop.setProperty("string", "test!");
        Assert.assertEquals("different string", "test!", prop.getProperty("string", null));
        prop.setProperty("string", null);
        // Return the previous value
        Assert.assertEquals("different string", "test!", prop.getProperty("string", null));
    }

    @Test
    public void testPutIntProperty() {
        WProperties prop = new WProperties();
        prop.putIntProperty("int", Integer.MIN_VALUE);
        Assert.assertEquals("different int", Integer.MIN_VALUE, prop.getIntProperty("int", 0), 0);
        prop.putIntProperty("int", Integer.MAX_VALUE);
        Assert.assertEquals("different int", Integer.MAX_VALUE, prop.getIntProperty("int", 0), 0);
    }

    @Test
    public void testPutLongProperty() {
        WProperties prop = new WProperties();
        prop.putLongProperty("long", Long.MIN_VALUE);
        Assert.assertEquals("different long", Long.MIN_VALUE, prop.getLongProperty("long", 0L), 0L);
        prop.putLongProperty("long", Long.MAX_VALUE);
        Assert.assertEquals("different long", Long.MAX_VALUE, prop.getLongProperty("long", 0L), 0L);
    }

    @Test
    public void testPutBooleanProperty() {
        WProperties prop = new WProperties();
        prop.putBooleanProperty("boolean", true);
        Assert.assertEquals("different boolean", true, prop.getBooleanProperty("boolean", false));
        prop.putBooleanProperty("boolean", false);
        Assert.assertEquals("different boolean", false, prop.getBooleanProperty("boolean", true));
    }

    @Test
    public void testPutFloatProperty() {
        WProperties prop = new WProperties();
        prop.putFloatProperty("float", Float.MAX_VALUE);
        Assert.assertEquals("different float", Float.MAX_VALUE, prop.getFloatProperty("float", 0.0f), 0.0f);
        prop.putFloatProperty("float", Float.NaN);
        Assert.assertEquals("different float", Float.NaN, prop.getFloatProperty("float", 0.0f), 0.0f);
    }

    @Test
    public void testPutDoubleProperty() {
        WProperties prop = new WProperties();
        prop.putDoubleProperty("double", Math.PI);
        Assert.assertEquals("different double", Math.PI, prop.getDoubleProperty("double", 0.0), 0.0);
        prop.putDoubleProperty("double", Double.NEGATIVE_INFINITY);
        Assert.assertEquals("different double", Double.NEGATIVE_INFINITY, prop.getDoubleProperty("double", 0.0), 0.0);
        prop.putDoubleProperty("double", Double.NaN);
        Assert.assertEquals("different double", Double.NaN, prop.getDoubleProperty("double", 0.0), 0.0);
    }

    @Test
    public void testPutColorProperty() {
        WProperties prop = new WProperties();
        prop.putColorProperty("color", COLOR_ALPHA);
        Assert.assertEquals("different color", COLOR_ALPHA, prop.getColorProperty("color"));
        prop.putColorProperty("color", Color.GREEN);
        Assert.assertEquals("different color", Color.GREEN, prop.getColorProperty("color"));
        prop.putColorProperty("color", null);
        // Return the previous value
        Assert.assertEquals("different color", Color.GREEN, prop.getColorProperty("color"));
    }

    @Test
    public void testPutByteArrayProperty() {
        WProperties prop = new WProperties();
        byte[] data = new byte[] { 0, 3, 43, 32, 34, 54, 127, 0, (byte) 255 };
        prop.putByteArrayProperty("byte", data);
        Assert.assertArrayEquals("different byte data", data, prop.getByteArrayProperty("byte", null));
        prop.putByteArrayProperty("byte", null);
        // Return the previous value
        Assert.assertArrayEquals("different byte data", data, prop.getByteArrayProperty("byte", null));
        prop.putByteArrayProperty("byte", new byte[] {});
        Assert.assertArrayEquals("different byte data", new byte[] {}, prop.getByteArrayProperty("byte", null));
    }

    @Test
    public void testColor2Hexadecimal() {
        Assert.assertEquals("magenta without alpha => " + MAGENTA, MAGENTA,
            WProperties.color2Hexadecimal(Color.MAGENTA, false));
        Assert.assertEquals("magenta with alpha => " + MAGENTA_ALPHA, MAGENTA_ALPHA,
            WProperties.color2Hexadecimal(Color.MAGENTA, true));
        Assert.assertEquals("grey with alpha => " + GREY_ALPHA, GREY_ALPHA,
            WProperties.color2Hexadecimal(COLOR_ALPHA, true));
        Assert.assertEquals("grey withtout alpha => " + GREY, GREY, WProperties.color2Hexadecimal(COLOR_ALPHA, false));
    }

    @Test
    public void testHexadecimal2Color() {
        Assert.assertEquals(MAGENTA + " => magenta", Color.MAGENTA, WProperties.hexadecimal2Color(MAGENTA));
        Assert.assertEquals(MAGENTA_ALPHA + " => magenta", Color.MAGENTA, WProperties.hexadecimal2Color(MAGENTA_ALPHA));
        Assert.assertEquals(GREY_ALPHA + " => grey", COLOR_ALPHA, WProperties.hexadecimal2Color(GREY_ALPHA));
        Assert.assertNotEquals(GREY + " => grey", COLOR_ALPHA, WProperties.hexadecimal2Color(GREY));
        Assert.assertEquals("null => black", Color.BLACK, WProperties.hexadecimal2Color(null));
        Assert.assertEquals("sf => black", Color.BLACK, WProperties.hexadecimal2Color("sf"));
    }

}
