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
 * Entry class of the Dicomizer launcher, a thin wrapper over {@link AppLauncher}; see {@link
 * Weasis} for why each application has one. The Dicomizer configuration comes from the extended
 * configuration file the launcher names ({@code conf/dicomizer.json}).
 */
public final class Dicomizer {

  private Dicomizer() {}

  public static void main(String[] argv) throws Exception {
    AppLauncher.main(argv);
  }
}
