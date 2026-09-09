/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.formdev.flatlaf.util.SystemInfo;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.awt.Component;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.management.ObjectName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.launcher.WeasisMainFrame;
import org.weasis.pref.ConfigData;

class LauncherTest {
  @Mock private ImageViewerEventManager<?> eventManagerMock;
  private static final WeasisMainFrame mainFrame = new WeasisMainFrame();

  private AutoCloseable closeable;

  @BeforeAll
  static void setUpAll() throws Exception {
    System.setProperty("weasis.resources.path", "src/test/resources"); // NON-NLS
    System.setProperty("weasis.pref.dir", "target/test-classes"); // NON-NLS
    // ConfigData otherwise looks for conf/base.json next to the weasis-launcher code source, which
    // only exists when that module comes from the reactor and not from the local repository.
    System.setProperty(ConfigData.CONFIG_PROPERTIES_PROP, testConfigUri());

    mainFrame.setConfigData(new ConfigData(null));
    ObjectName objectName2 = new ObjectName("weasis:name=MainWindow"); // NON-NLS
    ManagementFactory.getPlatformMBeanServer().registerMBean(mainFrame, objectName2);
  }

  private static String testConfigUri() {
    var config = Path.of("target", "test-classes", "conf", "base.json");
    assertTrue(Files.isReadable(config), () -> "Missing test configuration: " + config);
    return config.toUri().toString();
  }

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    when(eventManagerMock.resolvePlaceholders(anyString()))
        .thenAnswer(
            (Answer<String>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  return args[0].toString();
                });
  }

  @AfterEach
  void tearDown() throws Exception {
    closeable.close();
  }

  @Test
  void launcherWithValidUriConfigurationShouldLaunchSuccessfully() {
    Launcher launcher = new Launcher();
    launcher.setConfiguration(new Launcher.URIConfiguration());
    ((Launcher.URIConfiguration) launcher.getConfiguration()).setUri("http://example.com");
    launcher.setName("Test Launcher"); // NON-NLS
    launcher.setEnable(true);

    try (MockedStatic<GuiUtils> mockedStatic = Mockito.mockStatic(GuiUtils.class)) {
      mockedStatic
          .when(() -> GuiUtils.openInDefaultBrowser(any(Component.class), any(URI.class)))
          .then(_ -> null);
      mockedStatic.when(GuiUtils::getUICore).thenReturn(UICore.getInstance());
      launcher.execute(eventManagerMock);
    }
    // Verify that the launch method was called. This assumes that the launch method's effects are
    // observable through the mock.
    verify(eventManagerMock, atLeastOnce()).dicomExportAction(any(Launcher.class));
  }

  @Test
  void launcherWithInvalidUriShouldNotLaunch() {
    Launcher launcher = new Launcher();
    launcher.setConfiguration(new Launcher.URIConfiguration());
    ((Launcher.URIConfiguration) launcher.getConfiguration())
        .setUri("http://example.com/invalid uri"); // NON-NLS
    launcher.setName("Invalid URI Launcher"); // NON-NLS
    launcher.setEnable(true);

    launcher.execute(eventManagerMock);
    // Assuming the invalid URI prevents launching, verify no interaction
    verify(eventManagerMock, never()).dicomExportAction(any(Launcher.class));
  }

  @Test
  void applicationLauncherWithCompatibleSystemShouldLaunch() {
    Launcher.ApplicationConfiguration configurationMock =
        mock(Launcher.ApplicationConfiguration.class);
    when(configurationMock.isValid()).thenReturn(true);

    Launcher launcher = new Launcher();
    launcher.setConfiguration(configurationMock);
    ((Launcher.ApplicationConfiguration) launcher.getConfiguration())
        .setBinaryPath("/path/to/application"); // NON-NLS
    launcher.setName("Compatible Application Launcher"); // NON-NLS
    launcher.setEnable(true);

    launcher.execute(eventManagerMock);
    verify(eventManagerMock, atLeastOnce()).dicomExportAction(any(Launcher.class));
    verify(configurationMock).launch(eventManagerMock);
  }

  @Test
  void applicationLauncherWithIncompatibleSystemShouldNotLaunch() {
    Launcher launcher = new Launcher();
    Launcher.ApplicationConfiguration config = new Launcher.ApplicationConfiguration();

    if (SystemInfo.isWindows) {
      config.setCompatibility(Launcher.Compatibility.LINUX);
    } else {
      config.setCompatibility(Launcher.Compatibility.WINDOWS);
    }
    config.setBinaryPath("/path/to/application"); // NON-NLS
    launcher.setConfiguration(config);
    launcher.setName("Incompatible Application Launcher"); // NON-NLS
    launcher.setEnable(true);

    launcher.execute(eventManagerMock);
    verifyNoInteractions(eventManagerMock);
  }

  @Test
  void disabledLauncherShouldNotExecute() {
    Launcher launcher = new Launcher();
    launcher.setConfiguration(new Launcher.URIConfiguration());
    ((Launcher.URIConfiguration) launcher.getConfiguration()).setUri("http://example.com");
    launcher.setName("Disabled Launcher"); // NON-NLS
    launcher.setEnable(false);

    launcher.execute(eventManagerMock);
    // Verify that the execute method does nothing when the launcher is disabled.
    verifyNoInteractions(eventManagerMock);
  }

  @Test
  void applicationConfigurationSurvivesAJsonRoundTrip() {
    Launcher launcher = new Launcher();
    launcher.setName("Horos");
    launcher.setIconPath("/home/user/horos.svg"); // NON-NLS
    launcher.setEnable(true);
    launcher.setButton(true);
    Launcher.ApplicationConfiguration config = new Launcher.ApplicationConfiguration();
    config.setBinaryPath("open -b org.horosproject.horos"); // NON-NLS
    config.setParameters(List.of("--args {dicom:wado.folder}")); // NON-NLS
    config.setWorkingDirectory("/tmp"); // NON-NLS
    config.setEnvironmentVariables(Map.of("LANG", "en_US.UTF-8")); // NON-NLS
    config.setCompatibility(Launcher.Compatibility.MAC);
    launcher.setConfiguration(config);

    Launcher copy = Launcher.fromJson(launcher.toJson());
    assertEquals("Horos", copy.getName());
    assertEquals("/home/user/horos.svg", copy.getIconPath());
    assertTrue(copy.isEnable());
    assertTrue(copy.isButton());
    var copiedConfig =
        assertInstanceOf(Launcher.ApplicationConfiguration.class, copy.getConfiguration());
    assertEquals("open -b org.horosproject.horos", copiedConfig.getBinaryPath());
    assertEquals(List.of("--args {dicom:wado.folder}"), copiedConfig.getParameters());
    assertEquals("/tmp", copiedConfig.getWorkingDirectory());
    assertEquals(Map.of("LANG", "en_US.UTF-8"), copiedConfig.getEnvironmentVariables());
    assertEquals(Launcher.Compatibility.MAC, copiedConfig.getCompatibility());
  }

  @Test
  void uriConfigurationSurvivesAJsonRoundTrip() {
    Launcher launcher = new Launcher();
    launcher.setName("Web");
    Launcher.URIConfiguration config = new Launcher.URIConfiguration();
    config.setUri("http://example.com");
    launcher.setConfiguration(config);

    Launcher copy = Launcher.fromJson(launcher.toJson());
    var copiedConfig = assertInstanceOf(Launcher.URIConfiguration.class, copy.getConfiguration());
    assertEquals("http://example.com", copiedConfig.getUri());
    assertNull(copy.getIconPath());
  }

  /** Files written by 4.7 repeat launchType and may localize the second occurrence. */
  @Test
  void legacyFileWithDuplicatedAndLocalizedLaunchTypeIsRead() {
    Launcher launcher =
        Launcher.fromJson(
            parse(
                """
                {"name":"flatpak","iconPath":"","enable":false,"button":true,"local":true,
                 "configuration":{"launchType":"Application","launchType":"Anwendung",
                 "binaryPath":"flatpak run io.github.nroduit.Weasis","parameters":[""],
                 "workingDirectory":"","environmentVariables":{},"compatibility":"LINUX"}}"""));

    assertEquals("flatpak", launcher.getName());
    assertTrue(launcher.isButton());
    var config =
        assertInstanceOf(Launcher.ApplicationConfiguration.class, launcher.getConfiguration());
    assertEquals("flatpak run io.github.nroduit.Weasis", config.getBinaryPath());
    assertEquals(Launcher.Compatibility.LINUX, config.getCompatibility());
    assertTrue(config.getEnvironmentVariables().isEmpty());
  }

  @Test
  void configurationWithoutLaunchTypeIsResolvedFromItsMembers() {
    var uri =
        Launcher.fromJson(
            parse(
                """
        {"name":"a","configuration":{"uri":"http://example.com"}}"""));
    assertInstanceOf(Launcher.URIConfiguration.class, uri.getConfiguration());

    var app =
        Launcher.fromJson(
            parse(
                """
        {"name":"b","configuration":{"binaryPath":"/usr/bin/app"}}"""));
    assertInstanceOf(Launcher.ApplicationConfiguration.class, app.getConfiguration());
  }

  @Test
  void unknownCompatibilityKeepsTheCurrentSystemDefault() {
    var launcher =
        Launcher.fromJson(
            parse(
                """
                {"name":"c","configuration":{"launchType":"Application",
                 "binaryPath":"/usr/bin/app","compatibility":"BeOS"}}"""));

    var config =
        assertInstanceOf(Launcher.ApplicationConfiguration.class, launcher.getConfiguration());
    assertTrue(config.isCompatibleWithCurrentSystem());
  }

  private static JsonObject parse(String json) {
    try (JsonReader reader = Json.createReader(new StringReader(json))) {
      return reader.readObject();
    }
  }
}
