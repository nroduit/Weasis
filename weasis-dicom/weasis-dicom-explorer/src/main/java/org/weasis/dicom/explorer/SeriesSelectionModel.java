/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.swing.JPanel;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.dicom.explorer.DicomExplorer.PatientPane;
import org.weasis.dicom.explorer.DicomExplorer.SeriesPane;
import org.weasis.dicom.explorer.DicomExplorer.StudyPane;

public class SeriesSelectionModel extends ArrayList<Series<?>> {

  public static final String FOREGROUND = "List.foreground";
  public static final String BACKGROUND = "List.background";
  public static final String SELECTION_FOREGROUND = "List.selectionForeground";
  public static final String SELECTION_BACKGROUND = "List.selectionBackground";

  private final PatientPane patientContainer;

  private Series<?> anchorSelection;
  private Series<?> leadSelection;
  private boolean openingSeries = false;

  public SeriesSelectionModel(PatientPane patientContainer) {
    this.patientContainer = Objects.requireNonNull(patientContainer);
  }

  @Override
  public void add(int index, Series<?> element) {
    if (!contains(element)) {
      super.add(index, element);
      setBackgroundColor(element, false);
    }
  }

  @Override
  public boolean add(Series<?> e) {
    if (!contains(e)) {
      setBackgroundColor(e, true);
      return super.add(e);
    }
    return false;
  }

  @Override
  public boolean addAll(Collection<? extends Series<?>> c) {
    for (Series<?> series : c) {
      setBackgroundColor(series, true);
    }
    return super.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends Series<?>> c) {
    for (Series<?> series : c) {
      setBackgroundColor(series, true);
    }
    return super.addAll(index, c);
  }

  public Series<?> getAnchorSelection() {
    return anchorSelection;
  }

  public Series<?> getLeadSelection() {
    return leadSelection;
  }

  @Override
  public void clear() {
    this.anchorSelection = null;
    this.leadSelection = null;
    for (Series<?> s : this) {
      setBackgroundColor(s, false);
    }
    super.clear();
  }

