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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Runs and documents a Picocli command model: a fresh {@link CommandLine} per execution, plain text
 * output captured into a {@link CommandResult}, and a Markdown rendering of the model.
 */
public final class CommandModel {
  private static final Logger LOGGER = LoggerFactory.getLogger(CommandModel.class);
  private static final int USAGE_WIDTH = 100;

  /** The option a Picocli message quotes, as in {@code Invalid value for option '--pane'}. */
  private static final Pattern OPTION_IN_MESSAGE = Pattern.compile("'(-{1,2}[\\w-]+)'");

  /** The option a verb declares to let its caller replace a file it did not write. */
  public static final String FORCE = "--force"; // NON-NLS

  private static final int MARKDOWN_WIDTH = 240;

  private CommandModel() {}

  public static CommandLine newCommandLine(Object model) {
    CommandLine cl =
        model instanceof CommandSpec spec ? new CommandLine(spec) : new CommandLine(model);
    cl.setColorScheme(Help.defaultColorScheme(Ansi.OFF));
    cl.setUsageHelpAutoWidth(false);
    cl.setUsageHelpWidth(USAGE_WIDTH);
    // The 4.x parser resolved a unique prefix of a long option (--ser for --series).
    cl.setAbbreviatedOptionsAllowed(true);
    addHelpOption(cl.getCommandSpec());
    hideAliases(cl.getCommandSpec());
    registerSemanticTypes(cl);
    // The flag only reaches the subcommands registered when it is set, and the aliases are added
    // above, so every spec is told once the model is complete.
    specs(cl.getCommandSpec())
        .forEach(spec -> spec.parser().caseInsensitiveEnumValuesAllowed(true));
    return cl;
  }

  /**
   * Turns the aliases of a verb into hidden subcommands of their own: the spelling of an earlier
   * major keeps working, while the help, the reference and the descriptor show the current one
   * alone.
   */
  private static void hideAliases(CommandSpec parent) {
    for (CommandLine sub : List.copyOf(parent.subcommands().values())) {
      CommandSpec spec = sub.getCommandSpec();
      String[] aliases = spec.aliases();
      if (aliases.length == 0 || spec.usageMessage().hidden()) {
        continue;
      }
      spec.aliases(new String[0]);
      for (String alias : aliases) {
        parent.removeSubcommand(alias);
        CommandLine copy = new CommandLine(spec.userObject());
        copy.getCommandSpec().name(alias).usageMessage().hidden(true);
        copy.getCommandSpec().aliases(new String[0]);
        addHelpOption(copy.getCommandSpec());
        parent.addSubcommand(alias, copy);
      }
    }
  }

  /** The verbs of a model, without the hidden spellings of an earlier major. */
  private static Stream<CommandSpec> visibleVerbs(CommandSpec root) {
    return root.subcommands().values().stream()
        .map(CommandLine::getCommandSpec)
        .filter(spec -> !spec.usageMessage().hidden())
        .distinct();
  }

  /**
   * Registers one converter per {@link SemanticType} the model declares, so a type validates itself
   * at parse time wherever it is used and a plugin adds its own types without touching this class.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void registerSemanticTypes(CommandLine cl) {
    for (CommandSpec spec : specs(cl.getCommandSpec())) {
      for (ArgSpec arg : arguments(spec)) {
        Class<?> type = elementType(arg);
        if (SemanticType.class.isAssignableFrom(type)) {
          cl.registerConverter((Class) type, SemanticType.converterOf(type));
        }
      }
    }
  }

  private static List<CommandSpec> specs(CommandSpec root) {
    List<CommandSpec> all = new ArrayList<>();
    all.add(root);
    root.subcommands().values().stream()
        .map(CommandLine::getCommandSpec)
        .distinct()
        .forEach(spec -> all.addAll(specs(spec)));
    return all;
  }

  private static List<ArgSpec> arguments(CommandSpec spec) {
    List<ArgSpec> args = new ArrayList<>(spec.options());
    args.addAll(spec.positionalParameters());
    return args;
  }

  /** The type one value of an argument carries: the element type of a list or an array. */
  static Class<?> elementType(ArgSpec arg) {
    Class<?> type = arg.type();
    if (type.isArray()
        || Collection.class.isAssignableFrom(type)
        || Map.class.isAssignableFrom(type)) {
      Class<?>[] aux = arg.auxiliaryTypes();
      return aux.length > 0 ? aux[aux.length - 1] : type;
    }
    return type;
  }

