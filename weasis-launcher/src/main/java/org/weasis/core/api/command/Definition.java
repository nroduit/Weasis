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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The definition a {@link SemanticType} carries into the glossary: what the value is, in one
 * sentence, and whether it is a handle (an id another verb hands out, which therefore needs a
 * producer declared with {@link Produces}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Definition {
  /** One sentence: what a value of this type is. */
  String value();

  /** True for an id another verb returns; false for a value the caller writes itself. */
  boolean handle() default false;
}
