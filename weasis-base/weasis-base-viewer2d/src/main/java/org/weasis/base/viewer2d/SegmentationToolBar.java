package org.weasis.base.viewer2d;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.WtoolBar;

public class SegmentationToolBar<E extends ImageElement> extends WtoolBar {

    public SegmentationToolBar(final ImageViewerEventManager<E> eventManager, int index) {
        super("Segmentation", index);

        final JButton jButtonRotate90 =
            new JButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/rotate.png"))); //$NON-NLS-1$
        jButtonRotate90.setToolTipText("Remove Backgournd"); //$NON-NLS-1$
        jButtonRotate90.addActionListener(e -> doBackgroundRemoval(false));
        add(jButtonRotate90);

        final JToggleButton jButtonFlip =
            new JToggleButton(new ImageIcon(MouseActions.class.getResource("/icon/32x32/flip.png"))); //$NON-NLS-1$
        jButtonFlip.setToolTipText(Messages.getString("RotationToolBar.flip")); //$NON-NLS-1$
        jButtonFlip.addActionListener(e -> test());
        add(jButtonFlip);

    }

    private void test() {
        ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();

        Mat frame = OpencvConverter.fromBufferedImage(view.getSourceImage());
       // Imgproc.blur(frame, frame, new Size(9, 9));
        Imgproc.resize(frame, frame,  new Size(2048, 2048));
        ImageElement imgElement = new ImageElement(new OpencvConverter(frame), 0);
        view.getImageLayer().setImage(imgElement, null);
    }

    /**
     * Perform the operations needed for removing a uniform background
     * 
     * @param frame
     *            the current frame
     * @param inverse
     * @return an image with only foreground objects
     */
    private void doBackgroundRemoval(boolean inverse) {
        ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();

        // init
        Mat hsvImg = new Mat();
        List<Mat> hsvPlanes = new ArrayList<>();
        Mat thresholdImg = new Mat();

        int thresh_type = Imgproc.THRESH_BINARY_INV;
        if (inverse) {
            thresh_type = Imgproc.THRESH_BINARY;
        }

        Mat frame = OpencvConverter.fromBufferedImage(view.getSourceImage());
        // threshold the image with the average hue value
        hsvImg.create(frame.size(), CvType.CV_8U);
        Imgproc.cvtColor(frame, hsvImg, Imgproc.COLOR_BGR2HSV);
        Core.split(hsvImg, hsvPlanes);

        // get the average hue value of the image
        double threshValue = this.getHistAverage(hsvImg, hsvPlanes.get(0));

        Imgproc.threshold(hsvPlanes.get(0), thresholdImg, threshValue, 179.0, thresh_type);

        Imgproc.blur(thresholdImg, thresholdImg, new Size(5, 5));

        // dilate to fill gaps, erode to smooth edges
        Imgproc.dilate(thresholdImg, thresholdImg, new Mat(), new Point(-1, -1), 1);
        Imgproc.erode(thresholdImg, thresholdImg, new Mat(), new Point(-1, -1), 3);

        Imgproc.threshold(thresholdImg, thresholdImg, threshValue, 179.0, Imgproc.THRESH_BINARY);

        // create the new image
        Mat foreground = new Mat(frame.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
        frame.copyTo(foreground, thresholdImg);
        ImageElement imgElement = new ImageElement(new OpencvConverter(frame), 0);
        view.getImageLayer().setImage(imgElement, null);
    }

    /**
     * Get the average hue value of the image starting from its Hue channel histogram
     * 
     * @param hsvImg
     *            the current frame in HSV
     * @param hueValues
     *            the Hue component of the current frame
     * @return the average Hue value
     */
    private double getHistAverage(Mat hsvImg, Mat hueValues) {
        // init
        double average = 0.0;
        Mat hist_hue = new Mat();
        // 0-180: range of Hue values
        MatOfInt histSize = new MatOfInt(180);
        List<Mat> hue = new ArrayList<>();
        hue.add(hueValues);

        // compute the histogram
        Imgproc.calcHist(hue, new MatOfInt(0), new Mat(), hist_hue, histSize, new MatOfFloat(0, 179));

        // get the average Hue value of the image
        // (sum(bin(h)*h))/(image-height*image-width)
        // -----------------
        // equivalent to get the hue of each pixel in the image, add them, and
        // divide for the image size (height and width)
        for (int h = 0; h < 180; h++) {
            // for each bin, get its value and multiply it for the corresponding
            // hue
            average += (hist_hue.get(h, 0)[0] * h);
        }

        // return the average hue of the image
        return average = average / hsvImg.size().height / hsvImg.size().width;
    }

    /**
     * Apply Canny
     * 
     * @param frame
     *            the current frame
     * @return an image elaborated with Canny
     */
    private Mat doCanny(Mat frame) {
        // init
        Mat grayImage = new Mat();
        Mat detectedEdges = new Mat();

        // convert to grayscale
        Imgproc.cvtColor(frame, grayImage, Imgproc.COLOR_BGR2GRAY);

        // reduce noise with a 3x3 kernel
        Imgproc.blur(grayImage, detectedEdges, new Size(3, 3));

        // canny detector, with ratio of lower:upper threshold of 3:1
        double val = 0.5;
        Imgproc.Canny(detectedEdges, detectedEdges, val, val * 3);

        // using Canny's output as a mask, display the result
        Mat dest = new Mat();
        frame.copyTo(dest, detectedEdges);

        return dest;
    }

}
