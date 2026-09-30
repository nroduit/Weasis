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

import java.util.concurrent.Callable;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * Base of a Picocli verb: the spec, the {@code --json} mixin, output to the captured writer and
 * usage errors. Subclasses implement {@link #run()} and throw for execution failures.
 */
public abstract class AbstractVerb implements Callable<Integer> {

  @Spec protected CommandSpec spec;

  @Mixin protected OutputOptions output;

  @Override
  public Integer call() throws Exception {
    run();
    return CommandResult.OK;
  }

  protected abstract void run() throws Exception;

  protected boolean isJson() {
    return output.isJson();
  }

  protected void print(String text) {
    spec.commandLine().getOut().println(text);
  }

  /** A usage error (exit status 2) reported with the verb's synopsis. */
  protected ParameterException invalid(String message) {
    return new ParameterException(spec.commandLine(), message);
  }
}
