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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.Plugin;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.weasis.pref.ConfigData;

class WeasisPluginManagerTest {

  /**
   * pf4j 3.15.0 iterates its live {@code startedPlugins} list while {@code doStopPlugin()} removes
   * from it, so the stock {@code stopPlugins()} throws {@code ConcurrentModificationException}
   * after the first plugin and leaves the rest running.
   */
  @Test
  void stopPluginsStopsEveryStartedPluginInReverseOrder(@TempDir Path pluginsRoot) {
    TestPluginManager manager = new TestPluginManager(pluginsRoot);
    List<String> stopped = new ArrayList<>();
    List.of("weasis-core", "weasis-dicom-codec", "weasis-dicom-viewer3d")
        .forEach(id -> manager.register(id, stopped));

    manager.stopPlugins();

    assertEquals(List.of("weasis-dicom-viewer3d", "weasis-dicom-codec", "weasis-core"), stopped);
    assertTrue(manager.getStartedPlugins().isEmpty());
  }

  /** Exposes the protected pf4j state so started plugins can be registered without a real cache. */
  private static final class TestPluginManager extends WeasisPluginManager {

    TestPluginManager(Path pluginsRoot) {
      super(pluginsRoot);
    }

    void register(String id, List<String> stopped) {
      Plugin plugin = mock(Plugin.class);
      doAnswer(invocation -> stopped.add(id)).when(plugin).stop();
      PluginDescriptor descriptor = mock(PluginDescriptor.class);
      when(descriptor.getPluginId()).thenReturn(id);
      PluginWrapper wrapper = mock(PluginWrapper.class);
      when(wrapper.getPluginId()).thenReturn(id);
      when(wrapper.getDescriptor()).thenReturn(descriptor);
      when(wrapper.getPlugin()).thenReturn(plugin);
      when(wrapper.getPluginState()).thenReturn(PluginState.STARTED);

      plugins.put(id, wrapper);
      startedPlugins.add(wrapper);
    }
  }

  @Test
  void classpathJarNamesKeepsOnlyJarFileNames() {
    String cp =
        String.join(
            File.pathSeparator,
            "/opt/weasis/app/weasis-launcher.jar",
            "/opt/weasis/app/common-lib/weasis-core-5.0.0.jar",
            "/opt/weasis/app/common-lib/joml-1.10.9.jar",
            "/opt/weasis/app/classes",
            "relative/zip4j-2.11.5.jar");
    Set<String> names = WeasisPluginManager.classpathJarNames(cp);
    assertEquals(
        Set.of(
            "weasis-launcher.jar", "weasis-core-5.0.0.jar", "joml-1.10.9.jar", "zip4j-2.11.5.jar"),
        names);
  }

  @Test
  void classpathJarNamesHandlesEmptyClasspath() {
    assertTrue(WeasisPluginManager.classpathJarNames("").isEmpty());
  }

  @Test
  void loadCommonLibrariesPublishesDownloadedJarsOnceThroughTheCommonLibClassLoader(
      @TempDir Path cacheDir) throws IOException {
    Path libDir = Files.createDirectories(cacheDir.resolve(ConfigData.F_LIB_FOLDER));
    Path core = Files.createFile(libDir.resolve("weasis-core-5.0.0.jar"));
    Files.createFile(libDir.resolve("not-a-library.txt"));
    WeasisPluginManager manager =
        new WeasisPluginManager(cacheDir.resolve(ConfigData.F_PLUGIN_FOLDER));

    loadCommonLibraries(manager);
    loadCommonLibraries(manager);

    URLClassLoader classLoader = (URLClassLoader) manager.getCommonLibClassLoader();
    assertArrayEquals(new URL[] {core.toUri().toURL()}, classLoader.getURLs());
  }

  /** A jar shipped in the app image must keep being loaded by the application class loader. */
  @Test
  void loadCommonLibrariesSkipsJarsAlreadyOnTheApplicationClasspath(@TempDir Path cacheDir)
      throws IOException {
    Set<String> classpathJars = WeasisPluginManager.classpathJarNames();
    assumeFalse(classpathJars.isEmpty(), "No jar on the test classpath");
    Path libDir = Files.createDirectories(cacheDir.resolve(ConfigData.F_LIB_FOLDER));
    Files.createFile(libDir.resolve(classpathJars.iterator().next()));
    WeasisPluginManager manager =
        new WeasisPluginManager(cacheDir.resolve(ConfigData.F_PLUGIN_FOLDER));

    loadCommonLibraries(manager);

    assertEquals(0, ((URLClassLoader) manager.getCommonLibClassLoader()).getURLs().length);
  }

  /** Restores the context class loader, which {@code loadCommonLibraries} replaces. */
  private static void loadCommonLibraries(WeasisPluginManager manager) {
    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    try {
      manager.loadCommonLibraries();
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  /** Plugins must resolve the shared libraries before reaching the application class loader. */
  @Test
  void pluginClassLoaderIsParentedToTheCommonLibClassLoader(@TempDir Path pluginsRoot) {
    WeasisPluginManager manager = new WeasisPluginManager(pluginsRoot);
    PluginDescriptor descriptor = mock(PluginDescriptor.class);
    when(descriptor.getPluginId()).thenReturn("weasis-dicom-codec");

    ClassLoader classLoader = new WeasisPluginLoader(manager).loadPlugin(pluginsRoot, descriptor);

    assertSame(manager.getCommonLibClassLoader(), classLoader.getParent());
  }
}