  /**
   * The type of an argument as the descriptor names it: the vocabulary name of a semantic type,
   * else a normalized primitive.
   */
  static String typeName(ArgSpec arg) {
    Class<?> type = elementType(arg);
    return SemanticType.class.isAssignableFrom(type)
        ? SemanticType.nameOf(type)
        : SemanticType.primitiveOf(type);
  }

  /** The values of an enum argument, in declaration order; empty when it is not an enum. */
  static List<String> enumValues(ArgSpec arg) {
    Class<?> type = elementType(arg);
    if (!type.isEnum()) {
      return List.of();
    }
    return Stream.of(type.getEnumConstants()).map(Object::toString).toList();
  }

  // Every verb answers --help, whether or not the plugin declared the standard help mixin; -? is
  // the spelling the 4.x parser accepted and the site documents.
  private static void addHelpOption(CommandSpec spec) {
    boolean hasHelp = spec.options().stream().anyMatch(OptionSpec::usageHelp);
    if (!hasHelp && spec.findOption("--help") == null && spec.findOption("-h") == null) {
      spec.addOption(
          OptionSpec.builder("-h", "-?", "--help") // NON-NLS
              .usageHelp(true)
              .description("Show this help and exit.")
              .build());
    }
    spec.subcommands().values().stream()
        .map(CommandLine::getCommandSpec)
        .distinct()
        .forEach(CommandModel::addHelpOption);
  }

  /** Executes {@code verb} with {@code args}; an empty verb runs the root command. */
  public static CommandResult execute(Object model, String verb, String[] args) {
    CommandLine cl = newCommandLine(model);
    String[] argv = verb == null || verb.isEmpty() ? args : prepend(verb, args);
    if (argv.length == 0 && !isRunnable(cl.getCommandSpec().userObject())) {
      return CommandResult.usage(cl.getUsageMessage());
    }
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    cl.setOut(new PrintWriter(out, true));
    cl.setErr(new PrintWriter(err, true));
    cl.setExecutionExceptionHandler(
        (ex, cmd, parseResult) -> {
          LOGGER.warn("Command {} failed", cmd.getCommandSpec().qualifiedName(":"), ex);
          String text = ex.getMessage() == null ? ex.toString() : ex.getMessage();
          CommandContext.current().setError(executionError(ex, text));
          cmd.getErr().println(text);
          return CommandResult.ERROR;
        });
    CommandLine.IParameterExceptionHandler parameterErrors = cl.getParameterExceptionHandler();
    cl.setParameterExceptionHandler(
        (ex, unparsed) -> {
          CommandContext.current().setError(parameterError(ex));
          return parameterErrors.handleParseException(ex, unparsed);
        });
    cl.setExecutionStrategy(CommandModel::runWithinPolicy);
    int status = cl.execute(argv);
    CommandResult result = new CommandResult(status, out.toString(), err.toString(), List.of());
    String notice = legacyNotice(cl, verb, args);
    CommandContext.current().addNotice(notice);
    return result.withNotice(notice);
  }

  /**
   * The gate of the restriction policies: the command is parsed, so its capabilities and the values
   * that carry them are known, and the policy of the running instance decides before anything runs.
   */
  private static int runWithinPolicy(CommandLine.ParseResult parseResult) {
    CommandContext context = CommandContext.current();
    Optional<CommandPolicy.Refusal> refusal =
        context.policy().check(context.origin(), request(parseResult));
    if (refusal.isPresent()) {
      context.setError(CommandError.refused(refusal.get()));
      parseResult.commandSpec().commandLine().getErr().println(refusal.get().message());
      return CommandResult.FORBIDDEN;
    }
    return new CommandLine.RunLast().execute(parseResult);
  }

