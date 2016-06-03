package org.weasis.core.api.image;

@FunctionalInterface
public interface OpEventListener {

    void handleImageOpEvent(ImageOpEvent event);

}