/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.image.DataBuffer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Utility class to enable compatibility with Java 2 1.4 real-valued DataBuffer classes. Factory methods and data array
 * accessors are defined to use reflection. The core Java classes are given precedence over their JAI equivalents.
 */
public final class DataBufferUtils {

    private DataBufferUtils() {
    }

    /**
     * Priority ordered array of DataBufferFloat class names.
     */
    private static final String[] FLOAT_CLASS_NAMES = { "java.awt.image.DataBufferFloat", //$NON-NLS-1$
        "javax.media.jai.DataBufferFloat", "com.sun.media.jai.codecimpl.util.DataBufferFloat" }; //$NON-NLS-1$ //$NON-NLS-2$

    /**
     * Priority ordered array of DataBufferDouble class names.
     */
    private static final String[] DOUBLE_CLASS_NAMES = { "java.awt.image.DataBufferDouble", //$NON-NLS-1$
        "javax.media.jai.DataBufferDouble", "com.sun.media.jai.codecimpl.util.DataBufferDouble" }; //$NON-NLS-1$ //$NON-NLS-2$

    /**
     * Classes to be used for DB float and double.
     */
    private static Class floatClass = null;
    private static Class doubleClass = null;

    /**
     * Initialize float and double DB classes.
     */
    static {
        floatClass = getDataBufferClass(DataBuffer.TYPE_FLOAT);
        doubleClass = getDataBufferClass(DataBuffer.TYPE_DOUBLE);
    }

