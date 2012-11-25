package org.weasis.core.api.service;

import javax.imageio.spi.IIORegistry;

public final class ImageioUtil {

    public static void registerServiceProvider(IIORegistry registry, Class clazz) {
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

    public static void unRegisterServiceProvider(IIORegistry registry, Class clazz) {
        Object spi = registry.getServiceProviderByClass(clazz);
        if (spi != null) {
            registry.deregisterServiceProvider(spi);
        }
    }
}
