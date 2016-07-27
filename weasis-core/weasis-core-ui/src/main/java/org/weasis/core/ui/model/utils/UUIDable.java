package org.weasis.core.ui.model.utils;

import java.io.Serializable;

public interface UUIDable extends Serializable {

    /**
     * Return a Universally Unique IDentifier (UUID)
     * 
     * @return UUID
     */
    String getUuid();

    /**
     * Set a Universally Unique IDentifier (UUID)
     * 
     * @param uuid
     */
    void setUuid(String uuid);
}
