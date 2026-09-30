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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandHelp;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The {@code weasis-cli} entry point: sends one command to the running Weasis instance through the
 * singleton socket and prints its result. Loads no configuration and no plugin.
 */
@Command(
    name = "weasis-cli",
    mixinStandardHelpOptions = true,
    versionProvider = CommandClient.Version.class,
    description = "Runs a command in the running Weasis instance and prints its result.",
    footer = {
      "",
      "Exit status: 0 ok, 1 command error, 2 usage error, 3 no running instance, 4 timeout,",
      "5 connection error, 6 not permitted remotely.",
      "Help: weasis-cli help [<namespace>[:<verb>]] browses the commands (interactive in a terminal);",
      "weasis-cli help --markdown prints the whole reference.",
      "Examples: weasis-cli workspace:list, weasis-cli view2d:window-level --window 400 --level 40"
    })
public final class CommandClient implements Callable<Integer> {

  private static final String LOCALHOST = "127.0.0.1"; // NON-NLS
  static final String LAUNCH_COMMAND_ENV = "WEASIS_LAUNCH_COMMAND"; // NON-NLS
  static final String LAUNCH_DIR_ENV = "WEASIS_LAUNCH_DIR"; // NON-NLS
  private static final Pattern LAUNCH_TOKEN = Pattern.compile("\"([^\"]*)\"|\\S+");
  static final int DEFAULT_TIMEOUT = 30;
  private static final int LAUNCH_WAIT_SECONDS = 90;
  private static final int HELP_TIMEOUT = 3;
  private static final String SECTION_NAMESPACES = "instanceCommands"; // NON-NLS
  private static final String CLIENT = "weasis-cli"; // NON-NLS

  @Option(
      names = "--instance",
      paramLabel = "<id>",
      description = "Instance id prefix when several Weasis instances are running.")
  String instance;

  @Option(
      names = "--timeout",
      paramLabel = "<s>",
      defaultValue = "" + DEFAULT_TIMEOUT,
      description = "Seconds to wait for the command (default: ${DEFAULT-VALUE}, 0 = default).")
  int timeout;

  @Spec CommandSpec spec;

  @Option(names = "--launch", description = "Start Weasis when no instance is running.")
  boolean launch;

  @Option(
      names = "--script",
      paramLabel = "<file>",
      description = "Run the commands of this file in one call: short for script:run --file.")
  String script;

  @Option(
      names = {"-y", "--assume-yes"},
      description =
          "Answer yes to the confirmations the command would ask in Weasis (e.g. build the MPR "
              + "volume with geometric rectification despite few slices) instead of showing them.")
  boolean assumeYes;

  @Option(names = ClientAotCache.TRAIN_OPTION, hidden = true)
  boolean aotTrain;

  @Option(names = ClientAotCache.VERIFY_OPTION, hidden = true)
  boolean aotVerify;

  @Parameters(
      paramLabel = "<command>",
      description = "namespace:verb (or namespace verb) followed by its arguments.")
  List<String> command = new ArrayList<>();

  private final Supplier<List<SingletonFiles.Instance>> instances;
  private final BufferedReader in;
  private final boolean terminal;
  private final PrintStream out;
  private final PrintStream err;

  CommandClient(
      Supplier<List<SingletonFiles.Instance>> instances,
      BufferedReader in,
      boolean terminal,
      PrintStream out,
      PrintStream err) {
    this.instances = instances;
    this.in = in;
    this.terminal = terminal;
    this.out = out;
    this.err = err;
  }

  public static void main(String[] args) {
    quietLogging();
    int status = run(args, SingletonFiles::list, System.out, System.err);
    if (status == CommandResult.OK || status == CommandResult.NO_INSTANCE) {
      ClientAotCache.afterRun();
    }
    System.exit(status);
  }

  static int run(
      String[] args,
      Supplier<List<SingletonFiles.Instance>> instances,
      PrintStream out,
      PrintStream err) {
    BufferedReader stdin =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    return run(args, instances, stdin, hasTerminal(), out, err);
  }

  /** Runs with an explicit input and terminal state, for the help browser and its tests. */
  static int run(
      String[] args,
      Supplier<List<SingletonFiles.Instance>> instances,
      BufferedReader in,
      boolean terminal,
      PrintStream out,
      PrintStream err) {
    CommandLine cl = new CommandLine(new CommandClient(instances, in, terminal, out, err));
    cl.setStopAtPositional(true);
    cl.setOut(new java.io.PrintWriter(out, true));
    cl.setErr(new java.io.PrintWriter(err, true));
    addNamespacesSection(cl, instances);
    return cl.execute(args);
  }

  /**
   * Adds to the usage help the namespaces of the running instance, unknown to the client itself.
   */
  private static void addNamespacesSection(
      CommandLine cl, Supplier<List<SingletonFiles.Instance>> instances) {
    List<String> keys = new ArrayList<>(cl.getHelpSectionKeys());
    keys.add(keys.indexOf(UsageMessageSpec.SECTION_KEY_FOOTER), SECTION_NAMESPACES);
    cl.setHelpSectionKeys(keys);
    cl.getHelpSectionMap().put(SECTION_NAMESPACES, help -> namespaces(instances));
  }

