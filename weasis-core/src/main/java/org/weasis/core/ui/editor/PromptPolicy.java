/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

/**
 * How a viewer settles the yes/no questions it would ask while opening a series, such as building
 * an MPR volume with geometric rectification despite few slices. Headless callers choose {@link
 * #ACCEPT} or {@link #DECLINE}; the viewer records the question as a warning instead of asking.
 */
public enum PromptPolicy {
  /** Show the dialog and let the user answer. */
  ASK,
  /** Take the recommended action without asking. */
  ACCEPT,
  /** Take the fallback action without asking. */
  DECLINE
}
