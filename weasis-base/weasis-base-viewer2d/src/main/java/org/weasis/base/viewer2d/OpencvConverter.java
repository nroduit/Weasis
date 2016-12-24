package org.weasis.base.viewer2d;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.media.jai.PlanarImage;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;

public class OpencvConverter implements MediaReader {

    private ImageElement image = null;
    private Mat mat;

    public OpencvConverter(Mat mat) {
        if (mat == null) {
            throw new IllegalArgumentException("media uri is null"); //$NON-NLS-1$
        }
        this.mat = mat;
    }

    @Override
    public Object getTagValue(TagW tag) {
        MediaElement element = getSingleImage();
        if (tag != null && element != null) {
            return element.getTagValue(tag);
        }
        return null;
    }

    @Override
    public void replaceURI(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTag(TagW tag, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return false;
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub
    }

    @Override
    public URI getUri() {
        // TODO Auto-generated method stub
        return new File("unknown").toURI();
    }

    @Override
    public MediaElement[] getMediaElement() {
        MediaElement element = getSingleImage();
        if (element != null) {
            return new MediaElement[] { element };
        }
        return null;
    }
    
    @Override
    public MediaSeries<MediaElement> getMediaSeries() {
        String sUID = null;
        MediaElement element = getSingleImage();
        if (element != null) {
            sUID = (String) element.getTagValue(TagW.get("SeriesInstanceUID")); //$NON-NLS-1$
        }
        if (sUID == null) {
      //      sUID = uri == null ? "unknown" : uri.toString(); //$NON-NLS-1$
        }
        MediaSeries<MediaElement> series =
            new Series<MediaElement>(TagW.SubseriesInstanceUID, sUID, AbstractFileModel.series.getTagView()) { // $NON-NLS-1$

                @Override
                public String getMimeType() {
                    synchronized (this) {
                        for (MediaElement img : medias) {
                            return img.getMimeType();
                        }
                    }
                    return null;
                }

                @Override
                public void addMedia(MediaElement media) {
                    if (media instanceof ImageElement) {
                        this.add(media);
                        DataExplorerModel model = (DataExplorerModel) getTagValue(TagW.ExplorerModel);
                        if (model != null) {
                            model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.ADD, model, null,
                                new SeriesEvent(SeriesEvent.Action.ADD_IMAGE, this, media)));
                        }
                    }
                }
            };

        ImageElement img = getSingleImage();
        if (img != null) {
            series.add(getSingleImage());
            series.setTag(TagW.FileName, img.getName());
        }
        return series;
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MediaElement getPreview() {
        return getSingleImage();
    }

    

    @Override
    public PlanarImage getImageFragment(MediaElement media) throws Exception {
        if (media instanceof ImageElement) {
            return PlanarImage.wrapRenderedImage(matToBufferedImage(mat));
        }
        return null;
    }

    @Override
    public int getMediaElementNumber() {
        return 1;
    }

    private ImageElement getSingleImage() {
        if (image == null) {
            image = new ImageElement(this, 0);
        }
        return image;
    }

    @Override
    public Map<TagW, Object> getMediaFragmentTags(Object key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public Codec getCodec() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] getReaderDescription() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Converts/writes a Mat into a BufferedImage.
     * 
     * @param matrix
     *            Mat of type CV_8UC3 or CV_8UC1
     * @return BufferedImage of type TYPE_3BYTE_BGR or TYPE_BYTE_GRAY
     */
    public static BufferedImage matToBufferedImage(Mat matrix) {

        int cols = matrix.cols();
        int rows = matrix.rows();
        int elemSize = (int) matrix.elemSize();
        int depth = matrix.depth();
        int channels = matrix.channels();
        int bpp = (elemSize * 8) / channels;
        int type = bpp <= 8 ? DataBuffer.TYPE_BYTE : bpp <= 16 ? DataBuffer.TYPE_USHORT : DataBuffer.TYPE_INT;

        ColorSpace cs;
        WritableRaster raster;
        ComponentColorModel colorModel;

        switch (channels) {
            case 1:
                cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(cs, new int[] { bpp }, false, true, Transparency.OPAQUE, type);
                raster = colorModel.createCompatibleWritableRaster(cols, rows);
                break;
            case 3:
                cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel =
                    new ComponentColorModel(cs, new int[] { bpp, bpp, bpp }, false, false, Transparency.OPAQUE, type);
                raster = Raster.createInterleavedRaster(type, cols, rows, cols * channels, channels,
                    new int[] { 2, 1, 0 }, null);
                break;
            default:
                throw new UnsupportedOperationException(
                    "No implementation to handle " + matrix.channels() + " channels");
        }

        DataBuffer buf = raster.getDataBuffer();

        if (buf instanceof DataBufferByte) {
            matrix.get(0, 0, ((DataBufferByte) buf).getData());
        } else if (buf instanceof DataBufferUShort) {
            matrix.get(0, 0, ((DataBufferUShort) buf).getData());
        } else if (buf instanceof DataBufferShort) {
            matrix.get(0, 0, ((DataBufferShort) buf).getData());
        } else if (buf instanceof DataBufferInt) {
            matrix.get(0, 0, ((DataBufferInt) buf).getData());
        }
        return new BufferedImage(colorModel, raster, false, null);

    }

    public static Mat fromBufferedImage(RenderedImage img) {
        DataBuffer buf = img.getData().getDataBuffer();
        int[] samples = img.getSampleModel().getSampleSize();
        int[] offsets;
        if (img.getSampleModel() instanceof ComponentSampleModel) {
            offsets = ((ComponentSampleModel) img.getSampleModel()).getBandOffsets();
        } else {
            offsets = new int[samples.length];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = i;
            }
        }

        if (buf instanceof DataBufferByte) {
            Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC(samples.length));

            if (Arrays.equals(offsets, new int[] { 0, 1, 2 })) {
                byte[] pixels = ((DataBufferByte) buf).getData();
                byte b;
                for (int i = 0; i < pixels.length; i = i + 3) {
                    b = pixels[i];
                    pixels[i] = pixels[i + 2];
                    pixels[i + 2] = b;
                }
                // Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
            }
            mat.put(0, 0, ((DataBufferByte) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferUShort) {
            Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_16SC(samples.length));
            mat.put(0, 0, ((DataBufferUShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferShort) {
            Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_16SC(samples.length));
            mat.put(0, 0, ((DataBufferShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferInt) {
            Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_16SC(samples.length));
            mat.put(0, 0, ((DataBufferInt) buf).getData());
            return mat;
        }

        // rgb to bgr
        // byte b;
        // for (int i = 0; i < pixels.length; i = i + 3) {
        // b = pixels[i];
        // pixels[i] = pixels[i + 2];
        // pixels[i + 2] = b;
        // }

        return null;
    }

    public static BufferedImage toBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        byte[] bytes = new byte[mat.channels() * mat.cols() * mat.rows()];
        mat.get(0, 0, bytes);
        BufferedImage img = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(bytes, 0, pixels, 0, bytes.length);
        return img;
    }

    @Override
    public FileCache getFileCache() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public String getMediaFragmentMimeType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean buildFile(File ouptut) {
        // TODO Auto-generated method stub
        return false;
    }
}
