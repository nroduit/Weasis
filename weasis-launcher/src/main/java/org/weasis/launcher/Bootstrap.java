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

/**
 * Minimal, dependency-free entry point for the packaged application. It runs the AOT-cache relaunch
 * check (stage 1) <em>before</em> the JVM ever references {@link AppLauncher}, so the short-lived
 * stage-1 parent process never loads AppLauncher's static Logback setup or its dependency graph —
 * that class-loading is what made the relaunch expensive. When no relaunch happens (the stage-2
 * child, or AOT disabled) it delegates to the real launcher, which is loaded only then.
 */
public final class Bootstrap {

  private Bootstrap() {}

  public static void main(String[] argv) throws Exception {
    if (AotCache.relaunch(argv)) {
      return; // stage-1 parent: it spawned the stage-2 child and exited
    }
    AppLauncher.main(argv);
  }
}
