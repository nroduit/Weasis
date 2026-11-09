/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.awt.GraphicsEnvironment;
import java.awt.SplashScreen;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JOptionPane;

/**
 * Stage 1 of the packaged launchers (Weasis, Dicomizer, weasis-cli). It depends on the JDK only and
 * runs before anything downloaded: the single-instance check, the resolution and verification of
 * the launcher set, then the stage-2 JVM with the set on its class path. Two end states, nothing in
 * between: the set named by the hub, complete and verified, with the hub's package; or the embedded
 * set of {@code $APPDIR/launcher/}, the installer as shipped. Every decision is one line in {@code
 * bootstrap.log}.
 */
public final class Bootstrap {

  private static final Duration TRAINING_GRACE = Duration.ofMinutes(5);

  private final String[] argv;
  private final long t0 = System.currentTimeMillis();
  private final Path appDir;
  private final Path javaHome = Path.of(System.getProperty("java.home")); // NON-NLS
  private final String kind = System.getProperty(Contract.P_KIND, Contract.KIND_APP);
  private final String appVersion =
      System.getProperty(Contract.P_JPACKAGE_VERSION, "dev"); // NON-NLS
  private final Path cacheDir = PlatformDirs.get(PlatformDirs.Dir.CACHE);
  private final Path stateDir = PlatformDirs.get(PlatformDirs.Dir.STATE);
  private final RuntimeFacts facts = RuntimeFacts.current();
  private final LauncherSets sets = new LauncherSets(cacheDir);
  private final Map<String, String> decision = new LinkedHashMap<>();

  Bootstrap(String[] argv) {
    this.argv = argv;
    this.appDir = locateAppDir();
  }

  public static void main(String[] argv) {
    int code;
    try {
      code = new Bootstrap(argv).run();
    } catch (Throwable t) {
      BootLog.note("fatal: " + t); // NON-NLS
      code = 1;
      showError("Weasis cannot start: " + t.getMessage()); // NON-NLS
    }
    System.exit(code);
  }

  /**
   * {@code $APPDIR}: the directory of this jar, unless {@code weasis.bootstrap.appdir} says
   * otherwise.
   */
  static Path locateAppDir() {
    String explicit = System.getProperty(Contract.P_APPDIR);
    if (explicit != null && !explicit.isBlank()) {
      return Path.of(explicit).toAbsolutePath();
    }
    try {
      Path location =
          Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return (Files.isDirectory(location) ? location : location.getParent()).toAbsolutePath();
    } catch (Exception e) {
      return Path.of("").toAbsolutePath(); // NON-NLS
    }
  }

  int run() throws Exception {
    boolean cli = Contract.KIND_CLI.equals(kind);
    List<String> args = List.of(argv);
    ConfigSource.applyProxy(PlatformDirs.get(PlatformDirs.Dir.CONFIG));
    ConfigSource local = ConfigSource.resolve(argv, appDir, null);

    int code;
    try (SingleInstance single = new SingleInstance(stateDir)) {
      if (!cli) {
        single.lock(lockName());
        if (single.handOver(args, activationProperties(local)) == SingleInstance.Outcome.HANDLED) {
          closeSplash();
          decision.put("set", "none"); // NON-NLS
          decision.put("reason", "handed over"); // NON-NLS
          return finish(0);
        }
      }
      code = cli ? runClient(args) : runApplication(args, single);
    }
    return finish(code);
  }

  private int finish(int code) {
    decision.put("exit", String.valueOf(code)); // NON-NLS
    StringBuilder line = new StringBuilder();
    decision.forEach(
        (k, v) -> line.append(line.isEmpty() ? "" : " ").append(k).append('=').append(v));
    BootLog.write(stateDir, line.toString());
    return code;
  }

  private String lockName() {
    String entry = System.getProperty(Contract.P_ENTRY, Contract.DEFAULT_ENTRY);
    return "bootstrap-" + entry.substring(entry.lastIndexOf('.') + 1).toLowerCase(); // NON-NLS
  }

  /** What the command line says about the instance, for the running application to compare. */
  static Map<String, String> activationProperties(ConfigSource source) {
    Map<String, String> props = new LinkedHashMap<>();
    props.put(Contract.P_VERSION, String.valueOf(Contract.PROTOCOL));
    props.put(Contract.P_USER, source.user());
    props.put(Contract.P_CODEBASE_URL, source.codebaseUrl());
    props.put(Contract.P_CONFIG_URL, source.configServiceUrl());
    props.put(Contract.P_EXTENDED_CONFIG, source.extendedConfig());
    props.put(Contract.P_PROFILE, source.profile());
    return props;
  }

