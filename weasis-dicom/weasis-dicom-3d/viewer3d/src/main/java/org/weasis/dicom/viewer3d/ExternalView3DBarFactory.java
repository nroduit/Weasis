/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import java.util.Hashtable;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableFactory;

@org.osgi.service.component.annotations.Component(
    service = InsertableFactory.class,
    property = {"org.weasis.dicom.viewer2d.View2dContainer=true"})
public class ExternalView3DBarFactory implements InsertableFactory {

  @Override
  public Insertable createInstance(Hashtable<String, Object> properties) {
    return new ExternalView3DToolbar(60);
  }

  @Override
  public void dispose(Insertable component) {
    // Do nothing
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof ExternalView3DToolbar;
  }

  @Override
  public Insertable.Type getType() {
    return Insertable.Type.TOOLBAR;
  }
}
