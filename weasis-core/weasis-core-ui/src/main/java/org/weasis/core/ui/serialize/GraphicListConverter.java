package org.weasis.core.ui.serialize;

import java.util.Map;

import org.simpleframework.xml.convert.Converter;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.model.GraphicList;

public class GraphicListConverter implements Converter<GraphicList> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphicListConverter.class);

    public GraphicListConverter() {
    }

    @Override
    public GraphicList read(InputNode node) throws Exception {
        GraphicList graphics = new GraphicList();
        Persister serializer = DefaultSerializer.getInstance().getSerializer();
        Map<String, Class<?>> map = DefaultSerializer.getInstance().getClassMap();
        while (true) {
            InputNode next = node.getNext();
            if (next == null) {
                return graphics;
            }
            Graphic g = null;
            try {
                Class<?> val = map.get(next.getName());
                if (val != null && Graphic.class.isAssignableFrom(val)) {
                    g = serializer.read((Class<? extends Graphic>) val, next);
                }
            } catch (Exception e) {
                LOGGER.error("Cannot instantiate a graphic: {}", e.getMessage());
            }
            if (g != null) {
                graphics.list.add(g);
            }
        }
    }

    @Override
    public void write(OutputNode node, GraphicList gl) throws Exception {
        if (gl != null) {
            Persister serializer = DefaultSerializer.getInstance().getSerializer();
            synchronized (gl) {
                for (Graphic g : gl.list) {
                    try {
                        serializer.write(g, node);
                    } catch (Exception e) {
                        LOGGER.error("Cannot write a graphic: {}", e.getMessage());
                    }
                }
            }
        }
    }

}