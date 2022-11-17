/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.awt.datatransfer.Transferable;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.weasis.core.api.gui.util.Filter;

public interface MediaSeries<E> extends MediaSeriesGroup, Transferable {

  enum MEDIA_POSITION {
    FIRST,
    MIDDLE,
    LAST,
    RANDOM
  }

  List<E> getSortedMedias(Comparator<E> comparator);

  void addMedia(E media);

  void add(E media);

  void add(int index, E media);

  void addAll(Collection<? extends E> c);

  void addAll(int index, Collection<? extends E> c);

  E getMedia(MEDIA_POSITION position, Filter<E> filter, Comparator<E> sort);

  Iterable<E> getMedias(Filter<E> filter, Comparator<E> sort);

  List<E> copyOfMedias(Filter<E> filter, Comparator<E> sort);

  E getMedia(int index, Filter<E> filter, Comparator<E> sort);

  MediaElement getFirstSpecialElement();

  @Override
  void dispose();

  int size(Filter<E> filter);

  SeriesImporter getSeriesLoader();

  void setSeriesLoader(SeriesImporter seriesLoader);

  String getToolTips();

  String getSeriesNumber();

  boolean isOpen();

  boolean isSelected();

  boolean isFocused();

  String getMimeType();

  void setOpen(boolean b);

  void setSelected(boolean b, E selectedMedia);

  void setFocused(boolean b);

  E getNearestImage(double location, int offset, Filter<E> filter, Comparator<E> sort);

  int getNearestImageIndex(double location, int offset, Filter<E> filter, Comparator<E> sort);

  long getFileSize();
}
