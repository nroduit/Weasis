/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import org.pf4j.ExtensionPoint;

/**
 * Extension point for plugin commands. Plugins can implement this interface to register commands
 * that can be executed.
 */
public interface CommandExtension extends ExtensionPoint {

  /**
   * Get the command namespace (e.g., "dicom", "dcmview2d")
   *
   * @return the command namespace
   */
  String getNamespace();

  /**
   * Execute a command with arguments
   *
   * @param commandName the command name without namespace
   * @param args the command arguments
   * @throws Exception if command execution fails
   */
  void execute(String commandName, String[] args) throws Exception;

  /**
   * Check if this extension handles the given command
   *
   * @param commandName the command name (with or without namespace)
   * @return true if this extension handles the command
   */
  default boolean handles(String commandName) {
    if (commandName == null) {
      return false;
    }
    String namespace = getNamespace();
    return commandName.startsWith(namespace + ":") || commandName.equals(namespace);
  }

  /**
   * Get help text for this command extension
   *
   * @return help text describing available commands
   */
  default String getHelp() {
    return "No help available for " + getNamespace();
  }
}
