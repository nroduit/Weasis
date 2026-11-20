/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import javax.accessibility.AccessibilityProvider;

/**
 * The assistive technology named by the native options of the macOS launchers, where the launcher
 * set runs in the bootstrap process. AWT resolves {@code
 * javax.accessibility.assistive_technologies} through the system class loader, which holds {@code
 * weasis-bootstrap.jar} only there: the launcher's own {@code
 * org.weasis.launcher.EmptyAccessibilityProvider} sits in the {@code URLClassLoader} of the set and
 * fails the toolkit with an {@code AWTError}. A separate stage-2 process (Linux, Windows) names the
 * launcher's class, which is on its class path.
 */
public class EmptyAccessibilityProvider extends AccessibilityProvider {
  @Override
  public String getName() {
    return "EmptyAccessibilityProvider"; // NON-NLS
  }

  @Override
  public void activate() {
    // nothing to activate: the provider only stops the discovery of assistive technologies
  }
}
