/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.pref;

import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class Viewer3dPrefFactory implements PreferencesPageFactory {

  @Override
  public PageDescriptor describe() {
    return new PageDescriptor(Viewer3dPrefView.PAGE_TITLE, Viewer3dPrefView.PAGE_POSITION);
  }

  @Override
  public AbstractItemDialogPage createInstance(Map<String, Object> properties) {
    return new Viewer3dPrefView();
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof Viewer3dPrefView;
  }
}
