package org.weasis.imageio.codec;

import java.lang.reflect.Field;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.spi.ServiceRegistry;

public final class ImageioUtil {

    // Hack to get the default imageio registry, workaround to ensure to be thread safe.
    private final static IIORegistry registry;
    static {
        IIORegistry temp = null;
        try {
            Field field = ImageIO.class.getDeclaredField("theRegistry"); //$NON-NLS-1$
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

    public static <T> void registerServiceProviderPriority(Class<? extends T> clazz, Class<T> category,
        String formatName) {
        try {
            T spi = clazz.newInstance();
            // Resister again the spi classes with the bundle classloader
            registry.registerServiceProvider(clazz.newInstance(), category);
            Iterator<T> list = registry.getServiceProviders(category, new ContainsFilter(formatName), true);
            while (list.hasNext()) {
                T item = list.next();
                if (item != spi) {
                    registry.setOrdering(category, spi, item);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ContainsFilter implements ServiceRegistry.Filter {

        String name;

        public ContainsFilter(String name) {
            this.name = name;
        }

        @Override
        public boolean filter(Object spi) {
            try {
                if (spi instanceof ImageReaderWriterSpi) {
                    return contains(((ImageReaderWriterSpi) spi).getFormatNames(), name);
                }
            } catch (Exception e) {
            }
            return false;
        }
    }

    private static boolean contains(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (name.equalsIgnoreCase(names[i])) {
                return true;
            }
        }
        return false;
    }

    public static void unRegisterServiceProvider(Class clazz) {
        Object spi = registry.getServiceProviderByClass(clazz);
        if (spi != null) {
            registry.deregisterServiceProvider(spi);
        }
    }

    public static void registerServiceProvider(IIOServiceProvider serviceProvider) {
        try {
            registry.registerServiceProvider(serviceProvider);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deregisterServiceProvider(IIOServiceProvider serviceProvider) {
        if (serviceProvider != null) {
            registry.deregisterServiceProvider(serviceProvider);
        }
    }
}