  /** What the caller got wrong in the grammar of the verb, and what it takes. */
  private static CommandError parameterError(CommandLine.ParameterException ex) {
    CommandLine cl = ex.getCommandLine();
    String synopsis = synopsis(cl.getCommandSpec().qualifiedName(":"), cl.getCommandSpec());
    String message = ex.getMessage() == null ? "Invalid command" : ex.getMessage();
    if (ex instanceof CommandLine.UnmatchedArgumentException unmatched) {
      return CommandError.usage(message, synopsis, unmatched.getSuggestions());
    }
    if (ex instanceof CommandLine.MissingParameterException
        || ex instanceof CommandLine.MutuallyExclusiveArgsException
        || ex instanceof CommandLine.MaxValuesExceededException) {
      return CommandError.usage(message, synopsis, List.of());
    }
    return CommandError.invalidValue(message, optionName(ex), synopsis);
  }

  /** The option a parameter exception names, when its message quotes one. */
  private static String optionName(CommandLine.ParameterException ex) {
    Matcher matcher = OPTION_IN_MESSAGE.matcher(ex.getMessage() == null ? "" : ex.getMessage());
    return matcher.find() ? matcher.group(1) : null;
  }

  /** What a verb threw: a failure it described, else a plain execution error. */
  private static CommandError executionError(Throwable ex, String message) {
    if (ex instanceof CommandFailure failure) {
      return failure.error();
    }
    if (ex instanceof IllegalStateException) {
      return CommandError.of(CommandError.NOT_APPLICABLE, message);
    }
    if (ex instanceof IllegalArgumentException) {
      return CommandError.of(CommandError.INVALID_VALUE, message);
    }
    return CommandError.of(CommandError.FAILED, message);
  }

  /** What the parsed command asks for: the capabilities declared, and the paths and URLs given. */
  static CommandPolicy.Request request(CommandLine.ParseResult parseResult) {
    CommandLine.ParseResult verb = parseResult;
    while (verb.hasSubcommand()) {
      verb = verb.subcommand();
    }
    Set<Capability> capabilities = EnumSet.noneOf(Capability.class);
    capabilities.addAll(declared(verb.commandSpec().userObject()));
    List<Path> reads = new ArrayList<>();
    List<Path> writes = new ArrayList<>();
    List<RemoteUrl> urls = new ArrayList<>();
    List<ArgSpec> matched = new ArrayList<>(verb.matchedOptions());
    matched.addAll(verb.matchedPositionals());
    for (ArgSpec arg : matched) {
      Set<Capability> needs = capabilities(arg);
      if (needs.isEmpty()) {
        continue;
      }
      capabilities.addAll(needs);
      for (String value : arg.originalStringValues()) {
        collect(needs, value, reads, writes, urls);
      }
    }
    return new CommandPolicy.Request(
        capabilities,
        reads,
        writes,
        urls,
        verb.hasMatchedOption(FORCE),
        CommandContext.current().assumeYes());
  }

  private static void collect(
      Set<Capability> needs,
      String value,
      List<Path> reads,
      List<Path> writes,
      List<RemoteUrl> urls) {
    if (value == null || value.isBlank()) {
      return;
    }
    try {
      if (needs.contains(Capability.FILE_WRITE)) {
        writes.add(Path.of(value));
      }
      if (needs.contains(Capability.FILE_READ)) {
        reads.add(Path.of(value));
      }
      if (needs.contains(Capability.NETWORK_READ)
          || needs.contains(Capability.NETWORK_WRITE)
          || needs.contains(Capability.SEND_DICOM)) {
        urls.add(RemoteUrl.parse(value));
      }
    } catch (IllegalArgumentException e) {
      // a malformed value is a usage error of the converter, not a policy decision
    }
  }

  /** The capabilities of an argument: what it declares, and what its semantic type implies. */
  static Set<Capability> capabilities(ArgSpec arg) {
    Set<Capability> needs = EnumSet.noneOf(Capability.class);
    needs.addAll(declared(arg.userObject()));
    Class<?> type = elementType(arg);
    if (type == OutputPath.class) {
      needs.add(Capability.FILE_WRITE);
    } else if (type == InputPath.class) {
      needs.add(Capability.FILE_READ);
    } else if (type == RemoteUrl.class && needs.stream().noneMatch(CommandModel::isNetwork)) {
      needs.add(Capability.NETWORK_READ);
    }
    return needs;
  }

  private static boolean isNetwork(Capability capability) {
    return capability == Capability.NETWORK_READ
        || capability == Capability.NETWORK_WRITE
        || capability == Capability.SEND_DICOM;
  }