  // ---------------------------------------------------------------- application

  private int runApplication(List<String> args, SingleInstance single) throws Exception {
    Http http = new Http(headers());
    ConfigSource source = ConfigSource.resolve(argv, appDir, http);
    PackageConfig embedded = embeddedConfig(source);
    Fetched remote = fetchRemote(source, http);

    Optional<LauncherSets.LauncherSet> embeddedSet = LauncherSets.embedded(appDir);
    Choice choice = chooseSet(remote, embedded, embeddedSet.orElse(null), http);
    if (choice.set() == null) {
      showError("Weasis is not installed completely: no launcher set in " + appDir); // NON-NLS
      decision.put("set", "none"); // NON-NLS
      decision.put("reason", "no embedded set"); // NON-NLS
      return 1;
    }

    String entry = System.getProperty(Contract.P_ENTRY);
    if (entry == null) {
      entry = (choice.hub() ? remote.config() : embedded).entryClass(Contract.DEFAULT_ENTRY);
    }
    Map<String, String> props = stage2Properties(choice, remote);
    AotCache aot = AotCache.of(cacheDir, entryName(entry), "", choice.set().id());
    Stage2.Plan plan =
        new Stage2.Plan(choice.set(), choice.options(), aotFlags(aot, true), props, entry, args);
    describe(choice, aot);

    closeSplash();
    if (PlatformDirs.isMacOS()) {
      single.close();
      sets.markCurrent(choice.set());
      BootLog.note("in-process launch, JVM options of the package not applied"); // NON-NLS
      return Stage2.runInProcess(plan);
    }

    Stage2.Outcome outcome =
        Stage2.run(
            Stage2.command(javaHome, plan),
            true,
            () -> {
              single.close();
              sets.markCurrent(choice.set());
            });
    decision.put("ready", String.valueOf(outcome.ready())); // NON-NLS
    if (outcome.exitCode() == Contract.EXIT_REFUSED) {
      showError(
          "Weasis refused to start in this environment; see bootstrap.log and boot.log in "
              + stateDir.resolve("log")); // NON-NLS
      return outcome.exitCode();
    }
    if (outcome.exitCode() == Contract.EXIT_UNSUPPORTED) {
      // stage 2 showed the message itself; the embedded set bundles the same natives
      decision.put("reason", "\"unsupported system\""); // NON-NLS
      return outcome.exitCode();
    }
    if (!outcome.earlyFailure()) {
      return outcome.exitCode();
    }
    return recover(choice, embedded, embeddedSet.orElse(null), aot, plan, outcome, args, single);
  }

  /** Restarts once with the embedded set, the embedded options and no AOT cache. */
  private int recover(
      Choice choice,
      PackageConfig embedded,
      LauncherSets.LauncherSet embeddedSet,
      AotCache aot,
      Stage2.Plan plan,
      Stage2.Outcome outcome,
      List<String> args,
      SingleInstance single)
      throws Exception {
    String reason = "stage 2 exited " + outcome.exitCode() + " before READY"; // NON-NLS
    // Only a cache in use can have crashed the JVM; a training run writes its cache at exit.
    boolean aotUsed = usesAotCache(plan);
    if (aotUsed) {
      aot.discard();
    }
    if (choice.hub()) {
      sets.markFailed(choice.set(), reason);
    }
    boolean recoverable = choice.hub() || aotUsed || choice.hubOptions();
    if (!recoverable || embeddedSet == null) {
      decision.put("restart", "0"); // NON-NLS
      showError(
          "Weasis could not start (exit "
              + outcome.exitCode()
              + "); see bootstrap.log and boot.log in "
              + stateDir.resolve("log")); // NON-NLS
      return outcome.exitCode();
    }
    BootLog.note("recovery: " + reason); // NON-NLS
    Choice fallback =
        new Choice(
            embeddedSet,
            options(embedded.jvmOptions(PlatformDirs.osKey())),
            false,
            false,
            choice.hub() ? reason : null);
    Map<String, String> props = stage2Properties(fallback, null);
    Stage2.Plan retry =
        new Stage2.Plan(embeddedSet, fallback.options(), List.of(), props, plan.entryClass(), args);
    decision.put("restart", "1"); // NON-NLS
    decision.put("set", "embedded"); // NON-NLS
    decision.put("id", "embedded"); // NON-NLS
    decision.remove("sha"); // NON-NLS
    decision.put("options", "embedded"); // NON-NLS
    decision.put("aot", "off"); // NON-NLS
    Stage2.Outcome second = Stage2.run(Stage2.command(javaHome, retry), true, single::close);
    decision.put("ready", String.valueOf(second.ready())); // NON-NLS
    if (second.earlyFailure()) {
      showError(
          "Weasis could not start (exit "
              + second.exitCode()
              + "); see bootstrap.log and boot.log in "
              + stateDir.resolve("log")); // NON-NLS
      return 1;
    }
    return second.exitCode();
  }