  @Override
  public Series<?> remove(int index) {
    Series<?> s = super.remove(index);
    if (s != null) {
      setBackgroundColor(s, false);
    }
    return s;
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof Series) {
      setBackgroundColor((Series<?>) o, false);
    }
    return super.remove(o);
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    if (fromIndex < toIndex) {
      int seriesSize = this.size();
      int end = Math.min(toIndex, seriesSize);
      int start = Math.max(fromIndex, 0);
      for (int i = start; i < end; i++) {
        Series<?> val = this.get(i);
        setBackgroundColor(val, false);
      }
      super.removeRange(start, end);
    }
  }

  @Override
  public Series<?> set(int index, Series<?> element) {
    Series<?> s = super.set(index, element);
    if (s != null) {
      setBackgroundColor(s, false);
    }
    if (element != null) {
      setBackgroundColor(element, true);
    }
    return s;
  }

  private void setBackgroundColor(Series<?> series, boolean selected) {
    if (series != null) {
      Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
      if (thumb != null) {
        Container parent = thumb.getParent();
        if (parent instanceof JPanel) {
          Color background;
          Color foreground;
          if (selected) {
            background = FlatUIUtils.getUIColor(SELECTION_BACKGROUND, Color.DARK_GRAY);
            foreground = FlatUIUtils.getUIColor(SELECTION_FOREGROUND, Color.LIGHT_GRAY);
          } else {
            background = FlatUIUtils.getUIColor(BACKGROUND, Color.LIGHT_GRAY);
            foreground = FlatUIUtils.getUIColor(FOREGROUND, Color.DARK_GRAY);
          }
          parent.setBackground(background);
          if (parent instanceof SeriesPane pane) {
            pane.getLabel().setForeground(foreground);
          }
        }
      }
    }
  }

  private void requestFocus(Series<?> series) {
    if (series != null) {
      Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
      if (thumb != null) {
        if (!thumb.hasFocus() && thumb.isRequestFocusEnabled()) {
          thumb.requestFocus();
        }
      }
    }
  }

  void adjustSelection(InputEvent e, Series<?> series) {
    if (e != null && series != null) {
      boolean anchorSelected;
      Series<?> anchor = anchorSelection;
      if (anchor == null) {
        anchorSelected = false;
      } else {
        anchorSelected = this.contains(anchor);
      }

      if ((e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0) {
        if (e.isShiftDown()) {
          if (anchorSelected) {
            addSelectionInterval(anchor, series);
          } else {
            removeSelectionInterval(anchor, series);
          }
        } else if (e instanceof KeyEvent) {
          setSelectionInterval(series, series);
        } else if (this.contains(series)) {
          removeSelectionInterval(series, series);
        } else {
          addSelectionInterval(series, series);
        }
      } else if (e.isShiftDown()) {
        setSelectionInterval(anchor, series);
      } else {
        setSelectionInterval(series, series);
      }
    }
  }

  void setSelectionInterval(Series<?> anchorIndex, Series<?> row) {
    this.clear();
    addSelectionInterval(anchorIndex, row);
  }

  void removeSelectionInterval(Series<?> anchorIndex, Series<?> row) {
    if (anchorIndex == null || row == null) {
      return;
    }

    int fromIndex = -1;
    int toIndex = -1;
    for (int i = 0; i < this.size(); i++) {
      Series<?> val = this.get(i);
      if (anchorIndex == val) {
        if (fromIndex == -1) {
          fromIndex = i;
        } else {
          toIndex = i;
          break;
        }
      }
      if (row == val) {
        if (fromIndex == -1) {
          fromIndex = i;
        } else {
          toIndex = i;
          break;
        }
      }
    }

    removeRange(fromIndex, toIndex + 1);

    requestFocus(row);
    this.anchorSelection = anchorIndex;
    this.leadSelection = row;
  }

  void addSelectionInterval(Series anchorIndex, Series row) {
    if (anchorIndex == null || row == null) {
      return;
    }

    boolean first = false;
    if (anchorIndex == row) {
      add(anchorIndex);
    } else {
      pat:
      for (StudyPane studyPane : patientContainer.getStudyPaneList()) {
        for (SeriesPane series : studyPane.getSeriesPaneList()) {
          if (anchorIndex == series.getSequence()) {
            add(anchorIndex);
            if (first) {
              break pat;
            }
            first = true;
          } else if (row == series.getSequence()) {
            add(row);
            if (first) {
              break pat;
            }
            first = true;
          } else if (first) {
            add((Series) series.getSequence());
          }
        }
      }
    }
    requestFocus(row);
    this.anchorSelection = anchorIndex;
    this.leadSelection = row;
  }

  Series getFirstElement() {
    if (patientContainer != null) {
      for (StudyPane studyPane : patientContainer.getStudyPaneList()) {
        List<SeriesPane> list = studyPane.getSeriesPaneList();
        if (list.size() > 0) {
          return (Series) list.get(0).getSequence();
        }
      }
    }
    return null;
  }

  Series getLastElement() {
    if (patientContainer != null) {
      List<StudyPane> st = patientContainer.getStudyPaneList();
      for (int j = st.size() - 1; j >= 0; j--) {
        List<SeriesPane> list = st.get(j).getSeriesPaneList();
        if (list.size() > 0) {
          return (Series) list.get(list.size() - 1).getSequence();
        }
      }
    }
    return null;
  }

  Series getPreviousElement(Series element) {
    if (element != null && patientContainer != null) {
      boolean next = false;
      List<StudyPane> st = patientContainer.getStudyPaneList();
      for (int j = st.size() - 1; j >= 0; j--) {
        List<SeriesPane> list = st.get(j).getSeriesPaneList();
        for (int k = list.size() - 1; k >= 0; k--) {
          if (next) {
            return (Series) list.get(k).getSequence();
          }
          if (element == list.get(k).getSequence()) {
            next = true;
          }
        }
      }
      return getFirstElement();
    }
    return null;
  }

  Series getNextElement(Series element) {
    if (element != null && patientContainer != null) {
      boolean next = false;
      for (StudyPane studyPane : patientContainer.getStudyPaneList()) {
        List<SeriesPane> list = studyPane.getSeriesPaneList();
        for (SeriesPane seriesPane : list) {
          if (next) {
            return (Series) seriesPane.getSequence();
          }
          if (element == seriesPane.getSequence()) {
            next = true;
          }
        }
      }
      return getLastElement();
    }
    return null;
  }

  public boolean isOpeningSeries() {
    return openingSeries;
  }

  public void setOpeningSeries(boolean openingSeries) {
    this.openingSeries = openingSeries;
  }
}
