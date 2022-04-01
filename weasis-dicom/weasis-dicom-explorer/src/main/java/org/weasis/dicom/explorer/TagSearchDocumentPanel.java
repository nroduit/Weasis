/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomFieldsView.SearchHighlightPainter;

public class TagSearchDocumentPanel extends AbstractTagSearchPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(TagSearchDocumentPanel.class);
  private static final Highlighter.HighlightPainter searchHighlightPainter =
      new SearchHighlightPainter(IconColor.ACTIONS_YELLOW.color);
  private static final Highlighter.HighlightPainter searchResultHighlightPainter =
      new SearchHighlightPainter((IconColor.ACTIONS_BLUE.color));

  private final List<Integer> searchPositions = new ArrayList<>();
  private final JTextComponent textComponent;
  private int currentSearchIndex = 0;
  private String currentSearchPattern;

  public TagSearchDocumentPanel(JTextComponent textComponent) {
    this.textComponent = textComponent;
    textComponent.addKeyListener(this);
    textComponent.setFocusable(true);
  }

  protected void previous() {
    if (!searchPositions.isEmpty()) {
      currentSearchIndex =
          currentSearchIndex <= 0 ? searchPositions.size() - 1 : currentSearchIndex - 1;
      showCurrentSearch(currentSearchPattern);
    }
  }

  protected void next() {
    if (!searchPositions.isEmpty()) {
      currentSearchIndex =
          currentSearchIndex >= searchPositions.size() - 1 ? 0 : currentSearchIndex + 1;
      showCurrentSearch(currentSearchPattern);
    }
  }

  public void highlight(String pattern) {
    removeHighlights(textComponent);
    searchPositions.clear();
    if (StringUtil.hasText(pattern)) {
      try {
        Highlighter highlighter = textComponent.getHighlighter();
        Document doc = textComponent.getDocument();
        String text = doc.getText(0, doc.getLength()).toUpperCase();
        String patternUp = pattern.toUpperCase();
        int pos = 0;

        while ((pos = text.indexOf(patternUp, pos)) >= 0) {
          if (searchPositions.isEmpty()) {
            highlighter.addHighlight(pos, pos + patternUp.length(), searchHighlightPainter);
          } else {
            highlighter.addHighlight(pos, pos + patternUp.length(), searchResultHighlightPainter);
          }
          searchPositions.add(pos);
          pos += patternUp.length();
        }
      } catch (BadLocationException e) {
        LOGGER.error("Highlight result of search", e);
      }
    }
  }

  public void removeHighlights(JTextComponent textComponent) {
    Highlighter highlighter = textComponent.getHighlighter();
    for (Highlighter.Highlight highlight : highlighter.getHighlights()) {
      if (highlight.getPainter() instanceof SearchHighlightPainter) {
        highlighter.removeHighlight(highlight);
      }
    }
  }

  public void showCurrentSearch(String pattern) {
    if (!searchPositions.isEmpty() && StringUtil.hasText(pattern)) {
      removeHighlights(textComponent);

      try {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchPositions.size()) {
          currentSearchIndex = 0;
        }
        int curPos = searchPositions.get(currentSearchIndex);
        Highlighter highlighter = textComponent.getHighlighter();

        for (Integer pos : searchPositions) {
          if (pos == curPos) {
            highlighter.addHighlight(pos, pos + pattern.length(), searchHighlightPainter);
          } else {
            highlighter.addHighlight(pos, pos + pattern.length(), searchResultHighlightPainter);
          }
        }
        textComponent.scrollRectToVisible(textComponent.modelToView(curPos));
      } catch (BadLocationException e) {
        LOGGER.error("Highlight result of search", e);
      }
    }
  }

  @Override
  protected void filter() {
    currentSearchPattern = textFieldSearch.getText().trim();
    highlight(currentSearchPattern);
  }
}
