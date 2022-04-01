/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Implementation;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.ImageRendering;
import org.dcm4che3.img.Transcoder;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.io.DicomOutputStream;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SimpleTaggable;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.opencv.data.PlanarImage;

public class DicomMediaIO implements DcmMediaReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomMediaIO.class);

  public static final File DICOM_EXPORT_DIR =
      AppProperties.buildAccessibleTempDirectory("dicom"); // NON-NLS
  public static final File CACHE_UNCOMPRESSED_DIR =
      AppProperties.buildAccessibleTempDirectory(
          AppProperties.FILE_CACHE_DIR.getName(), "dcm-rawcv"); // NON-NLS

  public static final String DICOM_MIMETYPE = "application/dicom"; // NON-NLS
  public static final String IMAGE_MIMETYPE = "image/dicom"; // NON-NLS
  public static final String SERIES_VIDEO_MIMETYPE = "video/dicom"; // NON-NLS
  public static final String SERIES_MIMETYPE = "series/dicom"; // NON-NLS
  public static final String SERIES_PR_MIMETYPE = "pr/dicom"; // NON-NLS
  public static final String SERIES_KO_MIMETYPE = "ko/dicom"; // NON-NLS

  public static final String SERIES_ENCAP_DOC_MIMETYPE = "encap/dicom"; // NON-NLS
  public static final String UNREADABLE = "unreadable/dicom"; // NON-NLS
  public static final String SERIES_XDSI = "xds-i/dicom"; // NON-NLS

  private static final AtomicInteger instanceID = new AtomicInteger(1);
  public static final TagManager tagManager = new TagManager();

  static {
    // PatientPseudoUID is the unique identifying tag for this patient group
    // -------- Mandatory Tags --------
    tagManager.addTag(Tag.PatientID, Level.PATIENT);
    tagManager.addTag(Tag.PatientName, Level.PATIENT);
    // -------- End of Mandatory Tags --------
    tagManager.addTag(Tag.PatientBirthDate, Level.PATIENT);
    tagManager.addTag(Tag.PatientBirthTime, Level.PATIENT);
    tagManager.addTag(
        Tag.PatientAge, Level.SERIES); // needs to be updated for each series if computed
    tagManager.addTag(Tag.PatientSex, Level.PATIENT);
    tagManager.addTag(Tag.IssuerOfPatientID, Level.PATIENT);
    tagManager.addTag(Tag.PatientWeight, Level.PATIENT);
    tagManager.addTag(Tag.PatientComments, Level.PATIENT);

    // StudyInstanceUID is the unique identifying tag for this study group
    tagManager.addTag(Tag.StudyID, Level.STUDY);
    tagManager.addTag(Tag.StudyDate, Level.STUDY);
    tagManager.addTag(Tag.StudyTime, Level.STUDY);
    tagManager.addTag(Tag.StudyDescription, Level.STUDY);
    tagManager.addTag(Tag.StudyComments, Level.STUDY);
    tagManager.addTag(Tag.AccessionNumber, Level.STUDY);
    tagManager.addTag(Tag.ModalitiesInStudy, Level.STUDY); // not required
    tagManager.addTag(Tag.NumberOfStudyRelatedInstances, Level.STUDY); // not required
    tagManager.addTag(Tag.NumberOfStudyRelatedSeries, Level.STUDY); // not required

    // SubseriesInstanceUID is the unique identifying tag for this series group
    // -------- Mandatory Tags --------
    tagManager.addTag(Tag.SeriesInstanceUID, Level.SERIES);
    tagManager.addTag(Tag.Modality, Level.SERIES);
    // -------- End of Mandatory Tags --------
    tagManager.addTag(Tag.SeriesDescription, Level.SERIES);
    tagManager.addTag(Tag.SOPClassUID, Level.SERIES);
    tagManager.addTag(Tag.RetrieveAETitle, Level.SERIES); // not required
    tagManager.addTag(Tag.ReferringPhysicianName, Level.SERIES);
    tagManager.addTag(Tag.InstitutionName, Level.SERIES);
    tagManager.addTag(Tag.InstitutionalDepartmentName, Level.SERIES);
    tagManager.addTag(Tag.StationName, Level.SERIES);
    tagManager.addTag(Tag.Manufacturer, Level.SERIES);
    tagManager.addTag(Tag.ManufacturerModelName, Level.SERIES);
    tagManager.addTag(Tag.SeriesNumber, Level.SERIES);
    tagManager.addTag(Tag.NumberOfFrames, Level.SERIES);
    tagManager.addTag(Tag.SeriesDate, Level.SERIES);
    tagManager.addTag(Tag.SeriesTime, Level.SERIES);
    tagManager.addTag(Tag.PerformedProcedureStepStartDate, Level.SERIES); // not
    // required
    tagManager.addTag(Tag.PerformedProcedureStepStartTime, Level.SERIES); // not
    // required
    // Should be in image C.7.6.5 Cine Module
    // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.5.html
    tagManager.addTag(Tag.PreferredPlaybackSequencing, Level.SERIES);
    tagManager.addTag(Tag.CineRate, Level.SERIES);
    tagManager.addTag(Tag.RecommendedDisplayFrameRate, Level.SERIES);
    tagManager.addTag(Tag.KVP, Level.SERIES);
    tagManager.addTag(Tag.BodyPartExamined, Level.SERIES);
    tagManager.addTag(Tag.FrameOfReferenceUID, Level.SERIES);
    tagManager.addTag(Tag.NumberOfSeriesRelatedInstances, Level.SERIES);
    tagManager.addTag(Tag.Laterality, Level.SERIES);

    // SOPInstanceUID is the unique identifying tag of a DICOM object
    // -------- Mandatory Tags --------
    // Tags for identifying group (Patient, Study, Series)
    tagManager.addTag(Tag.PatientID, Level.INSTANCE);
    tagManager.addTag(Tag.PatientName, Level.INSTANCE);
    tagManager.addTag(Tag.PatientBirthDate, Level.INSTANCE);
    tagManager.addTag(Tag.IssuerOfPatientID, Level.INSTANCE);
    tagManager.addTag(Tag.StudyInstanceUID, Level.INSTANCE);
    tagManager.addTag(Tag.SeriesInstanceUID, Level.INSTANCE);
    tagManager.addTag(Tag.Modality, Level.INSTANCE);
    // -------- End of Mandatory Tags --------

    tagManager.addTag(Tag.GantryDetectorTilt, Level.INSTANCE);
    tagManager.addTag(Tag.PatientOrientation, Level.INSTANCE);
    tagManager.addTag(Tag.SliceLocation, Level.INSTANCE);
    tagManager.addTag(Tag.SliceThickness, Level.INSTANCE);
    tagManager.addTag(Tag.AcquisitionDate, Level.INSTANCE);
    tagManager.addTag(Tag.AcquisitionTime, Level.INSTANCE);
    tagManager.addTag(Tag.ContentDate, Level.INSTANCE);
    tagManager.addTag(Tag.ContentTime, Level.INSTANCE);
    tagManager.addTag(Tag.DiffusionBValue, Level.INSTANCE);
    tagManager.addTag(Tag.MIMETypeOfEncapsulatedDocument, Level.INSTANCE);
    tagManager.addTag(Tag.PixelDataProviderURL, Level.INSTANCE);

    for (Entry<Modality, ModalityInfoData> entry : ModalityView.getModalityViewEntries()) {
      readTagsInModalityView(entry.getValue().getCornerInfo(CornerDisplay.TOP_LEFT).getInfos());
      readTagsInModalityView(entry.getValue().getCornerInfo(CornerDisplay.TOP_RIGHT).getInfos());
      readTagsInModalityView(entry.getValue().getCornerInfo(CornerDisplay.BOTTOM_RIGHT).getInfos());
    }

    // TODO init with a profile
    DicomMediaUtils.enableAnonymizationProfile(true);
  }

  public static final Map<String, DicomSpecialElementFactory> DCM_ELEMENT_FACTORIES =
      new HashMap<>();

  static {
    /*
     * DICOM PR and KO are not displayed with a special viewer but are transversally managed objects. So they are
     * not registered from a viewer.
     */
    DCM_ELEMENT_FACTORIES.put(
        "PR",
        new DicomSpecialElementFactory() {

          @Override
          public String getSeriesMimeType() {
            return SERIES_PR_MIMETYPE;
          }

          @Override
          public String[] getModalities() {
            return new String[] {"PR"};
          }

          @Override
          public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
            return new PRSpecialElement(mediaIO);
          }
        });
    DCM_ELEMENT_FACTORIES.put(
        "KO",
        new DicomSpecialElementFactory() {

          @Override
          public String getSeriesMimeType() {
            return SERIES_KO_MIMETYPE;
          }

          @Override
          public String[] getModalities() {
            return new String[] {"KO"};
          }

          @Override
          public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
            if (RejectedKOSpecialElement.isRejectionKOS(mediaIO)) {
              return new RejectedKOSpecialElement(mediaIO);
            }
            return new KOSpecialElement(mediaIO);
          }
        });
  }

  private static final SoftHashMap<DicomMediaIO, DicomMetaData> HEADER_CACHE =
      new SoftHashMap<>() {

        @Override
        public void removeElement(Reference<? extends DicomMetaData> soft) {
          DicomMediaIO key = reverseLookup.remove(soft);
          if (key != null) {
            hash.remove(key);
          }
        }
      };

  // The above softReference HEADER_CACHE shall be used instead of the following dcmMetadata
  // variable to get access to
  // the current DicomObject unless it's virtual and then URI doesn't exit. This case appends when
  // the dcmMetadata is
  // created within the application and is given to the ImageReader constructor
  private DicomMetaData dcmMetadata = null;

  private URI uri;
  private int numberOfFrame;
  private final Map<TagW, Object> tags;
  private MediaElement[] image = null;
  private String mimeType;
  private boolean hasPixel = false;

  private final FileCache fileCache;

  public DicomMediaIO(URI uri) {
    this.uri = Objects.requireNonNull(uri);
    this.numberOfFrame = 0;
    this.tags = new HashMap<>();
    this.mimeType = DICOM_MIMETYPE;
    this.fileCache = new FileCache(this);
  }

  public DicomMediaIO(File source) {
    this(Objects.requireNonNull(source).toURI());
  }

  public DicomMediaIO(Path path) {
    this(Objects.requireNonNull(path).toUri());
  }

  public DicomMediaIO(Attributes dcmItems) throws URISyntaxException {
    this(
        new URI(
            "data:" + Objects.requireNonNull(dcmItems).getString(Tag.SOPInstanceUID))); // NON-NLS
    this.dcmMetadata = new DicomMetaData(dcmItems, UID.ExplicitVRLittleEndian);
  }

  private static void readTagsInModalityView(TagView[] views) {
    for (TagView tagView : views) {
      if (tagView != null) {
        for (TagW tag : tagView.getTag()) {
          if (tag != null
              && !DicomMediaIO.tagManager.contains(tag, Level.PATIENT)
              && !DicomMediaIO.tagManager.contains(tag, Level.STUDY)
              && !DicomMediaIO.tagManager.contains(tag, Level.SERIES)) {
            DicomMediaIO.tagManager.addTag(tag, Level.INSTANCE);
          }
        }
      }
    }
  }

  @Override
  public synchronized void replaceURI(URI uri) {
    if (!Objects.equals(this.uri, Objects.requireNonNull(uri))) {
      this.uri = uri;
    }
  }

  /** @return true when the DICOM Object has no source file (only in memory) */
  public boolean isEditableDicom() {
    return dcmMetadata != null && "data".equals(uri.getScheme()); // NON-NLS
  }

  public synchronized boolean isReadableDicom() {
    if (UNREADABLE.equals(mimeType)) {
      // Return true only to display the error message in the view
      return true;
    }
    if ("data".equals(uri.getScheme()) && dcmMetadata == null) { // NON-NLS
      return false;
    }

    if (tags.size() == 0) {
      try {
        DicomMetaData md = readMetaData();
        Attributes fmi = md.getFileMetaInformation();
        Attributes header = md.getDicomObject();
        // Exclude DICOMDIR
        String mediaStorageSOPClassUID =
            fmi == null ? null : fmi.getString(Tag.MediaStorageSOPClassUID);
        if ("1.2.840.10008.1.3.10".equals(mediaStorageSOPClassUID)) {
          mimeType = UNREADABLE;
          close();
          return false;
        }
        if (hasPixel) {
          String ts = fmi == null ? null : fmi.getString(Tag.TransferSyntaxUID);
          if (ts != null && ts.startsWith("1.2.840.10008.1.2.4.10")) {
            // MPEG2 MP@ML 1.2.840.10008.1.2.4.100
            // MEPG2 MP@HL 1.2.840.10008.1.2.4.101
            // MPEG4 AVC/H.264 1.2.840.10008.1.2.4.102
            // MPEG4 AVC/H.264 BD 1.2.840.10008.1.2.4.103
            mimeType = SERIES_VIDEO_MIMETYPE;
          } else {
            mimeType = IMAGE_MIMETYPE;
          }
        } else {
          boolean special = setDicomSpecialType(header);
          if (!special) {
            // Not supported DICOM file
            mimeType = UNREADABLE;
            close();
            return false;
          }
        }

        writeInstanceTags(md);

      } catch (Exception | OutOfMemoryError e) {
        mimeType = UNREADABLE;
        LOGGER.error("Cannot read DICOM:", e);
        close();
        return false;
      }
    }
    return true;
  }

  private boolean setDicomSpecialType(Attributes header) {
    String modality = header.getString(Tag.Modality);
    if (modality != null) {
      String encap = header.getString(Tag.MIMETypeOfEncapsulatedDocument);
      DicomSpecialElementFactory factory = DCM_ELEMENT_FACTORIES.get(modality);
      if (factory != null) {
        mimeType = factory.getSeriesMimeType();
        // Can be not null for instance by ECG with encapsulated pdf
        if (encap == null) {
          return true;
        }
      }
      if (encap != null) {
        mimeType = SERIES_ENCAP_DOC_MIMETYPE;
        return true;
      }
    }
    return false;
  }

  public String getMimeType() {
    return mimeType;
  }

  @Override
  public Object getTagValue(TagW tag) {
    return tag == null ? null : tags.get(tag);
  }

  @Override
  public void setTag(TagW tag, Object value) {
    DicomMediaUtils.setTag(tags, tag, value);
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    if (value != null) {
      setTag(tag, value);
    }
  }

  @Override
  public boolean containTagKey(TagW tag) {
    return tags.containsKey(tag);
  }

  @Override
  public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
    return tags.entrySet().iterator();
  }

  private void writeInstanceTags(DicomMetaData md) {
    if (tags.size() > 0 || md == null || md.getDicomObject() == null) {
      return;
    }
    Attributes fmi = md.getFileMetaInformation();
    Attributes header = md.getDicomObject();

    tagManager.readTags(Level.INSTANCE, header, this);

    // -------- Mandatory Tags --------
    // Tags for identifying group (Patient, Study, Series)
    // Global Identifier for the patient.
    PatientComparator patientComparator = new PatientComparator(this);
    setTag(TagW.PatientPseudoUID, patientComparator.buildPatientPseudoUID());

    Integer instNb =
        DicomMediaUtils.getIntegerFromDicomElement(
            header, Tag.InstanceNumber, instanceID.incrementAndGet());
    setTag(TagD.get(Tag.InstanceNumber), instNb);
    setTag(
        TagD.get(Tag.SOPInstanceUID), header.getString(Tag.SOPInstanceUID, String.valueOf(instNb)));
    if (fmi != null) {
      setTagNoNull(TagD.get(Tag.TransferSyntaxUID), fmi.getString(Tag.TransferSyntaxUID));
    }
    // -------- End of Mandatory Tags --------

    writeImageValues(md);
    writeSharedFunctionalGroupsSequence(header);
    DicomMediaUtils.writePerFrameFunctionalGroupsSequence(this, header, 0);

    boolean pr = SERIES_PR_MIMETYPE.equals(mimeType);
    boolean ko = SERIES_KO_MIMETYPE.equals(mimeType);
    if (pr) {
      PrDicomObject prDcm = new PrDicomObject(header, md.getImageDescriptor());
      setTag(TagW.PrDicomObject, prDcm);
    }
    if (pr || ko) {
      // Set other required fields
      TagW[] tagIDs =
          TagD.getTagFromIDs(
              Tag.SeriesDescription, Tag.SeriesDate, Tag.SeriesTime, Tag.SeriesNumber);
      for (TagW tag : tagIDs) {
        tag.readValue(header, this);
      }
    }

    DicomMediaUtils.computeSlicePositionVector(this);
    DicomMediaUtils.setShutter(this, header);
    DicomMediaUtils.computeSUVFactor(header, this, 0);
  }

  private void writeSharedFunctionalGroupsSequence(Attributes header) {
    if (header != null) {
      DicomMediaUtils.writeFunctionalGroupsSequence(
          this, header.getNestedDataset(Tag.SharedFunctionalGroupsSequence));
    }
  }

  private void writeImageValues(DicomMetaData md) {
    if (md != null && md.getDicomObject() != null && hasPixel) {
      Attributes header = md.getDicomObject();
      ImageDescriptor desc = md.getImageDescriptor();
      TagD.get(Tag.ImagePositionPatient).readValue(header, this);
      TagD.get(Tag.ImageOrientationPatient).readValue(header, this);
      setTagNoNull(
          TagW.ImageOrientationPlane,
          ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(
              TagD.getTagValue(this, Tag.ImageOrientationPatient, double[].class)));

      int bitsAllocated = desc.getBitsAllocated();
      int bitsStored = desc.getBitsStored();

      int pixelRepresentation = desc.getPixelRepresentation();
      setTagNoNull(TagD.get(Tag.BitsAllocated), bitsAllocated);
      setTagNoNull(TagD.get(Tag.BitsStored), bitsStored);
      setTagNoNull(TagD.get(Tag.PixelRepresentation), pixelRepresentation);

      TagD.get(Tag.PixelSpacing).readValue(header, this);
      TagD.get(Tag.PixelAspectRatio).readValue(header, this);
      TagD.get(Tag.PixelSpacingCalibrationDescription).readValue(header, this);
      TagD.get(Tag.ImagerPixelSpacing).readValue(header, this);
      TagD.get(Tag.NominalScannedPixelSpacing).readValue(header, this);

      setTag(TagW.ModalityLUTData, desc.getModalityLUT());

      TagD.get(Tag.PixelIntensityRelationship).readValue(header, this);
      setTag(TagW.VOILUTsData, desc.getVoiLUT());

      TagD.get(Tag.Units).readValue(header, this);
      TagD.get(Tag.NumberOfFrames).readValue(header, this);

      int samplesPerPixel =
          DicomMediaUtils.getIntegerFromDicomElement(header, Tag.SamplesPerPixel, 1);
      setTag(TagD.get(Tag.SamplesPerPixel), samplesPerPixel);
      String photometricInterpretation =
          header.getString(Tag.PhotometricInterpretation, "MONOCHROME2");
      TagD.get(Tag.PresentationLUTShape).readValue(header, this);
      setTag(TagD.get(Tag.PhotometricInterpretation), photometricInterpretation);
      setTag(
          TagW.MonoChrome,
          samplesPerPixel == 1
              && !"PALETTE COLOR".equalsIgnoreCase(photometricInterpretation)); // NON-NLS

      setTag(TagD.get(Tag.Rows), desc.getRows());
      setTag(TagD.get(Tag.Columns), desc.getColumns());

      setTagNoNull(
          TagD.get(Tag.PixelPaddingValue),
          DicomMediaUtils.getIntPixelValue(
              header, Tag.PixelPaddingValue, pixelRepresentation != 0, bitsStored));
      setTagNoNull(
          TagD.get(Tag.PixelPaddingRangeLimit),
          DicomMediaUtils.getIntPixelValue(
              header, Tag.PixelPaddingRangeLimit, pixelRepresentation != 0, bitsStored));

      /*
       * * @see <a href=
       * "http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.html#sect_C.7.6.1.1.5" >C
       * .7.6.1.1.5 Lossy Image Compression</a>
       */
      setTagNoNull(
          TagD.get(Tag.LossyImageCompression),
          header.getString(
              Tag.LossyImageCompression, header.getString(Tag.LossyImageCompressionRetired)));
      TagD.get(Tag.LossyImageCompressionRatio).readValue(header, this);
      TagD.get(Tag.LossyImageCompressionMethod).readValue(header, this);
      TagD.get(Tag.DerivationDescription).readValue(header, this);

      setTagNoNull(TagW.ImageDescriptor, desc.getEmbeddedOverlay());
    }
  }

  public boolean containTag(int id) {
    for (TagW tagW : tags.keySet()) {
      if (tagW.getId() == id) {
        return true;
      }
    }
    return false;
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean buildFile(File output) {
    // When object is in memory, write it
    if (isEditableDicom()) {
      Attributes dcm = getDicomObject();
      if (dcm != null) {
        try (DicomOutputStream out = new DicomOutputStream(output)) {
          out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
          return true;
        } catch (IOException e) {
          LOGGER.error("Cannot write dicom file", e);
        }
      }
    }
    return false;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    if (Objects.requireNonNull(media).getKey() instanceof Integer) {
      return getImageFragment(media, (Integer) media.getKey(), true);
    }
    return null;
  }

  public PlanarImage getImageFragment(MediaElement media, int frame, boolean noEmbeddedOverlay)
      throws Exception {
    if (isReadableDicom() && frame >= 0 && frame < numberOfFrame && hasPixel) {
      FileCache cache = media.getFileCache();
      Optional<File> original = cache.getOriginalFile();
      if (original.isPresent()) {
        LOGGER.debug(
            "Start reading dicom image frame: {} sopUID: {}",
            frame,
            TagD.getTagValue(this, Tag.SOPInstanceUID));
        DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
        try (DicomFileInputStream inputStream = new DicomFileInputStream(original.get().toPath())) {
          reader.setInput(inputStream);
          ImageDescriptor desc = reader.getImageDescriptor();
          PlanarImage img = reader.getPlanarImage(frame, null);
          if (img.width() != desc.getColumns() || img.height() != desc.getRows()) {
            LOGGER.error(
                "The native image size ({}x{}) does not match with the DICOM attributes({}x{})",
                img.width(),
                img.height(),
                desc.getColumns(),
                desc.getRows());
          }
          return noEmbeddedOverlay ? ImageRendering.getImageWithoutEmbeddedOverlay(img, desc) : img;
        } finally {
          reader.dispose();
        }
      }
    }
    return null;
  }

  private static Mat getMatBuffer(ExtendSegmentedInputImageStream extParams) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(extParams.getFile(), "r")) {

      long cols = Arrays.stream(extParams.getSegmentLengths()).sum();
      Mat buf = new Mat(1, (int) cols, CvType.CV_8UC1);
      long[] pos = extParams.getSegmentPositions();
      int offset = 0;
      for (int i = 0; i < pos.length; i++) {
        int len = (int) extParams.getSegmentLengths()[i];
        byte[] b = new byte[len];
        raf.seek(pos[i]);
        raf.read(b);
        buf.put(0, offset, b);
        offset += len;
      }
      return buf;
    }
  }

  private static Mat getRawData(BulkData bulkData) {
    try (BufferedInputStream input = new BufferedInputStream(bulkData.openStream())) {
      Mat buf = new Mat(1, bulkData.length(), CvType.CV_8UC1);
      byte[] b = new byte[bulkData.length()];
      input.read(b, 0, b.length);
      buf.put(0, 0, b);
      return buf;
    } catch (Exception e) {
      LOGGER.error("Reading Waveform data");
    }
    return new Mat();
  }

  private MediaElement getSingleImage() {
    return getSingleImage(0);
  }

  private MediaElement getSingleImage(int frame) {
    MediaElement[] elements = getMediaElement();
    if (elements != null && elements.length > frame) {
      return elements[frame];
    }
    return null;
  }

  @Override
  public MediaElement getPreview() {
    return getSingleImage();
  }

  @Override
  public boolean delegate(DataExplorerModel explorerModel) {
    return false;
  }

  @Override
  public synchronized MediaElement[] getMediaElement() {
    if (image == null && isReadableDicom()) {
      if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
        image = new MediaElement[] {new DicomVideoElement(this, null)};
      } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
        image = new MediaElement[] {new DicomEncapDocElement(this, null)};
      } else {
        if (numberOfFrame > 0) {
          image = new MediaElement[numberOfFrame];
          for (int i = 0; i < image.length; i++) {
            image[i] = new DicomImageElement(this, i);
          }
          if (numberOfFrame > 1) {
            // IF enhanced DICOM, instance number can be overridden later
            // IF simple multiframe instance number is necessary
            for (int i = 0; i < image.length; i++) {
              image[i].setTag(TagD.get(Tag.InstanceNumber), i + 1);
            }
          }
        } else {
          String modality = TagD.getTagValue(this, Tag.Modality, String.class);
          if (modality != null) {
            DicomSpecialElementFactory factory = DCM_ELEMENT_FACTORIES.get(modality);
            if (factory != null) {
              image = new MediaElement[1];
              image[0] = factory.buildDicomSpecialElement(this);
            }
          }
          if (image == null) {
            // Corrupted image => should have one frame
            image = new MediaElement[0];
          }
        }
      }
    }
    return image;
  }

  @Override
  public MediaSeries<MediaElement> getMediaSeries() {
    Series<MediaElement> series = null;
    if (isReadableDicom()) {
      String seriesUID = TagD.getTagValue(this, Tag.SeriesInstanceUID, String.class);
      series = buildSeries(seriesUID);
      writeMetaData(series);
      // no need to apply splitting rules
      // also no model
      MediaElement[] elements = getMediaElement();
      if (elements != null) {
        for (MediaElement media : elements) {
          series.addMedia(media);
        }
      }
    }
    return series;
  }

  @Override
  public int getMediaElementNumber() {
    return numberOfFrame;
  }

  @Override
  public String getMediaFragmentMimeType() {
    return getMimeType();
  }

  @Override
  public Map<TagW, Object> getMediaFragmentTags(Object key) {
    if (key instanceof Integer val && val > 0) {
      // Clone the shared tag
      Map<TagW, Object> tagList = new HashMap<>(tags);
      SimpleTaggable taggable = new SimpleTaggable(tagList);
      if (DicomMediaUtils.writePerFrameFunctionalGroupsSequence(taggable, getDicomObject(), val)) {
        DicomMediaUtils.computeSlicePositionVector(taggable);
      }
      return tagList;
    }
    return tags;
  }

  @Override
  public void close() {
    HEADER_CACHE.remove(this);
  }

  @Override
  public Codec getCodec() {
    return BundleTools.getCodec(DicomMediaIO.DICOM_MIMETYPE, DicomCodec.NAME);
  }

  @Override
  public String[] getReaderDescription() {
    return new String[] {
      "DICOM Codec: " + DicomCodec.NAME, // NON-NLS
      "Version: " + Implementation.getVersionName(), // NON-NLS
      "Image decompression: OpenCV imgcodecs", // NON-NLS
      "Version: " + Core.VERSION // NON-NLS
    };
  }

  public Series<MediaElement> buildSeries(String seriesUID) {
    Series<? extends MediaElement> series;
    if (IMAGE_MIMETYPE.equals(mimeType)) {
      series = new DicomSeries(seriesUID);
    } else if (SERIES_VIDEO_MIMETYPE.equals(mimeType)) {
      series = new DicomVideoSeries(seriesUID);
    } else if (SERIES_ENCAP_DOC_MIMETYPE.equals(mimeType)) {
      series = new DicomEncapDocSeries(seriesUID);
    } else {
      series = new DicomSeries(seriesUID);
    }
    return (Series<MediaElement>) series;
  }

  @Override
  public Attributes getDicomObject() {
    try {
      DicomMetaData md = readMetaData();
      return md.getDicomObject();
    } catch (Exception e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.error("Cannot read DICOM:", e);
      } else {
        LOGGER.error(e.getMessage());
      }
    }
    return null;
  }

  @Override
  public DicomMetaData getDicomMetaData() {
    try {
      return readMetaData();
    } catch (Exception e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.error("Cannot read DICOM:", e);
      } else {
        LOGGER.error(e.getMessage());
      }
    }
    return null;
  }

  /** Reads the DICOM header meta-data, up to, but not including pixel data. */
  private synchronized DicomMetaData readMetaData() throws IOException {
    DicomMetaData header = HEADER_CACHE.get(this);
    if (header != null) {
      return header;
    } else if (dcmMetadata != null) {
      return dcmMetadata;
    }

    Optional<File> file = fileCache.getOriginalFile();
    if (file.isEmpty()) {
      throw new IllegalArgumentException("No file found!");
    }
    Path path = file.get().toPath();

    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    try (DicomFileInputStream inputStream = new DicomFileInputStream(path)) {
      reader.setInput(inputStream);
      DicomMetaData dicomMetaData = reader.getStreamMetadata();
      Attributes dcm = dicomMetaData.getDicomObject();
      this.numberOfFrame = dcm.getInt(Tag.NumberOfFrames, 0);
      VR.Holder pixelatedVR = new VR.Holder();
      Object pixelData = dcm.getValue(Tag.PixelData, pixelatedVR);
      if (pixelData == null) {
        pixelData = dcm.getValue(Tag.FloatPixelData, pixelatedVR);
      }
      if (pixelData == null) {
        pixelData = dcm.getValue(Tag.DoubleFloatPixelData, pixelatedVR);
      }

      if (pixelData != null) {
        hasPixel = true;
      }

      if (numberOfFrame <= 0 && hasPixel) {
        this.numberOfFrame = 1;
      }
      HEADER_CACHE.put(this, dicomMetaData);
      return dicomMetaData;
    } finally {
      reader.dispose();
    }
  }
}
