package org.weasis.acquire.explorer.gui.control;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquisitionView;
import org.weasis.acquire.explorer.core.ItemList;
import org.weasis.acquire.explorer.gui.model.actions.ChangePathSelectionAction;
import org.weasis.acquire.explorer.gui.model.list.ItemListComboBoxModel;
import org.weasis.acquire.explorer.gui.model.renderer.MediaSourceListCellRenderer;
import org.weasis.acquire.explorer.media.FileSystemDrive;
import org.weasis.acquire.explorer.media.MediaSource;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FontTools;

import net.samuelcampos.usbdrivedectector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedectector.USBStorageDevice;
import net.samuelcampos.usbdrivedectector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedectector.events.USBStorageEvent;

@SuppressWarnings("serial")
public class BrowsePanel extends JPanel implements IUSBDriveListener {
    private static final String USER_HOME = System.getProperty("user.home");

    protected static final Logger LOGGER = LoggerFactory.getLogger(BrowsePanel.class);

    private final AcquisitionView mainView;
    private final ItemList<MediaSource> mediaSourceList = new ItemList<>();
    private final ItemListComboBoxModel<MediaSource> mediaSourceListComboModel;
    private final JComboBox<MediaSource> mediaSourceSelectionCombo = new JComboBox<>();
    private final USBDeviceDetectorManager driveDetector = new USBDeviceDetectorManager();

    public BrowsePanel(AcquisitionView acquisitionView) {
        this.mainView = acquisitionView;
        try {
            String last = iniLastPath();
            mainView.setSystemDrive(new FileSystemDrive(last));
            mediaSourceList.addItem(mainView.getSystemDrive());
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }

        driveDetector.addDriveListener(this);

        mediaSourceListComboModel = new ItemListComboBoxModel<>(mediaSourceList);
        GridBagLayout gridBagLayout = new GridBagLayout();
        setLayout(gridBagLayout);
        mediaSourceSelectionCombo.setModel(mediaSourceListComboModel);
        mediaSourceSelectionCombo.setRenderer(new MediaSourceListCellRenderer(mediaSourceSelectionCombo));
        mediaSourceSelectionCombo.setMaximumRowCount(10);
        mediaSourceSelectionCombo.setFont(FontTools.getFont11());
        mediaSourceSelectionCombo.addActionListener(e -> {
            acquisitionView.setSystemDrive((FileSystemDrive) mediaSourceSelectionCombo.getSelectedItem());
            acquisitionView.loadSystemDrive();
        });

        // Update UI before adding the Tooltip feature in the combobox list
        mediaSourceSelectionCombo.updateUI();
        JMVUtils.addTooltipToComboList(mediaSourceSelectionCombo);

        GridBagConstraints gbcMediaSourceSelectionCombo = new GridBagConstraints();
        gbcMediaSourceSelectionCombo.fill = GridBagConstraints.HORIZONTAL;
        gbcMediaSourceSelectionCombo.weightx = 1.0;
        gbcMediaSourceSelectionCombo.anchor = GridBagConstraints.NORTHWEST;
        gbcMediaSourceSelectionCombo.insets = new Insets(5, 5, 5, 5);
        gbcMediaSourceSelectionCombo.gridx = 0;
        gbcMediaSourceSelectionCombo.gridy = 0;
        add(mediaSourceSelectionCombo, gbcMediaSourceSelectionCombo);

        final JButton pathSelectionBtn = new JButton(new ChangePathSelectionAction(mainView));
        pathSelectionBtn.setFont(FontTools.getFont11());
        GridBagConstraints gbcPathSelectionBtn = new GridBagConstraints();
        gbcPathSelectionBtn.insets = new Insets(5, 5, 5, 5);
        gbcPathSelectionBtn.anchor = GridBagConstraints.NORTHWEST;
        gbcPathSelectionBtn.gridx = 1;
        gbcPathSelectionBtn.gridy = 0;
        add(pathSelectionBtn, gbcPathSelectionBtn);

        // Allow combo to limit the size with long path
        JMVUtils.setPreferredWidth(mediaSourceSelectionCombo,
            mediaSourceSelectionCombo.getPreferredSize().width - pathSelectionBtn.getPreferredSize().width - 5);
    }

    private String iniLastPath() {

        File prefDir;
        Preferences prefs =
            BundlePreferences.getDefaultPreferences(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
        if (prefs == null) {
            prefDir = new File(USER_HOME); // $NON-NLS-1$
        } else {
            Preferences p = prefs.node(AcquisitionView.PREFERENCE_NODE);
            prefDir = new File(p.get(AcquisitionView.P_LAST_DIR, USER_HOME)); // $NON-NLS-1$
        }

        if (prefDir.canRead() && prefDir.isDirectory()) {
            return prefDir.getPath();
        }
        return USER_HOME;
    }

    public JComboBox<MediaSource> getMediaSourceSelectionCombo() {
        return mediaSourceSelectionCombo;
    }

    public ItemList<MediaSource> getMediaSourceList() {
        return mediaSourceList;
    }

    @Override
    public void usbDriveEvent(USBStorageEvent event) {
        LOGGER.debug(event.toString());

        switch (event.getEventType()) {
            case CONNECTED:
                addUsbDevice(event.getStorageDevice());
                break;
            case REMOVED:
                removeUsbDevice(event.getStorageDevice());
                break;
            default:
                break;
        }
    }

    private void addUsbDevice(USBStorageDevice storageDevice) {
        mediaSourceList.addItem(new FileSystemDrive(storageDevice.getRootDirectory().getPath()));
    }

    private void removeUsbDevice(USBStorageDevice storageDevice) {
        String id = storageDevice.getRootDirectory().getPath();

        mediaSourceList.getList().stream().filter(m -> m.getID().equals(id)).findFirst()
            .ifPresent(mediaSourceList::removeItem);
        if (!mediaSourceList.isEmpty()) {
            mediaSourceSelectionCombo.setSelectedIndex(0);
        }
    }
}
