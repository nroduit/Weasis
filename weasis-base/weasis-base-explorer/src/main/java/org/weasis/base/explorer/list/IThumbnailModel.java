/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer.list;

import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import javax.swing.ListModel;
import org.weasis.base.explorer.JIExplorerContext;

public interface IThumbnailModel<E> extends ListModel<E> {

  void setData(Path dir);

  void loadContent(Path dir);

  void loadContent(Path path, Filter<Path> filter);

  void reload();

  JIExplorerContext getReloadContext();

  void setReloadContext(JIExplorerContext reloadContext);

  boolean isEmpty();

  void notifyAsUpdated(int index);

  void addElement(final E obj);

  boolean contains(final E elem);

  boolean removeElement(final E obj);

  void clear();
}
