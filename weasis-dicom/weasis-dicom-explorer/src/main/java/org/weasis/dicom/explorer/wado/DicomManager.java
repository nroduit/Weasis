/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.explorer.DicomFieldsView.DicomData;
import org.weasis.dicom.explorer.Messages;

public class DicomManager {

    /** The single instance of this singleton class. */

    private static DicomManager instance;
    private TransferSyntax wadoTSUID;
    private boolean portableDirCache;
    private final List<DicomData> limitedDicomTags;

    private DicomManager() {
        limitedDicomTags = new ArrayList<>();
        portableDirCache = true;
        restoreDefaultValues();
        if ("superuser".equals(System.getProperty("weasis.user.prefs"))) { //$NON-NLS-1$ //$NON-NLS-2$
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences pref = BundlePreferences.getDefaultPreferences(context);
            if (pref != null) {
                Preferences prefNode = pref.node("wado"); //$NON-NLS-1$
                wadoTSUID = TransferSyntax.getTransferSyntax(prefNode.get("compression.type", "NONE")); //$NON-NLS-1$ //$NON-NLS-2$
                if (wadoTSUID.getCompression() != null) {
                    wadoTSUID.setCompression(prefNode.getInt("compression.rate", 75)); //$NON-NLS-1$
                }
            }
        }
        initRequiredDicomTags();
    }

    /**
     * Return the single instance of this class. This method guarantees the singleton property of this class.
     */
    public static synchronized DicomManager getInstance() {
        if (instance == null) {
            instance = new DicomManager();
        }
        return instance;
    }

    private void initRequiredDicomTags() {
        TagView[] patient = { new TagView(TagD.get(Tag.PatientName)), new TagView(TagD.get(Tag.PatientID)),
            new TagView(TagD.get(Tag.IssuerOfPatientID)), new TagView(TagD.get(Tag.PatientSex)),
            new TagView(TagD.get(Tag.PatientBirthDate)) };

        final TagView[] station = { new TagView(TagD.get(Tag.Manufacturer)),
            new TagView(TagD.get(Tag.ManufacturerModelName)), new TagView(TagD.get(Tag.StationName)) };

        TagView[] study = { new TagView(TagD.get(Tag.StudyInstanceUID)), new TagView(TagD.get(Tag.StudyDate)),
            new TagView(TagD.get(Tag.StudyTime)), new TagView(TagD.get(Tag.StudyID)),
            new TagView(TagD.get(Tag.AccessionNumber)), new TagView(TagD.get(Tag.StudyDescription)),
            new TagView(TagD.get(Tag.StudyComments)) };

        TagView[] series = { new TagView(TagD.get(Tag.SeriesInstanceUID)), new TagView(TagD.get(Tag.SeriesDate)),
            new TagView(TagD.get(Tag.SeriesTime)), new TagView(TagD.get(Tag.SeriesNumber)),
            new TagView(TagD.get(Tag.Modality)), new TagView(TagD.get(Tag.ReferringPhysicianName)),
            new TagView(TagD.get(Tag.InstitutionName)), new TagView(TagD.get(Tag.InstitutionalDepartmentName)),
            new TagView(TagD.get(Tag.SeriesDescription)), new TagView(TagD.get(Tag.BodyPartExamined)) };

        TagView[] image = { new TagView(TagD.get(Tag.SOPInstanceUID)),
            new TagView(TagD.getTagFromIDs(Tag.FrameType, Tag.ImageType)), new TagView(TagD.get(Tag.TransferSyntaxUID)),
            new TagView(TagD.get(Tag.InstanceNumber)), new TagView(TagD.get(Tag.ImageComments)),
            new TagView(TagD.getTagFromIDs(Tag.FrameLaterality, Tag.ImageLaterality, Tag.Laterality)),
            new TagView(TagD.get(Tag.PhotometricInterpretation)), new TagView(TagD.get(Tag.SamplesPerPixel)),
            new TagView(TagD.get(Tag.PixelRepresentation)), new TagView(TagD.get(Tag.Columns)),
            new TagView(TagD.get(Tag.Rows)), new TagView(TagD.get(Tag.BitsAllocated)),
            new TagView(TagD.get(Tag.BitsStored)) };

        TagView[] imgPlane = { new TagView(TagD.get(Tag.PixelSpacing)), new TagView(TagD.get(Tag.SliceLocation)),
            new TagView(TagD.get(Tag.SliceThickness)), new TagView(TagD.get(Tag.ImagePositionPatient)),
            new TagView(TagD.get(Tag.ImageOrientationPatient)), new TagView(TagD.get(Tag.StudyComments)) };

        TagView[] imgAcq = { new TagView(TagD.get(Tag.KVP)), new TagView(TagD.get(Tag.ContrastBolusAgent)) };

        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.pat"), patient, TagD.Level.PATIENT)); //$NON-NLS-1$
        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.station"), station, TagD.Level.SERIES)); //$NON-NLS-1$
        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.study"), study, TagD.Level.STUDY)); //$NON-NLS-1$
        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.series"), series, TagD.Level.SERIES)); //$NON-NLS-1$
        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.object"), image, TagD.Level.INSTANCE)); //$NON-NLS-1$
        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.plane"), imgPlane, TagD.Level.INSTANCE)); //$NON-NLS-1$
        limitedDicomTags.add(new DicomData(Messages.getString("DicomFieldsView.acqu"), imgAcq, TagD.Level.INSTANCE)); //$NON-NLS-1$
    }

    public List<DicomData> getLimitedDicomTags() {
        return limitedDicomTags;
    }

    public boolean isPortableDirCache() {
        return portableDirCache;
    }

    public void setPortableDirCache(boolean portableDirCache) {
        this.portableDirCache = portableDirCache;
    }

    public TransferSyntax getWadoTSUID() {
        return wadoTSUID;
    }

    public void setWadoTSUID(TransferSyntax wadoTSUID) {
        this.wadoTSUID = wadoTSUID == null ? TransferSyntax.NONE : wadoTSUID;
    }

    public void restoreDefaultValues() {
        this.wadoTSUID = TransferSyntax.NONE;
    }

    public void savePreferences() {
        if ("superuser".equals(System.getProperty("weasis.user.prefs"))) { //$NON-NLS-1$ //$NON-NLS-2$
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                Preferences prefNode = prefs.node("wado"); //$NON-NLS-1$
                BundlePreferences.putStringPreferences(prefNode, "compression.type", wadoTSUID.name()); //$NON-NLS-1$
                if (wadoTSUID.getCompression() != null) {
                    BundlePreferences.putIntPreferences(prefNode, "compression.rate", wadoTSUID.getCompression()); //$NON-NLS-1$
                }
            }
        }
    }
}
