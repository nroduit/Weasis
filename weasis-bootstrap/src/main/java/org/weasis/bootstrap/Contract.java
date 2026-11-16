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

/**
 * What stage 1 and stage 2 agree on. The launcher keeps the same names in {@code
 * org.weasis.launcher.BootstrapContract}; a change here is a change of {@link #PROTOCOL}.
 */
final class Contract {

  /** Version of the stage 1 ↔ stage 2 protocol: properties, READY line, exit codes. */
  static final int PROTOCOL = 1;

  /** Set by stage 1 on the stage-2 command line; its absence means a development launch. */
  static final String P_VERSION = "weasis.bootstrap.version";

  /** The application directory of the image ({@code $APPDIR}), where {@code conf/} lives. */
  static final String P_APPDIR = "weasis.bootstrap.appdir";

  /** {@code hub} or {@code embedded}: which launcher set stage 2 runs. */
  static final String P_SET = "weasis.bootstrap.set";

  /** Reason stage 1 fell back to the embedded set although the package declares one. */
  static final String P_FALLBACK = "weasis.bootstrap.fallback";

  /** Start time of stage 1 in epoch milliseconds, for the launch measurements of stage 2. */
  static final String P_T0 = "weasis.bootstrap.t0";

  /** File holding the configuration stage 1 fetched, and the URI it was fetched from. */
  static final String P_CONFIG_CACHE = "weasis.config.cache";

  static final String P_CONFIG_CACHE_URI = "weasis.config.cache.uri";

  /** Native: the entry class of this launcher (a thin wrapper, one per application). */
  static final String P_ENTRY = "weasis.bootstrap.entry";

  /** Native: {@code app} (default) or {@code cli}. */
  static final String P_KIND = "weasis.bootstrap.kind";

  /** Echo the decision line to stderr. */
  static final String P_TRACE = "weasis.bootstrap.trace";

  /** {@code signed} (default when a truststore exists) or {@code digest}. */
  static final String P_INTEGRITY = "weasis.bootstrap.integrity";

  /** Opt-out of the AOT cache, read by stage 1 ({@code -Dweasis.aot.cache=false}). */
  static final String P_AOT = "weasis.aot.cache";

  /** Overrides the JDK gate of {@code AOTClassLinking}. */
  static final String P_AOT_CLASSLINKING = "weasis.aot.classlinking";

  /**
   * macOS: set (through {@code JAVA_TOOL_OPTIONS}) on the launcher stage 1 replaced itself by to
   * add the AOT flags; that process runs the launcher set without replacing itself again.
   */
  static final String P_REEXEC = "weasis.bootstrap.reexec";

  /**
   * A file stage 1 writes before it hands the launch to a JVM using an AOT cache it cannot watch
   * (macOS), and that JVM deletes once READY: still there at the next start, the cache did not
   * reach READY.
   */
  static final String P_PENDING = "weasis.bootstrap.pending";

  static final String JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS";

  static final String P_JPACKAGE_VERSION = "jpackage.app-version";
  static final String P_LAUNCH_TYPE = "weasis.launch.type";
  static final String P_EXTENDED_CONFIG = "weasis.extended.config.properties";
  static final String P_CONFIG_PROPERTIES = "weasis.config.properties";
  static final String P_CONFIG_URL = "weasis.config.url";
  static final String P_CODEBASE_URL = "weasis.codebase.url";
  static final String P_CODEBASE_LOCAL = "weasis.codebase.local";
  static final String P_PORTABLE = "weasis.portable.dir";
  static final String P_USER = "weasis.user";
  static final String P_PROFILE = "weasis.profile";

  /** The line stage 2 prints on stdout once its plugin manager has started. */
  static final String READY = "READY";

  /** Stage 2 refuses this environment: no recovery, the error names the reason. */
  static final int EXIT_REFUSED = 78;

  /**
   * Stage 2 has told the user that this system cannot run the application (a glibc below the floor
   * of the native libraries): no retry with the embedded set, no second dialog.
   */
  static final int EXIT_UNSUPPORTED = 79;

  /** Suffix of the embedded set files, which keeps them off the class path jpackage builds. */
  static final String EMBEDDED_SUFFIX = ".embedded";

  static final String DEFAULT_ENTRY = "org.weasis.launcher.Weasis";
  static final String KIND_APP = "app";
  static final String KIND_CLI = "cli";

  private Contract() {}
}
