package org.weasis.core.ui.test.utils;

import java.util.Objects;
import java.util.UUID;

import javax.media.jai.PlanarImage;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaReader;

public class ModelListHelper extends XmlSerialisationHelper {
    public static final String UUID_1 = "uuid.1." + UUID.randomUUID().toString();
    public static final String UUID_2 = "uuid.2." + UUID.randomUUID().toString();
    public static final String UUID_3 = "uuid.3." + UUID.randomUUID().toString();

    @Mock protected MediaReader<PlanarImage> mediaIO;
    @Mock protected Object key;
    
    protected ImageElement mockImage(String uuid, String seriesUuid) {
        ImageElement img = PowerMockito.mock(ImageElement.class);
        
        if(Objects.isNull(uuid) && Objects.isNull(seriesUuid)) {
            PowerMockito.when(img.getTagValue(Mockito.any())).thenReturn(null);
        } else if(Objects.nonNull(uuid) && Objects.isNull(seriesUuid)) { 
            PowerMockito.when(img.getTagValue(Mockito.any())).thenReturn(uuid, null, uuid);
        } else {
            PowerMockito.when(img.getTagValue(Mockito.any())).thenReturn(uuid, seriesUuid);
        }
        return img;
    }
}
