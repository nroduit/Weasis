/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.service;

import java.awt.Color;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WPropertiesTest {
    private static final String MAGENTA = "ff00ff"; //$NON-NLS-1$
    private static final String MAGENTA_ALPHA = "ffff00ff"; //$NON-NLS-1$
    private static final String GREY = "808080"; //$NON-NLS-1$
    private static final String GREY_ALPHA = "80808080"; //$NON-NLS-1$
    private static final Color COLOR_ALPHA = new Color(128, 128, 128, 128);

    @Before
    public void setUp() {
    }

    @Test
    public void testSetPropertyString() {
        WProperties prop = new WProperties();
        prop.setProperty("string", "test!"); //$NON-NLS-1$ //$NON-NLS-2$
        Assert.assertEquals("different string", "test!", prop.getProperty("string", null)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        prop.setProperty("string", null); //$NON-NLS-1$
        // Return the previous value
        Assert.assertEquals("different string", "test!", prop.getProperty("string", null)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testPutIntProperty() {
        WProperties prop = new WProperties();
        prop.putIntProperty("int", Integer.MIN_VALUE); //$NON-NLS-1$
        Assert.assertEquals("different int", Integer.MIN_VALUE, prop.getIntProperty("int", 0), 0); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putIntProperty("int", Integer.MAX_VALUE); //$NON-NLS-1$
        Assert.assertEquals("different int", Integer.MAX_VALUE, prop.getIntProperty("int", 0), 0); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPutLongProperty() {
        WProperties prop = new WProperties();
        prop.putLongProperty("long", Long.MIN_VALUE); //$NON-NLS-1$
        Assert.assertEquals("different long", Long.MIN_VALUE, prop.getLongProperty("long", 0L), 0L); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putLongProperty("long", Long.MAX_VALUE); //$NON-NLS-1$
        Assert.assertEquals("different long", Long.MAX_VALUE, prop.getLongProperty("long", 0L), 0L); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPutBooleanProperty() {
        WProperties prop = new WProperties();
        prop.putBooleanProperty("boolean", true); //$NON-NLS-1$
        Assert.assertEquals("different boolean", true, prop.getBooleanProperty("boolean", false)); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putBooleanProperty("boolean", false); //$NON-NLS-1$
        Assert.assertEquals("different boolean", false, prop.getBooleanProperty("boolean", true)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPutFloatProperty() {
        WProperties prop = new WProperties();
        prop.putFloatProperty("float", Float.MAX_VALUE); //$NON-NLS-1$
        Assert.assertEquals("different float", Float.MAX_VALUE, prop.getFloatProperty("float", 0.0f), 0.0f); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putFloatProperty("float", Float.NaN); //$NON-NLS-1$
        Assert.assertEquals("different float", Float.NaN, prop.getFloatProperty("float", 0.0f), 0.0f); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPutDoubleProperty() {
        WProperties prop = new WProperties();
        prop.putDoubleProperty("double", Math.PI); //$NON-NLS-1$
        Assert.assertEquals("different double", Math.PI, prop.getDoubleProperty("double", 0.0), 0.0); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putDoubleProperty("double", Double.NEGATIVE_INFINITY); //$NON-NLS-1$
        Assert.assertEquals("different double", Double.NEGATIVE_INFINITY, prop.getDoubleProperty("double", 0.0), 0.0); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putDoubleProperty("double", Double.NaN); //$NON-NLS-1$
        Assert.assertEquals("different double", Double.NaN, prop.getDoubleProperty("double", 0.0), 0.0); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPutColorProperty() {
        WProperties prop = new WProperties();
        prop.putColorProperty("color", COLOR_ALPHA); //$NON-NLS-1$
        Assert.assertEquals("different color", COLOR_ALPHA, prop.getColorProperty("color")); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putColorProperty("color", Color.GREEN); //$NON-NLS-1$
        Assert.assertEquals("different color", Color.GREEN, prop.getColorProperty("color")); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putColorProperty("color", null); //$NON-NLS-1$
        // Return the previous value
        Assert.assertEquals("different color", Color.GREEN, prop.getColorProperty("color")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testPutByteArrayProperty() {
        WProperties prop = new WProperties();
        byte[] data = new byte[] { 0, 3, 43, 32, 34, 54, 127, 0, (byte) 255 };
        prop.putByteArrayProperty("byte", data); //$NON-NLS-1$
        Assert.assertArrayEquals("different byte data", data, prop.getByteArrayProperty("byte", null)); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putByteArrayProperty("byte", null); //$NON-NLS-1$
        Assert.assertArrayEquals("different byte data", null, prop.getByteArrayProperty("byte", null)); //$NON-NLS-1$ //$NON-NLS-2$
        prop.putByteArrayProperty("byte", new byte[] {}); //$NON-NLS-1$
        Assert.assertArrayEquals("different byte data", null, prop.getByteArrayProperty("byte", null)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testColor2Hexadecimal() {
        Assert.assertEquals("magenta without alpha => " + MAGENTA, MAGENTA, //$NON-NLS-1$
            WProperties.color2Hexadecimal(Color.MAGENTA, false));
        Assert.assertEquals("magenta with alpha => " + MAGENTA_ALPHA, MAGENTA_ALPHA, //$NON-NLS-1$
            WProperties.color2Hexadecimal(Color.MAGENTA, true));
        Assert.assertEquals("grey with alpha => " + GREY_ALPHA, GREY_ALPHA, //$NON-NLS-1$
            WProperties.color2Hexadecimal(COLOR_ALPHA, true));
        Assert.assertEquals("grey withtout alpha => " + GREY, GREY, WProperties.color2Hexadecimal(COLOR_ALPHA, false)); //$NON-NLS-1$
    }

    @Test
    public void testHexadecimal2Color() {
        Assert.assertEquals(MAGENTA + " => magenta", Color.MAGENTA, WProperties.hexadecimal2Color(MAGENTA)); //$NON-NLS-1$
        Assert.assertEquals(MAGENTA_ALPHA + " => magenta", Color.MAGENTA, WProperties.hexadecimal2Color(MAGENTA_ALPHA)); //$NON-NLS-1$
        Assert.assertEquals(GREY_ALPHA + " => grey", COLOR_ALPHA, WProperties.hexadecimal2Color(GREY_ALPHA)); //$NON-NLS-1$
        Assert.assertNotEquals(GREY + " => grey", COLOR_ALPHA, WProperties.hexadecimal2Color(GREY)); //$NON-NLS-1$
        Assert.assertEquals("null => black", Color.BLACK, WProperties.hexadecimal2Color(null)); //$NON-NLS-1$
        Assert.assertEquals("sf => black", Color.BLACK, WProperties.hexadecimal2Color("sf")); //$NON-NLS-1$ //$NON-NLS-2$
    }

}
