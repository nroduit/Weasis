package org.weasis.acquire.explorer.gui.control;

import java.awt.Dimension;
import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.media.jai.PlanarImage;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageStatus;
import org.weasis.acquire.explorer.dicom.Transform2Dicom;
import org.weasis.acquire.explorer.gui.dialog.AcquirePublishDialog;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.param.DicomNode;

public class AcquirePublishPanel extends JPanel {
    private static final long serialVersionUID = 7909124238543156489L;

    public static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishPanel.class);

    private final JButton publishBtn = new JButton("Publish");
    private final CircularProgressBar progressBar = new CircularProgressBar(0, 100);

    public AcquirePublishPanel() {
        // setBorder(new TitledBorder(null, "Publish", TitledBorder.LEADING, TitledBorder.TOP, null, null));

        publishBtn.addActionListener(e -> {
            final AcquirePublishDialog dialog = new AcquirePublishDialog(AcquirePublishPanel.this);
            JMVUtils.showCenterScreen(dialog, WinUtil.getParentWindow(AcquirePublishPanel.this));
        });

        publishBtn.setPreferredSize(new Dimension(150, 40));
        publishBtn.setFont(FontTools.getFont12Bold());

        add(publishBtn);
        progressBar.setVisible(false);
        add(progressBar);
    }

    public void publish(List<AcquireImageInfo> toPublish) {
        File exportDirDicom = Transform2Dicom.dicomize(toPublish);

        String host = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.host", "localhost");
        String aet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.aet", "DCM4CHEE");
        String port = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.acquire.dest.port", "11112");

        final DicomNode node = new DicomNode(aet, host, Integer.parseInt(port));

        try {
            toPublish.stream().forEach(AcquireImageInfo.changeStatus(AcquireImageStatus.SUBMITTED));
            progressBar.setVisible(true);
            Transform2Dicom.sendDicomFiles(exportDirDicom, node, progressBar);
            toPublish.stream().forEach(AcquireImageInfo.changeStatus(AcquireImageStatus.PUBLISHED));
        } catch (Exception ex) {
            LOGGER.error("Sending DICOM", ex);
            toPublish.stream().forEach(AcquireImageInfo.changeStatus(AcquireImageStatus.TO_PUBLISH));
        } finally {
            progressBar.setVisible(false);
        }
    }

    public void publishForTest(List<AcquireImageInfo> toPublish) {
        File exportDirImage =
            FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "acquire", "img"));
        File exportDirXml = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "acquire", "xml"));

        toPublish.stream().forEach(imageInfo -> {
            writteJpeg(exportDirImage, imageInfo);
            writeXml(exportDirXml, imageInfo);
        });
    }

    private void writeXml(File exportDirXml, AcquireImageInfo imgInfo) {
        ImageElement img = imgInfo.getImage();
        GraphicModel graphicManager = (GraphicModel) img.getTagValue(TagW.PresentationModel);

        if (Objects.nonNull(graphicManager)) {
            XmlSerializer.writePresentation(img, new File(exportDirXml, img.getName()));
        }
    }

    private void writteJpeg(File exportDirImage, AcquireImageInfo imageInfo) {
        ImageElement img = imageInfo.getImage();
        TagW tagUid = TagD.getUID(Level.INSTANCE);
        String uid = (String) img.getTagValue(tagUid);

        if (!imageInfo.getCurrentValues().equals(imageInfo.getDefaultValues())) {
            File imgFile = new File(exportDirImage, uid + ".jpg");
            PlanarImage transformedImage = img.getImage(imageInfo.getPostProcessOpManager(), false);

            if (!ImageFiler.writeJPG(imgFile, transformedImage, 0.8f)) {
                // out of memory
                imgFile.delete();
            }
        }
    }

}
