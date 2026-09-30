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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * What a remote caller is allowed to do, decided by the instance and never by the caller: where a
 * verb may write, whether it may reach the network, and whether it may open a page that shows
 * unmasked identity. Only the {@link CommandOrigin#REMOTE} origin is policed; a launch argument and
 * a {@code weasis://} link are the integration path of the site and stay as they were.
 *
 * <p>The defaults are closed: files go to the session directory and the user's home, and the
 * network is refused in both directions.
 */
public record CommandPolicy(
    RemotePolicy remote,
    FileRules files,
    NetworkRules network,
    boolean assumeYesAllowed,
    boolean shutdownAllowed,
    boolean pixelAttestAllowed,
    boolean locked) {

  /** Prefix of the system properties that override one key each. */
  public static final String PROPERTY_PREFIX = "weasis.commands.policy."; // NON-NLS

  private static final String ALLOW = "allow"; // NON-NLS
  private static final String LOCALHOST = "localhost"; // NON-NLS
  private static final String HTTPS = "https"; // NON-NLS

  /**
   * Where files may be read and written.
   *
   * @param writeRoots the only places a remote caller's files may land
   * @param writeToShare whether a root on a network share may be written to
   * @param readFromShare whether a file on a network share may be read
   * @param share how a share is recognized, so a test can state it
   */
  public record FileRules(
      List<Path> writeRoots, boolean writeToShare, boolean readFromShare, Predicate<Path> share) {

    public FileRules {
      writeRoots = List.copyOf(writeRoots);
    }

    public static FileRules defaults() {
      return new FileRules(defaultRoots(), false, true, NetworkPaths::isNetwork);
    }
  }

  /** Whether the network may be reached, and which hosts when it may. */
  public record NetworkRules(
      boolean read, Set<String> readHosts, boolean write, Set<String> writeHosts) {

    public NetworkRules {
      readHosts = Set.copyOf(readHosts);
      writeHosts = Set.copyOf(writeHosts);
    }

    public static NetworkRules closed() {
      return new NetworkRules(false, Set.of(), false, Set.of());
    }
  }

  /** A refusal: the policy key the caller can quote, and why the call was refused. */
  public record Refusal(String key, String reason) {

    private static final String PREFIX = "Refused by policy "; // NON-NLS

    /** The line a caller reads on the error stream. */
    public String message() {
      return PREFIX + key + ": " + reason;
    }

    /** The key inside such a line, for the audit, or {@code null} when it is another message. */
    public static String keyOf(String message) {
      if (message == null || !message.startsWith(PREFIX)) {
        return null;
      }
      int end = message.indexOf(':', PREFIX.length());
      return end < 0 ? null : message.substring(PREFIX.length(), end);
    }
  }

  /**
   * What a call asks for: the capabilities it declared and the values that carry them.
   *
   * @param force whether the caller asked to overwrite a file it did not write itself
   * @param confirmed whether the caller answered the questions the verb would ask ({@code -y})
   */
  public record Request(
      Set<Capability> capabilities,
      List<Path> reads,
      List<Path> writes,
      List<RemoteUrl> urls,
      boolean force,
      boolean confirmed) {

    public Request {
      capabilities = Set.copyOf(capabilities);
      reads = List.copyOf(reads);
      writes = List.copyOf(writes);
      urls = List.copyOf(urls);
    }

    public Request(
        Set<Capability> capabilities, List<Path> reads, List<Path> writes, List<RemoteUrl> urls) {
      this(capabilities, reads, writes, urls, false, false);
    }

    public static Request of(Capability... capabilities) {
      return new Request(Set.of(capabilities), List.of(), List.of(), List.of());
    }

    public boolean needs(Capability capability) {
      return capabilities.contains(capability);
    }
  }

  public static CommandPolicy defaults() {
    return new CommandPolicy(
        RemotePolicy.allowAll(),
        FileRules.defaults(),
        NetworkRules.closed(),
        true,
        true,
        false,
        false);
  }

  /** The policy of this instance: the defaults, with one system property per key that changes. */
  public static CommandPolicy fromSystemProperties() {
    return from(
        key -> System.getProperty(PROPERTY_PREFIX + key), RemotePolicy.fromSystemProperties());
  }

  /** The policy a lookup of the keys below {@value #PROPERTY_PREFIX} describes. */
  static CommandPolicy from(UnaryOperator<String> lookup, RemotePolicy remote) {
    CommandPolicy defaults = defaults();
    FileRules files =
        new FileRules(
            roots(lookup.apply("files.write.roots"), defaults.files().writeRoots()), // NON-NLS
            flag(lookup, "files.write.networkFs", defaults.files().writeToShare()), // NON-NLS
            flag(lookup, "files.read.networkFs", defaults.files().readFromShare()), // NON-NLS
            defaults.files().share());
    NetworkRules network =
        new NetworkRules(
            flag(lookup, "network.read", false), // NON-NLS
            hosts(lookup.apply("network.readHosts")), // NON-NLS
            flag(lookup, "network.write", false), // NON-NLS
            hosts(lookup.apply("network.writeHosts"))); // NON-NLS
    return new CommandPolicy(
        remote,
        files,
        network,
        flag(lookup, "confirmations.assumeYes", true), // NON-NLS
        flag(lookup, "shutdown", true), // NON-NLS
        flag(lookup, "pixels.attest", false), // NON-NLS
        flag(lookup, "locked", false)); // NON-NLS
  }

  /** The same policy with other file rules, for a configuration step or a test. */
  public CommandPolicy withFiles(FileRules rules) {
    return new CommandPolicy(
        remote, rules, network, assumeYesAllowed, shutdownAllowed, pixelAttestAllowed, locked);
  }

  /** The same policy with other network rules. */
  public CommandPolicy withNetwork(NetworkRules rules) {
    return new CommandPolicy(
        remote, files, rules, assumeYesAllowed, shutdownAllowed, pixelAttestAllowed, locked);
  }

  /** The same policy with stopping the application allowed or refused. */
  public CommandPolicy withShutdown(boolean allowed) {
    return new CommandPolicy(
        remote, files, network, assumeYesAllowed, allowed, pixelAttestAllowed, locked);
  }

  /** The same policy with pixel attestation by a remote caller allowed or refused. */
  public CommandPolicy withPixelAttest(boolean allowed) {
    return new CommandPolicy(
        remote, files, network, assumeYesAllowed, shutdownAllowed, allowed, locked);
  }

  /**
   * What refuses this call, or empty when it may run. Everything but {@link CommandOrigin#REMOTE}
   * passes.
   */
  public Optional<Refusal> check(CommandOrigin origin, Request request) {
    if (origin != CommandOrigin.REMOTE) {
      return Optional.empty();
    }
    if (request.needs(Capability.UI_QUERY)) {
      return refuse(
          "ui.identity", // NON-NLS
          "a page showing unmasked identity cannot be opened for a remote caller; read the values"
              + " through the commands instead");
    }
    Optional<Refusal> attest = checkPixelAttest(request);
    if (attest.isPresent()) {
      return attest;
    }
    Optional<Refusal> shutdown = checkShutdown(request);
    if (shutdown.isPresent()) {
      return shutdown;
    }
    Optional<Refusal> network = checkNetwork(request);
    if (network.isPresent()) {
      return network;
    }
    return checkFiles(request);
  }

  /**
   * Stopping the application ends the session of everyone using it, so a remote caller needs the
   * instance to allow it at all, and then has to say it means it: the policy answers whether the
   * verb may run, {@code -y} answers the question the application would ask on screen.
   */
  private Optional<Refusal> checkShutdown(Request request) {
    if (!request.needs(Capability.SHUTDOWN)) {
      return Optional.empty();
    }
    if (!shutdownAllowed) {
      return refuse("application.shutdown", "this instance does not let a remote caller stop it");
    }
    if (!request.confirmed()) {
      return refuse(
          "application.shutdown", // NON-NLS
          assumeYesAllowed
              ? "stopping the application ends every session it serves; repeat the command with -y"
              : "stopping the application ends every session it serves, and this instance takes no"
                  + " confirmation from a remote caller");
    }
    return Optional.empty();
  }

  /**
   * Declaring pixels free of burned-in identity is a person's judgment: the refusal it lifts exists
   * because nothing in the file can be trusted to say so. A remote caller may only relay a review a
   * person made elsewhere, and only on an instance that says it accepts such relays.
   */
  private Optional<Refusal> checkPixelAttest(Request request) {
    if (!request.needs(Capability.PIXEL_ATTEST) || pixelAttestAllowed) {
      return Optional.empty();
    }
    return refuse(
        "pixels.attest", // NON-NLS
        "only a person reviewing the image in Weasis can declare it free of burned-in identity;"
            + " an instance that relays reviews made elsewhere sets "
            + PROPERTY_PREFIX
            + "pixels.attest=allow");
  }

  private Optional<Refusal> checkNetwork(Request request) {
    boolean reads = request.needs(Capability.NETWORK_READ);
    boolean writes =
        request.needs(Capability.NETWORK_WRITE) || request.needs(Capability.SEND_DICOM);
    if (reads && !network.read()) {
      return refuse("network.read", "fetching from the network is closed to remote callers");
    }
    if (writes && !network.write()) {
      return refuse("network.write", "sending to the network is closed to remote callers");
    }
    for (RemoteUrl url : request.urls()) {
      Set<String> allowed = writes ? network.writeHosts() : network.readHosts();
      String key = writes ? "network.writeHosts" : "network.readHosts"; // NON-NLS
      Optional<Refusal> refusal = checkHost(url, allowed, key);
      if (refusal.isPresent()) {
        return refusal;
      }
    }
    return Optional.empty();
  }

  private Optional<Refusal> checkHost(RemoteUrl url, Set<String> allowed, String key) {
    String host = url.host();
    if (host.isEmpty()) {
      return Optional.empty(); // a location without a host, such as a file URL
    }
    if (!HTTPS.equalsIgnoreCase(url.scheme()) && !isLocal(host)) {
      return refuse(key, url + " is not https, which is required outside this machine");
    }
    if (!allowed.isEmpty() && allowed.stream().noneMatch(pattern -> matches(pattern, host))) {
      return refuse(key, host + " is not among the hosts this instance allows");
    }
    return Optional.empty();
  }

  private Optional<Refusal> checkFiles(Request request) {
    if (!files.readFromShare()) {
      for (Path path : request.reads()) {
        if (files.share().test(path.toAbsolutePath())) {
          return refuse(
              "files.read.networkFs", // NON-NLS
              path
                  + " is on a network share, which this instance does not read for a remote caller");
        }
      }
    }
    for (Path path : request.writes()) {
      Path target = path.toAbsolutePath().normalize();
      if (!files.writeToShare() && files.share().test(target)) {
        return refuse(
            "files.write.networkFs", // NON-NLS
            target + " is on a network share, where a remote caller does not write");
      }
      if (files.writeRoots().stream().noneMatch(root -> isUnder(target, root))) {
        return refuse(
            "files.write.roots", // NON-NLS
            target
                + " is outside the places a remote caller may write: "
                + join(files.writeRoots()));
      }
      if (!request.force()
          && java.nio.file.Files.exists(target)
          && !CommandContext.wasWritten(target)) {
        return refuse(
            "files.write.overwrite", // NON-NLS
            target
                + " already exists and was not written by this session; give --force to replace it");
      }
    }
    return Optional.empty();
  }

  /** Whether a namespace answers a remote caller at all. */
  public boolean allows(String namespace) {
    return remote.allows(namespace);
  }

  private static Optional<Refusal> refuse(String key, String reason) {
    return Optional.of(new Refusal(key, reason));
  }

  /** True when the path, once symbolic links are resolved as far as they exist, is under a root. */
  static boolean isUnder(Path path, Path root) {
    return real(path).startsWith(real(root));
  }

  private static Path real(Path path) {
    Path absolute = path.toAbsolutePath().normalize();
    Path existing = NetworkPaths.deepestExisting(absolute);
    if (existing == null) {
      return absolute;
    }
    try {
      Path resolved = existing.toRealPath();
      return existing.equals(absolute) ? resolved : resolved.resolve(existing.relativize(absolute));
    } catch (java.io.IOException e) {
      return absolute;
    }
  }

  /** A host matches a name exactly, or a pattern {@code *.example.org} by its domain. */
  static boolean matches(String pattern, String host) {
    String value = host.toLowerCase(Locale.ROOT);
    String expected = pattern.trim().toLowerCase(Locale.ROOT);
    if (expected.startsWith("*.")) {
      String domain = expected.substring(2);
      return value.equals(domain) || value.endsWith("." + domain);
    }
    return value.equals(expected);
  }

  private static boolean isLocal(String host) {
    return LOCALHOST.equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
  }

  /**
   * Where a remote caller may write until the instance says otherwise: the directory of this
   * session, the temporary directory of the machine, which is where a caller puts what it looks at,
   * and the user's home, so a capture asked for at a terminal lands where the person expects it.
   */
  private static List<Path> defaultRoots() {
    List<Path> roots = new ArrayList<>(3);
    addRoot(roots, System.getProperty("weasis.tmp.dir")); // NON-NLS
    addRoot(roots, System.getProperty("java.io.tmpdir")); // NON-NLS
    addRoot(roots, System.getProperty("user.home")); // NON-NLS
    return roots;
  }

  private static void addRoot(List<Path> roots, String directory) {
    if (directory == null || directory.isBlank()) {
      return;
    }
    Path path = Path.of(directory).toAbsolutePath().normalize();
    if (!roots.contains(path)) {
      roots.add(path);
    }
  }

  private static List<Path> roots(String value, List<Path> fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return Arrays.stream(value.split(java.io.File.pathSeparator))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> Path.of(s).toAbsolutePath().normalize())
        .map(Path.class::cast)
        .toList();
  }

  private static Set<String> hosts(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    return new LinkedHashSet<>(
        Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
  }

  private static boolean flag(UnaryOperator<String> lookup, String key, boolean fallback) {
    String value = lookup.apply(key);
    if (value == null) {
      return fallback;
    }
    return ALLOW.equalsIgnoreCase(value) || Boolean.parseBoolean(value);
  }

  private static String join(List<Path> paths) {
    return paths.stream().map(Path::toString).reduce((a, b) -> a + ", " + b).orElse("nowhere");
  }
}