  /** The {@link Needs} declaration of a verb class, an option field or its setter. */
  private static Set<Capability> declared(Object userObject) {
    AnnotatedElement element = annotated(userObject);
    Needs needs = element == null ? null : element.getAnnotation(Needs.class);
    return needs == null ? Set.of() : Set.of(needs.value());
  }

  /** Everything a verb may need: its own declaration and that of each of its arguments. */
  static List<Capability> capabilities(CommandSpec spec) {
    Set<Capability> needs = EnumSet.noneOf(Capability.class);
    needs.addAll(declared(spec.userObject()));
    arguments(spec).stream()
        .filter(arg -> !arg.hidden())
        .forEach(arg -> needs.addAll(capabilities(arg)));
    return needs.stream().sorted().toList();
  }

  /** The line the help shows under a verb that needs something from the machine. */
  static String needsLine(CommandSpec spec) {
    List<Capability> needs = capabilities(spec);
    return needs.isEmpty()
        ? ""
        : "Needs: " // NON-NLS
            + needs.stream().map(Capability::label).collect(Collectors.joining(", "));
  }

  /**
   * What a remote caller is told when it used a spelling of an earlier major: the verb it typed and
   * the hidden option names, with what they are called today. Silent for the other origins, whose
   * command lines were written once and are not maintained.
   */
  private static String legacyNotice(CommandLine cl, String verb, String[] args) {
    if (!CommandContext.current().isRemote()) {
      return null;
    }
    CommandLine sub = verb == null || verb.isEmpty() ? null : cl.getSubcommands().get(verb);
    if (sub == null) {
      return null;
    }
    CommandSpec spec = sub.getCommandSpec();
    List<String> legacy = new ArrayList<>();
    if (spec.usageMessage().hidden()) {
      visibleVerbs(cl.getCommandSpec())
          .filter(other -> other.userObject().getClass() == spec.userObject().getClass())
          .findFirst()
          .ifPresent(other -> legacy.add(verb + " is now " + other.name()));
    }
    List<String> typed = args == null ? List.of() : List.of(args);
    for (OptionSpec option : spec.options()) {
      if (!option.hidden()) {
        continue;
      }
      String current = renamedTo(option.userObject());
      for (String name : option.names()) {
        if (typed.stream().anyMatch(a -> a.equals(name) || a.startsWith(name + "="))) {
          legacy.add(current == null ? name + " is deprecated" : name + " is now " + current);
          break;
        }
      }
    }
    return legacy.isEmpty()
        ? null
        : "Earlier spellings used, still working: " + String.join(", ", legacy) + ".";
  }

  /**
   * Parses {@code namespace:verb args...} against the model without running it; a usage error
   * throws {@link picocli.CommandLine.ParameterException}. Meant for compatibility tests.
   */
  public static void parse(Object model, String commandLine) {
    String[] tokens = CommandManager.parseCommandString(commandLine);
    if (tokens.length == 0) {
      throw new IllegalArgumentException("Empty command line");
    }
    int colon = tokens[0].indexOf(':');
    String verb = colon > 0 ? tokens[0].substring(colon + 1) : "";
    String[] rest = java.util.Arrays.copyOfRange(tokens, 1, tokens.length);
    newCommandLine(model).parseArgs(verb.isEmpty() ? rest : prepend(verb, rest));
  }

  public static String usage(Object model) {
    return newCommandLine(model).getUsageMessage();
  }

  /**
   * Usage of one verb, its synopsis naming it {@code namespace:verb}; empty for an unknown verb.
   */
  public static Optional<String> usage(Object model, String verb) {
    CommandLine sub = newCommandLine(model).getSubcommands().get(verb);
    if (sub == null) {
      return Optional.empty();
    }
    CommandSpec spec = sub.getCommandSpec();
    String usage = sub.getUsageMessage();
    String needs = needsLine(spec);
    if (!needs.isEmpty()) {
      usage = usage + needs + System.lineSeparator();
    }
    return Optional.of(
        usage.replaceFirst(
            Pattern.quote(spec.qualifiedName(" ")),
            Matcher.quoteReplacement(spec.qualifiedName(":"))));
  }

