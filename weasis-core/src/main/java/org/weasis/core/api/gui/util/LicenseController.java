/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

/**
 * Define controller with operations to be executed when user interacts with license
 * dialog UI. A default implementation can be seen at the class {@link LicenseDialogController}.
 */
public interface LicenseController {

    /**
     * Save license data in the user's local PC. Additionally, will change local
     * weasis configuration to add new plugins references, possibly new URLs to
     * download plugins at config.properties. These procedures of changing local configuration
     * and saving the license might be inserted inside a remote OSGi boot jar that will
     * be downloaded and executed from an URL pointed in a license server field at the UI.
     */
  void save();

  /**
   * Cancel in progress operation as {@link #save()} or {@link #test()}
   */
  void cancel();

  /**
   * Just test the license/activation code and license server. Possibly ping license server URL
   * and check for an HTTP 200 code. Additionally, may download an OSGi boot jar witn new classes
   * to "install" the new plugins. However, doesn't execute any task that will change local user configuration.
   */
  boolean test();
}
