/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

/** Finds a loaded series by the id a snapshot stores for it. */
@FunctionalInterface
public interface SeriesResolver {

  Optional<MediaSeries<? extends MediaElement>> resolve(String id);

  /** Looks the id up in the model of every explorer of the application. */
  static SeriesResolver ofApplication() {
    return ofModels(applicationModels());
  }

  /** The model of every explorer of the application, in order. */
  static List<DataExplorerModel> applicationModels() {
    List<DataExplorerModel> models = new ArrayList<>();
    for (DataExplorerView explorer : GuiUtils.getUICore().getExplorerPlugins()) {
      DataExplorerModel model = explorer.getDataExplorerModel();
      if (model != null && !models.contains(model)) {
        models.add(model);
      }
    }
    if (!models.contains(ViewerPluginBuilder.DefaultDataModel)) {
      models.add(ViewerPluginBuilder.DefaultDataModel);
    }
    return models;
  }

  /** Looks the id up in every explorer model, in order. */
  static SeriesResolver ofModels(Collection<? extends DataExplorerModel> models) {
    return id -> models.stream().map(m -> m.findSeries(id)).flatMap(Optional::stream).findFirst();
  }

  /** Every series this resolver gives that the predicate accepts. */
  default SeriesResolver filter(Predicate<MediaSeries<? extends MediaElement>> accept) {
    return id -> resolve(id).filter(accept);
  }
}
