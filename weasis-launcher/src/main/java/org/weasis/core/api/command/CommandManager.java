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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.launcher.CommandProtocol;
import org.weasis.pref.PlatformDirectories;

/**
 * Dispatches {@code namespace:verb} commands to the plugin {@link CommandExtension}s. Every
 * execution runs under a {@link CommandContext}; remote origins are checked against the {@link
 * RemotePolicy} and written to the {@link CommandAudit}.
 */
public class CommandManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);

  public static final String HELP = "help"; // NON-NLS
  private static final String MARKDOWN_OPTION = "--markdown"; // NON-NLS
  private static final String JSON_OPTION = "--json"; // NON-NLS

  /** Help option printing the instructions an agent needs to drive this instance. */
  public static final String AGENT_OPTION = "--agent"; // NON-NLS

  /** Help option listing only the bare names of a level, comma-separated. */
  public static final String NAMES_OPTION = "--names"; // NON-NLS

  /**
   * Help option rendering a level as a {@link CommandHelp#index(String, List) parsable index}, used
   * by the {@code weasis-cli} help browser.
   */
  public static final String INDEX_OPTION = "--index"; // NON-NLS

  /**
   * Help option keeping only the namespaces relevant for the viewer type selected now ({@link
   * CommandExtension#isRelevantFor}), a smaller surface for an agent.
   */
  public static final String CONTEXT_OPTION = "--context"; // NON-NLS

  /**
   * Version of the command surface, independent of the application version: the minor grows with
   * additive changes (a verb, an option, a JSON key), the major with breaking ones. A verb or an
   * option is never removed within a major; a renamed one keeps its old name as a deprecated alias.
   */
  public static final String API_VERSION = "2.18"; // NON-NLS

  private static volatile Supplier<String> viewerType = () -> null;

  private final PluginManager pluginManager;
  private final ConcurrentMap<String, CommandExtension> commands;

  /** Namespace spelling of an earlier major to the namespace it reaches today. */
  private final ConcurrentMap<String, String> namespaceAliases;

  private final CommandAudit audit;
  private final CommandPolicy policy;

  public CommandManager(PluginManager pluginManager) {
    this(
        pluginManager,
        new CommandAudit(
            PlatformDirectories.get(PlatformDirectories.BaseDir.STATE).resolve("audit")), // NON-NLS
        CommandPolicy.fromSystemProperties());
  }

  public CommandManager(PluginManager pluginManager, CommandAudit audit, RemotePolicy remote) {
    this(pluginManager, audit, withRemote(remote));
  }

  public CommandManager(PluginManager pluginManager, CommandAudit audit, CommandPolicy policy) {
    this.pluginManager = pluginManager;
    this.commands = new ConcurrentHashMap<>();
    this.namespaceAliases = new ConcurrentHashMap<>();
    this.audit = audit;
    this.policy = policy;
    loadCommands();
  }

  private static CommandPolicy withRemote(RemotePolicy remote) {
    CommandPolicy defaults = CommandPolicy.defaults();
    return new CommandPolicy(
        remote,
        defaults.files(),
        defaults.network(),
        defaults.assumeYesAllowed(),
        defaults.shutdownAllowed(),
        defaults.pixelAttestAllowed(),
        defaults.locked());
  }

  /** What this instance allows a remote caller to do. */
  public CommandPolicy policy() {
    return policy;
  }

  private void loadCommands() {
    List<CommandExtension> extensions = new ArrayList<>(List.of(new ScriptCommands(this)));
    extensions.addAll(pluginManager.getExtensions(CommandExtension.class));
    LOGGER.info("Loading {} command extensions", extensions.size());

    for (CommandExtension extension : extensions) {
      String namespace = extension.getNamespace();
      if (commands.putIfAbsent(namespace, extension) == null) {
        LOGGER.info("Registered command namespace: {}", namespace);
        extension.getNamespaceAliases().forEach(alias -> namespaceAliases.put(alias, namespace));
      } else {
        LOGGER.warn("Command namespace already registered: {}", namespace);
      }
    }
  }

  public void reloadCommands() {
    commands.clear();
    namespaceAliases.clear();
    loadCommands();
  }

  /** Runs one command string from the launch arguments and prints its body on the console. */
  public void executeCommand(String commandString) {
    executeCommand(commandString, CommandOrigin.LAUNCH_ARGS);
  }

  public void executeCommand(String commandString, CommandOrigin origin) {
    CommandResult result = run(commandString, origin);
    if (!result.body().isEmpty()) {
      System.out.print(result.body()); // NOSONAR command console output
      if (!result.body().endsWith("\n")) {
        System.out.println(); // NOSONAR command console output
      }
    }
    if (!result.isOk()) {
      LOGGER.warn("Command '{}' returned {}: {}", commandString, result.status(), result.error());
    }
  }

  public void executeCommands(List<String> commands) {
    executeCommands(commands, CommandOrigin.LAUNCH_ARGS);
  }

  public void executeCommands(List<String> commands, CommandOrigin origin) {
    if (commands == null) {
      return;
    }
    for (String command : commands) {
      executeCommand(command, origin);
    }
  }

  /** Parses a quoted command string and runs it. */
  public CommandResult run(String commandString, CommandOrigin origin) {
    if (commandString == null || commandString.isBlank()) {
      return CommandResult.usage("Empty command");
    }
    return run(Arrays.asList(parseCommandString(commandString)), origin);
  }

  /**
   * Runs {@code argv}: the first token is {@code namespace:verb} or {@code namespace} followed by
   * the verb, the rest are the verb's arguments.
   */
  public CommandResult run(List<String> argv, CommandOrigin origin) {
    return run(argv, origin, false);
  }

  /** Runs {@code argv}; {@code assumeYes} answers yes to the confirmations the verb would ask. */
  public CommandResult run(List<String> argv, CommandOrigin origin, boolean assumeYes) {
    if (argv == null || argv.isEmpty()) {
      return CommandResult.usage("Empty command");
    }
    Target target = Target.parse(argv);
    if (HELP.equals(target.namespace())) {
      return help(target);
    }
    String namespace = resolve(target.namespace());
    CommandExtension extension = commands.get(namespace);
    if (extension == null) {
      return failed(
          origin,
          CommandResult.USAGE,
          CommandError.of(
              CommandError.UNKNOWN_COMMAND,
              "Unknown command namespace '"
                  + target.namespace()
                  + "'. Available: "
                  + namespaceNames()),
          target.namespace());
    }
    if (origin == CommandOrigin.REMOTE && !isRemotable(namespace, extension)) {
      return failed(
          origin,
          CommandResult.FORBIDDEN,
          new CommandError(
              CommandError.REFUSED_BY_POLICY,
              "Namespace '" + target.namespace() + "' is not available from a remote origin",
              null,
              null,
              null,
              "remote.namespaces", // NON-NLS
              List.of()),
          namespace + ":" + target.verb());
    }

    // A confirmation answers a question of the viewer, never a question of the policy
    boolean yes = assumeYes && (policy.assumeYesAllowed() || origin != CommandOrigin.REMOTE);
    CommandContext context = new CommandContext(origin, yes, policy);
    long start = System.nanoTime();
    CommandResult result;
    try {
      result =
          CommandContext.runWith(
              context, () -> extension.run(target.verb(), target.args(), origin));
    } catch (Throwable e) { // NOSONAR EDT Errors are rethrown here and must not kill the launcher
      LOGGER.error("Error executing command {}", target, e);
      result = CommandResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
    }
    result = result.withFiles(context.files());
    if (origin == CommandOrigin.REMOTE) {
      // The caller learns the current spelling without its command breaking (decision of 5.0).
      if (!namespace.equals(target.namespace())) {
        String notice =
            "'"
                + target.namespace()
                + ":' is the earlier spelling of '"
                + namespace
                + ":' and still works; use the new one.";
        context.addNotice(notice);
        result = result.withNotice(notice);
      }
      result = asErrorBody(result, context, namespace + ":" + target.verb());
      audit.record(
          origin,
          namespace,
          target.verb(),
          target.args(),
          result,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }
    return result;
  }

  /** An error the manager itself produces, rendered the way the origin reads errors. */
  private static CommandResult failed(
      CommandOrigin origin, int status, CommandError error, String command) {
    return CommandResult.of(
        status,
        origin == CommandOrigin.REMOTE ? error.toJson(command, List.of()) : error.message());
  }

  /**
   * A failed command answers a remote caller with one JSON object on the error stream: the code it
   * can act on, the message, the synopsis or the producing verb when there is one, and the notices
   * the call produced. A command that succeeded keeps its notices as plain lines, and the other
   * origins keep the plain message they have always had.
   */
  private static CommandResult asErrorBody(
      CommandResult result, CommandContext context, String command) {
    if (result.isOk()) {
      return result;
    }
    CommandError described = context.error();
    // what the verb printed on the stream is the message and, for a usage error, the whole help:
    // the body carries the message and the synopsis instead, and the notices of the call
    CommandError error =
        described == null
            ? CommandError.of(codeOf(result.status()), firstLine(result.error()))
            : described;
    return new CommandResult(
        result.status(), result.body(), error.toJson(command, context.notices()), result.files());
  }

  private static String firstLine(String text) {
    return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
  }

  /** The code of a status the verbs did not describe themselves. */
  private static String codeOf(int status) {
    return switch (status) {
      case CommandResult.USAGE -> CommandError.USAGE;
      case CommandResult.FORBIDDEN -> CommandError.REFUSED_BY_POLICY;
      case CommandResult.TIMEOUT -> CommandError.TIMEOUT;
      default -> CommandError.FAILED;
    };
  }

  /**
   * The navigable help: {@code help} lists the namespaces, {@code help <namespace>} its verbs and
   * {@code help <namespace>:<verb>} (or {@code help <namespace> <verb>}) one verb in detail.
   */
  private CommandResult help(Target target) {
    List<String> args = Arrays.asList(target.args());
    HelpFormat format = HelpFormat.of(args);
    Map<String, CommandExtension> scope =
        args.contains(CONTEXT_OPTION) ? relevantCommands() : commands;
    String namespace = target.verb();
    String verb = "";
    int colon = namespace.indexOf(':');
    if (colon > 0) {
      verb = namespace.substring(colon + 1);
      namespace = namespace.substring(0, colon);
    } else {
      verb = args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse("");
    }
    if (namespace.isEmpty()) {
      return rootHelp(format, scope);
    }
    String resolved = resolve(namespace);
    CommandExtension extension = commands.get(resolved);
    if (extension == null) {
      return CommandResult.usage(
          "Unknown command namespace '" + namespace + "'. Available: " + namespaceNames());
    }
    namespace = resolved;
    return verb.isEmpty()
        ? namespaceHelp(namespace, extension, format)
        : verbHelp(namespace, extension, verb, format);
  }

  private CommandResult rootHelp(HelpFormat format, Map<String, CommandExtension> scope) {
    return switch (format) {
      case AGENT -> CommandResult.ok(AgentSkill.render(scope.keySet()));
      case NAMES -> CommandResult.ok(namespaceNames(scope));
      case JSON -> CommandResult.ok(jsonHelp(scope));
      case MARKDOWN -> CommandResult.ok(markdownHelp(scope));
      case INDEX -> CommandResult.ok(CommandHelp.index(rootTitle(), namespaceEntries(scope)));
      case TEXT -> CommandResult.ok(CommandHelp.table(rootTitle(), namespaceEntries(scope), false));
    };
  }

  /** Names the viewer type selected now, for {@code help --context}; set by the UI core. */
  public static void setViewerTypeSupplier(Supplier<String> supplier) {
    viewerType = supplier == null ? () -> null : supplier;
  }

  /** The namespaces relevant for the viewer type selected now. */
  private Map<String, CommandExtension> relevantCommands() {
    String type;
    try {
      type = viewerType.get();
    } catch (RuntimeException e) {
      type = null;
    }
    Map<String, CommandExtension> relevant = new TreeMap<>();
    for (Map.Entry<String, CommandExtension> entry : commands.entrySet()) {
      if (entry.getValue().isRelevantFor(type)) {
        relevant.put(entry.getKey(), entry.getValue());
      }
    }
    return relevant;
  }

  private CommandResult namespaceHelp(
      String namespace, CommandExtension extension, HelpFormat format) {
    if (format == HelpFormat.AGENT) {
      return CommandResult.ok(AgentSkill.render(commands.keySet()));
    }
    if (format == HelpFormat.JSON) {
      JsonWriter out = new JsonWriter();
      json(out, namespace, extension);
      return CommandResult.ok(out.toString());
    }
    if (format == HelpFormat.MARKDOWN) {
      return CommandResult.ok(markdown(namespace, extension));
    }
    Object model = extension.getCommandModel();
    if (model == null) {
      return CommandResult.ok(extension.getHelp());
    }
    List<CommandHelp.Entry> verbs = CommandModel.verbs(model);
    if (verbs.isEmpty()) { // the namespace is a single verb
      return CommandResult.ok(CommandModel.usage(model));
    }
    String description = CommandModel.rootDescription(model);
    String title = description.isEmpty() ? namespace : namespace + ": " + description;
    return switch (format) {
      case NAMES ->
          CommandResult.ok(
              verbs.stream().map(CommandHelp.Entry::name).collect(Collectors.joining(", ")));
      case INDEX -> CommandResult.ok(CommandHelp.index(title, verbs));
      default -> CommandResult.ok(CommandHelp.table(title, verbs, false));
    };
  }

  private static CommandResult verbHelp(
      String namespace, CommandExtension extension, String verb, HelpFormat format) {
    Object model = extension.getCommandModel();
    if (model == null) {
      return CommandResult.ok(extension.getHelp());
    }
    Optional<String> text =
        switch (format) {
          case JSON -> {
            JsonWriter out = new JsonWriter();
            yield CommandModel.json(out, model, verb)
                ? Optional.of(out.toString())
                : Optional.empty();
          }
          case MARKDOWN -> CommandModel.markdown(namespace, model, verb);
          default -> CommandModel.usage(model, verb);
        };
    return text.map(CommandResult::ok)
        .orElseGet(
            () ->
                CommandResult.usage(
                    "Unknown verb '"
                        + verb
                        + "' in namespace '"
                        + namespace
                        + "'. Available: "
                        + CommandModel.verbs(model).stream()
                            .map(CommandHelp.Entry::name)
                            .collect(Collectors.joining(", "))));
  }

  private static String rootTitle() {
    return "Weasis commands (API " + API_VERSION + ", application " + applicationVersion() + ")";
  }

  private String namespaceNames() {
    return namespaceNames(commands);
  }

  private static String namespaceNames(Map<String, CommandExtension> scope) {
    return String.join(", ", new TreeMap<>(scope).keySet());
  }

  /** Every namespace with the first sentence of its description. */
  private static List<CommandHelp.Entry> namespaceEntries(Map<String, CommandExtension> scope) {
    return new TreeMap<>(scope)
        .entrySet().stream()
            .map(e -> new CommandHelp.Entry(e.getKey(), summary(e.getValue())))
            .toList();
  }

  private static String summary(CommandExtension extension) {
    Object model = extension.getCommandModel();
    String text = model == null ? extension.getHelp() : CommandModel.rootDescription(model);
    return CommandHelp.firstSentence(text.lines().findFirst().orElse(""));
  }

  private enum HelpFormat {
    TEXT,
    INDEX,
    NAMES,
    MARKDOWN,
    JSON,
    AGENT;

    static HelpFormat of(List<String> args) {
      if (args.contains(AGENT_OPTION)) {
        return AGENT;
      }
      if (args.contains(JSON_OPTION)) {
        return JSON;
      }
      if (args.contains(MARKDOWN_OPTION)) {
        return MARKDOWN;
      }
      if (args.contains(NAMES_OPTION)) {
        return NAMES;
      }
      return args.contains(INDEX_OPTION) ? INDEX : TEXT;
    }
  }

  /** Whether {@code namespace:verb} names a verb this instance offers. */
  public boolean knows(String command) {
    int colon = command == null ? -1 : command.indexOf(':');
    if (colon <= 0) {
      return false;
    }
    CommandExtension extension = commands.get(resolve(command.substring(0, colon)));
    if (extension == null) {
      return false;
    }
    String verb = command.substring(colon + 1);
    Object model = extension.getCommandModel();
    if (model == null) {
      return true; // a namespace that parses its verbs itself answers for them
    }
    List<CommandHelp.Entry> verbs = CommandModel.verbs(model);
    return verbs.isEmpty() || verbs.stream().anyMatch(entry -> entry.name().equals(verb));
  }

  public boolean isRemotable(String namespace) {
    String resolved = resolve(namespace);
    CommandExtension extension = commands.get(resolved);
    return extension != null && isRemotable(resolved, extension);
  }

  /** The namespace a spelling reaches: itself, or what a legacy spelling was renamed to. */
  private String resolve(String namespace) {
    return namespaceAliases.getOrDefault(namespace, namespace);
  }

  private boolean isRemotable(String namespace, CommandExtension extension) {
    return extension.isRemotable() && policy.allows(namespace);
  }

  /** Runs {@code task} on a virtual thread and gives up after {@code timeout}. */
  public static CommandResult runWithTimeout(Callable<CommandResult> task, Duration timeout) {
    FutureTask<CommandResult> future = new FutureTask<>(task);
    Thread.ofVirtual().name("command-run").start(future); // NON-NLS
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return CommandResult.of(
          CommandResult.TIMEOUT, "Command timed out after " + timeout.toSeconds() + " s");
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      LOGGER.error("Command failed", cause);
      return CommandResult.error(
          cause.getMessage() == null ? cause.toString() : cause.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return CommandResult.error("Interrupted");
    }
  }

  static String[] parseCommandString(String commandString) {
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

  public List<String> getRegisteredNamespaces() {
    return List.copyOf(commands.keySet());
  }

  public String getHelp(String namespace) {
    CommandExtension extension = commands.get(namespace);
    return extension != null ? extension.getHelp() : null;
  }

  /** Markdown reference of every namespace, meant for the agent skill. */
  public String getMarkdownHelp() {
    return markdownHelp(commands);
  }

  private static String markdownHelp(Map<String, CommandExtension> scope) {
    StringBuilder sb =
        new StringBuilder("# Weasis commands\n\nCommand API ")
            .append(API_VERSION)
            .append(", protocol ")
            .append(CommandProtocol.VERSION)
            .append(", application ")
            .append(applicationVersion())
            .append(".\n\n");
    for (Map.Entry<String, CommandExtension> entry : new TreeMap<>(scope).entrySet()) {
      sb.append(markdown(entry.getKey(), entry.getValue())).append('\n');
    }
    return sb.toString();
  }

  /** JSON descriptor of every namespace: the machine-readable form of the reference. */
  public String getJsonHelp() {
    return jsonHelp(commands);
  }

  private static String jsonHelp(Map<String, CommandExtension> scope) {
    JsonWriter out = new JsonWriter();
    out.beginObject()
        .name("apiVersion") // NON-NLS
        .value(API_VERSION)
        .name("protocolVersion") // NON-NLS
        .value(CommandProtocol.VERSION)
        .name("applicationVersion") // NON-NLS
        .value(applicationVersion());
    out.name("exitCodes").beginObject(); // NON-NLS
    out.name("0").value("ok"); // NON-NLS
    out.name("1").value("command error"); // NON-NLS
    out.name("2").value("usage error"); // NON-NLS
    out.name("3").value("no running instance"); // NON-NLS
    out.name("4").value("timeout"); // NON-NLS
    out.name("5").value("connection error"); // NON-NLS
    out.name("6").value("not permitted remotely"); // NON-NLS
    out.endObject();
    out.name("namespaces").beginArray(); // NON-NLS
    for (Map.Entry<String, CommandExtension> entry : new TreeMap<>(scope).entrySet()) {
      json(out, entry.getKey(), entry.getValue());
    }
    out.endArray().endObject();
    return out.toString();
  }

  private static void json(JsonWriter out, String namespace, CommandExtension extension) {
    Object model = extension.getCommandModel();
    if (model != null) {
      CommandModel.json(out, namespace, model);
    } else {
      out.beginObject()
          .name("name") // NON-NLS
          .value(namespace)
          .name("help") // NON-NLS
          .value(extension.getHelp().strip())
          .endObject();
    }
  }

  /** The packaged application version, or {@code development}. */
  public static String applicationVersion() {
    return System.getProperty("jpackage.app-version", "development"); // NON-NLS
  }

  private static String markdown(String namespace, CommandExtension extension) {
    Object model = extension.getCommandModel();
    if (model != null) {
      return CommandModel.markdown(namespace, model);
    }
    return "## " + namespace + "\n\n```\n" + extension.getHelp().strip() + "\n```\n";
  }

  /** A parsed command target: namespace, verb (may be empty) and the remaining arguments. */
  record Target(String namespace, String verb, String[] args) {

    static Target parse(List<String> argv) {
      String first = argv.getFirst();
      int colon = first.indexOf(':');
      if (colon > 0) {
        return new Target(first.substring(0, colon), first.substring(colon + 1), rest(argv, 1));
      }
      if (argv.size() > 1 && !argv.get(1).startsWith("-")) {
        return new Target(first, argv.get(1), rest(argv, 2));
      }
      return new Target(first, "", rest(argv, 1));
    }

    private static String[] rest(List<String> argv, int from) {
      return argv.subList(Math.min(from, argv.size()), argv.size()).toArray(new String[0]);
    }

    @Override
    public String toString() {
      return namespace + ":" + verb + " " + String.join(" ", args);
    }
  }
}