  /** Markdown section of one verb, or empty when the namespace has no such verb. */
  public static Optional<String> markdown(String namespace, Object model, String verb) {
    CommandLine cl = newCommandLine(model);
    cl.setUsageHelpWidth(MARKDOWN_WIDTH);
    return verbSpec(cl, verb)
        .map(
            spec -> {
              StringBuilder sb = new StringBuilder();
              appendVerb(sb, namespace + ":" + verb, spec);
              return sb.toString();
            });
  }

  /** Writes the JSON descriptor of one verb; false when the namespace has no such verb. */
  public static boolean json(JsonWriter out, Object model, String verb) {
    Optional<CommandSpec> spec = verbSpec(newCommandLine(model), verb);
    spec.ifPresent(s -> jsonVerb(out, s.name(), s));
    return spec.isPresent();
  }

  private static Optional<CommandSpec> verbSpec(CommandLine cl, String verb) {
    return Optional.ofNullable(cl.getSubcommands().get(verb)).map(CommandLine::getCommandSpec);
  }

  /** The description of the root command, or an empty string. */
  public static String rootDescription(Object model) {
    String description = description(newCommandLine(model).getCommandSpec());
    return description == null ? "" : description;
  }

  /** The verbs of the model in name order, each with its description; empty without subcommands. */
  public static List<CommandHelp.Entry> verbs(Object model) {
    return visibleVerbs(newCommandLine(model).getCommandSpec())
        .sorted(Comparator.comparing(CommandSpec::name))
        .map(spec -> new CommandHelp.Entry(spec.name(), summary(spec)))
        .toList();
  }

  private static String summary(CommandSpec spec) {
    String description = description(spec);
    String summary = description == null ? "" : description;
    return isDeprecated(spec.userObject()) ? summary + " (deprecated)" : summary;
  }

  /** Markdown reference of a namespace: one section per verb with its options and parameters. */
  public static String markdown(String namespace, Object model) {
    StringBuilder sb = new StringBuilder();
    CommandLine cl = newCommandLine(model);
    cl.setUsageHelpWidth(MARKDOWN_WIDTH); // one-line synopses in the reference
    CommandSpec root = cl.getCommandSpec();
    sb.append("## ").append(namespace).append('\n').append('\n');
    appendDescription(sb, root);
    if (root.subcommands().isEmpty()) {
      appendVerb(sb, namespace, root);
    } else {
      visibleVerbs(root)
          .sorted((a, b) -> a.name().compareTo(b.name()))
          .forEach(spec -> appendVerb(sb, namespace + ":" + spec.name(), spec));
    }
    return sb.toString();
  }

  private static void appendVerb(StringBuilder sb, String qualifiedName, CommandSpec spec) {
    sb.append("### ").append(qualifiedName);
    String since = since(spec.userObject());
    if (since != null) {
      sb.append(" (since ").append(since).append(')');
    }
    if (isDeprecated(spec.userObject())) {
      sb.append(" (deprecated)");
    }
    sb.append('\n').append('\n');
    appendDescription(sb, spec);
    sb.append("Usage: `").append(synopsis(qualifiedName, spec)).append("`\n\n");
    String needs = needsLine(spec);
    if (!needs.isEmpty()) {
      sb.append(needs).append("\n\n");
    }
    List<PositionalParamSpec> params =
        spec.positionalParameters().stream().filter(p -> !p.hidden()).toList();
    if (!params.isEmpty()) {
      sb.append("| Parameter | Type | Description |\n|---|---|---|\n");
      for (PositionalParamSpec p : params) {
        sb.append("| ")
            .append(cell(p.paramLabel()))
            .append(" | ")
            .append(typeName(p))
            .append(" | ")
            .append(cell(String.join(" ", p.description())))
            .append(" |\n");
      }
      sb.append('\n');
    }
    List<OptionSpec> options =
        spec.options().stream()
            .filter(o -> !o.hidden() && !o.usageHelp() && !o.versionHelp())
            .toList();
    if (!options.isEmpty()) {
      sb.append("| Option | Type | Default | Description |\n|---|---|---|---|\n");
      for (OptionSpec o : options) {
        String def = isFlag(o) ? null : o.defaultValueString();
        if ("null".equals(def)) { // NON-NLS picocli renders an absent default as the text null
          def = null;
        }
        sb.append("| ")
            .append(cell(String.join(", ", o.names())))
            .append(" | ")
            .append(typeName(o))
            .append(" | ")
            .append(def == null ? "" : cell(def))
            .append(" | ")
            .append(cell(describe(o)))
            .append(" |\n");
      }
      sb.append('\n');
    }
    ResultSchema result = resultOf(spec);
    if (result != null && !result.fields().isEmpty()) {
      sb.append("| Result | Type | Description |\n|---|---|---|\n");
      for (var field : result.fields()) {
        sb.append("| ")
            .append(cell(field.name()))
            .append(" | ")
            .append(field.type())
            .append(" | ")
            .append(cell(field.description()))
            .append(" |\n");
      }
      sb.append('\n');
    }
  }

