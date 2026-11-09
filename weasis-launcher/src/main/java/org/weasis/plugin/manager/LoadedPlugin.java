/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin.manager;

import java.util.List;
import org.weasis.core.api.command.JsonWriter;

/**
 * One line of the loaded-plugins manifest: what the launcher decided about a plugin at this start,
 * loaded or not.
 *
 * @param id the plugin id
 * @param version its version
 * @param source {@code package}, {@code site} or {@code user}
 * @param file the jar name
 * @param sha256 the digest of the jar, null when not computed
 * @param publisherKeyId the key that signed it, null when unsigned
 * @param tier the trust tier
 * @param capabilities the declared capabilities
 * @param state {@code started}, {@code disabled}, {@code refused}, {@code candidate}, {@code
 *     failed}
 * @param reason why it is not started, null when it is
 */
public record LoadedPlugin(
    String id,
    String version,
    String source,
    String file,
    String sha256,
    String publisherKeyId,
    PluginPolicy.Tier tier,
    List<String> capabilities,
    String state,
    String reason) {

  public static final String PACKAGE = "package"; // NON-NLS
  public static final String SITE = "site"; // NON-NLS
  public static final String USER = "user"; // NON-NLS
  public static final String STARTED = "started"; // NON-NLS
  public static final String DISABLED = "disabled"; // NON-NLS
  public static final String REFUSED = "refused"; // NON-NLS
  public static final String CANDIDATE = "candidate"; // NON-NLS
  public static final String FAILED = "failed"; // NON-NLS

  public LoadedPlugin {
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }

  public boolean isStarted() {
    return STARTED.equals(state);
  }

  public LoadedPlugin withState(String newState, String why) {
    return new LoadedPlugin(
        id, version, source, file, sha256, publisherKeyId, tier, capabilities, newState, why);
  }

  public void write(JsonWriter out) {
    out.beginObject().name("id").value(id == null ? "" : id); // NON-NLS
    out.optional("version", version); // NON-NLS
    out.name("source").value(source); // NON-NLS
    out.optional("file", file); // NON-NLS
    out.optional("sha256", sha256); // NON-NLS
    out.optional(
        "publisherKeyId",
        publisherKeyId == null || publisherKeyId.isBlank() ? null : publisherKeyId); // NON-NLS
    out.name("tier")
        .value(tier == null ? PluginPolicy.Tier.UNVERIFIED.key() : tier.key()); // NON-NLS
    out.name("capabilities").beginArray(); // NON-NLS
    capabilities.forEach(out::value);
    out.endArray();
    out.name("state").value(state); // NON-NLS
    out.optional("reason", reason); // NON-NLS
    out.endObject();
  }

  /** The part of the line the fingerprint covers: identity and digest, never the state. */
  String fingerprintLine() {
    return id + "@" + version + ":" + (sha256 == null ? "" : sha256) + ":" + source;
  }
}
