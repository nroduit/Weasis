/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import org.weasis.core.api.gui.Insertable;
import org.weasis.core.ui.editor.Relevant;

/**
 * A toolbar of a viewer; {@link Relevant#isRelevant} decides whether it is shown for the selected
 * view (always, unless the toolbar overrides it).
 */
public interface Toolbar extends Insertable, Relevant {

  @Override
  Type getPanelType();

  WtoolBar getComponent();
}
