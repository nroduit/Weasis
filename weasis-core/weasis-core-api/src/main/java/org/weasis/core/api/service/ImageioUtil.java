package org.weasis.core.api.service;

import java.lang.reflect.Field;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;

public final class ImageioUtil {

    // Hack to get the default imageio registry, workaround to ensure to be thread safe.
    public final static IIORegistry registry;
    static {
        IIORegistry temp = null;
        try {
            Field field = ImageIO.class.getDeclaredField("theRegistry");
            field.setAccessible(true);
            temp = (IIORegistry) field.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (temp == null) {
                temp = IIORegistry.getDefaultInstance();
            }
            registry = temp;
        }
    }

    public static void registerServiceProvider(Class clazz) {
        Class spiClass = null;
        try {
            // If JRE contains imageio.jar in lib/ext, get spi classes and unregister them
            spiClass = Class.forName(clazz.getName(), true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
        }
        if (spiClass != null) {
            Object spi = registry.getServiceProviderByClass(spiClass);
            if (spi != null) {
                registry.deregisterServiceProvider(spi);
            }
        }
        try {
            // Resister again the spi classes with the bundle classloader
            registry.registerServiceProvider(clazz.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unRegisterServiceProvider(Class clazz) {
        Object spi = registry.getServiceProviderByClass(clazz);
        if (spi != null) {
            registry.deregisterServiceProvider(spi);
        }
    }
}
