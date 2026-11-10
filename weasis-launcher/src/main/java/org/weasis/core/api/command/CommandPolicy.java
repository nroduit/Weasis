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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    boolean locked,
    EventRules events,
    boolean remoteCheckpoint) {

  /** The seven-part policy with the default event rules, for the callers that predate them. */
  public CommandPolicy(
      RemotePolicy remote,
      FileRules files,
      NetworkRules network,
      boolean assumeYesAllowed,
      boolean shutdownAllowed,
      boolean pixelAttestAllowed,
      boolean locked) {
    this(
        remote,
        files,
        network,
        assumeYesAllowed,
        shutdownAllowed,
        pixelAttestAllowed,
        locked,
        EventRules.defaults());
  }

  public CommandPolicy {
    events = events == null ? EventRules.defaults() : events;
  }

  /** The eight-part policy with the remote checkpoint on, for the callers that predate it. */
  public CommandPolicy(
      RemotePolicy remote,
      FileRules files,
      NetworkRules network,
      boolean assumeYesAllowed,
      boolean shutdownAllowed,
      boolean pixelAttestAllowed,
      boolean locked,
      EventRules events) {
    this(
        remote,
        files,
        network,
        assumeYesAllowed,
        shutdownAllowed,
        pixelAttestAllowed,
        locked,
        events,
        true);
  }

  /** Prefix of the system properties that override one key each. */
  private static final Logger LOGGER = LoggerFactory.getLogger(CommandPolicy.class);

  public static final String PROPERTY_PREFIX = "weasis.commands.policy."; // NON-NLS

  /**
   * The policy file a site puts next to {@code base.json}; the keys are the ones of the properties.
   */
  public static final String FILE_NAME = "commands-policy.json"; // NON-NLS

  /** A policy with where it came from, for {@code weasis:info} and the log. */
  public record Loaded(CommandPolicy policy, String source) {}

  /**
   * The policy of this instance from its configuration: the defaults, then {@value #FILE_NAME} next
   * to the last configuration file that has one (the local file, then the site's), then one system
   * property per key that changes ({@code weasis.commands.policy.<key>}, and the {@code
   * weasis.commands.remote.*} properties of the remote part). The file is nested JSON, one object
   * per key segment ({@code {"network": {"read": "allow"}}}); an array joins with commas, a list of
   * roots with the path separator; {@code ${user.home}}, {@code ${session.tmp}} and any system
   * property are substituted.
   */
  public static Loaded fromConfiguration(List<URI> configurationFiles) {
    Map<String, String> file = new HashMap<>();
    String source = null;
    for (URI configuration : configurationFiles == null ? List.<URI>of() : configurationFiles) {
      URI sibling = sibling(configuration);
      if (sibling == null) {
        continue;
      }
      Map<String, String> read = readFile(sibling);
      if (read != null) {
        file = read;
        source = sibling.toString();
      }
    }
    boolean properties =
        System.getProperties().stringPropertyNames().stream()
            .anyMatch(
                k ->
                    k.startsWith(PROPERTY_PREFIX)
                        || k.equals(RemotePolicy.ENABLED_PROP)
                        || k.equals(RemotePolicy.NAMESPACES_PROP));
    Map<String, String> fileKeys = file;
    UnaryOperator<String> lookup =
        key -> {
          String property = System.getProperty(PROPERTY_PREFIX + key);
          return property != null ? property : fileKeys.get(key);
        };
    RemotePolicy remote = remote(lookup);
    String where =
        source == null
            ? (properties ? "properties" : "defaults") // NON-NLS
            : (properties ? source + " and properties" : source); // NON-NLS
    return new Loaded(from(lookup, remote), where);
  }

  /** The remote part: the file keys {@code remote.*}, the properties of either spelling on top. */
  private static RemotePolicy remote(UnaryOperator<String> lookup) {
    RemotePolicy properties = RemotePolicy.fromSystemProperties();
    String enabled = System.getProperty(RemotePolicy.ENABLED_PROP, lookup.apply("remote.enabled"));
    String namespaces =
        System.getProperty(RemotePolicy.NAMESPACES_PROP, lookup.apply("remote.namespaces"));
    if (enabled == null && namespaces == null) {
      return properties;
    }
    boolean on = enabled == null ? properties.enabled() : !"false".equalsIgnoreCase(enabled);
    Set<String> names = namespaces == null ? properties.namespaces() : hosts(namespaces);
    return new RemotePolicy(on, names.isEmpty() ? Set.of(RemotePolicy.ALL) : names);
  }

  private static URI sibling(URI configuration) {
    if (configuration == null) {
      return null;
    }
    String text = configuration.toString();
    int slash = text.lastIndexOf('/');
    return slash < 0 ? null : URI.create(text.substring(0, slash + 1) + FILE_NAME);
  }

  /** The flattened keys of the file, null when there is no such file. */
  @SuppressWarnings("unchecked")
  static Map<String, String> readFile(URI uri) {
    try {
      if ("file".equalsIgnoreCase(uri.getScheme())
          && !Files.isRegularFile(Path.of(uri))) { // NON-NLS
        return null;
      }
      try (Reader reader =
          new InputStreamReader(uri.toURL().openStream(), StandardCharsets.UTF_8)) {
        Object json = com.formdev.flatlaf.json.Json.parse(reader);
        Map<String, String> keys = new HashMap<>();
        if (json instanceof Map<?, ?> map) {
          flatten("", (Map<String, Object>) map, keys);
        }
        return keys;
      }
    } catch (IOException | RuntimeException e) {
      if (!(e instanceof java.io.FileNotFoundException)) {
        LOGGER.warn("Cannot read the command policy {}", uri, e);
      }
      return null;
    }
  }

  private static void flatten(String prefix, Map<String, Object> map, Map<String, String> into) {
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
      Object value = e.getValue();
      if (value instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) nested;
        flatten(key, child, into);
      } else if (value instanceof List<?> list) {
        String separator = key.endsWith(".roots") ? java.io.File.pathSeparator : ","; // NON-NLS
        into.put(
            key,
            list.stream()
                .map(String::valueOf)
                .map(CommandPolicy::substitute)
                .reduce((a, b) -> a + separator + b)
                .orElse(""));
      } else if (value != null) {
        into.put(key, substitute(String.valueOf(value)));
      }
    }
  }

  /** {@code ${session.tmp}}, {@code ${user.home}} and any system property, in a file value. */
  static String substitute(String value) {
    Matcher m = VARIABLE.matcher(value);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String name = m.group(1);
      String replacement =
          "session.tmp".equals(name) // NON-NLS
              ? System.getProperty(
                  "weasis.tmp.dir", System.getProperty("java.io.tmpdir", "")) // NON-NLS
              : System.getProperty(name, m.group());
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    return m.appendTail(sb).toString();
  }

  private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");

  /** The effective policy as the descriptor and {@code weasis:info} print it. */
  public void write(JsonWriter out) {
    out.beginObject();
    out.name("remote").beginObject().name("enabled").value(remote.enabled()); // NON-NLS
    out.name("namespaces").beginArray(); // NON-NLS
    remote.namespaces().stream().sorted().forEach(out::value);
    out.endArray().endObject();
    out.name("files").beginObject().name("writeRoots").beginArray(); // NON-NLS
    files.writeRoots().forEach(p -> out.value(p.toString()));
    out.endArray()
        .name("writeToShare") // NON-NLS
        .value(files.writeToShare())
        .name("readFromShare") // NON-NLS
        .value(files.readFromShare())
        .endObject();
    out.name("network").beginObject().name("read").value(network.read()); // NON-NLS
    out.name("readHosts").beginArray(); // NON-NLS
    network.readHosts().stream().sorted().forEach(out::value);
    out.endArray().name("write").value(network.write()); // NON-NLS
    out.name("writeHosts").beginArray(); // NON-NLS
    network.writeHosts().stream().sorted().forEach(out::value);
    out.endArray().endObject();
    out.name("confirmations")
        .beginObject()
        .name("assumeYes")
        .value(assumeYesAllowed)
        .endObject(); // NON-NLS
    out.name("shutdown").value(shutdownAllowed); // NON-NLS
    out.name("pixelsAttest").value(pixelAttestAllowed); // NON-NLS
    out.name("events").beginObject().name("enabled").value(events.enabled()); // NON-NLS
    out.name("namespaces").beginArray(); // NON-NLS
    events.namespaces().stream().sorted().forEach(out::value);
    out.endArray().name("maxSubscribers").value(events.maxSubscribers()).endObject(); // NON-NLS
    out.name("remoteCheckpoint").value(remoteCheckpoint); // NON-NLS
    out.name("locked").value(locked); // NON-NLS
    out.endObject();
  }

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

  /**
   * Whether a remote caller may observe the application through the events, which namespaces it may
   * observe, and how many may do so at once.
   *
   * @param namespaces the only namespaces whose events are streamed; empty for all
   */
  public record EventRules(boolean enabled, Set<String> namespaces, int maxSubscribers) {

    public EventRules {
      namespaces = Set.copyOf(namespaces);
    }

    public static EventRules defaults() {
      return new EventRules(true, Set.of(), 4);
    }

    public boolean allows(String namespace) {
      return enabled && (namespaces.isEmpty() || namespaces.contains(namespace));
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
    EventRules events =
        new EventRules(
            flag(lookup, "events", true), // NON-NLS
            hosts(lookup.apply("events.namespaces")), // NON-NLS
            integer(lookup.apply("events.maxSubscribers"), 4)); // NON-NLS
    return new CommandPolicy(
        remote,
        files,
        network,
        flag(lookup, "confirmations.assumeYes", true), // NON-NLS
        flag(lookup, "shutdown", true), // NON-NLS
        flag(lookup, "pixels.attest", false), // NON-NLS
        flag(lookup, "locked", false), // NON-NLS
        events,
        flag(lookup, "remote.checkpoint", true)); // NON-NLS
  }

  /** The same policy with the checkpoint before a remote batch on or off. */
  public CommandPolicy withRemoteCheckpoint(boolean on) {
    return new CommandPolicy(
        remote,
        files,
        network,
        assumeYesAllowed,
        shutdownAllowed,
        pixelAttestAllowed,
        locked,
        events,
        on);
  }

  /** The same policy with other event rules. */
  public CommandPolicy withEvents(EventRules rules) {
    return new CommandPolicy(
        remote,
        files,
        network,
        assumeYesAllowed,
        shutdownAllowed,
        pixelAttestAllowed,
        locked,
        rules);
  }

  /**
   * What refuses a subscription to the events, or empty when it may be opened. Only a remote
   * subscriber is policed; {@code active} is how many remote subscribers are already connected.
   */
  public Optional<Refusal> checkEvents(CommandOrigin origin, int active) {
    if (origin != CommandOrigin.REMOTE) {
      return Optional.empty();
    }
    if (!events.enabled()) {
      return refuse("events", "the events are closed to remote callers on this instance");
    }
    if (active >= events.maxSubscribers()) {
      return refuse(
          "events.maxSubscribers", // NON-NLS
          "this instance serves at most "
              + events.maxSubscribers()
              + " event subscriber(s) at once; close one or ask the instance for more");
    }
    return Optional.empty();
  }

  /** The same policy with other file rules, for a configuration step or a test. */
  public CommandPolicy withFiles(FileRules rules) {
    return new CommandPolicy(
        remote,
        rules,
        network,
        assumeYesAllowed,
        shutdownAllowed,
        pixelAttestAllowed,
        locked,
        events);
  }

  /** The same policy with other network rules. */
  public CommandPolicy withNetwork(NetworkRules rules) {
    return new CommandPolicy(
        remote,
        files,
        rules,
        assumeYesAllowed,
        shutdownAllowed,
        pixelAttestAllowed,
        locked,
        events);
  }

  /** The same policy with stopping the application allowed or refused. */
  public CommandPolicy withShutdown(boolean allowed) {
    return new CommandPolicy(
        remote, files, network, assumeYesAllowed, allowed, pixelAttestAllowed, locked, events);
  }

  /** The same policy with pixel attestation by a remote caller allowed or refused. */
  public CommandPolicy withPixelAttest(boolean allowed) {
    return new CommandPolicy(
        remote, files, network, assumeYesAllowed, shutdownAllowed, allowed, locked, events);
  }

  /**
   * What refuses this call, or empty when it may run. Everything but {@link CommandOrigin#REMOTE}
   * passes.
   */
  public Optional<Refusal> check(CommandOrigin origin, Request request) {
    if (origin != CommandOrigin.REMOTE) {
      return Optional.empty();
    }
    if (request.needs(Capability.EVENTS) && !events.enabled()) {
      return refuse("events", "the events are closed to remote callers on this instance");
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

  private static int integer(String value, int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Math.max(0, Integer.parseInt(value.trim()));
    } catch (NumberFormatException e) {
      return fallback;
    }
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