  private static String namespaces(Supplier<List<SingletonFiles.Instance>> instances) {
    Optional<SingletonFiles.Instance> target =
        instances.get().stream().filter(i -> SingletonFiles.isPrivate(i.file())).findFirst();
    if (target.isEmpty()) {
      return "%nCommands: no running Weasis instance (start one, or use --launch).%n".formatted();
    }
    try {
      CommandResult result =
          send(
              target.get(),
              List.of(CommandManager.HELP, CommandManager.INDEX_OPTION),
              HELP_TIMEOUT);
      if (result.isOk() && !result.body().isBlank()) {
        CommandHelp.Index index = CommandHelp.parseIndex(result.body());
        return "%nCommands in the running instance:%n%s"
            .formatted(CommandHelp.table("", index.entries(), false));
      }
    } catch (IOException e) {
      // an unreachable instance must not break the usage help
    }
    return "";
  }

  private static boolean hasTerminal() {
    Console console = System.console();
    return console != null && console.isTerminal();
  }

  @Override
  public Integer call() {
    if (aotVerify) {
      return CommandResult.OK;
    }
    if (aotTrain) {
      ClientAotCache.trainingWorkload();
      return CommandResult.OK;
    }
    if (script != null) {
      // one connection for the whole file; the remaining arguments are the options of the run
      List<String> run = new ArrayList<>(List.of("script:run", "--file", script)); // NON-NLS
      run.addAll(command);
      command = run;
    }
    if (command.isEmpty()) {
      err.println("Missing command. Try: weasis-cli help");
      return CommandResult.USAGE;
    }
    if (timeout < 0) {
      throw new ParameterException(spec.commandLine(), "--timeout must be 0 or positive");
    }
    if (timeout == 0) {
      timeout = DEFAULT_TIMEOUT;
    }
    Optional<SingletonFiles.Instance> target = resolveInstance();
    if (target.isEmpty()) {
      return CommandResult.NO_INSTANCE;
    }
    if (!SingletonFiles.isPrivate(target.get().file())) {
      err.println("Refusing to use " + target.get().file() + ": readable by other users");
      return CommandResult.CONNECTION;
    }
    try {
      return CommandManager.HELP.equals(command.getFirst())
          ? help(target.get())
          : execute(target.get());
    } catch (SocketTimeoutException e) {
      err.println("No answer within " + timeout + " s");
      return CommandResult.TIMEOUT;
    } catch (IOException e) {
      err.println("Cannot reach the Weasis instance: " + e.getMessage());
      return CommandResult.CONNECTION;
    }
  }

  private int execute(SingletonFiles.Instance target) throws IOException {
    CommandResult result = send(target, command, timeout, assumeYes);
    print(result);
    return result.status();
  }

  /**
   * {@code help [<namespace>[:<verb>]]}: browsed interactively in a terminal, otherwise printed
   * with the commands that lead to the next and the previous level. An output option ({@code
   * --markdown}, {@code --json}, ...) always prints.
   */
  private int help(SingletonFiles.Instance target) throws IOException {
    List<String> positional = command.stream().skip(1).filter(a -> !a.startsWith("-")).toList();
    boolean navigable = positional.size() == command.size() - 1;
    HelpBrowser.Location location = HelpBrowser.Location.of(positional);
    if (navigable && terminal) {
      return new HelpBrowser(argv -> send(target, argv, timeout), in, out, err).browse(location);
    }
    CommandResult result = send(target, command, timeout);
    print(result);
    if (navigable && result.isOk()) {
      out.println();
      out.print(hints(location));
      out.flush();
    }
    return result.status();
  }

  /** The commands that continue the static help from {@code location}. */
  static String hints(HelpBrowser.Location location) {
    if (location.isRoot()) {
      return hint("help <namespace>", "verbs of a namespace")
          + hint("help <namespace>:<verb>", "one verb in detail");
    }
    String namespace = location.namespace();
    if (location.isVerb()) {
      return hint("help " + namespace, "back to the verbs of " + namespace);
    }
    return hint("help " + namespace + ":<verb>", "one verb in detail")
        + hint("help", "back to the namespaces");
  }

  private static String hint(String arguments, String meaning) {
    return "%-36s %s%n".formatted(CLIENT + " " + arguments, meaning);
  }

  private void print(CommandResult result) {
    if (!result.error().isEmpty()) {
      err.print(result.error());
      if (!result.error().endsWith("\n")) {
        err.println();
      }
    }
    if (!result.body().isEmpty()) {
      out.print(result.body());
      if (!result.body().endsWith("\n")) {
        out.println();
      }
    }
    out.flush();
    err.flush();
  }

  private Optional<SingletonFiles.Instance> resolveInstance() {
    Optional<SingletonFiles.Instance> found = select(instances.get());
    if (found.isPresent()) {
      return found;
    }
    if (launch) {
      return launchAndWait();
    }
    if (instance != null && !instances.get().isEmpty()) {
      err.println("No running instance matches '" + instance + "'");
    } else {
      err.println("No running Weasis instance (use --launch to start one)");
    }
    return Optional.empty();
  }

