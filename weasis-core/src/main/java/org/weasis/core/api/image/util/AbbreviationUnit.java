/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

/**
 * <code>AbbreviationUnit</code> is similar to the Class <code>Unit</code>, except that the method
 * <code>toString()</code> returns the abbreviation of the unit.
 *
 * <p>
 *
 * @author Nicolas Roduit
 * @see Unit
 */
public record AbbreviationUnit(Unit unit) {

  @Override
  public String toString() {
    return unit.getAbbreviation();
  }
}
