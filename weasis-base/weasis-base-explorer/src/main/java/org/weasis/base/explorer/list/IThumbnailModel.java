/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.explorer.list;

import java.io.Serializable;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import javax.swing.ListModel;

import org.weasis.base.explorer.JIExplorerContext;

public interface IThumbnailModel<E> extends ListModel<E>, Serializable {

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
