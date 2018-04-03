![Weasis](weasis-distributions/resources/images/about.png)

The master branch contains Weasis 2.5.x (requires Java 8+) and the old branches are 2.0.x and 1.2.x (both require Java 6+).

[![CircleCI](https://circleci.com/gh/nroduit/Weasis.svg?style=svg&circle-token=000f0e7422ddff1a5351499010c9075f18e55522)](https://circleci.com/gh/nroduit/Weasis) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/50bdcb5275a84a9186a8e5d9d9d1b81d)](https://www.codacy.com/app/nicolas.roduit/Weasis?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=nroduit/Weasis&amp;utm_campaign=Badge_Grade)

[General information](https://nroduit.github.io)

[Live Demo with different datasets](https://nroduit.github.io/en/demo)

[Download binary releases](http://sourceforge.net/projects/dcm4che/files/Weasis)

[How to build Weasis](https://nroduit.github.io/en/getting-started/building-weasis)

[Issue Tracker](http://www.dcm4che.org/jira/browse/WEA)


### New features in Weasis 2.5 ###
* Embedded new codecs compiled for Windows 32/64-bit, Linux x86 32/64-bit and Mac OS X 64-bit.    
	* jpeg-baseline, jpeg-extended and jpeg-lossless (IJG 6b)   
	* jpeg-ls (CharLS 1.1.0)   
	* jpeg2000 codecs (OpegJPEG 2.1.2)   
* Allows to order the codecs by priority with a unique configuration for all the systems
* Supports multi-frame and multiple fragments at the same time
* DICOM send module (DICOM node configuration at the server side and/or locally)
* Export DICOM KO build in Weasis
* Export measurements and graphics into a DICOM PR or xml file
* DICOMIZER module
* Configuration in resources for series splitting rules and for displaying DICOM Attributes on the image
* Allows in preferences to adapt the stacktrace limit of the logger
* Exporting non DICOM files will allow to extract files from DICOM encapsulated document and DICOM video
* Minimal annotations mode available in Display tool
* Reading Presentation State improvement for overlay and shutter
* Apply Presentation LUT sequence
* Read DICOM image containing float or double data
* Requires Java 8
* See [JIRA Release Note](http://www.dcm4che.org/jira/secure/ReleaseNote.jspa?projectId=10090&version=11282)

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
* Display all kinds of DICOM files (including multiframe, enhanced, MPEG-2, MPEG-4, MIME Encapsulation, SR, PR, KOS and AU)
* Viewer for common image formats (TIFF, BMP, FlashPiX, GIF, JPEG, PNG, and PNM)
* Image manipulation (pan, zoom, windowing, presets, rotation, flip, scroll, crosshair, filtering...)
* Optimal performance for handling large images since there is no need to load the whole image data in memory at once (Uncompressed images, tiled TIFF, tiled jpeg2000 and tiled FlashPiX).
* Layouts for comparing series or studies
* Advanced series synchronization options
* Display Presentation States (GSPS) and Key Object Selection
* Create key images (Key Object Selection object)
* Support of Modality LUTs, VOI LUTs Presentation LUTs (even non-linear)
* Support of several screens, full-screen mode.
* Multiplanar reconstructions and Maximum Intensity Projection
* Display Structured Reports
* Display cross-lines
* Measurement and annotation tools
* Region statistics of pixels (Min, Max, Mean, StDev, Skewness, Kurtosis)
* SUV measurement
* Save measurements and annotations in DICOM PR or xml file
* Import CD/DVD and local DICOM files
* Export DICOM with several options (DICOMDIR, ZIP, ISO image file with Weasis, TIFF, JPEG, PNG...)
* Magnifier glass
* Native and DICOM printing

## Build weasis ##

Prerequisites: JDK 8 and Maven 3

See the instructions [here](http://www.dcm4che.org/confluence/display/WEA/Building+Weasis+from+source)

The snapshot version may require to build first other snapshot dependencies:   
1. [weasis-dicom-tools](https://github.com/nroduit/weasis-dicom-tools) run "mvn clean install"
1. In weasis root directory, run "mvn clean install"