  private Optional<SingletonFiles.Instance> select(List<SingletonFiles.Instance> running) {
    List<SingletonFiles.Instance> candidates =
        instance == null
            ? running
            : running.stream().filter(i -> i.id().startsWith(instance)).toList();
    if (candidates.size() > 1) {
      err.println(
          "Several instances are running, choose one with --instance: "
              + candidates.stream().map(SingletonFiles.Instance::id).toList());
      return Optional.empty();
    }
    return candidates.stream().findFirst();
  }

  private Optional<SingletonFiles.Instance> launchAndWait() {
    List<String> launcher = launchCommand();
    if (launcher.isEmpty()) {
      err.println(
          "Cannot find the Weasis launcher next to this executable (set "
              + LAUNCH_COMMAND_ENV
              + " to start it from a source build)");
      return Optional.empty();
    }
    try {
      // Detached from the terminal: an inherited console would keep printing the application log
      // over the shell prompt long after this client exited. Weasis writes its own log file.
      ProcessBuilder builder =
          new ProcessBuilder(launcher)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD);
      launchDirectory().ifPresent(builder::directory);
      builder.start();
    } catch (IOException e) {
      err.println("Cannot start " + String.join(" ", launcher) + ": " + e.getMessage());
      return Optional.empty();
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LAUNCH_WAIT_SECONDS);
    while (System.nanoTime() < deadline) {
      Optional<SingletonFiles.Instance> found = select(instances.get());
      if (found.isPresent()) {
        return found;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
    }
    err.println("Weasis did not start within " + LAUNCH_WAIT_SECONDS + " s");
    return Optional.empty();
  }

  /**
   * The launcher installed next to this client, else the command line of {@link
   * #LAUNCH_COMMAND_ENV} for a source build or a custom start-up.
   */
  private static List<String> launchCommand() {
    return siblingLauncher()
        .map(launcher -> List.of(launcher.toString()))
        .orElseGet(() -> customLaunchCommand(System.getenv(LAUNCH_COMMAND_ENV)));
  }

  /** Splits the command line on whitespace, double quotes protecting a path that contains some. */
  static List<String> customLaunchCommand(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    List<String> tokens = new ArrayList<>();
    Matcher matcher = LAUNCH_TOKEN.matcher(value);
    while (matcher.find()) {
      tokens.add(matcher.group(1) == null ? matcher.group() : matcher.group(1));
    }
    return List.copyOf(tokens);
  }

  /** Working directory of the launched process, when {@link #LAUNCH_DIR_ENV} points to one. */
  private static Optional<File> launchDirectory() {
    return Optional.ofNullable(System.getenv(LAUNCH_DIR_ENV))
        .filter(dir -> !dir.isBlank())
        .map(Path::of)
        .filter(Files::isDirectory)
        .map(Path::toFile);
  }

  // jpackage puts every launcher of the application in the same directory.
  private static Optional<Path> siblingLauncher() {
    return ProcessHandle.current()
        .info()
        .command()
        .map(Path::of)
        .map(Path::getParent)
        .flatMap(
            dir -> {
              for (String name : List.of("Weasis", "Weasis.exe", "weasis")) { // NON-NLS
                Path candidate = dir.resolve(name);
                if (Files.isExecutable(candidate)) {
                  return Optional.of(candidate);
                }
              }
              return Optional.empty();
            });
  }

  /** Sends {@code argv} to a running instance and returns the server's result. */
  static CommandResult send(SingletonFiles.Instance target, List<String> argv, int timeoutSeconds)
      throws IOException {
    return send(target, argv, timeoutSeconds, false);
  }

  static CommandResult send(
      SingletonFiles.Instance target, List<String> argv, int timeoutSeconds, boolean assumeYes)
      throws IOException {
    try (Socket socket = new Socket(LOCALHOST, target.port())) {
      // the server answers TIMEOUT itself; the socket only guards against a vanished server
      socket.setSoTimeout((int) Math.min((timeoutSeconds + 5L) * 1000, Integer.MAX_VALUE));
      OutputStream os = socket.getOutputStream();
      os.write(CommandProtocol.ENCODING_UTF8);
      PrintStream request = new PrintStream(os, true, StandardCharsets.UTF_8);
      CommandProtocol.writeRequest(
          request, target.secret(), new CommandProtocol.Request(argv, timeoutSeconds, assumeYes));
      BufferedReader in =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      return CommandProtocol.readResponse(in);
    }
  }

  private static void quietLogging() {
    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
      context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
    }
  }

  static final class Version implements IVersionProvider {
    @Override
    public String[] getVersion() {
      return new String[] {
        "weasis-cli " // NON-NLS
            + CommandManager.applicationVersion()
            + " (command API "
            + CommandManager.API_VERSION
            + ", protocol "
            + CommandProtocol.VERSION
            + ")"
      };
    }
  }
}
