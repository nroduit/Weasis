/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.ui.editor.image.ViewerPlugin;

/**
 * Producer-Consumer pattern implementation to handle events.
 * 
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, Sep 12
 */
public class EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisher.class);

    public static final String CONTAINER_SELECTED = "container.selected.";
    public static final String VIEWER_SELECTED = "viewer.selected.";
    public static final String CONTAINER_DO_ACTION = "container.do.";
    public static final String VIEWER_DO_ACTION = "viewer.do.";
    public static final String CONTAINER_ACTION_CHANGED = "container.actionChanged.";
    public static final String VIEWER_ACTION_CHANGED = "viewer.actionChanged.";
    public static final String ALL_VIEWERS_DO_ACTION = "allViewers.do.";

    /**
     * Util: get Action cmd assumming its the last part of expression.
     * 
     * @param expression
     *            Expression containing cmd as last part after dot.
     * @return the cmd, or null if cant find it.
     */
    public static String getActionCmd(final String expression) {
        if (expression != null) {
            int lastDot = expression.lastIndexOf('.');
            if (lastDot >= 0) {
                return expression.substring(lastDot + 1);
            }
        }
        return null;
    }

    private Buffer prodBuf;
    private Map<String, List<PropertyChangeListener>> listeners;
    private Thread consumerThread;

    private EventPublisher() {
        listeners = new HashMap<String, List<PropertyChangeListener>>();
        prodBuf = new Buffer();
        consumerThread = new Consumer(prodBuf);
        consumerThread.start();
    }

    public static EventPublisher getInstance() {
        return EventPublisherHolder.INSTANCE;
    }

    private static class EventPublisherHolder {
        private static final EventPublisher INSTANCE = new EventPublisher();
    }

    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        addPropertyChangeListener(null, listener);
    }

    public void addPropertyChangeListener(final String propertyRegex, final PropertyChangeListener listener) {
        if (listener != null) {
            List<PropertyChangeListener> list;
            if (listeners.containsKey(propertyRegex)) {
                list = listeners.get(propertyRegex);
            } else {
                list = new ArrayList<PropertyChangeListener>();
                listeners.put(propertyRegex, list);
            }
            list.add(listener);
        }
    }

    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        Iterator<List<PropertyChangeListener>> it = listeners.values().iterator();
        boolean wasRemoved = false;
        while (it.hasNext()) {
            if (it.next().remove(listener)) {
                wasRemoved = true;
            }
        }
        if (!wasRemoved) {
            LOGGER.error("Não foi possível remover PropertyChangeListener="
                + (listener == null ? "NULL" : listener.getClass().getName()));
        }
    }

    public void removePropertyChangeListener(final String propertyRegex, final PropertyChangeListener listener) {
        if (listener != null) {
            if (listeners.containsKey(propertyRegex)) {
                List<PropertyChangeListener> list = listeners.get(propertyRegex);
                list.remove(listener);
            }
        }
    }

    public PropertyChangeListener[] getPropertyChangeListeners(final String propertyName) {
        List<PropertyChangeListener> list = new ArrayList<PropertyChangeListener>();
        List<PropertyChangeListener> getNull = listeners.get(null);
        if (getNull != null) {
            list.addAll(listeners.get(null));
        }

        if (propertyName != null) {
            Iterator<String> iterator = listeners.keySet().iterator();
            while (iterator.hasNext()) {
                String next = iterator.next();
                if (next != null && propertyName.matches(next)) {
                    list.addAll(listeners.get(next));
                }
            }
        }
        return list.toArray(new PropertyChangeListener[list.size()]);
    }

    /**
     * Publishes events to all registered PropertyChangeListener and the selected ViewerPlugin on EDT.
     *
     * @param event
     */
    public synchronized void publish(final PropertyChangeEvent event) {
        if (event != null) {
            prodBuf.put(event);
        }
    }

    protected void publishOut(final PropertyChangeEvent event) {
        final String name = event.getPropertyName();
        final PropertyChangeListener[] fireTo = getPropertyChangeListeners(name);

        GuiExecutor.instance().execute(new Runnable() {
            @Override
            public void run() {

                synchronized (this) {
                    // Registered
                    for (PropertyChangeListener listener : fireTo) {
                        listener.propertyChange(event);
                    }

                    if (name != null
                        && (name.startsWith(ALL_VIEWERS_DO_ACTION) || name.startsWith("texture") || name
                            .startsWith("DicomModel"))) {
                        List<ViewerPlugin<?>> allViewerPlugins = GUIManager.getInstance().getAllViewerPlugins();
                        for (ViewerPlugin viewerPlugin : allViewerPlugins) {
                            if (viewerPlugin instanceof PropertyChangeListener) {
                                ((PropertyChangeListener) viewerPlugin).propertyChange(event);
                            }
                        }
                    } else {
                        // Selected Plugin
                        ViewerPlugin selectedViewerPlugin = GUIManager.getInstance().getSelectedView2dContainer();
                        if (selectedViewerPlugin instanceof PropertyChangeListener) {
                            ((PropertyChangeListener) selectedViewerPlugin).propertyChange(event);
                        }
                    }
                }
            }
        });
    }

    public void setPriority(int priority) {
        consumerThread.setPriority(priority);
    }

    private static class Buffer {
        private ConcurrentLinkedQueue<PropertyChangeEvent> contents = new ConcurrentLinkedQueue<PropertyChangeEvent>();

        public synchronized void put(PropertyChangeEvent evt) {
            contents.add(evt);
            notify();
        }

        public synchronized PropertyChangeEvent get() throws InterruptedException {
            while (contents.isEmpty()) { // wait till something appears in the buffer
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw e;
                }
            }

            notify();
            return contents.poll();
        }
    }

    /**
     * Consumer helper.
     */
    private class Consumer extends Thread {

        private Buffer consBuf;

        public Consumer(Buffer buf) {
            super("Events Thread");
            consBuf = buf;
        }

        @Override
        public void run() {
            PropertyChangeEvent value;
            while (true) {
                try {
                    publishOut(consBuf.get());
                } catch (InterruptedException e) {
                    return;
                }

            }
        }
    }

}
