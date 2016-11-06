/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.weasis.acquire.explorer.core.bean.Global;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.xml.sax.SAXException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-13 - ylar - Creation
 */
public class AcquireManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcquireManager.class);
    private static final AcquireManager instance = new AcquireManager();

    public static final String[] functions = { "patient" }; //$NON-NLS-1$

    public static final Global GLOBAL = new Global();

    private final Map<URI, AcquireImageInfo> images = new HashMap<>();
    private AcquireImageInfo currentAcquireImageInfo = null;
    private ViewCanvas<ImageElement> currentView = null;

    private AcquireManager() {
    }

    public static AcquireManager getInstance() {
        return instance;
    }

    public static AcquireImageInfo getCurrentAcquireImageInfo() {
        return getInstance().currentAcquireImageInfo;
    }

    public static void setCurrentAcquireImageInfo(AcquireImageInfo imageInfo) {
        getInstance().currentAcquireImageInfo = imageInfo;
    }

    public static ViewCanvas<ImageElement> getCurrentView() {
        return getInstance().currentView;
    }

    public static void setCurrentView(ViewCanvas<ImageElement> view) {
        getInstance().currentView = view;
        // Remove capabilities to open a view by dragging a thumbnail from the
        // import panel.
        view.getJComponent().setTransferHandler(null);
    }

    public static Collection<AcquireImageInfo> getAllAcquireImageInfo() {
        return getInstance().images.values();
    }

    public static AcquireImageInfo findById(String uid) {
        return getInstance().images.get(uid);
    }

    public static AcquireImageInfo findByImage(ImageElement image) {
        return getInstance().getAcquireImageInfo(image);
    }

    public static List<AcquireImageInfo> findbySerie(Serie serie) {
        return getAcquireImageInfoList().stream().filter(i -> i.getSerie().equals(serie)).collect(Collectors.toList());
    }

    public static List<Serie> getBySeries() {
        return getInstance().images.entrySet().stream().map(e -> e.getValue().getSerie()).distinct().sorted()
            .collect(Collectors.toList());
    }

    public static Map<Serie, List<AcquireImageInfo>> groupBySeries() {
        return getAcquireImageInfoList().stream().collect(Collectors.groupingBy(AcquireImageInfo::getSerie));
    }

    public static Serie getSerie(Serie searched) {
        Optional<Serie> serie = getBySeries().stream().filter(s -> s.equals(searched)).findFirst();
        if (serie.isPresent()) {
            return serie.get();
        }
        return searched;
    }

    public static void remove(ImageElement element) {
        Optional.ofNullable(element).ifPresent(e -> getInstance().images.remove(e.getMediaURI()));
    }

    public static void importImages(Serie searched, List<ImageElement> selected, int maxRangeInMinutes) {
        Serie serie = null;

        if (!Serie.Type.DATE.equals(searched.getType())) {
            serie = AcquireManager.getSerie(searched);
        }

        for (ImageElement element : selected) {
            AcquireImageInfo info = AcquireManager.findByImage(element);
            if (info == null) {
                continue;
            }

            if (Serie.Type.DATE.equals(searched.getType())) {
                LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, info.getImage());
                Optional<Serie> ser =
                    getBySeries().stream().filter(s -> Serie.Type.DATE.equals(s.getType())).filter(s -> {
                        LocalDateTime start = s.getDate();
                        LocalDateTime end = date;
                        if (end.isBefore(start)) {
                            start = date;
                            end = s.getDate();
                        }
                        Duration duration = Duration.between(start, end);
                        return duration.toMinutes() < maxRangeInMinutes;
                    }).findFirst();

                serie = ser.isPresent() ? ser.get() : AcquireManager.getSerie(new Serie(date));
                info.setSerie(serie);
                if (ser.isPresent()) {
                    List<AcquireImageInfo> list = findbySerie(serie);
                    if (list.size() > 2) {
                        recalculateCentralTime(list);
                    }
                }
            } else {
                info.setSerie(serie);
            }
        }
    }

    private static void recalculateCentralTime(List<AcquireImageInfo> list) {
        List<AcquireImageInfo> sortedList = list.stream()
            .sorted(Comparator.comparing(i -> TagD.dateTime(Tag.ContentDate, Tag.ContentTime, i.getImage())))
            .collect(Collectors.toList());
        AcquireImageInfo info = sortedList.get(sortedList.size() / 2);
        info.getSerie().setDate(TagD.dateTime(Tag.ContentDate, Tag.ContentTime, info.getImage()));
    }

    public static List<ImageElement> toImageElement(List<? extends MediaElement> medias) {
        return medias.stream().filter(m -> m instanceof ImageElement).map(ImageElement.class::cast)
            .collect(Collectors.toList());
    }

    /**
     * Set a new Patient Context and in case current state job is not finished ask user if cleaning unpublished images
     * should be done or canceled.
     *
     * @param argv
     * @throws IOException
     */
    public void patient(String[] argv) throws IOException {
        final String[] usage = { "Load Patient Context", "Usage: acquire:patient [Options] SOURCE", //$NON-NLS-1$ //$NON-NLS-2$
            "  -x --xml       Open Patient Context from an XML data containing all DICOM Tags ", //$NON-NLS-1$
            "  -i --inbound       Open Patient Context from an XML data containing all DICOM Tags, supported syntax is [Base64/GZip]", //$NON-NLS-1$
            "  -u --url       Open Patient Context from an XML (URL) file containing all DICOM TAGs", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            patientCommand(opt, args.get(0));
        });
    }

    private void patientCommand(Option opt, String arg) {

        Document newPatientContext = null;

        if (opt.isSet("xml")) { //$NON-NLS-1$
            newPatientContext = getPatientContext(arg, OPT_NONE);
        } else if (opt.isSet("inbound")) { //$NON-NLS-1$
            newPatientContext = getPatientContext(arg, OPT_B64ZIP);
        } else if (opt.isSet("url")) { //$NON-NLS-1$
            newPatientContext = getPatientContextFromUrl(arg);
        }

        if (newPatientContext != null) {
            if (isPatientContextIdentical(newPatientContext)) {
                return;
            }

            if (!isAcquireImagesAllPublished()) {
                // TODO ask user to clean unpublished job or not

                // if cancel
                return;
            }

            AcquireManager.GLOBAL.init(newPatientContext);
        }
    }

    /**
     * Evaluates if patientContext currently loaded is identical to the one that's expected to be loaded
     *
     * @return
     */

    private boolean isPatientContextIdentical(Document newPatientContext) {
        return AcquireManager.GLOBAL.containSameTagsValues(newPatientContext);
    }

    /**
     * Evaluate if acquire images job has work in progress.
     *
     * @return
     */
    private boolean isAcquireImagesAllPublished() {

        // TODO evaluer l'état de publication
        return true;
    }

    /**
     */
    private static final short OPT_NONE = 0;
    private static final short OPT_B64 = 1;
    private static final short OPT_ZIP = 2;
    private static final short OPT_URLSAFE = 4;

    private static final short OPT_B64ZIP = 3;
    private static final short OPT_B64URLSAFE = 5;
    private static final short OPT_B64URLSAFEZIP = 7;

    /**
     *
     * @param inputString
     * @param codeOption
     * @return
     */
    private static Document getPatientContext(String inputString, short codeOption) {
        return getPatientContext(inputString.getBytes(StandardCharsets.UTF_8), codeOption);
    }

    /**
     *
     * @param byteArray
     * @param codeOption
     * @return
     */
    private static Document getPatientContext(byte[] byteArray, short codeOption) {
        if (byteArray == null || byteArray.length == 0) {
            throw new IllegalArgumentException("empty byteArray parameter"); //$NON-NLS-1$
        }

        if (codeOption != OPT_NONE) {
            try {
                if ((codeOption & OPT_B64) == OPT_B64) {
                    if ((codeOption & OPT_URLSAFE) == OPT_URLSAFE) {
                        byteArray = Base64.getUrlDecoder().decode(byteArray);
                    } else {
                        byteArray = Base64.getDecoder().decode(byteArray);
                    }
                }

                if ((codeOption & OPT_ZIP) == OPT_ZIP) {
                    byteArray = GzipManager.gzipUncompressToByte(byteArray);
                }
            } catch (Exception e) {
                LOGGER.error("Decode Patient Context", e); //$NON-NLS-1$
                return null;
            }
        }

        Document patientContext = null;

        try (InputStream inputStream = new ByteArrayInputStream(byteArray)) {
            LOGGER.debug("Source XML :\n{}", new String(byteArray)); //$NON-NLS-1$

            patientContext = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            LOGGER.error("Parsing Patient Context XML", e); //$NON-NLS-1$
        }

        return patientContext;
    }

    /**
     *
     * @param uri
     * @return
     */
    private static Document getPatientContextFromUri(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("empty URI parameter"); //$NON-NLS-1$
        }

        // TODO could be more secure to limit the loading buffer size !!!
        byte[] byteArray = getURIContent(uri);
        String uriPath = uri.getPath();

        if (uriPath.endsWith(".gz") || (uriPath.endsWith(".xml") == false //$NON-NLS-1$ //$NON-NLS-2$
            && MimeInspector.isMatchingMimeTypeFromMagicNumber(byteArray, "application/x-gzip"))) { //$NON-NLS-1$
            return getPatientContext(byteArray, OPT_ZIP);
        } else {
            return getPatientContext(byteArray, OPT_NONE);
        }
    }

    /**
     *
     * @param url
     * @return
     */
    private static Document getPatientContextFromUrl(String url) {
        return getPatientContextFromUri(getURIFromURL(url));
    }

    /**
     *
     * @param urlStr
     * @return
     */
    private static URI getURIFromURL(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) {
            throw new IllegalArgumentException("empty urlString parameter"); //$NON-NLS-1$
        }

        URI uri = null;

        if (urlStr.startsWith("http") == false) { //$NON-NLS-1$
            try {
                File file = new File(urlStr);
                if (file.canRead()) {
                    uri = file.toURI();
                }
            } catch (Exception e) {
                LOGGER.error("{} is supposed to be a file URL but cannot be converted to a valid URI", urlStr, e); //$NON-NLS-1$
            }
        }
        if (uri == null) {
            try {
                uri = new URL(urlStr).toURI();
            } catch (MalformedURLException | URISyntaxException e) {
                LOGGER.error("getURIFromURL : {}", urlStr, e); //$NON-NLS-1$
            }
        }

        return uri;
    }

    /**
     *
     * @param uri
     * @return
     */
    private static byte[] getURIContent(URI uri) {

        if (uri == null) {
            throw new IllegalArgumentException("empty URI parameter"); //$NON-NLS-1$
        }

        byte[] byteArray = null;

        URLConnection urlConnection = null;
        try {
            URL url = uri.toURL();
            urlConnection = url.openConnection();

            LOGGER.debug("download from URL: {}", url); //$NON-NLS-1$
            logHttpError(urlConnection);

            // TODO urlConnection.setRequestProperty with session TAG ??
            // @see >>
            // org.weasis.dicom.explorer.wado.downloadmanager.buildDicomSeriesFromXml

            // note: fastest way to convert inputStream to string according to :
            // http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
            try (InputStream inputStream = urlConnection.getInputStream()) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }

                byteArray = outputStream.toByteArray();
            }

        } catch (Exception e) {
            LOGGER.error("getURIContent from : {}", uri.getPath(), e); //$NON-NLS-1$
        }

        return byteArray;
    }

    /**
     * @param urlConnection
     */
    private static void logHttpError(URLConnection urlConnection) {
        // TODO same method as in dicom.explorer.wado.downloadmanager => move
        // this in a common place

        if (urlConnection instanceof HttpURLConnection) {
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            httpURLConnection.setConnectTimeout(5000);

            try {
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    // Following is only intended LOG more info about Http
                    // Server Error

                    InputStream errorStream = httpURLConnection.getErrorStream();
                    if (errorStream != null) {
                        try (InputStreamReader inputStream = new InputStreamReader(errorStream, "UTF-8"); //$NON-NLS-1$
                                        BufferedReader reader = new BufferedReader(inputStream)) {
                            StringBuilder stringBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                stringBuilder.append(line);
                            }
                            String errorDescription = stringBuilder.toString();
                            if (StringUtil.hasText(errorDescription)) {
                                LOGGER.warn("HttpURLConnection - HTTP Status {} - {}", responseCode + " ["  //$NON-NLS-1$//$NON-NLS-2$
                                    + httpURLConnection.getResponseMessage() + "]", errorDescription); //$NON-NLS-1$
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("lOG http response message", e); //$NON-NLS-1$
            }
        }
    }

    /*
     * ===================================== PRIVATE METHODS =====================================
     */

    private static List<AcquireImageInfo> getAcquireImageInfoList() {
        return getInstance().images.entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());

    }

    private AcquireImageInfo getAcquireImageInfo(ImageElement image) {
        if (image == null) {
            return null;
        }
        TagW tagUid = TagD.getUID(Level.INSTANCE);
        String uuid = (String) image.getTagValue(tagUid);
        if (uuid == null) {
            uuid = UIDUtils.createUID();
            image.setTag(tagUid, uuid);
        }

        AcquireImageInfo info = images.get(image.getMediaURI());
        if (info == null) {
            readTags(image);
            info = new AcquireImageInfo(image);
            images.put(image.getMediaURI(), info);
        }
        return info;
    }

    private static void readTags(ImageElement element) {
        Optional<File> file = element.getFileCache().getOriginalFile();
        if (file.isPresent()) {
            Date date = null;
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(file.get());
                if (metadata != null) {
                    ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                    if (directory != null) {
                        date = directory.getDate(ExifDirectoryBase.TAG_DATETIME_ORIGINAL);
                        if (date == null) {
                            date = directory.getDate(ExifDirectoryBase.TAG_DATETIME);
                        }

                        element.setTagNoNull(TagD.get(Tag.DateOfSecondaryCapture),
                            directory.getDate(ExifDirectoryBase.TAG_DATETIME_DIGITIZED));

                    }
                    ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
                    if (ifd0 != null) {
                        element.setTagNoNull(TagD.get(Tag.Manufacturer), ifd0.getString(ExifDirectoryBase.TAG_MAKE));
                        element.setTagNoNull(TagD.get(Tag.ManufacturerModelName),
                            ifd0.getString(ExifDirectoryBase.TAG_MODEL));

                        // try {
                        // int orientation =
                        // ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                        // } catch (MetadataException e) {
                        // e.printStackTrace();
                        // }

                        // AffineTransform affineTransform = new
                        // AffineTransform();
                        //
                        // switch (orientation) {
                        // case 1:
                        // break;
                        // case 2: // Flip X
                        // affineTransform.scale(-1.0, 1.0);
                        // affineTransform.translate(-width, 0);
                        // break;
                        // case 3: // PI rotation
                        // affineTransform.translate(width, height);
                        // affineTransform.rotate(Math.PI);
                        // break;
                        // case 4: // Flip Y
                        // affineTransform.scale(1.0, -1.0);
                        // affineTransform.translate(0, -height);
                        // break;
                        // case 5: // - PI/2 and Flip X
                        // affineTransform.rotate(-Math.PI / 2);
                        // affineTransform.scale(-1.0, 1.0);
                        // break;
                        // case 6: // -PI/2 and -width
                        // affineTransform.translate(height, 0);
                        // affineTransform.rotate(Math.PI / 2);
                        // break;
                        // case 7: // PI/2 and Flip
                        // affineTransform.scale(-1.0, 1.0);
                        // affineTransform.translate(-height, 0);
                        // affineTransform.translate(0, width);
                        // affineTransform.rotate(3 * Math.PI / 2);
                        // break;
                        // case 8: // PI / 2
                        // affineTransform.translate(0, width);
                        // affineTransform.rotate(3 * Math.PI / 2);
                        // break;
                        // default:
                        // break;
                        // }
                        //
                        // AffineTransformOp affineTransformOp = new
                        // AffineTransformOp(affineTransform,
                        // AffineTransformOp.TYPE_BILINEAR);
                        // BufferedImage destinationImage = new
                        // BufferedImage(originalImage.getHeight(),
                        // originalImage.getWidth(), originalImage.getType());
                        // destinationImage =
                        // affineTransformOp.filter(originalImage,
                        // destinationImage);
                    }
                }
            } catch (ImageProcessingException | IOException e) {
                LOGGER.error("Error when reading exif tags", e); //$NON-NLS-1$
            }
            LocalDateTime dateTime = date == null
                ? LocalDateTime.from(Instant.ofEpochMilli(element.getLastModified()).atZone(ZoneId.systemDefault()))
                : TagUtil.toLocalDateTime(date);
            element.setTagNoNull(TagD.get(Tag.ContentDate), dateTime.toLocalDate());
            element.setTagNoNull(TagD.get(Tag.ContentTime), dateTime.toLocalTime());
        }
    }

}