  /** The set, the stage-2 options, where they come from, and why the embedded set was forced. */
  record Choice(
      LauncherSets.LauncherSet set,
      List<String> options,
      boolean hub,
      boolean hubOptions,
      String fallback) {}

  private Choice chooseSet(
      Fetched remote, PackageConfig embedded, LauncherSets.LauncherSet embeddedSet, Http http) {
    List<String> embeddedOptions = options(embedded.jvmOptions(PlatformDirs.osKey()));
    if (remote == null || !remote.config().declaresLauncherSet()) {
      BootLog.note(
          remote == null
              ? "installed configuration"
              : "package declares no launcher set"); // NON-NLS
      return new Choice(embeddedSet, embeddedOptions, false, false, null);
    }
    Optional<String> refusal = facts.refusal(remote.config());
    if (refusal.isPresent()) {
      return new Choice(embeddedSet, embeddedOptions, false, false, refusal.get());
    }
    Integrity.Level level = integrityLevel();
    List<Certificate> trusted = level == Integrity.Level.SIGNED ? truststore() : List.of();
    Object result = sets.resolve(remote.config(), http, level, trusted, this::progress);
    if (result instanceof LauncherSets.Refusal r) {
      return new Choice(embeddedSet, embeddedOptions, false, false, r.reason());
    }
    LauncherSets.LauncherSet set = (LauncherSets.LauncherSet) result;
    List<String> refused = new ArrayList<>();
    List<String> options =
        options(JvmOptions.allowed(remote.config().jvmOptions(PlatformDirs.osKey()), refused));
    if (!refused.isEmpty()) {
      BootLog.note("served options refused: " + refused); // NON-NLS
    }
    return new Choice(set, options, true, true, null);
  }

  /** The stage-2 options with {@code $APPDIR} resolved to this image. */
  private List<String> options(List<String> options) {
    return JvmOptions.substitute(options, appDir.toString());
  }

  private void progress(long bytes) {
    // the splash of stage 1 shows no progress bar; the bytes are visible in a trace
  }

  private Integrity.Level integrityLevel() {
    String level = System.getProperty(Contract.P_INTEGRITY);
    if ("digest".equalsIgnoreCase(level)) { // NON-NLS
      BootLog.note("integrity lowered to digest by " + Contract.P_INTEGRITY); // NON-NLS
      return Integrity.Level.DIGEST;
    }
    if (!"signed".equalsIgnoreCase(level) && truststore().isEmpty()) { // NON-NLS
      BootLog.note("no truststore: the launcher set is checked by digest only"); // NON-NLS
      return Integrity.Level.DIGEST;
    }
    return Integrity.Level.SIGNED;
  }

  private List<Certificate> truststore() {
    String dir = System.getProperty("weasis.bootstrap.truststore"); // NON-NLS
    Path path =
        dir != null ? Path.of(dir) : appDir.resolve("launcher").resolve("truststore"); // NON-NLS
    return Integrity.loadTruststore(path);
  }

