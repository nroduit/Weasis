/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.CheckBoxModel;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GroupCheckBoxMenu;

public class SynchOptionsCheckBoxGroup extends GroupCheckBoxMenu {

  private List<CheckBoxModel> menuItems;

  public SynchOptionsCheckBoxGroup() {

    HashMap<Feature, Boolean> actions = new HashMap<>();
    actions.put(ActionW.SCROLL_SERIES, true);
    actions.put(ActionW.PAN, true);
    actions.put(ActionW.ZOOM, true);
    actions.put(ActionW.ROTATION, true);
    actions.put(ActionW.FLIP, true);
    actions.put(ActionW.WINDOW, true);
    actions.put(ActionW.LEVEL, true);
    actions.put(ActionW.SPATIAL_UNIT, true);

    menuItems = new ArrayList<>();

    for (Map.Entry<Feature, Boolean> entry : actions.entrySet()) {
      menuItems.add(new CheckBoxModel(entry.getKey().getTitle(), entry.getValue()));
    }

    this.setModel(menuItems);
  }
}
