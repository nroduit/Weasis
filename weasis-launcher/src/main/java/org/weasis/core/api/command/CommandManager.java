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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventCatalog;
import org.weasis.core.api.event.EventCommands;
import org.weasis.core.api.event.EventFilter;
import org.weasis.core.api.event.EventType;
import org.weasis.core.api.event.WeasisEvents;
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
  public static final String API_VERSION = "2.30"; // NON-NLS

  /** Namespace of the event verbs and of the audit lines of the event stream. */
  public static final String EVENTS = "events"; // NON-NLS

  private static volatile Supplier<String> viewerType = () -> null;

  private final PluginManager pluginManager;
  private final ConcurrentMap<String, CommandExtension> commands;

  /** Namespace spelling of an earlier major to the namespace it reaches today. */
  private final ConcurrentMap<String, String> namespaceAliases;

  private final CommandAudit audit;
  private final CommandPolicy policy;
  private final EventBus bus;
  private final java.util.concurrent.atomic.AtomicInteger remoteStreams =
      new java.util.concurrent.atomic.AtomicInteger();

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
    this(pluginManager, audit, policy, EventBus.global());
  }

  public CommandManager(
      PluginManager pluginManager, CommandAudit audit, CommandPolicy policy, EventBus bus) {
    this.pluginManager = pluginManager;
    this.commands = new ConcurrentHashMap<>();
    this.namespaceAliases = new ConcurrentHashMap<>();
    this.audit = audit;
    this.policy = policy;
    this.bus = bus;
    loadCommands();
  }

  /** The bus the events of this instance go through. */
  public EventBus eventBus() {
    return bus;
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
    List<CommandExtension> extensions =
        new ArrayList<>(List.of(new ScriptCommands(this), new EventCommands(bus)));
    extensions.addAll(pluginManager.getExtensions(CommandExtension.class));
    LOGGER.info("Loading {} command extensions", extensions.size());

    for (CommandExtension extension : extensions) {
      String namespace = extension.getNamespace();
      String refusal = ownership(extension, namespace);
      if (refusal != null) {
        LOGGER.warn("Command namespace {} refused: {}", namespace, refusal);
        continue;
      }
      if (commands.putIfAbsent(namespace, extension) == null) {
        LOGGER.info("Registered command namespace: {}", namespace);
        extension.getNamespaceAliases().forEach(alias -> namespaceAliases.put(alias, namespace));
        bus.catalog().declare(extension.getEventTypes());
      } else {
        LOGGER.warn("Command namespace already registered: {}", namespace);
      }
    }
  }

  /**
   * The consistency checks of a plugin's namespace (M5 of the plugin manager): a plugin that is not
   * part of the package registers verbs only in the namespaces its manifest declares ({@code
   * Weasis-Namespaces}), never in a namespace of the package, and its verbs need no capability the
   * manifest does not declare ({@code Weasis-Capabilities}). Null when the namespace may be
   * registered, else the reason.
   */
  String ownership(CommandExtension extension, String namespace) {
    if (!(pluginManager instanceof org.weasis.plugin.WeasisPluginManager manager)) {
      return null;
    }
    PluginWrapper wrapper = manager.whichPlugin(extension.getClass());
    if (wrapper == null
        || manager.getGate() == null
        || manager.getGate().isPackage(wrapper.getPluginPath())) {
      return null;
    }
    java.util.Set<String> declared =
        org.weasis.plugin.manager.PluginGate.namespacesOf(wrapper.getDescriptor());
    if (!declared.contains(namespace.toLowerCase(java.util.Locale.ROOT))) {
      return "the plugin "
          + wrapper.getPluginId()
          + " does not declare the namespace "
          + namespace
          + " (Weasis-Namespaces)";
    }
    CommandExtension owner = commands.get(namespace);
    if (owner != null) {
      PluginWrapper other = manager.whichPlugin(owner.getClass());
      return "the namespace "
          + namespace
          + " belongs to "
          + (other == null ? "the application" : other.getPluginId());
    }
    java.util.List<String> declaredCapabilities =
        org.weasis.plugin.manager.PluginGate.capabilitiesOf(wrapper.getDescriptor());
    Object model = extension.getCommandModel();
    if (model != null) {
      for (picocli.CommandLine.Model.CommandSpec spec : CommandModel.verbSpecs(model)) {
        for (Capability needed : CommandModel.capabilities(spec)) {
          if (!declaredCapabilities.contains(needed.name())) {
            return "the verb "
                + spec.name()
                + " needs the capability "
                + needed.name()
                + " which the plugin does not declare (Weasis-Capabilities)";
          }
        }
      }
    }
    return null;
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

    if (origin == CommandOrigin.REMOTE && !CommandContext.isNested()) {
      // the steps of a script share the script's transaction
      remoteBatchBoundary(namespace, target.verb());
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
      long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      audit.record(origin, namespace, target.verb(), target.args(), result, millis);
      bus.publish(
          Event.of(WeasisEvents.COMMAND_FINISHED)
              .origin(origin)
              .put("command", namespace + ":" + target.verb()) // NON-NLS
              .put("status", result.status()) // NON-NLS
              .put("durationMs", millis)); // NON-NLS
    }
    return result;
  }

  /**
   * Opens the event stream of a remote caller: the policy decides whether the events are open, how
   * many may listen at once and which namespaces they see, and the audit gets one line for the
   * subscription and one when it ends.
   *
   * @throws CommandFailure with the single refusal shape when the policy refuses
   */
  public EventStreamGrant openEventStream(EventFilter filter, CommandOrigin origin) {
    Optional<CommandPolicy.Refusal> refusal = policy.checkEvents(origin, remoteStreams.get());
    if (refusal.isEmpty() && origin == CommandOrigin.REMOTE && !policy.remote().enabled()) {
      refusal =
          Optional.of(
              new CommandPolicy.Refusal(
                  "remote.enabled", "remote callers are refused on this instance")); // NON-NLS
    }
    String[] args = {"--filter=" + filter.describe()}; // NON-NLS
    if (refusal.isPresent()) {
      audit.record(
          origin,
          EVENTS,
          "subscribe", // NON-NLS
          args,
          CommandResult.of(CommandResult.FORBIDDEN, refusal.get().message()),
          0);
      throw new CommandFailure(CommandError.refused(refusal.get()));
    }
    remoteStreams.incrementAndGet();
    audit.record(origin, EVENTS, "subscribe", args, CommandResult.ok(), 0); // NON-NLS
    return new EventStreamGrant(filter, origin, args);
  }

  /** A subscription the policy allowed; closing it writes the audit line of its end. */
  public final class EventStreamGrant implements AutoCloseable {
    private final EventFilter filter;
    private final CommandOrigin origin;
    private final String[] args;
    private final long start = System.nanoTime();
    private volatile boolean closed;

    private EventStreamGrant(EventFilter filter, CommandOrigin origin, String[] args) {
      this.filter = filter;
      this.origin = origin;
      this.args = args;
    }

    public EventFilter filter() {
      return filter;
    }

    public EventBus bus() {
      return bus;
    }

    /** Whether the policy lets this subscriber see an event of that namespace. */
    public boolean allows(Event event) {
      return origin != CommandOrigin.REMOTE || policy.events().allows(event.namespace());
    }

    /** The event as this subscriber may see it. */
    public Event mask(Event event) {
      return origin == CommandOrigin.REMOTE ? bus.maskForRemote(event) : event;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      remoteStreams.decrementAndGet();
      audit.record(
          origin,
          EVENTS,
          "unsubscribe", // NON-NLS
          args,
          CommandResult.ok(),
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }
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
  /**
   * The display's transaction boundary, registered by the viewer; {@link StateBoundary#NONE} until
   * then.
   */
  private static volatile StateBoundary stateBoundary = StateBoundary.NONE;

  /** A remote batch is one transaction while its calls are closer than this to each other. */
  static final Duration REMOTE_BATCH_GAP = Duration.ofSeconds(30);

  private volatile long lastRemoteCallNanos = Long.MIN_VALUE;

  /** Registers the boundary the viewer provides, or {@code null} for none. */
  public static void setStateBoundary(StateBoundary boundary) {
    stateBoundary = boundary == null ? StateBoundary.NONE : boundary;
  }

  /** The boundary of the display: a checkpoint before a batch, the rollback when it fails. */
  public static StateBoundary stateBoundary() {
    return stateBoundary;
  }

  /**
   * Opens a transaction for a remote caller when its previous call is older than {@link
   * #REMOTE_BATCH_GAP}: the checkpoint lets the user, or the agent, undo the batch in one step.
   * Verbs that only read or that manage the checkpoints themselves never open one.
   */
  private void remoteBatchBoundary(String namespace, String verb) {
    if (!policy.remoteCheckpoint() || !opensTransaction(namespace, verb)) {
      return;
    }
    long now = System.nanoTime();
    long last = lastRemoteCallNanos;
    lastRemoteCallNanos = now;
    if (last != Long.MIN_VALUE && now - last < REMOTE_BATCH_GAP.toNanos()) {
      return;
    }
    try {
      stateBoundary.checkpoint(
          "before remote batch " + Instant.now().truncatedTo(ChronoUnit.SECONDS),
          CommandOrigin.REMOTE);
    } catch (RuntimeException e) {
      LOGGER.debug("No checkpoint before the remote batch", e);
    }
  }

  /** The namespaces whose verbs never change the display. */
  private static final Set<String> READ_ONLY_NAMESPACES =
      Set.of("help", "events", "config", "script", "state", "weasis"); // NON-NLS

  static boolean opensTransaction(String namespace, String verb) {
    if (READ_ONLY_NAMESPACES.contains(namespace)) {
      return false;
    }
    return verb == null
        || !(verb.equals("list") // NON-NLS
            || verb.equals("info") // NON-NLS
            || verb.equals("state") // NON-NLS
            || verb.equals("capture") // NON-NLS
            || verb.equals("stats") // NON-NLS
            || verb.equals("explain") // NON-NLS
            || verb.equals("tag") // NON-NLS
            || verb.equals("objects") // NON-NLS
            || verb.equals("match") // NON-NLS
            || verb.equals("validate") // NON-NLS
            || verb.equals("features") // NON-NLS
            || verb.equals("destinations")); // NON-NLS
  }

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
      return CommandResult.ok(extension.getHelp() + eventsText(namespace));
    }
    List<CommandHelp.Entry> verbs = CommandModel.verbs(model);
    if (verbs.isEmpty()) { // the namespace is a single verb
      return CommandResult.ok(CommandModel.usage(model) + eventsText(namespace));
    }
    String description = CommandModel.rootDescription(model);
    String title = description.isEmpty() ? namespace : namespace + ": " + description;
    return switch (format) {
      case NAMES ->
          CommandResult.ok(
              verbs.stream().map(CommandHelp.Entry::name).collect(Collectors.joining(", ")));
      case INDEX -> CommandResult.ok(CommandHelp.index(title, verbs));
      default -> CommandResult.ok(CommandHelp.table(title, verbs, false) + eventsText(namespace));
    };
  }

  /** The events of a namespace, appended to its text help; empty when it declares none. */
  private String eventsText(String namespace) {
    List<EventType> events = bus.catalog().types(namespace);
    if (events.isEmpty()) {
      return "";
    }
    return System.lineSeparator()
        + "Events ('weasis-cli events --filter "
        + namespace
        + ":*' streams them):"
        + System.lineSeparator()
        + EventCatalog.text(events);
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

  private String markdownHelp(Map<String, CommandExtension> scope) {
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
    List<EventType> events = bus.catalog().types();
    if (!events.isEmpty()) {
      sb.append("## Events\n\n")
          .append(
              "What the instance publishes on its event bus; `weasis-cli events` streams them, "
                  + "`events:recent` polls them.\n\n");
      for (String namespace : bus.catalog().namespaces()) {
        sb.append("### ")
            .append(namespace)
            .append(":\n\n")
            .append(EventCatalog.markdownTable(bus.catalog().types(namespace)))
            .append('\n');
      }
    }
    return sb.toString();
  }

  /** JSON descriptor of every namespace: the machine-readable form of the reference. */
  public String getJsonHelp() {
    return jsonHelp(commands);
  }

  private String jsonHelp(Map<String, CommandExtension> scope) {
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
    out.endArray();
    out.name("events").beginObject(); // NON-NLS
    for (String namespace : bus.catalog().namespaces()) {
      out.name(namespace);
      bus.catalog().write(out, bus.catalog().types(namespace));
    }
    out.endObject().endObject();
    return out.toString();
  }

  private void json(JsonWriter out, String namespace, CommandExtension extension) {
    Object model = extension.getCommandModel();
    List<EventType> events = bus.catalog().types(namespace);
    if (model != null) {
      CommandModel.json(out, namespace, model, events);
    } else {
      out.beginObject()
          .name("name") // NON-NLS
          .value(namespace)
          .name("help") // NON-NLS
          .value(extension.getHelp().strip());
      if (!events.isEmpty()) {
        out.name("events"); // NON-NLS
        bus.catalog().write(out, events);
      }
      out.endObject();
    }
  }

  /** The packaged application version, or {@code development}. */
  public static String applicationVersion() {
    return System.getProperty("jpackage.app-version", "development"); // NON-NLS
  }

  private String markdown(String namespace, CommandExtension extension) {
    Object model = extension.getCommandModel();
    String text =
        model != null
            ? CommandModel.markdown(namespace, model)
            : "## " + namespace + "\n\n```\n" + extension.getHelp().strip() + "\n```\n";
    List<EventType> events = bus.catalog().types(namespace);
    if (events.isEmpty()) {
      return text;
    }
    return text
        + "### Events of "
        + namespace
        + ":\n\n"
        + EventCatalog.markdownTable(events)
        + '\n';
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
