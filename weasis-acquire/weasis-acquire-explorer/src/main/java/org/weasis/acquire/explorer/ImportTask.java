package org.weasis.acquire.explorer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.media.data.ImageElement;

/**
 * Do the process of convert to JPEG and dicomize given image collection to a temporary folder. All the job is done
 * outside of the EDT instead of setting AcquireImageStatus change. But, full process progression can still be listened
 * with propertyChange notification of this workerTask.
 *
 * @version $Rev$ $Date$
 */

public class ImportTask extends SwingWorker<Void, AcquireImageInfo> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportTask.class);

    private SeriesGroup searchedSeries;
    private final Collection<ImageElement> imagesToImport;
    private int maxRangeInMinutes;

    public ImportTask(SeriesGroup searchedSeries, Collection<ImageElement> toImport, int maxRangeInMinutes) {
        this.searchedSeries = Objects.requireNonNull(searchedSeries);
        this.imagesToImport = Objects.requireNonNull(toImport);
        this.maxRangeInMinutes = maxRangeInMinutes;
    }

    @Override
    protected Void doInBackground() throws Exception {

        final int nbImageToProcess = imagesToImport.size();
        int nbImageProcessed = 0;

        try {
            for (ImageElement imageElement : imagesToImport) {
                nbImageProcessed++;

                AcquireImageInfo imageInfo = AcquireManager.findByImage(imageElement);
                if (imageInfo == null) {
                    continue;
                }

                imageInfo.setSeries(AcquireManager.findSeries(searchedSeries, imageInfo, maxRangeInMinutes));

                setProgress(nbImageProcessed * 100 / nbImageToProcess);
                publish(imageInfo);
            }

        } catch (Exception ex) {
            LOGGER.error("ImportTask process", ex); //$NON-NLS-1$
            return null;
        }

        return null;
    }

    @Override
    protected void process(List<AcquireImageInfo> chunks) {
        if (SeriesGroup.Type.DATE.equals(searchedSeries.getType())) {

            // TODO do group Map<SeriesGroup, List<AcquireImageInfo>> to avoid much computing

            chunks.stream().forEach(imageInfo -> {
                List<AcquireImageInfo> imageInfoList = AcquireManager.findbySerie(imageInfo.getSeries());
                // ADD imageInfo here since it's not in the dataModel yet => see AcquireManager.addImages()
                imageInfoList.add(imageInfo);
                if (imageInfoList.size() > 2) {
                    AcquireManager.recalculateCentralTime(imageInfoList);
                }
            });
        }

        AcquireManager.getInstance().addImages(chunks);
    }

}
