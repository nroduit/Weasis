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

import java.util.List;

/**
 * Why a command failed, in the shape a caller can act on: a stable {@code code}, the message a
 * person reads, and the few fields that say how to get it right — the synopsis of the verb, the
 * option at fault, the verb that produces the handle that was not found, the policy that refused,
 * near-miss spellings. A remote caller reads it as one JSON object on the error stream; the other
 * origins keep the plain message, as a command line has always answered.
 *
 * @param code one of the constants below, stable across versions
 * @param message the sentence a person reads
 * @param usage the synopsis of the verb, when the caller has to rewrite the call
 * @param option the option or parameter at fault
 * @param producedBy the verb that hands out the kind of handle that was not found
 * @param policy the key of the restriction policy that refused, as the caller can quote it
 * @param suggestions near-miss spellings of what the caller wrote
 */
public record CommandError(
    String code,
    String message,
    String usage,
    String option,
    String producedBy,
    String policy,
    List<String> suggestions) {

  /** The call does not match the grammar of the verb: a missing, unknown or exclusive option. */
  public static final String USAGE = "usage"; // NON-NLS

  /** A value does not parse as the type of its option. */
  public static final String INVALID_VALUE = "invalid-value"; // NON-NLS

  /** A handle (view, series, graphic, entry…) names nothing the application holds. */
  public static final String UNKNOWN_HANDLE = "unknown-handle"; // NON-NLS

  /** The namespace or the verb does not exist. */
  public static final String UNKNOWN_COMMAND = "unknown-command"; // NON-NLS

  /** The verb is valid but not applicable here: no viewer selected, a pane of another kind. */
  public static final String NOT_APPLICABLE = "not-applicable"; // NON-NLS

  /** A restriction policy refused the call. */
  public static final String REFUSED_BY_POLICY = "refused-by-policy"; // NON-NLS

  /** The verb ran and failed. */
  public static final String FAILED = "failed"; // NON-NLS

  /** The verb did not answer in time. */
  public static final String TIMEOUT = "timeout"; // NON-NLS

  public CommandError {
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
  }

  public static CommandError of(String code, String message) {
    return new CommandError(code, message, null, null, null, null, List.of());
  }

  /** A grammar error: the synopsis tells the caller what the verb takes. */
  public static CommandError usage(String message, String usage, List<String> suggestions) {
    return new CommandError(USAGE, message, usage, null, null, null, suggestions);
  }

  /** A value the type refused, named by its option. */
  public static CommandError invalidValue(String message, String option, String usage) {
    return new CommandError(INVALID_VALUE, message, usage, option, null, null, List.of());
  }

  /** A handle that names nothing, with the verb that hands out that kind of handle. */
  public static CommandError unknownHandle(String message, String producedBy) {
    return new CommandError(UNKNOWN_HANDLE, message, null, null, producedBy, null, List.of());
  }

  public static CommandError refused(CommandPolicy.Refusal refusal) {
    return new CommandError(
        REFUSED_BY_POLICY, refusal.message(), null, null, null, refusal.key(), List.of());
  }

  public CommandError withUsage(String synopsis) {
    return new CommandError(code, message, synopsis, option, producedBy, policy, suggestions);
  }

  /**
   * The body a remote caller reads on the error stream: this error, the command it called, and the
   * notices the call produced, such as a deprecated spelling.
   */
  public String toJson(String command, List<String> notices) {
    JsonWriter out = new JsonWriter();
    out.beginObject().name("code").value(code).name("message").value(message); // NON-NLS
    out.optional("command", command); // NON-NLS
    out.optional("usage", usage); // NON-NLS
    out.optional("option", option); // NON-NLS
    out.optional("producedBy", producedBy); // NON-NLS
    out.optional("policy", policy); // NON-NLS
    array(out, "suggestions", suggestions); // NON-NLS
    array(out, "notices", notices); // NON-NLS
    return out.endObject().toString();
  }

  private static void array(JsonWriter out, String name, List<String> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    out.name(name).beginArray();
    values.forEach(out::value);
    out.endArray();
  }
}
