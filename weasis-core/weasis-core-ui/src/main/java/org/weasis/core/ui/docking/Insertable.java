package org.weasis.core.ui.docking;

public interface Insertable {

    public enum Type {
        EXPLORER, TOOL, TOOLBAR, EMPTY,
    }

    String getComponentName();

    Type getType();

    boolean isComponentEnabled();

    void setComponentEnabled(boolean enabled);

    int getComponentPosition();

    void setComponentPosition(int postion);

}