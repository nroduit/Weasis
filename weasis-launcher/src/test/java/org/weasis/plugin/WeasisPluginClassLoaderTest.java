/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;

class WeasisPluginClassLoaderTest {

  private static final String CLASS_NAME = PluginContext.class.getName();
  private static final String CLASS_RESOURCE = CLASS_NAME.replace('.', '/') + ".class";

  /**
   * The whole point of the class: a non-parallel-capable loader locks on itself, which serializes
   * every class load of a plugin and deadlocks when two threads cross while delegating to each
   * other's dependencies.
   */
  @Test
  void isParallelCapableSoLocksArePerClassName() {
    WeasisPluginClassLoader loader = loader("viewer", List.of(), mock(PluginManager.class));

    Object first = loader.classLoadingLock("org.weasis.A");
    Object second = loader.classLoadingLock("org.weasis.B");

    assertNotSame(loader, first, "a parallel-capable loader must not lock on itself");
    assertNotSame(first, second);
    assertSame(first, loader.classLoadingLock("org.weasis.A"));
  }

  @Test
  void loadsPluginClassesBeforeApplicationClasses(@TempDir Path dir) throws Exception {
    WeasisPluginClassLoader loader = loader("viewer", List.of(), mock(PluginManager.class));
    loader.addFile(jarWithPluginContext(dir).toFile());

    Class<?> loaded = loader.loadClass(CLASS_NAME);

    assertSame(loader, loaded.getClassLoader());
    assertNotSame(PluginContext.class, loaded);
    loader.close();
  }

  @Test
  void loadsClassesFromDependencies(@TempDir Path dir) throws Exception {
    PluginManager pluginManager = mock(PluginManager.class);
    WeasisPluginClassLoader codec = loader("codec", List.of(), pluginManager);
    codec.addFile(jarWithPluginContext(dir).toFile());
    when(pluginManager.getPluginClassLoader("codec")).thenReturn(codec);

    WeasisPluginClassLoader viewer =
        loader("viewer", List.of(new PluginDependency("codec")), pluginManager);

    assertSame(codec, viewer.loadClass(CLASS_NAME).getClassLoader());
    assertEquals(CLASS_RESOURCE, resourceName(viewer.getResource(CLASS_RESOURCE)));
    viewer.close();
    codec.close();
  }

  /** pf4j dereferences the dependency loader unless the dependency is declared optional. */
  @Test
  void unresolvedDependencyDoesNotThrowNullPointerException() {
    PluginManager pluginManager = mock(PluginManager.class);
    WeasisPluginClassLoader viewer =
        loader("viewer", List.of(new PluginDependency("missing")), pluginManager);

    assertThrows(ClassNotFoundException.class, () -> viewer.loadClass("org.weasis.Absent"));
  }

  private static WeasisPluginClassLoader loader(
      String pluginId, List<PluginDependency> dependencies, PluginManager pluginManager) {
    PluginDescriptor descriptor = mock(PluginDescriptor.class);
    when(descriptor.getPluginId()).thenReturn(pluginId);
    when(descriptor.getDependencies()).thenReturn(dependencies);
    return new WeasisPluginClassLoader(
        pluginManager, descriptor, WeasisPluginClassLoaderTest.class.getClassLoader());
  }

  /** Builds a plugin jar holding a second copy of an existing application class. */
  private static Path jarWithPluginContext(Path dir) throws IOException {
    Path jar = dir.resolve("plugin.jar");
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out);
        InputStream bytecode =
            WeasisPluginClassLoaderTest.class.getResourceAsStream("/" + CLASS_RESOURCE)) {
      jarOut.putNextEntry(new JarEntry(CLASS_RESOURCE));
      bytecode.transferTo(jarOut);
      jarOut.closeEntry();
    }
    return jar;
  }

  private static String resourceName(java.net.URL url) {
    String path = url.toString();
    return path.substring(path.lastIndexOf('!') + 2);
  }
}
