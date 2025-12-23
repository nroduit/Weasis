/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer;

import java.util.Map;
import org.pf4j.Extension;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.plugin.SingletonExtension;

@Extension
@SingletonExtension
public class DefaultExplorerFactory implements DataExplorerViewFactory {

  private DefaultExplorer explorer = null;
  private FileTreeModel model = null;

  @Override
  public DataExplorerView createDataExplorerView(Map<String, Object> properties) {
    if (model == null) {
      model = JIUtility.createTreeModel();
    }
    if (explorer == null) {
      JIThumbnailCache thumbCache = new JIThumbnailCache();
      explorer = new DefaultExplorer(model, thumbCache);
      explorer.iniLastPath();
    }
    return explorer;
  }

  public void stop() {
    if (explorer != null) {
      explorer.saveLastPath();
    }
  }
}
