[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://opensource.org/licenses/EPL-2.0) [![Build Status](https://travis-ci.com/nroduit/Weasis.svg?branch=master)](https://travis-ci.com/nroduit/Weasis)   
[![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-framework&metric=ncloc)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-framework) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-framework&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-framework) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-framework&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-framework) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-framework&metric=security_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-framework) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-framework&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.weasis%3Aweasis-framework)    

Weasis is a free medical DICOM viewer used in healthcare by hospitals, health networks, multicenter research trials, and patients.

![Weasis](weasis-distributions/resources/images/about.png)

* [General information](https://nroduit.github.io)
* [Live Demo with different datasets](https://nroduit.github.io/en/demo)
* [Download binary releases](https://nroduit.github.io/en/getting-started)
* [Issues](https://github.com/nroduit/Weasis/issues) ([Old Issue Tracker](https://dcm4che.atlassian.net/projects/WEA))

# Build Weasis

The master branch contains Weasis 3.x.x (requires Java 8+) and the old branches are 2.5.x, 2.0.x (Java 6+) and 1.2.x (Java 6+).

See [How to build Weasis](https://nroduit.github.io/en/getting-started/building-weasis)

# [Release History](CHANGELOG.md)

# General Features
* Flexible integration to HIS or PHR (see [integration documentation](https://nroduit.github.io/en/basics/customize/integration/))
* Desktop distribution (Windows, Mac OS X, and Linux)
* Web access through [weasis protocol](https://nroduit.github.io/en/getting-started/weasis-protocol)
* Embedded DICOM viewer (portable distribution) in CD/DVD or other portable media
* [Multi-language support](https://nroduit.github.io/en/getting-started/translating/)
* [Configuration of preferences](https://nroduit.github.io/en/basics/customize/preferences/) on server-side and client-side
* [API for building custom plug-ins](https://nroduit.github.io/en/basics/customize/build-plugins/)
* DICOM Send (storeSCU and STOW-RS)
* DICOM Query/Retrieve (C-GET, C-MOVE and WADO-URI) and DICOMWeb (QUERY and RETRIEVE)
* Dicomizer module (allow importing standard images and convert them in DICOM)

# Viewer Features
![screenshot](https://user-images.githubusercontent.com/993975/39397039-2180c178-4af9-11e8-9c72-2c1e9aa16eae.jpg)     
  
* Display all kinds of DICOM files (including multi-frame, enhanced, MPEG-2, MPEG-4, MIME Encapsulation, SR, PR, KOS, AU, RT and ECG)
* Viewer for common image formats (TIFF, BMP, GIF, JPEG, PNG, RAS, HDR, and PNM)
* Image manipulation (pan, zoom, windowing, presets, rotation, flip, scroll, crosshair, filtering...)
* Layouts for comparing series or studies
* Advanced series synchronization options
* Display Presentation States (GSPS) and Key Object Selection
* Create key images (Key Object Selection object) by selection
* Support of Modality LUTs, VOI LUTs, and Presentation LUTs (even non-linear)
* Support of several screens with different calibration, support of HiDPI (High Dots Per Inch) monitors, full-screen mode
* Multiplanar reconstructions and Maximum Intensity Projection
* Display Structured Reports
* Display and search into all DICOM attributes
* Display cross-lines
* Measurement and annotation tools
* Region statistics of pixels (Min, Max, Mean, StDev, Skewness, Kurtosis, Entropy)
* Histogram of modality values
* SUV measurement
* Save measurements and annotations in DICOM PR or XML file
* Import CD/DVD and local DICOM files
* Export DICOM with several options (DICOMDIR, ZIP, ISO image file with Weasis, TIFF, JPEG, PNG...)
* Magnifier glass
* Native and DICOM printing
* Read DICOM image containing float or double data (Parametric Map)
* DICOM ECG Viewer