  /** The result a verb declares through {@link Returns}, or null. */
  static ResultSchema resultOf(CommandSpec spec) {
    return spec.userObject() instanceof Returns returns ? returns.result() : null;
  }

  /** The description of an argument with its since / deprecated markers. */
  private static String describe(ArgSpec arg) {
    StringBuilder d = new StringBuilder(String.join(" ", arg.description()));
    String since = since(arg.userObject());
    if (since != null) {
      d.append(" (since ").append(since).append(')');
    }
    if (isDeprecated(arg.userObject())) {
      d.append(" (deprecated)");
    }
    return d.toString();
  }

  /** The current spelling of an element kept under an earlier name, or null. */
  static String renamedTo(Object userObject) {
    AnnotatedElement element = annotated(userObject);
    Renamed renamed = element == null ? null : element.getAnnotation(Renamed.class);
    return renamed == null ? null : renamed.value();
  }

  /** The {@link Since} value of an annotated verb class or option field, or null. */
  static String since(Object userObject) {
    AnnotatedElement element = annotated(userObject);
    Since since = element == null ? null : element.getAnnotation(Since.class);
    return since == null ? null : since.value();
  }

  static boolean isDeprecated(Object userObject) {
    AnnotatedElement element = annotated(userObject);
    return element != null && element.isAnnotationPresent(Deprecated.class);
  }

  private static AnnotatedElement annotated(Object userObject) {
    return switch (userObject) {
      case null -> null;
      case AnnotatedElement element -> element;
      default -> userObject.getClass();
    };
  }

  /** JSON descriptor of a namespace: verbs, options, parameters, versions and deprecations. */
  public static void json(JsonWriter out, String namespace, Object model) {
    json(out, namespace, model, List.of());
  }

  /** The descriptor of a namespace with the events it publishes, when it declares some. */
  public static void json(
      JsonWriter out,
      String namespace,
      Object model,
      List<org.weasis.core.api.event.EventType> events) {
    CommandLine cl = newCommandLine(model);
    CommandSpec root = cl.getCommandSpec();
    out.beginObject().name("name").value(namespace); // NON-NLS
    out.optional("description", description(root)); // NON-NLS
    out.optional("since", since(root.userObject())); // NON-NLS
    if (isDeprecated(root.userObject())) {
      out.name("deprecated").value(true); // NON-NLS
    }
    out.name("verbs").beginArray(); // NON-NLS
    if (root.subcommands().isEmpty()) {
      jsonVerb(out, root.name(), root);
    } else {
      visibleVerbs(root)
          .sorted((a, b) -> a.name().compareTo(b.name()))
          .forEach(spec -> jsonVerb(out, spec.name(), spec));
    }
    out.endArray();
    if (!events.isEmpty()) {
      out.name("events").beginArray(); // NON-NLS
      events.forEach(type -> type.write(out));
      out.endArray();
    }
    out.endObject();
  }

