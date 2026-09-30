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

/** Where a command comes from; remote origins are policed and audited. */
public enum CommandOrigin {
  /** Arguments of the launch command line. */
  LAUNCH_ARGS,
  /** A {@code weasis://} URI or a file opened through the operating system. */
  URI,
  /** The singleton socket, i.e. the {@code weasis-cli} client or any local process. */
  REMOTE
}
