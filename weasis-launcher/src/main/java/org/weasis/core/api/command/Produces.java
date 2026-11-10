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
 * The handle types a verb hands out in its result: {@code workspace:list} produces {@code ViewId},
 * {@code dicom:list} produces {@code SeriesUid}. With the types the options consume, the descriptor
 * holds the whole handle graph: the help of a type names its producers, an unknown handle names
 * the verb to call first, and the vocabulary tests check that every consumed handle has a producer
 * and that every produced type appears in the verb's result.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Produces {
  Class<? extends SemanticType>[] value();
}
