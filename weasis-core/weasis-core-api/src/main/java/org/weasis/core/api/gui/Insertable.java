package org.weasis.core.api.gui;

public interface Insertable {

    public enum Type {
        EXPLORER, TOOL, TOOLBAR, EMPTY, PREFERENCES
    }

    String getComponentName();

    Type getType();

    boolean isComponentEnabled();

    void setComponentEnabled(boolean enabled);

    int getComponentPosition();

    void setComponentPosition(int position);

}