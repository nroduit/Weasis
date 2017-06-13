package org.weasis.core.api.media.data;

import java.io.File;

public interface Thumbnailable {

    void registerListeners();

    File getThumbnailPath();

    void dispose();

    void removeMouseAndKeyListener();

}