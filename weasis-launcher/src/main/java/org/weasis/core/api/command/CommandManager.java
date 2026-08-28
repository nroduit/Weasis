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

import java.util.ArrayList;
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
   * Splits a command line on whitespace, keeping single- or double-quoted segments together and
   * dropping the quotes. Callers quote the paths they pass, so splitting on whitespace alone hands
   * the command an argument that still carries its quotes.
   *
   * @param commandString the command string
   * @return array of tokens
   */
  private String[] parseCommandString(String commandString) {
    List<String> tokens = new ArrayList<>();
    StringBuilder token = new StringBuilder();
    char quote = 0;
    boolean started = false;
    for (int i = 0; i < commandString.length(); i++) {
      char c = commandString.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        } else {
          token.append(c);
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
        started = true;
      } else if (Character.isWhitespace(c)) {
        if (started) {
          tokens.add(token.toString());
          token.setLength(0);
          started = false;
        }
      } else {
        token.append(c);
        started = true;
      }
    }
    if (started) {
      tokens.add(token.toString());
    }
    return tokens.toArray(new String[0]);
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
