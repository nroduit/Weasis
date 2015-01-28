package org.weasis.dicom.codec;
/*
Core SWING Advanced Programming 
By Kim Topley
ISBN: 0 13 083292 8       
Publisher: Prentice Hall  
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileReader;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.View;

public class HighlightExample {
  public static void main(String[] args) {

	  
    JFrame f = new JFrame("Highlight example");
    final JTextPane textPane = new JTextPane();
    textPane.setHighlighter(highlighter);
    JPanel pane = new JPanel();
    pane.setLayout(new BorderLayout());
    pane.add(new JLabel("Enter word: "), "West");
    final JTextField tf = new JTextField();
    pane.add(tf, "Center");
    f.getContentPane().add(pane, "South");
    f.getContentPane().add(new JScrollPane(textPane), "Center");

    try {
      textPane.read(new FileReader("/home/nicolas/Downloads/wado2.xml"), null);
    } catch (Exception e) {
      System.out.println("Failed to load file " + args[0]);
      System.out.println(e);
    }
    final WordSearcher searcher = new WordSearcher(textPane);

    tf.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        word = tf.getText().trim();
        int offset = searcher.search(word);
        if (offset != -1) {
          try {
            textPane.scrollRectToVisible(textPane
                .modelToView(offset));
          } catch (BadLocationException e) {
          }
        }
      }
    });

    textPane.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent evt) {
        searcher.search(word);
      }

      public void removeUpdate(DocumentEvent evt) {
        searcher.search(word);
      }

      public void changedUpdate(DocumentEvent evt) {
      }
    });

    f.setSize(400, 400);
    f.setVisible(true);
  }

  public static String word;

  public static Highlighter highlighter = new UnderlineHighlighter(null);
}

// A simple class that searches for a word in
// a document and highlights occurrences of that word

class WordSearcher {
  public WordSearcher(JTextComponent comp) {
    this.comp = comp;
    this.painter = new UnderlineHighlighter.UnderlineHighlightPainter(
        Color.red);
  }

  // Search for a word and return the offset of the
  // first occurrence. Highlights are added for all
  // occurrences found.
  public int search(String word) {
    int firstOffset = -1;
    Highlighter highlighter = comp.getHighlighter();

    // Remove any existing highlights for last word
    Highlighter.Highlight[] highlights = highlighter.getHighlights();
    for (int i = 0; i < highlights.length; i++) {
      Highlighter.Highlight h = highlights[i];
      if (h.getPainter() instanceof UnderlineHighlighter.UnderlineHighlightPainter) {
        highlighter.removeHighlight(h);
      }
    }

    if (word == null || word.equals("")) {
      return -1;
    }

    // Look for the word we are given - insensitive search
    String content = null;
    try {
      Document d = comp.getDocument();
      content = d.getText(0, d.getLength()).toLowerCase();
    } catch (BadLocationException e) {
      // Cannot happen
      return -1;
    }

    word = word.toLowerCase();
    int lastIndex = 0;
    int wordSize = word.length();

    while ((lastIndex = content.indexOf(word, lastIndex)) != -1) {
      int endIndex = lastIndex + wordSize;
      try {
        highlighter.addHighlight(lastIndex, endIndex, painter);
      } catch (BadLocationException e) {
        // Nothing to do
      }
      if (firstOffset == -1) {
        firstOffset = lastIndex;
      }
      lastIndex = endIndex;
    }

    return firstOffset;
  }

  protected JTextComponent comp;

  protected Highlighter.HighlightPainter painter;

}

class UnderlineHighlighter extends DefaultHighlighter {
  public UnderlineHighlighter(Color c) {
    painter = (c == null ? sharedPainter : new UnderlineHighlightPainter(c));
  }

  // Convenience method to add a highlight with
  // the default painter.
  public Object addHighlight(int p0, int p1) throws BadLocationException {
    return addHighlight(p0, p1, painter);
  }

  public void setDrawsLayeredHighlights(boolean newValue) {
    // Illegal if false - we only support layered highlights
    if (newValue == false) {
      throw new IllegalArgumentException(
          "UnderlineHighlighter only draws layered highlights");
    }
    super.setDrawsLayeredHighlights(true);
  }

  // Painter for underlined highlights
  public static class UnderlineHighlightPainter extends
      LayeredHighlighter.LayerPainter {
    public UnderlineHighlightPainter(Color c) {
      color = c;
    }

    public void paint(Graphics g, int offs0, int offs1, Shape bounds,
        JTextComponent c) {
      // Do nothing: this method will never be called
    }

    public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds,
        JTextComponent c, View view) {
      g.setColor(color == null ? c.getSelectionColor() : color);

      Rectangle alloc = null;
      if (offs0 == view.getStartOffset() && offs1 == view.getEndOffset()) {
        if (bounds instanceof Rectangle) {
          alloc = (Rectangle) bounds;
        } else {
          alloc = bounds.getBounds();
        }
      } else {
        try {
          Shape shape = view.modelToView(offs0,
              Position.Bias.Forward, offs1,
              Position.Bias.Backward, bounds);
          alloc = (shape instanceof Rectangle) ? (Rectangle) shape
              : shape.getBounds();
        } catch (BadLocationException e) {
          return null;
        }
      }

      FontMetrics fm = c.getFontMetrics(c.getFont());
      int baseline = alloc.y + alloc.height - fm.getDescent() + 1;
      g.drawLine(alloc.x, baseline, alloc.x + alloc.width, baseline);
      g.drawLine(alloc.x, baseline + 1, alloc.x + alloc.width,
          baseline + 1);

      return alloc;
    }

    protected Color color; // The color for the underline
  }

  // Shared painter used for default highlighting
  protected static final Highlighter.HighlightPainter sharedPainter = new UnderlineHighlightPainter(
      null);

  // Painter used for this highlighter
  protected Highlighter.HighlightPainter painter;
}