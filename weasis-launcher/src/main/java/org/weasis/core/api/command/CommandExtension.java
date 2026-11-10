/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import org.pf4j.ExtensionPoint;

/**
 * Extension point for plugin commands. A namespace either provides a Picocli command model ({@link
 * #getCommandModel()}) or overrides {@link #execute(String, String[])} for hand-parsed verbs; the
 * manager always dispatches through {@link #run(String, String[], CommandOrigin)}.
 */
public interface CommandExtension extends ExtensionPoint {

  /** The command namespace, e.g. {@code dicom} or {@code view2d}. */
  String getNamespace();

  /**
   * The spellings of earlier majors that still reach this namespace. They are hidden from the help
   * and the descriptor, and a remote caller that uses one is told the current spelling.
   */
  default List<String> getNamespaceAliases() {
    return List.of();
  }

  /**
   * A Picocli annotated root command, or a {@code CommandSpec}, whose subcommands are the verbs of
   * this namespace; {@code null} when the namespace parses its verbs itself.
   */
  default Object getCommandModel() {
    return null;
  }

  /**
   * The events this namespace publishes on the {@link org.weasis.core.api.event.EventBus}, declared
   * where the verbs are so the catalog, {@code help <namespace>} and the descriptor list them.
   */
  default List<org.weasis.core.api.event.EventType> getEventTypes() {
    return List.of();
  }

  /**
   * The terms this namespace adds to the glossary of the command surface: the nouns of its option
   * names and JSON keys that the platform does not define, with their definition, read from a
   * resource with {@link Glossary#read(Class, String)}. A term the platform already defines with
   * another text is refused at registration.
   */
  default List<Term> getTerms() {
    return List.of();
  }

  /** False for namespaces that must not be reachable from the singleton socket. */
  default boolean isRemotable() {
    return true;
  }

  /**
   * Whether the namespace serves the viewer type selected now ({@code 2d}, {@code mpr}, {@code 3d},
   * ...; null when no viewer is selected); {@code help --context} lists only the namespaces that
   * do. True by default: a namespace that does not depend on the viewer.
   */
  default boolean isRelevantFor(String viewerTypeId) {
    return true;
  }

  /**
   * Executes a hand-parsed verb; only called when there is no command model.
   *
   * @param commandName the verb without namespace
   */
  default void execute(String commandName, String[] args) throws Exception {
    throw new UnsupportedOperationException(
        "Namespace " + getNamespace() + " has neither a command model nor an execute method");
  }

  /** Executes a verb and returns its result; the default runs the model or {@link #execute}. */
  default CommandResult run(String commandName, String[] args, CommandOrigin origin)
      throws Exception {
    Object model = getCommandModel();
    if (model != null) {
      return CommandModel.execute(model, commandName, args);
    }
    execute(commandName, args);
    return CommandResult.ok();
  }

  /** Whether this extension handles the command name, with or without namespace. */
  default boolean handles(String commandName) {
    if (commandName == null) {
      return false;
    }
    for (String namespace : names()) {
      if (commandName.startsWith(namespace + ":") || commandName.equals(namespace)) {
        return true;
      }
    }
    return false;
  }

  /** The namespace and its legacy spellings. */
  default List<String> names() {
    List<String> names = new ArrayList<>();
    names.add(getNamespace());
    names.addAll(getNamespaceAliases());
    return names;
  }

  default String getHelp() {
    Object model = getCommandModel();
    return model == null ? "No help available for " + getNamespace() : CommandModel.usage(model);
  }
}
