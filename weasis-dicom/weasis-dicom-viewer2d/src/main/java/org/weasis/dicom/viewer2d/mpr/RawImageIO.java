/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.PlanarImage;

public class RawImageIO implements DcmMediaReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(RawImageIO.class);

  private static final String MIME_TYPE = "image/raw"; // NON-NLS

  private static final SoftHashMap<RawImageIO, DicomMetaData> HEADER_CACHE =
      new SoftHashMap<>() {

        @Override
        public void removeElement(Reference<? extends DicomMetaData> soft) {
          RawImageIO key = reverseLookup.remove(soft);
          if (key != null) {
            hash.remove(key);
          }
        }
      };

  protected FileRawImage imageCV;
  private final FileCache fileCache;

  private final HashMap<TagW, Object> tags;
  private final Codec codec;
  private Attributes attributes;

  public RawImageIO(FileRawImage imageCV, Codec codec) {
    this.imageCV = Objects.requireNonNull(imageCV);
    this.fileCache = new FileCache(this);
    this.tags = new HashMap<>();
    this.codec = codec;
  }

  public void setBaseAttributes(Attributes attributes) {
    this.attributes = attributes;
  }

  public File getDicomFile() {
    Attributes dcm = getDicomObject();

    File file = imageCV.getFile();
    BulkData bdl =
        new BulkData(
            file.toURI().toString(),
            FileRawImage.HEADER_LENGTH,
            (int) file.length() - FileRawImage.HEADER_LENGTH,
            false);
    dcm.setValue(Tag.PixelData, VR.OW, bdl);
    File tmpFile = new File(DicomMediaIO.DICOM_EXPORT_DIR, dcm.getString(Tag.SOPInstanceUID));
    try (DicomOutputStream out = new DicomOutputStream(tmpFile)) {
      out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
    } catch (IOException e) {
      LOGGER.error("Cannot write dicom file", e);
      return null;
    }
    return tmpFile;
  }

  @Override
  public PlanarImage getImageFragment(MediaElement media) throws Exception {
    if (media != null && media.getFile() != null) {
      return imageCV.read();
    }
    return null;
  }

  @Override
  public URI getUri() {
    return imageCV.getFile().toURI();
  }

  @Override
  public MediaElement getPreview() {
    return null;
  }

  @Override
  public boolean delegate(DataExplorerModel explorerModel) {
    return false;
  }

  @Override
  public MediaElement[] getMediaElement() {
    return null;
  }

  @Override
  public MediaSeries<MediaElement> getMediaSeries() {
    return null;
  }

  @Override
  public int getMediaElementNumber() {
    return 1;
  }

  @Override
  public String getMediaFragmentMimeType() {
    return MIME_TYPE;
  }

  @Override
  public Map<TagW, Object> getMediaFragmentTags(Object key) {
    return tags;
  }

  @Override
  public void close() {
    HEADER_CACHE.remove(this);
  }

  @Override
  public Codec getCodec() {
    return codec;
  }

  @Override
  public String[] getReaderDescription() {
    return new String[] {"File Raw Image Decoder from OpenCV"}; // NON-NLS
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

  public void copyTags(TagW[] tagList, MediaElement media, boolean allowNullValue) {
    if (tagList != null && media != null) {
      for (TagW tag : tagList) {
        Object value = media.getTagValue(tag);
        if (allowNullValue || value != null) {
          tags.put(tag, value);
        }
      }
    }
  }

  @Override
  public void replaceURI(URI uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Attributes getDicomObject() {
    DicomMetaData md = readMetaData();
    return md.getDicomObject();
  }

  @Override
  public FileCache getFileCache() {
    return fileCache;
  }

  @Override
  public boolean buildFile(File output) {
    return false;
  }

  @Override
  public DicomMetaData getDicomMetaData() {
    return readMetaData();
  }

  @Override
  public boolean isEditableDicom() {
    return false;
  }

  private synchronized DicomMetaData readMetaData() {
    DicomMetaData header = HEADER_CACHE.get(this);
    if (header != null) {
      return header;
    }
    Attributes dcm = new Attributes(tags.size() + attributes.size());
    SpecificCharacterSet cs = attributes.getSpecificCharacterSet();
    dcm.setSpecificCharacterSet(cs.toCodes());
    DicomMediaUtils.fillAttributes(tags, dcm);
    dcm.addAll(attributes);
    File file = imageCV.getFile();
    BulkData bdl =
        new BulkData(
            file.toURI().toString(),
            FileRawImage.HEADER_LENGTH,
            (int) file.length() - FileRawImage.HEADER_LENGTH,
            false);
    dcm.setValue(Tag.PixelData, VR.OW, bdl);
    header = new DicomMetaData(dcm, UID.ImplicitVRLittleEndian);
    HEADER_CACHE.put(this, header);
    return header;
  }
}
