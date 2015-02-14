![Weasis](weasis-distributions/resources/images/about.png)

The master branch contains Weasis 2. The old branch is 1.2.x.

[General information](http://www.dcm4che.org/confluence/display/WEA/Home)

[How to build Weasis](http://www.dcm4che.org/confluence/display/WEA/Building+Weasis+from+source)

[Issue Tracker](http://www.dcm4che.org/jira/browse/WEA)

## Weasis 2.0 ##

### General Features: ###
* Flexible integration to HIS or PHR (see [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector))
* Web based distribution ([embedded in a web page](https://github.com/nroduit/weasis-jnlp-distributions) or launch from an external window)
* Desktop portable distribution (Windows, Mac OS X and Linux)
* Embedded DICOM viewer (portable distribution) in CD/DVD or other portable media
* Can be configured with very low memory footprint. Do not require recent hardware.
* [Multi-language support](https://www.transifex.com/projects/p/weasis/)
* [Configuration of preferences](http://www.dcm4che.org/confluence/display/WEA/Weasis+Preferences) on server-side and client-side
* [API for building custom plug-ins](http://www.dcm4che.org/confluence/display/WEA/How+to+build+and+install+a+plug-in)

### Viewer Features: ###
* Display all kinds of DICOM files (including multiframe, enhanced, MPEG-2, MPEG-4, MIME Encapsulation, SR, PR, KOS, ECG and AU)
* Viewer for common image formats (TIFF, BMP, FlashPiX, GIF, JPEG, PNG, and PNM)
* Image manipulation (pan, zoom, windowing, presets, rotation, flip, scroll, crosshair, filtering...)
* Optimal performance for handling large images since there is no need to load the whole image data in memory at once (Uncompressed images, tiled TIFF, tiled jpeg2000 and tiled FlashPiX).
* Layouts for comparing series or studies
* Advanced series synchronization options
* Display Presentation States (GSPS) and Key Object Selection
* Create key images (Key Object Selection object)
* Support of Modality LUTs and VOI LUTs (even non-linear)
* Support of several screens, full-screen mode.
* Multiplanar reconstructions and Maximum Intensity Projection
* Display Structured Reports
* Display cross-lines
* Measurement and annotation tools
* SUV measurement
* Region statistics of pixels (Min, Max, Mean, StDev, Skewness, Kurtosis)
* Save measurements and annotations
* Import CD/DVD and local DICOM files
* Export DICOM with several options (DICOMDIR, ZIP, Anonymized, ISO image file with Weasis, TIFF, JPEG, PNG, HTML...)
* Magnifier glass
* Native and DICOM printing
