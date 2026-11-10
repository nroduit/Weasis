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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The instructions an agent needs to drive this instance, printed by {@code help --agent}. The text
 * lives next to the code, so it cannot drift from the surface it describes: the version and the
 * protocol are filled in, the namespaces of the running profile are listed, and a section about a
 * namespace this profile does not load is left out. A skill folder is a snapshot of it plus the
 * front matter its host format wants.
 *
 * <p>The template is filled where it says {@code @{name}}; a {@code ${…}} in it is an example of a
 * script reference and is left alone.
 */
public final class AgentSkill {

  private static final String RESOURCE = "agent-skill.md"; // NON-NLS

  /** {@code <!--if:namespace-->} … {@code <!--endif-->} around what one namespace is needed for. */
  private static final Pattern BLOCK =
      Pattern.compile("<!--if:([a-z0-9-]+)-->\\R(.*?)<!--endif-->\\R", Pattern.DOTALL);

  private AgentSkill() {}

  /** The instructions for a profile that loaded {@code namespaces}. */
  public static String render(Collection<String> namespaces) {
    return render(namespaces, Glossary.core());
  }

  /** The instructions with the glossary of the instance rendered into them. */
  public static String render(Collection<String> namespaces, Glossary glossary) {
    String text = template();
    text = blocks(text, new TreeSet<>(namespaces));
    text =
        text.replace("@{apiVersion}", CommandManager.API_VERSION) // NON-NLS
            .replace(
                "@{protocolVersion}",
                String.valueOf(org.weasis.launcher.CommandProtocol.VERSION)) // NON-NLS
            .replace("@{applicationVersion}", CommandManager.applicationVersion()) // NON-NLS
            .replace("@{namespaces}", String.join(", ", new TreeSet<>(namespaces))) // NON-NLS
            .replace("@{platform}", platform()) // NON-NLS
            .replace("@{glossary}", glossaryLines(glossary)); // NON-NLS
    return text;
  }

  /** The terms an agent must use, one line each, without the long definitions of the help. */
  private static String glossaryLines(Glossary glossary) {
    StringBuilder sb = new StringBuilder();
    for (Term term : glossary.terms()) {
      sb.append("- **").append(term.term()).append("**: ").append(term.definition());
      if (!term.avoid().isEmpty()) {
        sb.append(" (not ").append(String.join(", ", term.avoid())).append(')');
      }
      sb.append('\n');
    }
    return sb.toString().stripTrailing();
  }

  /** Keeps a block when its namespace is there, drops it with its content otherwise. */
  private static String blocks(String text, Collection<String> namespaces) {
    Matcher matcher = BLOCK.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String kept = namespaces.contains(matcher.group(1)) ? matcher.group(2) : "";
      matcher.appendReplacement(out, Matcher.quoteReplacement(kept));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String template() {
    try (InputStream in = AgentSkill.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("The agent instructions are missing from the jar");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String platform() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT); // NON-NLS
    if (os.startsWith("win")) { // NON-NLS
      return "Windows"; // NON-NLS
    }
    return os.startsWith("mac") || os.contains("darwin") ? "macOS" : "Linux"; // NON-NLS
  }

  /** The names a skill folder carries beside the instructions. */
  public static List<String> snapshots() {
    return List.of("SKILL.md", "reference.md", "commands.json"); // NON-NLS
  }
}
