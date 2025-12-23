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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages command extensions from plugins */
public class CommandManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

  private final PluginManager pluginManager;
  private final ConcurrentMap<String, CommandExtension> commands;

  public CommandManager(PluginManager pluginManager) {
    this.pluginManager = pluginManager;
    this.commands = new ConcurrentHashMap<>();
    loadCommands();
  }

  /** Load all command extensions from plugins */
  private void loadCommands() {
    List<CommandExtension> extensions = pluginManager.getExtensions(CommandExtension.class);
    LOGGER.info("Loading {} command extensions", extensions.size());

    for (CommandExtension extension : extensions) {
      String namespace = extension.getNamespace();
      if (commands.putIfAbsent(namespace, extension) == null) {
        LOGGER.info("Registered command namespace: {}", namespace);
      } else {
        LOGGER.warn("Command namespace already registered: {}", namespace);
      }
    }
  }

  /** Reload commands from plugins (useful after plugin updates) */
  public void reloadCommands() {
    commands.clear();
    loadCommands();
  }

  /**
   * Execute a command string
   *
   * @param commandString the full command string (e.g., "dicom:get -l file.dcm")
   */
  public void executeCommand(String commandString) {
    if (commandString == null || commandString.trim().isEmpty()) {
      return;
    }

    try {
      // Parse command string
      String[] parts = parseCommandString(commandString);
      if (parts.length == 0) {
        return;
      }

      String fullCommand = parts[0];
      String[] args = new String[parts.length - 1];
      System.arraycopy(parts, 1, args, 0, args.length);

      // Extract namespace and command name
      String namespace;
      String commandName;

      int colonIndex = fullCommand.indexOf(':');
      if (colonIndex > 0) {
        namespace = fullCommand.substring(0, colonIndex);
        commandName = fullCommand.substring(colonIndex + 1);
      } else {
        namespace = fullCommand;
        commandName = "";
      }

      // Find and execute command
      CommandExtension extension = commands.get(namespace);
      if (extension != null) {
        LOGGER.debug("Executing command: {} with args: {}", fullCommand, args);
        extension.execute(commandName, args);
      } else {
        LOGGER.warn("No command extension found for namespace: {}", namespace);
      }

    } catch (Exception e) {
      LOGGER.error("Error executing command: {}", commandString, e);
    }
  }

  /**
   * Execute multiple commands
   *
   * @param commands list of command strings
   */
  public void executeCommands(List<String> commands) {
    if (commands == null) {
      return;
    }

    for (String command : commands) {
      executeCommand(command);
    }
  }

  /**
   * Parse command string into tokens, respecting quotes
   *
   * @param commandString the command string
   * @return array of tokens
   */
  private String[] parseCommandString(String commandString) {
    // Simple parsing - can be enhanced with proper quote handling
    return commandString.trim().split("\\s+");
  }

  /**
   * Get all registered command namespaces
   *
   * @return list of namespaces
   */
  public List<String> getRegisteredNamespaces() {
    return List.copyOf(commands.keySet());
  }

  /**
   * Get help for a specific namespace
   *
   * @param namespace the command namespace
   * @return help text, or null if namespace not found
   */
  public String getHelp(String namespace) {
    CommandExtension extension = commands.get(namespace);
    return extension != null ? extension.getHelp() : null;
  }

  /**
   * Get help for all registered commands
   *
   * @return combined help text
   */
  public String getAllHelp() {
    StringBuilder help = new StringBuilder("Available command namespaces:\n");
    for (String namespace : commands.keySet()) {
      help.append("\n").append(namespace).append(":\n");
      help.append(getHelp(namespace)).append("\n");
    }
    return help.toString();
  }
}