  private static void jsonVerb(JsonWriter out, String name, CommandSpec spec) {
    spec.usageMessage().width(MARKDOWN_WIDTH);
    out.beginObject().name("name").value(name); // NON-NLS
    out.optional("description", description(spec)); // NON-NLS
    out.optional("since", since(spec.userObject())); // NON-NLS
    if (isDeprecated(spec.userObject())) {
      out.name("deprecated").value(true); // NON-NLS
    }
    out.name("usage").value(synopsis(spec.qualifiedName(":"), spec)); // NON-NLS
    jsonNeeds(out, capabilities(spec));
    out.name("parameters").beginArray(); // NON-NLS
    for (PositionalParamSpec p : spec.positionalParameters()) {
      if (p.hidden()) {
        continue; // a spelling of an earlier major
      }
      out.beginObject()
          .name("label") // NON-NLS
          .value(p.paramLabel())
          .name("type") // NON-NLS
          .value(typeName(p))
          .name("arity") // NON-NLS
          .value(p.arity().toString())
          .name("description") // NON-NLS
          .value(String.join(" ", p.description()));
      jsonEnumValues(out, p);
      out.endObject();
    }
    out.endArray();
    out.name("options").beginArray(); // NON-NLS
    for (OptionSpec o : spec.options()) {
      if (o.hidden() || o.usageHelp() || o.versionHelp()) {
        continue;
      }
      out.beginObject().name("names").beginArray(); // NON-NLS
      for (String n : o.names()) {
        out.value(n);
      }
      out.endArray();
      out.name("label").value(isFlag(o) ? "" : o.paramLabel()); // NON-NLS
      out.name("type").value(typeName(o)); // NON-NLS
      jsonEnumValues(out, o);
      out.name("arity").value(o.arity().toString()); // NON-NLS
      out.name("required").value(o.required()); // NON-NLS
      String def = isFlag(o) ? null : o.defaultValueString();
      out.optional("default", "null".equals(def) ? null : def); // NON-NLS
      out.name("description").value(String.join(" ", o.description())); // NON-NLS
      jsonNeeds(out, capabilities(o).stream().sorted().toList());
      out.optional("since", since(o.userObject())); // NON-NLS
      if (isDeprecated(o.userObject())) {
        out.name("deprecated").value(true); // NON-NLS
      }
      ArgGroupSpec group = o.group();
      if (group != null) {
        out.name("group") // NON-NLS
            .beginObject()
            .name("exclusive") // NON-NLS
            .value(group.exclusive())
            .name("multiplicity") // NON-NLS
            .value(group.multiplicity().toString())
            .endObject();
      }
      out.endObject();
    }
    out.endArray();
    ResultSchema result = resultOf(spec);
    if (result != null) {
      out.name("result"); // NON-NLS
      result.write(out);
    }
    out.endObject();
  }

  private static void jsonNeeds(JsonWriter out, List<Capability> needs) {
    if (needs.isEmpty()) {
      return;
    }
    out.name("needs").beginArray(); // NON-NLS
    needs.forEach(capability -> out.value(capability.key()));
    out.endArray();
  }

  private static void jsonEnumValues(JsonWriter out, ArgSpec arg) {
    List<String> values = enumValues(arg);
    if (values.isEmpty()) {
      return;
    }
    out.name("values").beginArray(); // NON-NLS
    values.forEach(out::value);
    out.endArray();
  }

  private static String description(CommandSpec spec) {
    String[] description = spec.usageMessage().description();
    return description.length == 0 ? null : String.join(" ", description).trim();
  }

  private static void appendDescription(StringBuilder sb, CommandSpec spec) {
    String[] description = spec.usageMessage().description();
    if (description.length > 0) {
      sb.append(String.join(" ", description).trim()).append('\n').append('\n');
    }
  }

  private static String synopsis(String qualifiedName, CommandSpec spec) {
    spec.usageMessage().width(MARKDOWN_WIDTH); // a fresh spec per rendering, so this is local
    String synopsis = new Help(spec, Help.defaultColorScheme(Ansi.OFF)).synopsis(0).trim();
    String prefix = spec.qualifiedName(" ");
    if (synopsis.startsWith(prefix)) {
      synopsis = synopsis.substring(prefix.length());
    }
    return (qualifiedName + " " + synopsis.replaceAll("\\s+", " ").trim()).trim();
  }

  private static String cell(String text) {
    return text.replace("|", "\\|").replace('\n', ' ').trim();
  }

  private static boolean isFlag(OptionSpec option) {
    return option.type() == boolean.class || option.type() == Boolean.class;
  }

  private static boolean isRunnable(Object userObject) {
    return userObject instanceof Runnable || userObject instanceof Callable;
  }

  private static String[] prepend(String first, String[] rest) {
    String[] argv = new String[rest.length + 1];
    argv[0] = first;
    System.arraycopy(rest, 0, argv, 1, rest.length);
    return argv;
  }
}
