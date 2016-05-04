package org.weasis.core.api.media.data;

public interface TagReadable {

    boolean containTagKey(TagW tag);

    Object getTagValue(TagW tag);

}