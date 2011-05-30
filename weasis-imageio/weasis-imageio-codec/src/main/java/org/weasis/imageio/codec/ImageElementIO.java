/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.imageio.codec;

import java.awt.RenderingHints;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;

public class ImageElementIO implements MediaReader<PlanarImage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageElementIO.class);

    protected URI uri;

    protected final String mimeType;

    private ImageElement image = null;

    private final Codec codec;

    public ImageElementIO(URI media, String mimeType, Codec codec) {
        if (media == null)
            throw new IllegalArgumentException("media uri is null"); //$NON-NLS-1$
        this.uri = media;
        this.mimeType = mimeType == null ? MimeInspector.UNKNOWN_MIME_TYPE : mimeType;
        this.codec = codec;
    }

    @Override
    public PlanarImage getMediaFragment(MediaElement<PlanarImage> media) throws Exception {
        if (media != null && media.getFile() != null) {
            ImageReader reader = getDefaultReader(mimeType);
            if (reader == null) {
                LOGGER.info("Cannot find a reader for the mime type: {}", mimeType); //$NON-NLS-1$
                return null;
            }
            PlanarImage img;
            RenderingHints hints = LayoutUtil.createTiledLayoutHints();
            if ("it.geosolutions.imageio.plugins.jp2k.JP2KKakaduImageReader".equals(reader.getClass().getName())) { //$NON-NLS-1$
                ParameterBlockJAI pb = new ParameterBlockJAI("ImageReadMT"); //$NON-NLS-1$
                pb.setParameter("Input", media.getFile()); //$NON-NLS-1$
                pb.setParameter("ImageChoice", 0); //$NON-NLS-1$
                pb.setParameter("ReadMetadata", true); //$NON-NLS-1$
                pb.setParameter("ReadThumbnails", false); //$NON-NLS-1$
                pb.setParameter("VerifyInput", true); //$NON-NLS-1$
                pb.setParameter("Listeners", null); // java.util.EventListener[] //$NON-NLS-1$
                pb.setParameter("Locale", null); // java.util.Locale //$NON-NLS-1$
                pb.setParameter("ReadParam", reader.getDefaultReadParam()); // javax.imageio.ImageReadParam //$NON-NLS-1$
                pb.setParameter("Reader", reader); // javax.imageio.ImageReader //$NON-NLS-1$
                img = JAI.create("ImageReadMT", pb, hints); //$NON-NLS-1$
            } else {
                ImageInputStream in = new FileImageInputStream(new RandomAccessFile(media.getFile(), "r")); //$NON-NLS-1$
                // hints.add(new RenderingHints(JAI.KEY_TILE_CACHE, null));
                ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
                pb.setParameter("Input", in); //$NON-NLS-1$
                pb.setParameter("Reader", reader); //$NON-NLS-1$
                img = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
            }

            // to avoid problem with alpha channel and png encoded in 24 and 32 bits
            img = ImageFiler.getReadableImage(img);
            return img;
        }
        return null;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void reset() {

    }

    @Override
    public MediaElement<PlanarImage> getPreview() {
        return getSingleImage();
    }

    @Override
    public boolean delegate(DataExplorerModel explorerModel) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public MediaElement[] getMediaElement() {
        MediaElement element = getSingleImage();
        if (element != null)
            return new MediaElement[] { element };
        return null;
    }

    @Override
    public MediaSeries<ImageElement> getMediaSeries() {
        MediaSeries<ImageElement> series = new Series<ImageElement>(TagW.CurrentFolder, this.toString(), null) {

            @Override
            public String getMimeType() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void addMedia(MediaElement media) {
                // TODO Auto-generated method stub

            }
        };
        series.add(getSingleImage());
        return series;
    }

    @Override
    public int getMediaElementNumber() {
        // TODO Auto-generated method stub
        return 0;
    }

    private ImageElement getSingleImage() {
        if (image == null) {
            image = new ImageElement(this, 0);
        }
        return image;
    }

    @Override
    public String getMediaFragmentMimeType(Object key) {
        return mimeType;
    }

    @Override
    public HashMap<TagW, Object> getMediaFragmentTags(Object key) {
        return new HashMap<TagW, Object>();
    }

    @Override
    public URI getMediaFragmentURI(Object key) {
        return uri;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public Codec getCodec() {
        return codec;
    }

    @Override
    public String[] getReaderDescription() {
        return new String[] { "Image Codec: " + codec.getCodecName() }; //$NON-NLS-1$
    }

    public ImageReader getDefaultReader(String mimeType) {
        if (mimeType != null) {
            Iterator readers = ImageIO.getImageReadersByMIMEType(mimeType);
            if (readers.hasNext())
                return (ImageReader) readers.next();
        }
        return null;
    }

    @Override
    public Object getTagValue(TagW tag) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void replaceURI(URI uri) {
        // TODO Auto-generated method stub

    }
}