    /**
     * Return the class for the specified data type.
     *
     * @param dataType
     *            The data type from among <code>DataBuffer.TYPE_*</code>.
     */
    private static final Class getDataBufferClass(int dataType) {
        // Set the array of class names.
        String[] classNames = null;
        switch (dataType) {
            case DataBuffer.TYPE_FLOAT:
                classNames = FLOAT_CLASS_NAMES;
                break;
            case DataBuffer.TYPE_DOUBLE:
                classNames = DOUBLE_CLASS_NAMES;
                break;
            default:
                throw new IllegalArgumentException("dataType == " + dataType + "!"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Initialize the return value.
        Class dataBufferClass = null;

        // Loop over the class names array in priority order.
        for (int i = 0; i < classNames.length; i++) {
            try {
                // Attempt to get the class.
                dataBufferClass = Class.forName(classNames[i]);

                // Break if the class was found.
                if (dataBufferClass != null) {
                    break;
                }
            } catch (ClassNotFoundException e) {
                // Ignore the exception.
            }
        }

        // Throw an exception if no class was found.
        if (dataBufferClass == null) {
            throw new RuntimeException("dataBufferClass not found: " //$NON-NLS-1$
                + (dataType == DataBuffer.TYPE_FLOAT ? "DataBufferFloat" : "DataBufferDouble")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return dataBufferClass;
    }

    /**
     * Construct a <code>DataBuffer</code> of the requested type using the parameters with the specified types and
     * values.
     */
    private static final DataBuffer constructDataBuffer(int dataType, Class[] paramTypes, Object[] paramValues) {

        Class dbClass = null;
        switch (dataType) {
            case DataBuffer.TYPE_FLOAT:
                dbClass = floatClass;
                break;
            case DataBuffer.TYPE_DOUBLE:
                dbClass = doubleClass;
                break;
            default:
                throw new IllegalArgumentException("dataType == " + dataType + "!"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        DataBuffer dataBuffer = null;
        try {
            Constructor constructor = dbClass.getConstructor(paramTypes);
            dataBuffer = (DataBuffer) constructor.newInstance(paramValues);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create dataBuffer"); //$NON-NLS-1$
        }

        return dataBuffer;
    }

    /**
     * Invoke the <code>DataBuffer</code> method of the specified name using the parameters with the specified types and
     * values.
     */
    private static final Object invokeDataBufferMethod(DataBuffer dataBuffer, String methodName, Class[] paramTypes,
        Object[] paramValues) {
        if (dataBuffer == null) {
            throw new IllegalArgumentException("dataBuffer == null!"); //$NON-NLS-1$
        }

        Class dbClass = dataBuffer.getClass();

        Object returnValue = null;
        try {
            Method method = dbClass.getMethod(methodName, paramTypes);
            returnValue = method.invoke(dataBuffer, paramValues);
        } catch (Exception e) {
            throw new RuntimeException("Error when invoking" + " \"" + methodName + "\"."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        return returnValue;
    }

    public static final DataBuffer createDataBufferFloat(float[][] dataArray, int size) {
        return constructDataBuffer(DataBuffer.TYPE_FLOAT, new Class[] { float[][].class, int.class },
            new Object[] { dataArray, size });
    }

    public static final DataBuffer createDataBufferFloat(float[][] dataArray, int size, int[] offsets) {
        return constructDataBuffer(DataBuffer.TYPE_FLOAT, new Class[] { float[][].class, int.class, int[].class },
            new Object[] { dataArray, size, offsets });
    }

    public static final DataBuffer createDataBufferFloat(float[] dataArray, int size) {
        return constructDataBuffer(DataBuffer.TYPE_FLOAT, new Class[] { float[].class, int.class },
            new Object[] { dataArray, size });
    }

    public static final DataBuffer createDataBufferFloat(float[] dataArray, int size, int offset) {
        return constructDataBuffer(DataBuffer.TYPE_FLOAT, new Class[] { float[].class, int.class, int.class },
            new Object[] { dataArray, size, offset });
    }

    public static final DataBuffer createDataBufferFloat(int size) {
        return constructDataBuffer(DataBuffer.TYPE_FLOAT, new Class[] { int.class }, new Object[] { size });
    }

    public static final DataBuffer createDataBufferFloat(int size, int numBanks) {
        return constructDataBuffer(DataBuffer.TYPE_FLOAT, new Class[] { int.class, int.class },
            new Object[] { size, numBanks });
    }

    public static final float[][] getBankDataFloat(DataBuffer dataBuffer) {
        return (float[][]) invokeDataBufferMethod(dataBuffer, "getBankData", null, null); //$NON-NLS-1$
    }

    public static final float[] getDataFloat(DataBuffer dataBuffer) {
        return (float[]) invokeDataBufferMethod(dataBuffer, "getData", null, null); //$NON-NLS-1$
    }

    public static final float[] getDataFloat(DataBuffer dataBuffer, int bank) {
        return (float[]) invokeDataBufferMethod(dataBuffer, "getData", new Class[] { int.class }, //$NON-NLS-1$
            new Object[] { bank });
    }

    public static final DataBuffer createDataBufferDouble(double[][] dataArray, int size) {
        return constructDataBuffer(DataBuffer.TYPE_DOUBLE, new Class[] { double[][].class, int.class },
            new Object[] { dataArray, size });
    }

    public static final DataBuffer createDataBufferDouble(double[][] dataArray, int size, int[] offsets) {
        return constructDataBuffer(DataBuffer.TYPE_DOUBLE, new Class[] { double[][].class, int.class, int[].class },
            new Object[] { dataArray, size, offsets });
    }

    public static final DataBuffer createDataBufferDouble(double[] dataArray, int size) {
        return constructDataBuffer(DataBuffer.TYPE_DOUBLE, new Class[] { double[].class, int.class },
            new Object[] { dataArray, size });
    }

    public static final DataBuffer createDataBufferDouble(double[] dataArray, int size, int offset) {
        return constructDataBuffer(DataBuffer.TYPE_DOUBLE, new Class[] { double[].class, int.class, int.class },
            new Object[] { dataArray, size, offset });
    }

    public static final DataBuffer createDataBufferDouble(int size) {
        return constructDataBuffer(DataBuffer.TYPE_DOUBLE, new Class[] { int.class }, new Object[] { size });
    }

    public static final DataBuffer createDataBufferDouble(int size, int numBanks) {
        return constructDataBuffer(DataBuffer.TYPE_DOUBLE, new Class[] { int.class, int.class },
            new Object[] { size, numBanks });
    }

    public static final double[][] getBankDataDouble(DataBuffer dataBuffer) {
        return (double[][]) invokeDataBufferMethod(dataBuffer, "getBankData", null, null); //$NON-NLS-1$
    }

    public static final double[] getDataDouble(DataBuffer dataBuffer) {
        return (double[]) invokeDataBufferMethod(dataBuffer, "getData", null, null); //$NON-NLS-1$
    }

    public static final double[] getDataDouble(DataBuffer dataBuffer, int bank) {
        return (double[]) invokeDataBufferMethod(dataBuffer, "getData", new Class[] { int.class }, //$NON-NLS-1$
            new Object[] { bank });
    }
}
