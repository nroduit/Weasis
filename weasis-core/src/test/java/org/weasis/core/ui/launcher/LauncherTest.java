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

import static org.mockito.Mockito.*;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Component;
import java.lang.management.ManagementFactory;
import java.net.URI;
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

    mainFrame.setConfigData(new ConfigData(null));
    ObjectName objectName2 = new ObjectName("weasis:name=MainWindow"); // NON-NLS
    ManagementFactory.getPlatformMBeanServer().registerMBean(mainFrame, objectName2);
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
}
