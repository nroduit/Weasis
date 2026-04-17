/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime.component.time;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.weasis.core.ui.tp.raven.datetime.TimePicker;
import org.weasis.core.ui.tp.raven.datetime.component.time.event.TimeActionListener;

/*  PanelClock is a class that provides a clock component for the TimeSpinner.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class PanelClock extends JPanel {

  private final TimePicker timePicker;
  private final TimeActionListener timeActionListener;
  private boolean use24hour;
  private boolean hourSelectionView = true;
  private AnimationChange animationChange;
  private final int margin12h = 20;
  private final int margin24h = 50;
  private Color color;

  public void setHourSelectionView(boolean hourSelectionView) {
    if (this.hourSelectionView != hourSelectionView) {
      this.hourSelectionView = hourSelectionView;
      repaint();
      runAnimation(true);
    }
  }

  public void setHourSelectionViewImmediately(boolean hourSelectionView) {
    if (this.hourSelectionView != hourSelectionView) {
      this.hourSelectionView = hourSelectionView;
      repaint();
      runAnimation(false);
    }
  }

  public boolean isHourSelectionView() {
    return hourSelectionView;
  }

  public void setUse24hour(boolean use24hour) {
    if (this.use24hour != use24hour) {
      this.use24hour = use24hour;
    }
  }

  public boolean isUse24hour() {
    return use24hour;
  }

  public PanelClock(TimePicker timePicker, TimeActionListener timeActionListener) {
    this.timePicker = timePicker;
    this.timeActionListener = timeActionListener;
    init();
  }

  private void init() {
    animationChange = new AnimationChange(this);
    putClientProperty(
        FlatClientProperties.STYLE,
        "border:5,15,5,15;"
            + "background:null;"
            + "foreground:contrast($Component.accentColor,$Panel.background,#fff)");
    MouseAdapter mouseAdapter =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (isEnabled()) {
              mouseChanged(e);
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (isEnabled()) {
              timeActionListener.selectionViewChanged(hourSelectionView);
            }
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            if (isEnabled()) {
              mouseChanged(e);
            }
          }

          private void mouseChanged(MouseEvent e) {
            int value = getValueOf(e.getPoint(), hourSelectionView);
            if (hourSelectionView) {
              timePicker.getTimeSelectionModel().setHour(value);
            } else {
              timePicker.getTimeSelectionModel().setMinute(value);
            }
          }
        };
    addMouseListener(mouseAdapter);
    addMouseMotionListener(mouseAdapter);
  }

  public void updateClock() {
    TimeSelectionModel timeSelectionModel = timePicker.getTimeSelectionModel();
    if (hourSelectionView) {
      animationChange.set(getAngleOf(timeSelectionModel.getHour(), true), getTargetMargin());
    } else {
      animationChange.set(getAngleOf(timeSelectionModel.getMinute(), false), getTargetMargin());
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    FlatUIUtils.setRenderingHints(g2);
    Insets insets = getInsets();
    int width = getWidth() - (insets.left + insets.right);
    int height = getHeight() - (insets.top + insets.bottom);

    int size = Math.min(width, height);
    g2.translate(insets.left, insets.top);
    int x = (width - size) / 2;
    int y = (height - size) / 2;

    //  create clock background
    g2.setColor(getClockBackground());
    g2.fill(new Ellipse2D.Double(x, y, size, size));

    //  create selection
    paintSelection(g2, x, y, size);

    //  create clock number
    paintClockNumber(g2, x, y, size);
    g2.dispose();
  }

  protected void paintSelection(Graphics2D g2, int x, int y, int size) {
    TimeSelectionModel timeSelectionModel = timePicker.getTimeSelectionModel();
    AffineTransform tran = g2.getTransform();
    size = size / 2;
    final float margin = UIScale.scale(animationChange.getMargin());
    float centerSize = UIScale.scale(8f);
    float lineSize = UIScale.scale(3);
    float selectSize = UIScale.scale(25f);
    float unselectSize = UIScale.scale(4);
    float lineHeight = size - margin;
    Area area =
        new Area(
            new Ellipse2D.Float(
                x + size - (centerSize / 2), y + size - (centerSize / 2), centerSize, centerSize));
    if ((hourSelectionView && timeSelectionModel.getHour() != -1)
        || (!hourSelectionView && timeSelectionModel.getMinute() != -1)) {
      area.add(
          new Area(
              new RoundRectangle2D.Float(
                  x + size - (lineSize / 2),
                  y + margin,
                  lineSize,
                  lineHeight,
                  lineSize,
                  lineSize)));
      area.add(
          new Area(
              new Ellipse2D.Float(
                  x + size - (selectSize / 2),
                  y + margin - selectSize / 2,
                  selectSize,
                  selectSize)));
      if (!hourSelectionView
          && !animationChange.isRunning()
          && (timeSelectionModel.getMinute() % 5 != 0)) {
        area.subtract(
            new Area(
                new Ellipse2D.Float(
                    x + size - (unselectSize / 2),
                    y + margin - unselectSize / 2,
                    unselectSize,
                    unselectSize)));
      }
    }
    g2.setColor(getSelectedColor());
    float angle = animationChange.getAngle();
    g2.rotate(Math.toRadians(angle), (double) x + size, (double) y + size);
    g2.fill(area);
    g2.setTransform(tran);
  }

  protected void paintClockNumber(Graphics2D g2, int x, int y, int size) {
    paintClockNumber(g2, x, y, size, margin12h, 0, hourSelectionView ? 1 : 5);
    if (hourSelectionView && use24hour) {
      paintClockNumber(g2, x, y, size, margin24h, 12, 1);
    }
  }

  protected void paintClockNumber(
      Graphics2D g2, int x, int y, int size, int margin, int start, int add) {
    TimeSelectionModel timeSelectionModel = timePicker.getTimeSelectionModel();
    final int mg = UIScale.scale(margin);
    float center = size / 2f;
    float angle = 360 / 12f;
    for (int i = 1; i <= 12; i++) {
      float ag = angle * i - 90;
      int value = fixHour((start + i * add), hourSelectionView);
      float nx = (float) (center + (Math.cos(Math.toRadians(ag)) * (center - mg)));
      float ny = (float) (center + (Math.sin(Math.toRadians(ag)) * (center - mg)));
      int hour;
      int minute;
      if (hourSelectionView) {
        hour = valueToTime(value, true);
        minute = timeSelectionModel.getMinute();
      } else {
        hour = timeSelectionModel.getHour();
        minute = value;
      }
      boolean isSelectedAble = timeSelectionModel.checkSelection(hour, minute);
      paintNumber(
          g2, x + nx, y + ny, fixNumberAndToString(value), isSelected(value), isSelectedAble);
    }
  }

  protected void paintNumber(
      Graphics2D g2, float x, float y, String num, boolean isSelected, boolean isSelectedAble) {
    FontMetrics fm = g2.getFontMetrics();
    Rectangle2D rec = fm.getStringBounds(num, g2);
    float x1 = (float) (x - rec.getWidth() / 2f);
    float y1 = (float) (y - rec.getHeight() / 2f);
    if (!isSelectedAble) {
      g2.setColor(UIManager.getColor("Label.disabledForeground"));
    } else if (isSelected) {
      g2.setColor(getSelectedForeground());
    } else {
      g2.setColor(UIManager.getColor("Panel.foreground"));
    }
    g2.drawString(num, x1, y1 + fm.getAscent());
  }

  protected Color getClockBackground() {
    if (FlatLaf.isLafDark()) {
      return ColorFunctions.lighten(getBackground(), 0.03f);
    } else {
      return ColorFunctions.darken(getBackground(), 0.03f);
    }
  }

  protected boolean isSelected(int value) {
    if (hourSelectionView) {
      int hour = getHourValue(timePicker.getTimeSelectionModel().getHour());
      return value == hour;
    } else {
      return value == timePicker.getTimeSelectionModel().getMinute();
    }
  }

  protected Color getSelectedColor() {
    if (color != null) {
      return color;
    }
    return UIManager.getColor("Component.accentColor");
  }

  protected Color getSelectedForeground() {
    return getForeground();
  }

  /** Convert angle to hour or minute base on the hourView Return value hour or minute */
  private int getValueOf(float angle, boolean hourView) {
    float ag = angle / 360;
    int value = (int) (ag * (hourView ? 12 : 60));
    if (hourView) {
      if (isUse24hour()) {
        return value == 0 ? 12 : value;
      } else {
        return value == 12 ? 0 : value;
      }
    } else {
      return value == 60 ? 0 : value;
    }
  }

  /**
   * Convert point location to the value hour or minute base on the hourView Return value hour or
   * minute
   */
  private int getValueOf(Point point, boolean hourView) {
    float angle = getAngleOf(point) + (hourView ? 360 / 12f / 2f : 360 / 60f / 2f);
    int value = getValueOf(angle, hourView);
    if (hourView) {
      boolean isAdd12Hour =
          (!use24hour && !timePicker.getHeader().isAm()) || (use24hour && is24hourSelect(point));
      return fixHour(value + (isAdd12Hour ? 12 : 0), true);
    } else {
      return value;
    }
  }

  private boolean is24hourSelect(Point point) {
    Insets insets = getInsets();
    int width = getWidth() - (insets.left + insets.right);
    int height = getHeight() - (insets.top + insets.bottom);
    int size = Math.min(width, height) / 2;
    int distanceTarget = (size - UIScale.scale(margin12h + 15));
    float centerX = insets.left + width / 2f;
    float centerY = insets.top + height / 2f;
    double distance =
        Math.sqrt(Math.pow((point.x - centerX), 2) + Math.pow((point.y - centerY), 2));
    return distance < distanceTarget;
  }

  /** Convert hour or minute to the angle base on the hourView Return angle vales */
  private float getAngleOf(int number, boolean hourView) {
    float ag = 360 / (hourView ? 12f : 60f);
    return fixAngle(ag * number);
  }

  /** Convert point location to angle Return angle */
  private float getAngleOf(Point point) {
    Insets insets = getInsets();
    int width = getWidth() - (insets.left + insets.right);
    int height = getHeight() - (insets.top + insets.bottom);
    float centerX = insets.left + width / 2f;
    float centerY = insets.top + height / 2f;
    float x = point.x - centerX;
    float y = point.y - centerY;
    double angle = Math.toDegrees(Math.atan2(y, x)) + 90;
    if (angle < 0) {
      angle += 360;
    }
    return (float) angle;
  }

  /** Make the angle is between 0 and 360-1 */
  private float fixAngle(float angle) {
    if (angle > 360) {
      angle -= 360;
    }
    if (angle == 360) {
      return 0;
    }
    return angle;
  }

  /**
   * Fix hour or minute base on the hourView If 24h ( return 0 to 23 ) If 12h ( return 1 to 12 ) If
   * minute ( return 0 to 59 )
   */
  private int fixHour(int value, boolean hourView) {
    if (hourView) {
      if (use24hour) {
        if (value == 24) {
          return 0;
        }
      }
    } else {
      if (value == 60) {
        return 0;
      }
    }
    return value;
  }

  private String fixNumberAndToString(int num) {
    if (num == 0) {
      return "00";
    }
    return num + "";
  }

  private int getHourValue(int hour) {
    if (isUse24hour()) {
      return hour;
    } else {
      hour = (timePicker.getHeader().isAm() ? hour : hour - 12);
      return hour == 0 ? 12 : hour;
    }
  }

  private int valueToTime(int value, boolean hourView) {
    if (!hourView) {
      return value;
    }
    if (!isUse24hour()) {
      boolean isAm = timePicker.getHeader().isAm();
      value += isAm ? 0 : 12;
      if (isAm && value == 12) {
        return 0;
      }
      if (!isAm && value == 24) {
        return 12;
      }
    }
    return value;
  }

  private boolean is24hour() {
    int hour = timePicker.getTimeSelectionModel().getHour();
    return use24hour && (hour == 0 || hour > 12);
  }

  private int getTargetMargin() {
    return is24hour() && hourSelectionView ? margin24h : margin12h;
  }

  /** Start animation selection change */
  private void runAnimation(boolean animate) {
    int value =
        hourSelectionView
            ? timePicker.getTimeSelectionModel().getHour()
            : timePicker.getTimeSelectionModel().getMinute();
    float angleTarget = getAngleOf(value, hourSelectionView);
    float marginTarget = getTargetMargin();
    animationChange.start(angleTarget, marginTarget, animate);
  }

  public void setColor(Color color) {
    this.color = color;
  }
}
