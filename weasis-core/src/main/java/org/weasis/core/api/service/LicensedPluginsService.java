/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

/**
 * Defines a set of standard methods to be implemented by licensed plugins boot
 * jar. This jar (OSGi module) should then be provided remotely at a license
 * server. It will be downloaded and will have a class implementing this
 * interface. That class will be an OSGi service installed on-the-fly, while
 * Weasis is still running and used mainly to change user local configuration,
 * validate licenses and validate endpoints from where other plugins/bundles
 * will be downloaded.
 */
public interface LicensedPluginsService {

    /**
     * Updates local weasis config.properties, adding more URLs for additional
     * plugins.
     */
    void updateConfig() throws Exception;

    /**
     * Ping/validate relevant endpoints. Mainly those providing additional plugins.
     */
    void validateEndpoints() throws Exception;

    /**
     * Read a locally stored license file and send it to a remote address for the
     * first validation.
     */
    void validateLicense() throws Exception;

    /**
     * Custom code to be executed before ask user to restart Weasis in order to
     * apply new configuration (config.propertes with newly added URLs for plugins.
     * 
     */
    void askUserForRestart() throws Exception;
}
