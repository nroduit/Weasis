/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

/**
 * @author Nicolas Roduit
 * @param <T> set the Type of the returning element
 */
@FunctionalInterface
public interface Copyable<T> {
  /**
   * @return a new instance with identical values
   */
  T copy();
}
