package org.weasis.acquire.explorer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * Do the process of publish DICOM files from the given temporary folder. Operation is a CSTORE to a DICOM node
 * destination. All the job is done outside of the EDT instead of setting AcquireImageStatus change and removing related
 * Acquired Images from the dataModel. But, full process progression can still be listened with propertyChange
 * notification of this workerTask.
 *
 * @version $Rev$ $Date$
 */

public class PublishDicomTask extends SwingWorker<DicomState, File> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishDicomTask.class);

    private final File exportDirDicom;
    private final DicomNode callingNode;
    private final DicomNode destinationNode;

    private DicomProgress dicomProgress = new DicomProgress();

    public PublishDicomTask(File exportDirDicom) {
        this.exportDirDicom = Objects.requireNonNull(exportDirDicom);

        callingNode = new DicomNode(BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.aet", "WEASIS_AE")); //$NON-NLS-1$ //$NON-NLS-2$

        String host = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.host", "localhost"); //$NON-NLS-1$ //$NON-NLS-2$
        String aet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.aet", "DCM4CHEE"); //$NON-NLS-1$ //$NON-NLS-2$
        String port = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.port", "11112"); //$NON-NLS-1$ //$NON-NLS-2$

        destinationNode = new DicomNode(aet, host, Integer.parseInt(port));
        LOGGER.debug("destinationNode is : {}", destinationNode); //$NON-NLS-1$

        initDicomProgress();
    }

    private void initDicomProgress() {
        dicomProgress.addProgressListener(progress -> {
            int completed = progress.getNumberOfCompletedSuboperations() + progress.getNumberOfFailedSuboperations();
            int remaining = progress.getNumberOfRemainingSuboperations();

            setProgress((completed * 100) / (completed + remaining));
            publish(progress.getProcessedFile());
        });
    }

    @Override
    protected DicomState doInBackground() throws Exception {
        List<String> exportFilesDicomPath = new ArrayList<>();
        exportFilesDicomPath.add(exportDirDicom.getPath());
        try {
            return CStore.process(callingNode, destinationNode, exportFilesDicomPath, dicomProgress);
        } finally {
            FileUtil.recursiveDelete(exportDirDicom);
        }
    }

    @Override
    protected void process(List<File> chunks) {
        if (!dicomProgress.isLastFailed()) {
            chunks.stream().filter(Objects::nonNull).map(imageFile -> AcquireManager.findByUId(imageFile.getName()))
                .filter(Objects::nonNull).forEach(imageInfo -> {
                    imageInfo.setStatus(AcquireImageStatus.PUBLISHED);
                    AcquireManager.getInstance().removeImage(imageInfo.getImage());
                });
        }
    }

}
