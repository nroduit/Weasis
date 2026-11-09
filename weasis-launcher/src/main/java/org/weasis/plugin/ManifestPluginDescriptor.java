/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin;

import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginDescriptor;

public class ManifestPluginDescriptor extends DefaultPluginDescriptor {
  private String nativeFolder;
  private String nativeFiles;
  private String namespaces;
  private String capabilities;
  private String commandApi;

  public ManifestPluginDescriptor(PluginDescriptor base) {
    super(
        base.getPluginId(),
        base.getPluginDescription(),
        base.getPluginClass(),
        base.getVersion(),
        base.getRequires(),
        base.getProvider(),
        base.getLicense());
    getDependencies().addAll(base.getDependencies());
  }

  public String getNativeFolder() {
    return nativeFolder;
  }

  public void setNativeFolder(String nativeFolder) {
    this.nativeFolder = nativeFolder;
  }

  public String getNativeFiles() {
    return nativeFiles;
  }

  public void setNativeFiles(String nativeFiles) {
    this.nativeFiles = nativeFiles;
  }

  /** The {@code Weasis-Namespaces} header: the command namespaces the plugin may register. */
  public String getNamespaces() {
    return namespaces;
  }

  public void setNamespaces(String namespaces) {
    this.namespaces = namespaces;
  }

  /** The {@code Weasis-Capabilities} header: what the plugin declares it needs. */
  public String getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(String capabilities) {
    this.capabilities = capabilities;
  }

  /** The {@code Weasis-Command-API} header: the command API the plugin was built against. */
  public String getCommandApi() {
    return commandApi;
  }

  public void setCommandApi(String commandApi) {
    this.commandApi = commandApi;
  }
}
