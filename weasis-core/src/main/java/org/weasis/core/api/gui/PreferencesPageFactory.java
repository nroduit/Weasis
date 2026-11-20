/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui;

import java.util.Map;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

public interface PreferencesPageFactory extends InsertableFactory {

  /**
   * The title of a preference page and its place in the tree, known without building the page.
   *
   * @param title the title the page will have: the two must be the same, since a page is found by
   *     its title
   * @param position the position of the page; 501–599 puts it under Viewer, 601–699 under DICOM,
   *     701–799 under Draw &amp; Measure, another value below 1000 under General, 1000 and more at
   *     the root
   */
  record PageDescriptor(String title, int position) {}

  /**
   * Describes the page so that the preference dialog builds it only when it is shown. Without a
   * description (the default) the page is built when the dialog opens.
   */
  default PageDescriptor describe() {
    return null;
  }

  @Override
  AbstractItemDialogPage createInstance(Map<String, Object> properties);

  @Override
  default void dispose(Insertable component) {}

  @Override
  default Type getType() {
    return Insertable.Type.PREFERENCES;
  }
}
