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

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Where the configuration of this launch comes from, decided from the command line and the system
 * properties with the precedence of the launcher's {@code ConfigData}: system properties first,
 * then the {@code weasis:config} arguments ({@code cdb=}, {@code wcfg=}, {@code pro=}), then the
 * properties of the launch configuration service named by {@code wcfg}. No URL, a {@code file:}
 * codebase, a portable launch or {@code weasis.codebase.local} mean the installed configuration.
 */
record ConfigSource(
    Optional<URI> configUri,
    String codebaseUrl,
    String configServiceUrl,
    String extendedConfig,
    String user,
    String profile,
    String authorization) {

  private static final Pattern WEASIS_URI =
      Pattern.compile("^weasis(-\\w+)?://.*", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPACE_EXCEPT_QUOTES = Pattern.compile("'[^']*'|\"[^\"]*\"|( )");
  private static final String CONFIG_COMMAND = "weasis:config"; // NON-NLS

  /** True when the configuration is served by a remote codebase. */
  boolean isRemote() {
    return configUri.isPresent()
        && !"file".equalsIgnoreCase(configUri.get().getScheme()); // NON-NLS
  }

  static ConfigSource resolve(String[] args, Path appDir, Http http) {
    Map<String, String> props = new LinkedHashMap<>();
    putIfText(
        props, Contract.P_CONFIG_PROPERTIES, System.getProperty(Contract.P_CONFIG_PROPERTIES));
    putIfText(props, Contract.P_EXTENDED_CONFIG, System.getProperty(Contract.P_EXTENDED_CONFIG));
    for (String name : System.getProperties().stringPropertyNames()) {
      if (name.startsWith("weasis.")) { // NON-NLS
        putIfText(props, name, System.getProperty(name));
      }
    }
    Map<String, List<String>> params = configParams(commands(args));
    params.getOrDefault("wcfg", List.of()).stream()
        .findFirst() // NON-NLS
        .ifPresent(v -> props.put(Contract.P_CONFIG_URL, v));
    params.getOrDefault("cdb", List.of()).stream()
        .findFirst() // NON-NLS
        .ifPresent(v -> props.putIfAbsent(Contract.P_CODEBASE_URL, v));
    params.getOrDefault("auth", List.of()).stream()
        .findFirst() // NON-NLS
        .ifPresent(v -> props.putIfAbsent("http.authorization", v)); // NON-NLS
    applyProperties(props, params.getOrDefault("pro", List.of())); // NON-NLS

    String service = props.get(Contract.P_CONFIG_URL);
    if (hasText(service)) {
      applyProperties(props, serviceProperties(service, props, http));
    }

    String codebase = props.getOrDefault(Contract.P_CODEBASE_URL, "");
    Optional<URI> configUri = Optional.empty();
    if (hasText(codebase)
        && !props.containsKey(Contract.P_CODEBASE_LOCAL)
        && System.getProperty(Contract.P_PORTABLE) == null) {
      String config = props.get(Contract.P_CONFIG_PROPERTIES);
      if (!hasText(config)) {
        config = codebase + "/conf/base.json"; // NON-NLS
      }
      configUri = uri(config, appDir);
    } else {
      codebase = "";
    }
    return new ConfigSource(
        configUri,
        codebase,
        props.getOrDefault(Contract.P_CONFIG_URL, ""),
        props.getOrDefault(Contract.P_EXTENDED_CONFIG, ""),
        props.getOrDefault(Contract.P_USER, System.getProperty("user.name", "")), // NON-NLS
        props.getOrDefault(Contract.P_PROFILE, ""),
        props.getOrDefault("http.authorization", "")); // NON-NLS
  }

  private static Optional<URI> uri(String value, Path appDir) {
    try {
      if (value.startsWith("file:conf/")) { // NON-NLS
        return Optional.of(appDir.resolve(value.substring(5)).toUri());
      }
      return Optional.of(new URI(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * The commands of the command line, from a {@code weasis://} URI or from {@code $}-prefixed
   * words.
   */
  static List<String> commands(String[] args) {
    List<String> commands = new ArrayList<>();
    if (args == null) {
      return commands;
    }
    for (String arg : args) {
      if (WEASIS_URI.matcher(arg).matches()) {
        String url = URLDecoder.decode(arg, StandardCharsets.UTF_8);
        String[] parts = url.split("\\$");
        for (int i = 1; i < parts.length; i++) {
          commands.add(parts[i].strip());
        }
      }
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("$") && args[i].length() > 1) {
        StringBuilder command = new StringBuilder(args[i].substring(1));
        while (i + 1 < args.length && !args[i + 1].startsWith("$")) {
          i++;
          command.append(' ').append(args[i]);
        }
        commands.add(command.toString());
      }
    }
    return commands;
  }

  /** The {@code name=value} parameters of the {@code weasis:config} command, grouped by name. */
  static Map<String, List<String>> configParams(List<String> commands) {
    Map<String, List<String>> params = new LinkedHashMap<>();
    for (String command : commands) {
      if (!command.startsWith(CONFIG_COMMAND) || command.length() <= CONFIG_COMMAND.length() + 2) {
        continue;
      }
      for (String token :
          splitSpaceExceptInQuotes(command.substring(CONFIG_COMMAND.length() + 1))) {
        String[] kv = token.split("=", 2);
        if (kv.length == 2 && hasText(kv[0])) {
          params.computeIfAbsent(kv[0], k -> new ArrayList<>()).add(unquote(kv[1]));
        }
      }
    }
    return params;
  }

  private static void applyProperties(Map<String, String> props, List<String> values) {
    for (String value : values) {
      String[] kv = value.strip().split("\\s+", 2);
      if (kv.length == 2 && !isProtected(kv[0])) {
        props.putIfAbsent(kv[0], kv[1]);
      }
    }
  }

  private static boolean isProtected(String key) {
    return key.startsWith("weasis.bootstrap.") // NON-NLS
        || key.equals(Contract.P_CODEBASE_LOCAL)
        || key.startsWith("java.") // NON-NLS
        || key.startsWith("user."); // NON-NLS
  }

  /**
   * The properties of the launch configuration service (an XML document of properties and
   * arguments).
   */
  private static List<String> serviceProperties(
      String service, Map<String, String> props, Http http) {
    List<String> values = new ArrayList<>();
    try {
      URI uri = new URI(service);
      byte[] body;
      if ("file".equalsIgnoreCase(uri.getScheme())) { // NON-NLS
        body = Files.readAllBytes(Path.of(uri));
      } else {
        body = http.fetch(serviceUri(uri, props.get(Contract.P_USER)));
      }
      XMLInputFactory factory = XMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
      XMLStreamReader reader =
          factory.createXMLStreamReader(new StringReader(new String(body, StandardCharsets.UTF_8)));
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT
            && "property".equals(reader.getLocalName())) { // NON-NLS
          String name = reader.getAttributeValue(null, "name"); // NON-NLS
          String value = reader.getAttributeValue(null, "value"); // NON-NLS
          if (hasText(name) && value != null) {
            values.add(name + " " + value);
          }
        }
      }
      reader.close();
    } catch (Exception e) {
      BootLog.note("configuration service unreachable: " + e); // NON-NLS
    }
    return values;
  }

  /** Adds the {@code user} and {@code host} query parameters the service expects. */
  static URI serviceUri(URI service, String user) throws Exception {
    Map<String, String> query = new LinkedHashMap<>();
    if (hasText(service.getQuery())) {
      for (String part : service.getQuery().split("&")) {
        String[] kv = part.split("=", 2);
        query.put(kv[0], kv.length > 1 ? kv[1] : "");
      }
    }
    String u = hasText(user) ? user : System.getProperty("user.name", ""); // NON-NLS
    query.put("user", u); // NON-NLS
    query.put("host", java.net.InetAddress.getLocalHost().getHostName()); // NON-NLS
    StringBuilder sb = new StringBuilder();
    query.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : "&").append(k).append('=').append(v));
    return new URI(
        service.getScheme(),
        null,
        service.getHost(),
        service.getPort(),
        service.getPath(),
        sb.toString(),
        null);
  }

  static List<String> splitSpaceExceptInQuotes(String s) {
    List<String> list = new ArrayList<>();
    Matcher m = SPACE_EXCEPT_QUOTES.matcher(s);
    StringBuilder b = new StringBuilder();
    while (m.find()) {
      if (m.group(1) == null) {
        m.appendReplacement(b, Matcher.quoteReplacement(m.group(0)));
        list.add(b.toString().strip());
        b.setLength(0);
      } else {
        m.appendReplacement(b, "");
        String arg = b.toString().strip();
        if (!arg.isEmpty()) {
          list.add(arg);
        }
        b.setLength(0);
      }
    }
    m.appendTail(b);
    String tail = b.toString().strip();
    if (!tail.isEmpty()) {
      list.add(tail);
    }
    return list.stream().filter(v -> !v.isEmpty()).toList();
  }

  private static String unquote(String value) {
    return value.replaceAll("(?:^\")|(?:\"$)", "");
  }

  private static void putIfText(Map<String, String> map, String key, String value) {
    if (hasText(value)) {
      map.put(key, value);
    }
  }

  static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }

  /** Reads the manual proxy of this workstation from the machine settings of weasis-core. */
  static void applyProxy(Path configDir) {
    Path machineFile =
        configDir.resolve("machine").resolve("settings").resolve("weasis-core.json"); // NON-NLS
    if (!Files.isRegularFile(machineFile)) {
      return;
    }
    try {
      Object root = JsonMini.parse(Files.readString(machineFile, StandardCharsets.UTF_8));
      if (!(root instanceof Map<?, ?> json)
          || !(json.get("values") instanceof Map<?, ?> values)) { // NON-NLS
        return;
      }
      if (!Boolean.parseBoolean(String.valueOf(values.get("proxy.manual")))) { // NON-NLS
        return;
      }
      proxy(values, "http"); // NON-NLS
      proxy(values, "https"); // NON-NLS
      Object exceptions = values.get("proxy.exceptions"); // NON-NLS
      if (exceptions instanceof String e && hasText(e)) {
        System.setProperty("http.nonProxyHosts", e); // NON-NLS
      }
    } catch (IOException | RuntimeException e) {
      BootLog.note("cannot read the proxy settings: " + e); // NON-NLS
    }
  }

  private static void proxy(Map<?, ?> values, String scheme) {
    Object host = values.get("proxy." + scheme + ".host"); // NON-NLS
    Object port = values.get("proxy." + scheme + ".port"); // NON-NLS
    if (host instanceof String h && hasText(h)) {
      System.setProperty(scheme + ".proxyHost", h); // NON-NLS
      if (port instanceof String p && hasText(p)) {
        System.setProperty(scheme + ".proxyPort", p); // NON-NLS
      }
    }
  }
}
