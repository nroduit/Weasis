package com.codeminders.demo.ui.dicomstore;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

public class AutoRefreshComboBoxExtension {

    private static final long TIME_TO_INVALIDATE_CACHE_MS = 10_000;

    private long lastUpdateTime = System.currentTimeMillis();

    private AutoRefreshComboBoxExtension(JComboBox<?> comboBox, Supplier<Boolean> reload) {
        addDataUpdateListener(comboBox);
        addDataReloadListener(comboBox, reload);
    }

    public static AutoRefreshComboBoxExtension wrap(JComboBox<?> comboBox, Supplier<Boolean> reload) {
        return new AutoRefreshComboBoxExtension(comboBox, reload);
    }

    private void addDataReloadListener(JComboBox<?> comboBox, Supplier<Boolean> reload) {
        comboBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isTimeoutPassed()) {
                    if (Boolean.TRUE.equals(reload.get())) {
                        lastUpdateTime = System.currentTimeMillis();
                    }
                }
            }
        });
    }

    private void addDataUpdateListener(JComboBox<?> comboBox) {
        comboBox.getModel().addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                lastUpdateTime = System.currentTimeMillis();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                lastUpdateTime = System.currentTimeMillis();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                lastUpdateTime = System.currentTimeMillis();
            }
        });
    }

    private boolean isTimeoutPassed() {
        return (System.currentTimeMillis() - lastUpdateTime) > TIME_TO_INVALIDATE_CACHE_MS;
    }

}