  private Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", facts.userAgent(appVersion)); // NON-NLS
    headers.put("Weasis-Runtime-Modules", facts.modulesHeader()); // NON-NLS
    sets.failedSha().ifPresent(sha -> headers.put("Weasis-Bootstrap-Failed", sha)); // NON-NLS
    String auth = System.getProperty("http.authorization"); // NON-NLS
    if (auth != null && !auth.isBlank()) {
      headers.put("Authorization", auth); // NON-NLS
    }
    return headers;
  }

  /** The fetched remote configuration: its bytes on disk, the URI, and the keys read from it. */
  record Fetched(PackageConfig config, URI uri, Path file) {}

  private Fetched fetchRemote(ConfigSource source, Http http) {
    if (!source.isRemote()) {
      return null;
    }
    URI uri = source.configUri().orElseThrow();
    if (!source.authorization().isEmpty()) {
      System.setProperty("http.authorization", source.authorization()); // NON-NLS
    }
    try {
      byte[] json = new Http(headers()).fetch(uri);
      PackageConfig config = PackageConfig.parse(json, source.codebaseUrl());
      Path file = writeConfigCache(uri, json);
      return new Fetched(mergeExtended(config, source, http), uri, file);
    } catch (IOException | RuntimeException e) {
      BootLog.note("configuration unreachable: " + e.getMessage()); // NON-NLS
      return null;
    }
  }

  private PackageConfig mergeExtended(PackageConfig config, ConfigSource source, Http http) {
    String extended = source.extendedConfig();
    if (extended.isEmpty() || extended.startsWith("file:conf/")) { // NON-NLS
      return config;
    }
    try {
      return config.merge(http.fetch(new URI(extended)));
    } catch (Exception e) {
      BootLog.note("extended configuration unreachable: " + e.getMessage()); // NON-NLS
      return config;
    }
  }

  private PackageConfig embeddedConfig(ConfigSource source) {
    Path conf = appDir.resolve("conf"); // NON-NLS
    PackageConfig config = PackageConfig.empty();
    try {
      Path base = conf.resolve("base.json"); // NON-NLS
      if (Files.isRegularFile(base)) {
        config =
            PackageConfig.parse(
                Files.readAllBytes(base), appDir.toUri().toString().replaceAll("/$", ""));
      }
      String extended = source.extendedConfig();
      if (extended.startsWith("file:conf/")) { // NON-NLS
        Path file = appDir.resolve(extended.substring(5));
        if (Files.isRegularFile(file)) {
          config = config.merge(Files.readAllBytes(file));
        }
      }
    } catch (IOException | RuntimeException e) {
      BootLog.note("embedded configuration unreadable: " + e); // NON-NLS
    }
    return config;
  }

  /**
   * The configuration stage 2 reads instead of fetching again; the temp directory when the cache is
   * degraded.
   */
  private Path writeConfigCache(URI uri, byte[] json) {
    String name =
        Integrity.sha256(uri.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16)
            + ".json"; // NON-NLS
    for (Path dir :
        List.of(
            cacheDir.resolve("config"),
            Path.of(System.getProperty("java.io.tmpdir"), "weasis-bootstrap"))) { // NON-NLS
      try {
        Files.createDirectories(dir);
        Path tmp = dir.resolve(name + ".part-" + ProcessHandle.current().pid()); // NON-NLS
        Files.write(tmp, json);
        Path file = dir.resolve(name);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return file;
      } catch (IOException e) {
        BootLog.note(
            "configuration cache not writable in " + dir + ": " + e.getMessage()); // NON-NLS
      }
    }
    return null;
  }

  private Map<String, String> stage2Properties(Choice choice, Fetched remote) {
    Map<String, String> props = new LinkedHashMap<>();
    for (String name : System.getProperties().stringPropertyNames()) {
      if (name.startsWith("weasis.") // NON-NLS
          && !name.equals(Contract.P_ENTRY)
          && !name.equals(Contract.P_KIND)
          && !name.equals(Contract.P_TRACE)) {
        props.put(name, System.getProperty(name));
      }
    }
    props.put(Contract.P_JPACKAGE_VERSION, appVersion);
    props.put(Contract.P_LAUNCH_TYPE, "NATIVE"); // NON-NLS
    props.put(Contract.P_VERSION, String.valueOf(Contract.PROTOCOL));
    props.put(Contract.P_APPDIR, appDir.toString());
    props.put(Contract.P_SET, choice.set().kind());
    props.put(Contract.P_T0, String.valueOf(t0));
    if (remote != null && remote.file() != null && choice.fallback() == null) {
      props.put(Contract.P_CONFIG_CACHE, remote.file().toString());
      props.put(Contract.P_CONFIG_CACHE_URI, remote.uri().toString());
    }
    if (choice.fallback() != null) {
      props.put(Contract.P_FALLBACK, choice.fallback());
    }
    return props;
  }

  private List<String> aotFlags(AotCache aot, boolean train) {
    if (aot == null) {
      return List.of();
    }
    try {
      aot.prepare();
      return aot.flags(train);
    } catch (IOException e) {
      BootLog.note("AOT cache directory unusable: " + e); // NON-NLS
      return List.of();
    }
  }

  private void describe(Choice choice, AotCache aot) {
    decision.put("set", choice.set().kind()); // NON-NLS
    decision.put("id", choice.set().id()); // NON-NLS
    if (!choice.set().sha().isEmpty()) {
      decision.put("sha", choice.set().sha()); // NON-NLS
    }
    if (choice.fallback() != null) {
      decision.put("reason", "\"" + choice.fallback() + "\""); // NON-NLS
    }
    decision.put("jdk", String.valueOf(facts.jdk())); // NON-NLS
    decision.put("options", choice.hubOptions() ? "hub" : "embedded"); // NON-NLS
    String aotMode = "off"; // NON-NLS
    if (aot != null && !aot.disabled()) {
      aotMode = aot.exists() ? "use" : "train"; // NON-NLS
    }
    decision.put("aot", aotMode); // NON-NLS
    decision.put("restart", "0"); // NON-NLS
  }

  private static boolean usesAotCache(Stage2.Plan plan) {
    return plan.aotFlags().stream().anyMatch(f -> f.startsWith("-XX:AOTCache=")); // NON-NLS
  }

  private static String entryName(String entryClass) {
    return entryClass.substring(entryClass.lastIndexOf('.') + 1).toLowerCase(); // NON-NLS
  }

  // ---------------------------------------------------------------- client

  /**
   * The client fetches nothing: it runs the set the application ran last, else the embedded one.
   */
  private int runClient(List<String> args) throws Exception {
    LauncherSets.LauncherSet set =
        sets.current().or(() -> LauncherSets.embedded(appDir)).orElse(null);
    if (set == null) {
      System.err.println(
          "weasis-cli is not installed completely: no launcher set in " + appDir); // NON-NLS
      return 5;
    }
    ConfigSource local = ConfigSource.resolve(argv, appDir, null);
    PackageConfig embedded = embeddedConfig(local);
    List<String> options = options(embedded.jvmOptions(Contract.KIND_CLI));
    String entry =
        System.getProperty(Contract.P_ENTRY, "org.weasis.launcher.CommandClient"); // NON-NLS
    Choice choice = new Choice(set, options, !set.embedded(), false, null);
    AotCache aot = AotCache.of(cacheDir, "weasis-cli", "cli", set.id()); // NON-NLS
    Stage2.Plan plan =
        new Stage2.Plan(
            set, options, aotFlags(aot, false), stage2Properties(choice, null), entry, args);
    describe(choice, aot);
    if (PlatformDirs.isMacOS()) {
      return Stage2.runInProcess(plan);
    }
    Stage2.Outcome outcome = Stage2.run(Stage2.command(javaHome, plan), false, () -> {});
    if (outcome.earlyFailure() && usesAotCache(plan)) {
      aot.discard();
      decision.put("restart", "1"); // NON-NLS
      decision.put("aot", "off"); // NON-NLS
      Stage2.Plan retry = new Stage2.Plan(set, options, List.of(), plan.properties(), entry, args);
      return Stage2.run(Stage2.command(javaHome, retry), false, () -> {}).exitCode();
    }
    if (aot != null && !aot.exists() && !aot.disabled()) {
      trainClientCache(aot, plan);
    }
    return outcome.exitCode();
  }

  /** Trains the client cache in a detached process, at most once per grace period. */
  private void trainClientCache(AotCache aot, Stage2.Plan plan) {
    try {
      Path marker = aot.trainingMarker();
      if (Files.exists(marker)
          && Files.getLastModifiedTime(marker)
              .toInstant()
              .plus(TRAINING_GRACE)
              .isAfter(Instant.now())) {
        return;
      }
      Files.writeString(marker, Instant.now().toString());
      Stage2.Plan training =
          new Stage2.Plan(
              plan.set(),
              plan.jvmOptions(),
              aot.trainingFlags(),
              plan.properties(),
              plan.entryClass(),
              List.of("--aot-train")); // NON-NLS
      Stage2.spawnDetached(Stage2.command(javaHome, training));
    } catch (IOException e) {
      BootLog.note("cannot train the client cache: " + e); // NON-NLS
    }
  }

  // ---------------------------------------------------------------- ui

  private static void closeSplash() {
    try {
      SplashScreen splash = SplashScreen.getSplashScreen();
      if (splash != null) {
        splash.close();
      }
    } catch (Exception e) {
      // no splash to close
    }
  }

  static void showError(String message) {
    System.err.println(message);
    try {
      if (!GraphicsEnvironment.isHeadless()
          && !Contract.KIND_CLI.equals(System.getProperty(Contract.P_KIND))) {
        JOptionPane.showMessageDialog(
            null, message, "Weasis", JOptionPane.ERROR_MESSAGE); // NON-NLS
      }
    } catch (Exception e) {
      // no display
    }
  }
}
