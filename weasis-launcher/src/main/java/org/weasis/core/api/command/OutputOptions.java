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

import picocli.CommandLine.Option;

/**
 * Picocli mixin shared by verbs that return data: {@code --json} asks for a structured body, which
 * remote origins get by default. Verbs that write a file declare their own {@code --out} option.
 */
public class OutputOptions {

  @Option(names = "--json", description = "Structured JSON output (default for remote calls).")
  private boolean json;

  public boolean isJson() {
    return json || CommandContext.current().isRemote();
  }
}
