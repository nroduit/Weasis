/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.utils;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Hashtable;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.util.FontTools;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 *
 */
public class SwingHelper {
    private SwingHelper() {
        // Cannot use constructor
    }

    /**
     * @return A new JPanel
     * @since 2.5.0
     */
    public static JPanel newPanel() {
        return new JPanel();
    }

    /**
     *
     * @param element
     * @param height
     * @return
     * @since 2.5.0
     */
    public static <T extends JComponent> T setHeight(T element, int height) {
        Dimension dim = element.getPreferredSize();
        dim.height = height;
        element.setPreferredSize(dim);
        return element;
    }

    /**
     * @author Yannick LARVOR
     * @version 2.5.0
     * @since 2.5.0 - 2016-04-08 - ylar - Creation
     */
    public static class Layout {
        private Layout() {
            // Cannot use Layout() constructor
        }

        /**
         *
         * @param element
         * @return
         * @since 2.5.0
         */
        public static <T extends JComponent> T verticalBox(T element) {
            element.setLayout(new BoxLayout(element, BoxLayout.Y_AXIS));
            return element;
        }

        /**
         *
         * @param element
         * @return
         * @since 2.5.0
         */
        public static <T extends JComponent> T grid(T element) {
            return grid(element, 1);
        }

        /**
         *
         * @param element
         * @param columns
         * @return
         * @since 2.5.0
         */
        public static <T extends JComponent> T grid(T element, int columns) {
            element.setLayout(new GridLayout(0, columns, 10, 10));
            return element;
        }
    }

    /**
     *
     * @param value
     * @param min
     * @param max
     * @return
     * @since 2.5.0
     */
    public static JSlider newSlider(int value, int min, int max) {
        return new JSlider(min, max, value);
    }

    /**
     *
     * @param value
     * @param min
     * @param max
     * @param nbDiv
     * @return
     * @since 2.5.0
     */
    public static JSlider newSlider(int value, int min, int max, int nbDiv) {
        JSlider slider = newSlider(value, min, max);

        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        setSliderLabelValues(slider, nbDiv);

        return slider;
    }

    /**
     *
     * @param slider
     * @param div
     * @since 2.5.0
     */
    public static void setSliderLabelValues(JSlider slider, final int div) {
        final int min = slider.getMinimum();
        final int max = slider.getMaximum();

        if (div < 1) {
            return;
        }
        int space = (max - min) / (div - 1);
        final int spacing = space < 1 ? 1 : space;

        if (!slider.getPaintLabels()) {
            return;
        }

        final Hashtable<Integer, JLabel> table = new Hashtable<>();

        GuiExecutor.instance().invokeAndWait(() -> {
            for (int i = 0; i < div; i++) {
                Integer index = i * spacing + min;
                table.put(index, new JLabel(index.toString()));
            }
        });

        slider.setLabelTable(table);
        SliderChangeListener.setFont(slider, FontTools.getFont10());
        slider.setMinorTickSpacing(1);
        slider.setMajorTickSpacing(spacing);
    }
}
