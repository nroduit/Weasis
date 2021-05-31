/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.imageio.codec;

import java.lang.reflect.Field;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.spi.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class ImageioUtil.
 *
 * @author Nicolas Roduit
 */
public final class ImageioUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageioUtil.class);
  // Hack to get the default imageio registry. Otherwise the method getDefaultInstance() is not
  // thread safe (that will
  // duplicate the registry).
  private static final IIORegistry registry;

  static {
    IIORegistry temp = null;
    try {
      Field field = ImageIO.class.getDeclaredField("theRegistry");
      field.setAccessible(true);
      temp = (IIORegistry) field.get(null);
    } catch (Exception e) {
      LOGGER.error("Cannot get IIORegistry}", e);
    } finally {
      if (temp == null) {
        temp = IIORegistry.getDefaultInstance();
      }
      registry = temp;
    }
  }

  public static void registerServiceProvider(Class<?> clazz) {
    Class<?> spiClass = null;
    try {
      // If JRE contains imageio.jar in lib/ext, get SPI classes and unregister them (avoid to
      // duplicate the
      // services)
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
      // Resister again the SPI class
      registry.registerServiceProvider(clazz.newInstance());
    } catch (Exception e) {
      LOGGER.error("Cannot register service {}", clazz.getName(), e);
    }
  }

  public static <T> void registerServiceProviderInHighestPriority(
      Class<? extends T> clazz, Class<T> category, String formatName) {
    try {
      T spi = clazz.newInstance();
      // Resister again the SPI classes with the bundle classloader
      registry.registerServiceProvider(spi, category);
      Iterator<T> list =
          registry.getServiceProviders(category, new ContainsFilter(formatName), true);
      while (list.hasNext()) {
        T item = list.next();
        if (item != spi) {
          registry.setOrdering(category, spi, item);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Cannot register service {}", clazz.getName(), e);
    }
  }

  public static <T> void registerServiceProviderInLowestPriority(
      Class<? extends T> clazz, Class<T> category, String formatName) {
    try {
      T spi = clazz.newInstance();
      // Resister again the SPI classes with the bundle classloader
      registry.registerServiceProvider(spi, category);
      Iterator<T> list =
          registry.getServiceProviders(category, new ContainsFilter(formatName), true);
      while (list.hasNext()) {
        T item = list.next();
        if (item != spi) {
          registry.setOrdering(category, item, spi);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Cannot register service {}", clazz.getName(), e);
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

  public static void unRegisterServiceProvider(Class<?> clazz) {
    Object spi = registry.getServiceProviderByClass(clazz);
    if (spi != null) {
      registry.deregisterServiceProvider(spi);
    }
  }

  public static void registerServiceProvider(IIOServiceProvider serviceProvider) {
    try {
      registry.registerServiceProvider(serviceProvider);
    } catch (Exception e) {
      LOGGER.error("Cannot register IIOServiceProvider", e);
    }
  }

  public static void deregisterServiceProvider(IIOServiceProvider serviceProvider) {
    if (serviceProvider != null) {
      registry.deregisterServiceProvider(serviceProvider);
    }
  }
}
