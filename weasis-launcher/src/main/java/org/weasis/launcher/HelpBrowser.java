/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.weasis.core.api.command.CommandHelp;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandResult;

/**
 * Interactive form of {@code weasis-cli help}: the namespaces, the verbs of a namespace and one
 * verb are three levels navigated by number or name, with back and quit. Every screen comes from
 * the running instance; the indexes are fetched once per session.
 */
final class HelpBrowser {

  /** Sends a command to the running instance. */
  @FunctionalInterface
  interface Sender {
    CommandResult send(List<String> argv) throws IOException;
  }

  /** A level of the help: the root, a namespace or one of its verbs. */
  record Location(String namespace, String verb) {
    static final Location ROOT = new Location("", "");

    /**
     * The positional arguments after {@code help}: {@code ns}, {@code ns:verb} or {@code ns verb}.
     */
    static Location of(List<String> positional) {
      if (positional.isEmpty()) {
        return ROOT;
      }
      String first = positional.getFirst();
      int colon = first.indexOf(':');
      if (colon > 0) {
        return new Location(first.substring(0, colon), first.substring(colon + 1));
      }
      return new Location(first, positional.size() > 1 ? positional.get(1) : "");
    }

    boolean isRoot() {
      return namespace.isEmpty();
    }

    boolean isVerb() {
      return !verb.isEmpty();
    }

    Location up() {
      return isVerb() ? new Location(namespace, "") : ROOT;
    }

    Location child(String name) {
      return isRoot() ? new Location(name, "") : new Location(namespace, name);
    }
  }

  private sealed interface Step {
    record Go(Location to) implements Step {}

    record Exit(int status) implements Step {}
  }

  private static final Set<String> QUIT = Set.of("q", "quit", "exit"); // NON-NLS
  private static final Set<String> BACK = Set.of("", "b", "back", ".."); // NON-NLS

  private final Sender sender;
  private final BufferedReader in;
  private final PrintStream out;
  private final PrintStream err;
  private final Map<Location, CommandResult> indexes = new HashMap<>();

  HelpBrowser(Sender sender, BufferedReader in, PrintStream out, PrintStream err) {
    this.sender = sender;
    this.in = in;
    this.out = out;
    this.err = err;
  }

  /** Navigates from {@code start} until the user quits; returns the exit status. */
  int browse(Location start) throws IOException {
    Location location = start;
    boolean first = true;
    while (true) {
      Step step = location.isVerb() ? showVerb(location, first) : showIndex(location, first);
      switch (step) {
        case Step.Exit(int status) -> {
          return status;
        }
        case Step.Go(Location to) -> location = to;
      }
      first = false;
    }
  }

  private Step showIndex(Location location, boolean first) throws IOException {
    CommandResult result = index(location);
    if (!result.isOk()) {
      return failed(result, location, first);
    }
    CommandHelp.Index index = CommandHelp.parseIndex(result.body());
    out.println();
    if (index.entries().isEmpty()) { // a namespace without a verb list: its help as it is
      out.print(result.body());
      out.println();
      return prompt(location, List.of());
    }
    out.print(CommandHelp.table(index.title(), index.entries(), true));
    out.println();
    return prompt(location, index.entries());
  }

  private Step showVerb(Location location, boolean first) throws IOException {
    CommandResult result =
        sender.send(List.of(CommandManager.HELP, location.namespace() + ":" + location.verb()));
    if (!result.isOk()) {
      return failed(result, location, first);
    }
    out.println();
    out.print(result.body());
    if (!result.body().endsWith("\n")) {
      out.println();
    }
    out.println();
    CommandResult siblings = index(location.up());
    List<CommandHelp.Entry> entries =
        siblings.isOk() ? CommandHelp.parseIndex(siblings.body()).entries() : List.of();
    return prompt(location, entries);
  }

  /** A failure on the requested location exits with its status; a later one goes back a level. */
  private Step failed(CommandResult result, Location location, boolean first) {
    err.println(result.error().strip());
    return first ? new Step.Exit(result.status()) : new Step.Go(location.up());
  }

  private CommandResult index(Location location) throws IOException {
    CommandResult cached = indexes.get(location);
    if (cached != null) {
      return cached;
    }
    List<String> argv =
        location.isRoot()
            ? List.of(CommandManager.HELP, CommandManager.INDEX_OPTION)
            : List.of(CommandManager.HELP, location.namespace(), CommandManager.INDEX_OPTION);
    CommandResult result = sender.send(argv);
    if (result.isOk()) {
      indexes.put(location, result);
    }
    return result;
  }

  /** Reads choices until one leads somewhere: a number or a name goes down, back goes up. */
  private Step prompt(Location location, List<CommandHelp.Entry> entries) throws IOException {
    out.println(instructions(location, entries));
    while (true) {
      out.print(promptText(location));
      out.flush();
      String line = in.readLine();
      if (line == null) {
        out.println();
        return new Step.Exit(CommandResult.OK);
      }
      String token = line.strip();
      if (QUIT.contains(token.toLowerCase())) {
        return new Step.Exit(CommandResult.OK);
      }
      if (BACK.contains(token.toLowerCase())) {
        return location.isRoot() ? new Step.Exit(CommandResult.OK) : new Step.Go(location.up());
      }
      Optional<Location> target = resolve(location, entries, token);
      if (target.isPresent()) {
        return new Step.Go(target.get());
      }
      err.println("No entry matches '" + token + "'");
    }
  }

  private static String instructions(Location location, List<CommandHelp.Entry> entries) {
    if (location.isRoot()) {
      return "Type a number or a namespace (or namespace:verb); Enter or q quits.";
    }
    if (location.isVerb()) {
      return entries.isEmpty()
          ? "Enter goes back, q quits."
          : "Enter goes back to the verbs of "
              + location.namespace()
              + ", q quits; a number or a name shows another verb.";
    }
    return entries.isEmpty()
        ? "Enter goes back to the namespaces, q quits."
        : "Type a number or a verb; Enter goes back to the namespaces, q quits.";
  }

  private static String promptText(Location location) {
    if (location.isRoot()) {
      return "help> ";
    }
    return (location.isVerb() ? location.namespace() + ":" + location.verb() : location.namespace())
        + "> ";
  }

  /** A number, an exact name, a unique prefix, or {@code namespace:verb} from the root. */
  private static Optional<Location> resolve(
      Location location, List<CommandHelp.Entry> entries, String token) {
    Location level = location.isVerb() ? location.up() : location;
    if (level.isRoot() && token.indexOf(':') > 0) {
      Location direct = Location.of(List.of(token));
      return entries.stream().anyMatch(e -> e.name().equals(direct.namespace()))
          ? Optional.of(direct)
          : Optional.empty();
    }
    if (!token.isEmpty() && token.chars().allMatch(Character::isDigit)) {
      int number = token.length() > 6 ? 0 : Integer.parseInt(token); // longer never fits a list
      return number >= 1 && number <= entries.size()
          ? Optional.of(level.child(entries.get(number - 1).name()))
          : Optional.empty();
    }
    Optional<CommandHelp.Entry> exact =
        entries.stream().filter(e -> e.name().equalsIgnoreCase(token)).findFirst();
    if (exact.isPresent()) {
      return Optional.of(level.child(exact.get().name()));
    }
    List<CommandHelp.Entry> prefixed =
        entries.stream()
            .filter(e -> e.name().toLowerCase().startsWith(token.toLowerCase()))
            .toList();
    return prefixed.size() == 1
        ? Optional.of(level.child(prefixed.getFirst().name()))
        : Optional.empty();
  }
}
