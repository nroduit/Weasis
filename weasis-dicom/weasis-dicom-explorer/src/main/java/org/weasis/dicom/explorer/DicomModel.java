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
package org.weasis.dicom.explorer;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.Tree;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.codec.DicomEncapDocElement;
import org.weasis.dicom.codec.DicomEncapDocSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomVideoElement;
import org.weasis.dicom.codec.DicomVideoSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadRemoteDicom;

public class DicomModel implements TreeModel, DataExplorerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomModel.class);

    public static final String[] functions = { "get", "close" }; //$NON-NLS-1$ //$NON-NLS-2$
    public final static String NAME = "DICOM"; //$NON-NLS-1$
    public final static String PREFERENCE_NODE = "dicom.model"; //$NON-NLS-1$

    public final static TreeModelNode patient = new TreeModelNode(1, 0, TagW.PatientPseudoUID);
    public final static TreeModelNode study = new TreeModelNode(2, 0, TagW.StudyInstanceUID);
    public final static TreeModelNode series = new TreeModelNode(3, 0, TagW.SubseriesInstanceUID);

    public final static ArrayList<TreeModelNode> modelStrucure = new ArrayList<TreeModelNode>(5);
    static {
        modelStrucure.add(root);
        modelStrucure.add(patient);
        modelStrucure.add(study);
        modelStrucure.add(series);
    }
    public final static Executor loadingExecutor = Executors.newSingleThreadExecutor();
    private final Tree<MediaSeriesGroup> model;
    private PropertyChangeSupport propertyChange = null;
    private final TagW[] multiframeSplittingRules = new TagW[] { TagW.ImageType, TagW.SOPInstanceUID, TagW.FrameType,
        TagW.FrameAcquisitionNumber, TagW.StackID };
    private final HashMap<Modality, TagW[]> splittingRules = new HashMap<Modality, TagW[]>();

    public DicomModel() {
        model = new Tree<MediaSeriesGroup>(rootNode);
        Preferences prefs = Activator.PREFERENCES.getDefaultPreferences();
        if (prefs == null) {
        } else {
            Preferences p = prefs.node(PREFERENCE_NODE);
        }
        splittingRules.put(Modality.Default, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID });
        splittingRules.put(Modality.CT, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID,
            TagW.ImageOrientationPlane, TagW.GantryDetectorTilt, TagW.ConvolutionKernel });
        splittingRules.put(Modality.PT, splittingRules.get(Modality.CT));
        splittingRules.put(Modality.MR, new TagW[] { TagW.ImageType, TagW.ContrastBolusAgent, TagW.SOPClassUID,
            TagW.ImageOrientationPlane, TagW.ScanningSequence, TagW.SequenceVariant, TagW.ScanOptions,
            TagW.RepetitionTime, TagW.EchoTime, TagW.InversionTime, TagW.FlipAngle });

    }

    public synchronized List<Codec> getCodecPlugins() {
        ArrayList<Codec> codecPlugins = new ArrayList<Codec>(1);
        synchronized (BundleTools.CODEC_PLUGINS) {
            for (Codec codec : BundleTools.CODEC_PLUGINS) {
                if (codec != null && codec.isMimeTypeSupported("application/dicom") && !codecPlugins.contains(codec)) { //$NON-NLS-1$
                    codecPlugins.add(codec);
                }
            }
        }
        return codecPlugins;
    }

    public Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node) {
        return model.getSuccessors(node);
    }

    public MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object value) {
        if (parent != null || value != null) {
            synchronized (model) {
                for (MediaSeriesGroup node : model.getSuccessors(parent)) {
                    if (node.equals(value)) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    public void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        synchronized (model) {
            model.addLeaf(root, leaf);
        }
    }

    public void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        synchronized (model) {
            Tree<MediaSeriesGroup> tree = model.getTree(root);
            if (tree != null) {
                tree.removeLeaf(leaf);
            }
        }
    }

    public MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode) {
        if (null != node && modelNode != null) {
            synchronized (model) {
                Tree<MediaSeriesGroup> tree = model.getTree(node);
                if (tree != null) {
                    Tree<MediaSeriesGroup> parent = null;
                    while ((parent = tree.getParent()) != null) {
                        if (parent.getHead().getTagID().equals(modelNode.getTagElement())) {
                            return parent.getHead();
                        }
                        tree = parent;
                    }
                }
            }
        }
        return null;
    }

    public void dispose() {
        synchronized (model) {
            for (Iterator<MediaSeriesGroup> iterator = this.getChildren(TreeModel.rootNode).iterator(); iterator
                .hasNext();) {
                MediaSeriesGroup pt = iterator.next();
                Collection<MediaSeriesGroup> studies = this.getChildren(pt);
                for (Iterator<MediaSeriesGroup> iterator2 = studies.iterator(); iterator2.hasNext();) {
                    MediaSeriesGroup study = iterator2.next();
                    Collection<MediaSeriesGroup> seriesList = this.getChildren(study);
                    for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                        Object item = it.next();
                        if (item instanceof Series) {
                            ((Series) item).dispose();
                        }
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public List<TreeModelNode> getModelStructure() {
        return modelStrucure;
    }

    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange == null) {
            propertyChange = new PropertyChangeSupport(this);
        }
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange != null) {
            propertyChange.removePropertyChangeListener(propertychangelistener);
        }

    }

    public void firePropertyChange(final ObservableEvent event) {
        if (propertyChange != null) {
            if (event == null) {
                throw new NullPointerException();
            }
            if (SwingUtilities.isEventDispatchThread()) {
                propertyChange.firePropertyChange(event);
            } else {
                SwingUtilities.invokeLater(new Runnable() {

                    public void run() {
                        propertyChange.firePropertyChange(event);
                    }
                });
            }
        }
    }

    public void mergeSeries(Series[] seriesList) {
        if (seriesList != null && seriesList.length > 1) {
            String uid = (String) seriesList[0].getTagValue(TagW.SeriesInstanceUID);
            boolean sameOrigin = true;
            if (uid != null) {
                for (int i = 1; i < seriesList.length; i++) {
                    if (!uid.equals(seriesList[i].getTagValue(TagW.SeriesInstanceUID))) {
                        sameOrigin = false;
                        break;
                    }
                }
            }
            if (sameOrigin) {
                int min = Integer.MAX_VALUE;
                Series base = seriesList[0];
                for (Series series : seriesList) {
                    Integer splitNb = (Integer) series.getTagValue(TagW.SplitSeriesNumber);
                    if (splitNb != null && min > splitNb) {
                        min = splitNb;
                        base = series;
                    }
                }
                for (Series series : seriesList) {
                    if (series != base) {
                        base.addAll(series.getMedias());
                        removeSeries(series);
                    }
                }
                base.sort(SortSeriesStack.instanceNumber);
                // update observer
                this.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Replace, DicomModel.this, base,
                    base));
            }
        }
    }

    public void removeSeries(MediaSeriesGroup dicomSeries) {
        if (dicomSeries != null) {
            if (LoadRemoteDicom.currentTasks.size() > 0) {
                if (dicomSeries instanceof DicomSeries) {
                    LoadRemoteDicom.stopDownloading((DicomSeries) dicomSeries);
                }
            }
            // remove first series in UI (Dicom Explorer, Viewer using this series)
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, DicomModel.this, null,
                dicomSeries));
            // remove in the data model
            MediaSeriesGroup studyGroup = getParent(dicomSeries, DicomModel.study);
            removeHierarchyNode(studyGroup, dicomSeries);
            LOGGER.info("Remove Series: {}", dicomSeries); //$NON-NLS-1$
            dicomSeries.dispose();
        }
    }

    public void removeStudy(MediaSeriesGroup studyGroup) {
        if (studyGroup != null) {
            if (LoadRemoteDicom.currentTasks.size() > 0) {
                Collection<MediaSeriesGroup> seriesList = getChildren(studyGroup);
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup group = it.next();
                    if (group instanceof DicomSeries) {
                        LoadRemoteDicom.stopDownloading((DicomSeries) group);
                    }
                }
            }
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, DicomModel.this, null,
                studyGroup));
            MediaSeriesGroup patientGroup = getParent(studyGroup, DicomModel.patient);
            removeHierarchyNode(patientGroup, studyGroup);
            LOGGER.info("Remove Study: {}", studyGroup); //$NON-NLS-1$
        }
    }

    public void removePatient(MediaSeriesGroup patientGroup) {
        if (patientGroup != null) {
            if (LoadRemoteDicom.currentTasks.size() > 0) {
                Collection<MediaSeriesGroup> studyList = getChildren(patientGroup);
                for (Iterator<MediaSeriesGroup> it = studyList.iterator(); it.hasNext();) {
                    MediaSeriesGroup studyGroup = it.next();
                    Collection<MediaSeriesGroup> seriesList = getChildren(studyGroup);
                    for (Iterator<MediaSeriesGroup> it2 = seriesList.iterator(); it2.hasNext();) {
                        MediaSeriesGroup group = it2.next();
                        if (group instanceof DicomSeries) {
                            LoadRemoteDicom.stopDownloading((DicomSeries) group);
                        }
                    }
                }
            }
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, DicomModel.this, null,
                patientGroup));
            removeHierarchyNode(rootNode, patientGroup);
            LOGGER.info("Remove Patient: {}", patientGroup); //$NON-NLS-1$
        }
    }

    public static boolean isSpecialModality(Series series) {
        String modality = series == null ? null : (String) series.getTagValue(TagW.Modality);
        return (modality != null && ("PR".equals(modality) || "KO".equals(modality) || "SR".equals(modality))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private void splitSeries(DicomMediaIO dicomReader, Series original, MediaElement media) {
        MediaSeriesGroup study = getParent(original, DicomModel.study);
        String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
        int k = 1;
        while (true) {
            String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
            MediaSeriesGroup group = getHierarchyNode(study, uid);
            if (group == null) {
                break;
            }
            k++;
        }
        String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
        Series s = dicomReader.buildSeries(uid);
        dicomReader.writeMetaData(s);
        Object val = original.getTagValue(TagW.SplitSeriesNumber);
        if (val == null) {
            original.setTag(TagW.SplitSeriesNumber, 1);
        }
        s.setTag(TagW.SplitSeriesNumber, k + 1);
        s.setTag(TagW.ExplorerModel, this);
        addHierarchyNode(study, s);
        s.addMedia(media);
        LOGGER.info("Series splitting: {}", s); //$NON-NLS-1$
    }

    private void replaceSeries(DicomMediaIO dicomReader, Series original, MediaElement media) {
        MediaSeriesGroup study = getParent(original, DicomModel.study);
        String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
        int k = 1;
        while (true) {
            String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
            MediaSeriesGroup group = getHierarchyNode(study, uid);
            if (group == null) {
                break;
            }
            k++;
        }
        String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
        Series s = dicomReader.buildSeries(uid);
        dicomReader.writeMetaData(s);
        Object val = original.getTagValue(TagW.SplitSeriesNumber);
        if (val == null) {
            // -1 convention to exclude this Series
            original.setTag(TagW.SplitSeriesNumber, -1);
        }
        s.setTag(TagW.SplitSeriesNumber, k);
        s.setTag(TagW.ExplorerModel, this);
        addHierarchyNode(study, s);
        s.addMedia(media);
        LOGGER.info("Replace Series: {}", s); //$NON-NLS-1$
    }

    public boolean applySplittingRules(Series original, MediaElement media) {
        if (media != null && media.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomReader = (DicomMediaIO) media.getMediaReader();
            if (original instanceof DicomSeries) {
                // Handle cases when the Series is created before getting the image (downloading)
                if (media instanceof DicomVideoElement || media instanceof DicomEncapDocElement) {
                    replaceSeries(dicomReader, original, media);
                    return true;
                }
                DicomSeries initialSeries = (DicomSeries) original;
                int frames = dicomReader.getMediaElementNumber();
                if (frames < 1) {
                    initialSeries.addMedia(media);
                } else {
                    Modality modality = Modality.getModality((String) initialSeries.getTagValue(TagW.Modality));

                    TagW[] rules = frames > 1 ? multiframeSplittingRules : splittingRules.get(modality);

                    if (rules == null) {
                        rules = splittingRules.get(Modality.Default);
                    }

                    if (isSimilar(rules, initialSeries, media)) {
                        initialSeries.addMedia(media);
                        return false;
                    }
                    MediaSeriesGroup study = getParent(original, DicomModel.study);
                    String seriesUID = (String) original.getTagValue(TagW.SeriesInstanceUID);
                    int k = 1;
                    while (true) {
                        String uid = "#" + k + "." + seriesUID; //$NON-NLS-1$ //$NON-NLS-2$
                        MediaSeriesGroup group = getHierarchyNode(study, uid);
                        if (group instanceof DicomSeries) {
                            if (isSimilar(rules, (DicomSeries) group, media)) {
                                ((DicomSeries) group).addMedia(media);
                                return false;
                            }
                        } else {
                            break;
                        }
                        k++;
                    }
                    splitSeries(dicomReader, initialSeries, media);
                    return true;
                }
            } else if (original instanceof DicomVideoSeries || original instanceof DicomEncapDocSeries) {
                if (original.getMedias().size() > 0) {
                    splitSeries(dicomReader, original, media);
                    return true;
                } else {
                    original.addMedia(media);
                }
            }
        }
        return false;
    }

    private boolean isSimilar(TagW[] rules, DicomSeries series, final MediaElement media) {
        final DicomImageElement firstMedia = series.getMedia(0);
        if (firstMedia == null) {
            // no image
            return true;
        }
        for (TagW tagElement : rules) {
            Object tag = media.getTagValue(tagElement);
            Object tag2 = firstMedia.getTagValue(tagElement);
            // special case if both are null
            if (tag == null && tag2 == null) {
                continue;
            }
            if (tag != null && !tag.equals(tag2)) {
                return false;
            }
        }
        return true;
    }

    public void get(String[] argv) throws IOException {
        final String[] usage = { "Load DICOM files remotely or locally", "Usage: dicom:get [Options] SOURCE", //$NON-NLS-1$ //$NON-NLS-2$
            "  -l --local		Open DICOMs from local disk", //$NON-NLS-1$
            "  -p --portable       Open DICOMs from default directories at the same level of the executable", //$NON-NLS-1$
            "  -w --wado		Open DICOMs from an XML file containing UIDs", "  -? --help		show help" }; //$NON-NLS-1$ //$NON-NLS-2$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || (args.isEmpty() && !opt.isSet("portable"))) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            public void run() {

                firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, DicomModel.this, null,
                    DicomModel.this));
                // start importing local dicom series list
                if (opt.isSet("local")) { //$NON-NLS-1$
                    File[] files = new File[args.size()];
                    for (int i = 0; i < files.length; i++) {
                        files[i] = new File(args.get(i));
                    }
                    loadingExecutor.execute(new LoadLocalDicom(files, true, DicomModel.this));
                }
                // build WADO series list to download
                else if (opt.isSet("wado")) { //$NON-NLS-1$
                    loadingExecutor.execute(new LoadRemoteDicom(args.toArray(new String[args.size()]), DicomModel.this));
                }
                // Get DICOM folder (by default DICOM, dicom, IHE_PDI, ihe_pdi) at the same level at the Weasis
                // executable file
                else if (opt.isSet("portable")) { //$NON-NLS-1$

                    String prop = System.getProperty("weasis.portable.dicom.directory");
                    String baseDir = System.getProperty("weasis.portable.dir");

                    if (prop != null && baseDir != null) {
                        String[] dirs = prop.split(","); //$NON-NLS-1$
                        File[] files = new File[dirs.length];
                        for (int i = 0; i < files.length; i++) {
                            files[i] = new File(baseDir, dirs[i].trim().replaceAll("/", File.separator));
                        }
                        loadingExecutor.execute(new LoadLocalDicom(files, true, DicomModel.this));
                    }
                }
            }
        });
    }

    public void close(String[] argv) throws IOException {
        final String[] usage = { "Remove DICOM files in Dicom Explorer", //$NON-NLS-1$
            "Usage: dicom:close [patient | study | series] [ARGS]", //$NON-NLS-1$
            "  -p --patient <args>	Close patient, [arg] is patientUID (PatientID + Patient Birth Date, by default)", //$NON-NLS-1$
            "  -y --study <args>	Close study, [arg] is Study Instance UID", //$NON-NLS-1$
            "  -s --series <args>	Close series, [arg] is Series Instance UID", "  -? --help		show help" }; //$NON-NLS-1$ //$NON-NLS-2$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            public void run() {
                firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, DicomModel.this, null,
                    DicomModel.this));
                // start build local dicom series list
                if (opt.isSet("patient")) { //$NON-NLS-1$
                    for (String patientUID : args) {
                        MediaSeriesGroup patientGroup = null;
                        // In Weasis, Global Identity of the patient is composed of the patientID and the birth date by
                        // default
                        // TODO handle preferences choice for patientUID
                        patientGroup = getHierarchyNode(TreeModel.rootNode, patientUID);
                        if (patientGroup == null) {
                            System.out.println("Cannot find patient: " + patientUID); //$NON-NLS-1$
                            continue;
                        } else {
                            removePatient(patientGroup);
                        }
                    }
                } else if (opt.isSet("study")) { //$NON-NLS-1$
                    for (String studyUID : args) {
                        for (MediaSeriesGroup ptGroup : model.getSuccessors(rootNode)) {
                            MediaSeriesGroup stGroup = getHierarchyNode(ptGroup, studyUID);
                            if (stGroup != null) {
                                removeStudy(stGroup);
                                break;
                            }
                        }
                    }
                } else if (opt.isSet("series")) { //$NON-NLS-1$
                    for (String seriesUID : args) {
                        patientLevel: for (MediaSeriesGroup ptGroup : model.getSuccessors(rootNode)) {
                            for (MediaSeriesGroup stGroup : model.getSuccessors(ptGroup)) {
                                MediaSeriesGroup series = getHierarchyNode(stGroup, seriesUID);
                                if (series instanceof Series) {
                                    removeSeries(series);
                                    break patientLevel;
                                }
                            }
                        }
                    }
                }

            }
        });
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return patient;
    }

}
