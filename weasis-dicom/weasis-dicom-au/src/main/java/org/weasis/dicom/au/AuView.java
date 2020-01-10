/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.au;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

@SuppressWarnings("serial")
public class AuView extends JPanel implements SeriesViewerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuView.class);

    private Series<?> series;

    private Clip clip;
    private boolean playing = false; // whether the sound is currently playing

    private int audioLength; // Length of the sound.
    private int audioPosition = 0; // Current position within the sound

    private JButton play; // The Play/Stop button
    private JSlider progress; // Shows and sets current position in sound
    private JLabel time; // Displays audioPosition as a number
    private Timer timer; // Updates slider every 100 milliseconds

    public AuView() {
        this(null);
    }

    public AuView(Series series) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setPreferredSize(new Dimension(1024, 1024));
        setSeries(series);
    }

    public synchronized Series getSeries() {
        return series;
    }

    public synchronized void setSeries(Series newSeries) {
        MediaSeries<?> oldsequence = this.series;
        this.series = newSeries;

        if (oldsequence == null && newSeries == null) {
            return;
        }
        if (oldsequence != null && oldsequence.equals(newSeries)) {
            return;
        }

        closingSeries(oldsequence);

        if (series != null) {
            DicomSpecialElement s = null;
            List<DicomSpecialElement> specialElements =
                (List<DicomSpecialElement>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && !specialElements.isEmpty()) {
                // Should have only one object by series (if more, they are split in several sub-series in dicomModel)
                s = specialElements.get(0);
            }
            series.setOpen(true);
            series.setFocused(true);
            series.setSelected(true, null);
            try {
                showPlayer(s);

            } catch (Exception e) {
                LOGGER.error("Build audio player", e); //$NON-NLS-1$
            }
        }

    }

    private void closingSeries(MediaSeries<?> mediaSeries) {
        if (mediaSeries == null) {
            return;
        }
        boolean open = false;
        synchronized (UIManager.VIEWER_PLUGINS) {
            List<ViewerPlugin<?>> plugins = UIManager.VIEWER_PLUGINS;
            pluginList: for (final ViewerPlugin<?> plugin : plugins) {
                List<? extends MediaSeries<?>> openSeries = plugin.getOpenSeries();
                if (openSeries != null) {
                    for (MediaSeries<?> s : openSeries) {
                        if (mediaSeries == s) {
                            // The sequence is still open in another view or plugin
                            open = true;
                            break pluginList;
                        }
                    }
                }
            }
        }
        mediaSeries.setOpen(open);
        // TODO setSelected and setFocused must be global to all view as open
        mediaSeries.setSelected(false, null);
        mediaSeries.setFocused(false);
    }

    public void dispose() {
        if (series != null) {
            closingSeries(series);
            series = null;
        }
        if (clip != null) {
            clip.close();
        }
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.LAYOUT.equals(type) && event.getSeries() instanceof Series) {
            setSeries((Series<?>) event.getSeries());
        }
    }

    // Create a SoundPlayer component for the specified file.
    private void showPlayer(final DicomSpecialElement media)
        throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        AudioData audioData = getAudioData(media);
        if (audioData == null) {
            throw new IllegalStateException("Cannot build an AudioInputStream"); //$NON-NLS-1$
        }

        try (AudioInputStream audioStream = new AudioInputStream(audioData.bulkData.openStream(), audioData.audioFormat,
            audioData.bulkData.length() / audioData.audioFormat.getFrameSize())) {
            DataLine.Info info = new DataLine.Info(Clip.class, audioStream.getFormat());
            clip = (Clip) AudioSystem.getLine(info);
            clip.open(audioStream);
        }

        // Get the clip length in microseconds and convert to milliseconds
        audioLength = (int) (clip.getMicrosecondLength() / 1000);

        play = new JButton(Messages.getString("AuView.play")); // Play/stop button //$NON-NLS-1$
        progress = new JSlider(0, audioLength, 0); // Shows position in sound
        time = new JLabel("0"); // Shows position as a # //$NON-NLS-1$

        // When clicked, start or stop playing the sound
        play.addActionListener(e -> {
            if (playing) {
                stop();
            } else {
                play();
            }
        });

        progress.addChangeListener(e -> {
            int value = progress.getValue();
            time.setText(String.format("%.2f s", value / 1000.0)); //$NON-NLS-1$

            // If we're not already there, skip there.
            if (value != audioPosition) {
                skip(value);
            }
        });

        // This timer calls the tick( ) method 10 times a second to keep
        // our slider in sync with the music.
        timer = new javax.swing.Timer(100, e -> tick());

        // put those controls in a row
        Box row = Box.createHorizontalBox();
        row.add(play);
        row.add(progress);
        row.add(time);

        // And add them to this component.
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.add(row);

        addSampledControls();

        JButton export = new JButton(Messages.getString("AuView.export_audio")); //$NON-NLS-1$
        export.addActionListener(e -> saveAudioFile(media));

        this.add(Box.createVerticalStrut(15));
        row = Box.createHorizontalBox();
        row.add(export);
        this.add(row);
    }

    private void saveAudioFile(DicomSpecialElement media) {
        AudioData audioData = getAudioData(media);
        if (audioData != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setAcceptAllFileFilterUsed(false);
            FileFormatFilter filter = new FileFormatFilter("au", "AU"); //$NON-NLS-1$ //$NON-NLS-2$
            fileChooser.addChoosableFileFilter(filter);
            fileChooser.addChoosableFileFilter(new FileFormatFilter("wav", "WAVE")); //$NON-NLS-1$ //$NON-NLS-2$
            fileChooser.setFileFilter(filter);

            if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                if (fileChooser.getSelectedFile() != null) {
                    File file = fileChooser.getSelectedFile();
                    filter = (FileFormatFilter) fileChooser.getFileFilter();
                    String extension = filter == null ? ".au" : "." + filter.getDefaultExtension(); //$NON-NLS-1$ //$NON-NLS-2$
                    String filename = file.getName().endsWith(extension) ? file.getPath() : file.getPath() + extension;

                    try (AudioInputStream audioStream = new AudioInputStream(audioData.bulkData.openStream(),
                        audioData.audioFormat, audioData.bulkData.length() / audioData.audioFormat.getFrameSize())) {
                        if (".wav".equals(extension)) { //$NON-NLS-1$
                            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, new File(filename));
                        } else {
                            AudioSystem.write(audioStream, AudioFileFormat.Type.AU, new File(filename));
                        }
                    } catch (IOException ex) {
                        LOGGER.error("Cannot save audio file!", ex); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    /** Start playing the sound at the current position */
    public void play() {

        clip.start();

        timer.start();
        play.setText(Messages.getString("AuView.stop")); //$NON-NLS-1$
        playing = true;
    }

    /** Stop playing the sound, but retain the current position */
    public void stop() {
        timer.stop();

        clip.stop();

        play.setText(Messages.getString("AuView.play")); //$NON-NLS-1$
        playing = false;
    }

    /** Stop playing the sound and reset the position to 0 */
    public void reset() {
        stop();

        clip.setMicrosecondPosition(0);

        audioPosition = 0;
        progress.setValue(0);
    }

    /** Skip to the specified position */
    public void skip(int position) { // Called when user drags the slider
        if (position < 0 || position > audioLength) {
            return;
        }
        audioPosition = position;

        clip.setMicrosecondPosition(position * 1000L);

        progress.setValue(position); // in case skip( ) is called from outside
    }

    /** Return the length of the sound in ms or ticks */
    public int getLength() {
        return audioLength;
    }

    // An internal method that updates the progress bar.
    // The Timer object calls it 10 times a second.
    // If the sound has finished, it resets to the beginning
    void tick() {
        if (clip.isActive()) {
            audioPosition = (int) (clip.getMicrosecondPosition() / 1000);
            progress.setValue(audioPosition);
        } else {
            reset();
        }
    }

    // For sampled sounds, add sliders to control volume and balance
    void addSampledControls() {
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            if (gainControl != null) {
                this.add(createSlider(gainControl));
            }
        } catch (IllegalArgumentException e) {
            // If MASTER_GAIN volume control is unsupported, just skip it
        }

        try {
            // FloatControl.Type.BALANCE is probably the correct control to
            // use here, but it doesn't work for me, so I use PAN instead.
            FloatControl panControl = (FloatControl) clip.getControl(FloatControl.Type.PAN);
            if (panControl != null) {
                this.add(createSlider(panControl));
            }
        } catch (IllegalArgumentException e) {
        }
    }

    // Return a JSlider component to manipulate the supplied FloatControl for sampled audio.
    JSlider createSlider(final FloatControl c) {
        if (c == null) {
            return null;
        }
        final JSlider s = new JSlider(0, 1000);
        final float min = c.getMinimum();
        final float max = c.getMaximum();
        final float width = max - min;
        float fval = c.getValue();
        s.setValue((int) ((fval - min) / width * 1000));

        java.util.Hashtable<Integer, JLabel> labels = new java.util.Hashtable<>(3);
        labels.put(0, new JLabel(c.getMinLabel()));
        labels.put(500, new JLabel(c.getMidLabel()));
        labels.put(1000, new JLabel(c.getMaxLabel()));
        s.setLabelTable(labels);
        s.setPaintLabels(true);

        s.setBorder(new TitledBorder(c.getType().toString() + " " + c.getUnits())); //$NON-NLS-1$

        s.addChangeListener(e -> {
            int i = s.getValue();
            float f = min + (i * width / 1000.0f);
            c.setValue(f);
        });
        return s;
    }

    protected AudioData getAudioData(DicomSpecialElement media) {
        if (media instanceof DicomAudioElement) {
            DicomMediaIO dicomImageLoader = media.getMediaReader();
            Attributes attributes = dicomImageLoader.getDicomObject().getNestedDataset(Tag.WaveformSequence);
            if (attributes != null) {
                VR.Holder holder = new VR.Holder();
                Object data = attributes.getValue(Tag.WaveformData, holder);
                if (data instanceof BulkData) {
                    BulkData bulkData = (BulkData) data;
                    try {
                        int numChannels = attributes.getInt(Tag.NumberOfWaveformChannels, 0);
                        double sampleRate = attributes.getDouble(Tag.SamplingFrequency, 0.0);
                        int bitsPerSample = attributes.getInt(Tag.WaveformBitsAllocated, 0);
                        String spInterpretation = attributes.getString(Tag.WaveformSampleInterpretation, 0);

                        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.10.9.html
                        // SB: signed 8 bit linear
                        // UB: unsigned 8 bit linear
                        // MB: 8 bit mu-law (in accordance with ITU-T Recommendation G.711)
                        // AB: 8 bit A-law (in accordance with ITU-T Recommendation G.711)
                        // SS: signed 16 bit linear
                        // US: unsigned 16 bit linear

                        AudioFormat audioFormat;
                        if ("MB".equals(spInterpretation) || "AB".equals(spInterpretation)) { //$NON-NLS-1$ //$NON-NLS-2$
                            int frameSize =
                                (numChannels == AudioSystem.NOT_SPECIFIED || bitsPerSample == AudioSystem.NOT_SPECIFIED)
                                    ? AudioSystem.NOT_SPECIFIED : ((bitsPerSample + 7) / 8) * numChannels;
                            audioFormat = new AudioFormat("AB".equals(spInterpretation) ? Encoding.ALAW : Encoding.ULAW, //$NON-NLS-1$
                                (float) sampleRate, bitsPerSample, numChannels, frameSize, (float) sampleRate,
                                attributes.bigEndian());
                        } else {
                            boolean signed =
                                "UB".equals(spInterpretation) || "US".equals(spInterpretation) ? false : true; //$NON-NLS-1$ //$NON-NLS-2$
                            audioFormat = new AudioFormat((float) sampleRate, bitsPerSample, numChannels, signed,
                                attributes.bigEndian());
                        }
                        return new AudioData(bulkData, audioFormat);
                    } catch (Exception e) {
                        LOGGER.error("Get audio stream", e); //$NON-NLS-1$
                    }
                }
            }
        }
        return null;
    }

    static class AudioData {
        final BulkData bulkData;
        final AudioFormat audioFormat;

        public AudioData(BulkData bulkData, AudioFormat audioFormat) {
            this.bulkData = bulkData;
            this.audioFormat = audioFormat;
        }
    }

    public static void playSound(AudioInputStream audioStream, AudioFormat audioFormat) {
        if (audioStream != null && audioFormat != null) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            SourceDataLine sourceLine = null;
            try {
                sourceLine = (SourceDataLine) AudioSystem.getLine(info);
                sourceLine.open(audioFormat);

                sourceLine.start();

                byte[] bytesBuffer = new byte[8192];
                int bytesRead = -1;

                while ((bytesRead = audioStream.read(bytesBuffer)) != -1) {
                    sourceLine.write(bytesBuffer, 0, bytesRead);
                }
            } catch (Exception e) {
                LOGGER.error("Play audio stream", e); //$NON-NLS-1$
            } finally {
                if (sourceLine != null) {
                    sourceLine.drain();
                    sourceLine.close();
                }
            }
        }
    }

}
