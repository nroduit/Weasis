/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.explorer.list;

import java.util.List;

import org.weasis.core.api.media.data.MediaElement;

public interface IThumbnailListPane<E extends MediaElement> extends DiskFileList {

    List<E> getSelectedValuesList();
}
