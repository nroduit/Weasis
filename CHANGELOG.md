# Changelog

## [v4.0.0-rc](https://github.com/nroduit/Weasis/tree/v4.0.0-rc) (2022-04-24)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.8.1...v4.0.0-rc)

**Implemented enhancements:**

- Settings for opening new tab when importing images from local devices [\#259](https://github.com/nroduit/Weasis/issues/259)
- Exporting data: Improve exporting image rendering  [\#255](https://github.com/nroduit/Weasis/issues/255)
- Exporting data: Improve DICOM export \(generate new UIDs, transcoding\) [\#254](https://github.com/nroduit/Weasis/issues/254)
- Replace Substance Look and Feel by FlatLaf [\#253](https://github.com/nroduit/Weasis/issues/253)
- Move to Java 17 \(runtime and language specifications\) [\#252](https://github.com/nroduit/Weasis/issues/252)
- New API for image rendering directly based on OpenCV  [\#250](https://github.com/nroduit/Weasis/issues/250)
- Save the search criteria in DICOM Q/R [\#237](https://github.com/nroduit/Weasis/issues/237)
- Bad render of specific instances with encapsulated transfer syntax [\#226](https://github.com/nroduit/Weasis/issues/226)
- Apple silicon support - M1 \(arm\) processor for weasis-opencv on macOS [\#212](https://github.com/nroduit/Weasis/issues/212)

**Closed issues:**

- Removal of Java WebStart  [\#251](https://github.com/nroduit/Weasis/issues/251)

## [v3.8.1](https://github.com/nroduit/Weasis/tree/v3.8.1) (2022-02-03)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.8.0...v3.8.1)

**Implemented enhancements:**

- Improve the automatic layout choice when opening several series [\#242](https://github.com/nroduit/Weasis/issues/242)
- Update to dcm4che 5.25.1 [\#241](https://github.com/nroduit/Weasis/issues/241)
- Add new default layouts for panoramic screens and MPR [\#229](https://github.com/nroduit/Weasis/issues/229)

**Fixed bugs:**

- Dicomizer: picker date do not commit the value when the focus is lost [\#240](https://github.com/nroduit/Weasis/issues/240)
- Cannot display image with invalid shutter [\#236](https://github.com/nroduit/Weasis/issues/236)
- Support measurement computations across Ultrasound regions with different scale factors [\#227](https://github.com/nroduit/Weasis/issues/227)

**Closed issues:**

- Building on aarch64: is it supposed to work? [\#234](https://github.com/nroduit/Weasis/issues/234)
- Weasis icon at higher resolutions [\#233](https://github.com/nroduit/Weasis/issues/233)

## [v3.8.0](https://github.com/nroduit/Weasis/tree/v3.8.0) (2021-12-11)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.7.1...v3.8.0)

**Implemented enhancements:**

- Improve photo editor in Dicomizer [\#210](https://github.com/nroduit/Weasis/issues/210)
- Allow DICOMWeb OAuth2 authentication with STOW-RS [\#209](https://github.com/nroduit/Weasis/issues/209)
- Separate "drawing tools" and "measurement tools" \(chnage drawing icon\) [\#206](https://github.com/nroduit/Weasis/issues/206)
- Display order of "split series" [\#205](https://github.com/nroduit/Weasis/issues/205)
- Display View Position for mammogram [\#203](https://github.com/nroduit/Weasis/issues/203)
- Rotating ellipse feature [\#194](https://github.com/nroduit/Weasis/issues/194)

**Fixed bugs:**

- US images are stretched [\#217](https://github.com/nroduit/Weasis/issues/217)
- WADO retrieve without authentication combined with C-FIND does not work \(regression\) [\#200](https://github.com/nroduit/Weasis/issues/200)
- Deflate syntax is not supported for raw image and encapsulated document [\#197](https://github.com/nroduit/Weasis/issues/197)
- JPEG-LS with YBR\_FULL color model is not supported [\#196](https://github.com/nroduit/Weasis/issues/196)

## [v3.7.1](https://github.com/nroduit/Weasis/tree/v3.7.1) (2021-06-06)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.7.0...v3.7.1)

**Implemented enhancements:**

- Better management of inconsistent UIDs between the manifest and the file [\#186](https://github.com/nroduit/Weasis/issues/186)
- Support service account auth for Cloud Healthcare API integration [\#125](https://github.com/nroduit/Weasis/issues/125)

**Fixed bugs:**

- Trailing whitespace in AE title of DICOM node [\#192](https://github.com/nroduit/Weasis/issues/192)
- Uppercase DCM Files cannot be imported [\#190](https://github.com/nroduit/Weasis/issues/190)
- GSPS object is not rendered properly [\#181](https://github.com/nroduit/Weasis/issues/181)
- Weasis 3.7 fails to register the weasis:// protocol on macOS [\#180](https://github.com/nroduit/Weasis/issues/180)
- Sorting channel issue in ECG [\#178](https://github.com/nroduit/Weasis/issues/178)
- On Windows the Dicomizer exe launches the viewer  [\#176](https://github.com/nroduit/Weasis/issues/176)
- Rebuilding thumbnail doesn't work [\#174](https://github.com/nroduit/Weasis/issues/174)

## [v3.7.0](https://github.com/nroduit/Weasis/tree/v3.7.0) (2021-02-02)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.6.2...v3.7.0)

**Implemented enhancements:**

- Handle spatial calibration of US with multiple regions \(only when a single region in cm is present\) [\#169](https://github.com/nroduit/Weasis/issues/169)
- Update to weasis-dicom-tools 5.23.0.1 [\#167](https://github.com/nroduit/Weasis/issues/167)
- Update to OpenCV 4.5.0 [\#166](https://github.com/nroduit/Weasis/issues/166)
- Make the STOW-RS URL compatible with the DICOMWeb API [\#164](https://github.com/nroduit/Weasis/issues/164)
- Add DICOMWeb support in DICOM Query/Retrieve dialog [\#161](https://github.com/nroduit/Weasis/issues/161)
- Add graphic object opacity of DICOM Presentation State [\#153](https://github.com/nroduit/Weasis/issues/153)

**Fixed bugs:**

- Fix overriding weasis.resources.url in VM option or in command $weasis:config [\#165](https://github.com/nroduit/Weasis/issues/165)
- Raw RGB images encoded in bigendian are not rendered correctly [\#154](https://github.com/nroduit/Weasis/issues/154)

**Merged pull requests:**

- loadRemotePref [\#171](https://github.com/nroduit/Weasis/pull/171) ([bjacquemoud](https://github.com/bjacquemoud))

## [v3.6.2](https://github.com/nroduit/Weasis/tree/v3.6.2) (2020-08-26)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.6.1-3D...v3.6.2)

**Implemented enhancements:**

- Weasis shows green hues instead of black in JPEG-compressed Ultrasound images [\#140](https://github.com/nroduit/Weasis/issues/140)

**Fixed bugs:**

- Cannot read signed data with PaletteColor [\#148](https://github.com/nroduit/Weasis/issues/148)
- DICOM Print: warning DICOM status is treated as an error [\#147](https://github.com/nroduit/Weasis/issues/147)
- Cannot start MPR for series containing  ISO 2022 IR 149 charset [\#144](https://github.com/nroduit/Weasis/issues/144)
- Not all colors are displayed correctly in DICOM PR graphics [\#143](https://github.com/nroduit/Weasis/issues/143)
- Cannot add http headers when creating a new WEB Node [\#142](https://github.com/nroduit/Weasis/issues/142)
- Poor image display with a more than 100% factor in the Window 10 display settings. [\#141](https://github.com/nroduit/Weasis/issues/141)
- Cannot retrieve thumbnail with WADO-URI request [\#139](https://github.com/nroduit/Weasis/issues/139)

## [v3.6.1-3D](https://github.com/nroduit/Weasis/tree/v3.6.1-3D) (2020-07-14)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.6.0-3D...v3.6.1-3D)

## [v3.6.0-3D](https://github.com/nroduit/Weasis/tree/v3.6.0-3D) (2020-07-14)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.6.1...v3.6.0-3D)

## [v3.6.1](https://github.com/nroduit/Weasis/tree/v3.6.1) (2020-07-03)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.6.0...v3.6.1)

**Implemented enhancements:**

- Use weasis-core-img 4.3.0 and remove jai\_imageio dependency [\#135](https://github.com/nroduit/Weasis/issues/135)
- Update radiance to 3.0.0 for the native distribution [\#134](https://github.com/nroduit/Weasis/issues/134)
- Remembering Window position and size [\#123](https://github.com/nroduit/Weasis/issues/123)

**Fixed bugs:**

- Fix applying VOI Lut with a Presentation LUT and when there is no lookup table [\#133](https://github.com/nroduit/Weasis/issues/133)
- Fix patientid containing special characters in dicomweb command [\#132](https://github.com/nroduit/Weasis/issues/132)
- Command for loading DICOM locally doesn't work on Windows [\#128](https://github.com/nroduit/Weasis/issues/128)
- Opening files with Polish diacritic signs doesn't work on Windows [\#126](https://github.com/nroduit/Weasis/issues/126)
- Chinese charset exception when reading dataset [\#119](https://github.com/nroduit/Weasis/issues/119)
- Weasis 3.5.4 crashes on OS X 10.15.2 [\#116](https://github.com/nroduit/Weasis/issues/116)

## [v3.6.0](https://github.com/nroduit/Weasis/tree/v3.6.0) (2020-02-21)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.6.0-rc1...v3.6.0)

**Implemented enhancements:**

- Define a minimal local version for loading the remote plug-ins [\#113](https://github.com/nroduit/Weasis/issues/113)

**Fixed bugs:**

- Remove pack200 compression in Java 14 \(replace by xz for plug-in files\) [\#117](https://github.com/nroduit/Weasis/issues/117)

## [v3.6.0-rc1](https://github.com/nroduit/Weasis/tree/v3.6.0-rc1) (2020-02-03)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.5.4...v3.6.0-rc1)

**Implemented enhancements:**

- Always set the Dose Report series at the end when sorting series [\#110](https://github.com/nroduit/Weasis/issues/110)

**Fixed bugs:**

- Cannot display 16-bit image with DICOM PALETTE\_COLOR [\#112](https://github.com/nroduit/Weasis/issues/112)
- Mouse cursor is moving on the Weasis window \(when already open\) [\#108](https://github.com/nroduit/Weasis/issues/108)
- NPE when loading a manifest [\#107](https://github.com/nroduit/Weasis/issues/107)
- Issue of sorting series order [\#105](https://github.com/nroduit/Weasis/issues/105)

## [v3.5.4](https://github.com/nroduit/Weasis/tree/v3.5.4) (2019-11-01)

[Full Changelog](https://github.com/nroduit/Weasis/compare/3.5.4-rc...v3.5.4)

**Implemented enhancements:**

- DICOMWeb query/retrieve with a new command "dicom:rs" [\#102](https://github.com/nroduit/Weasis/issues/102)

**Fixed bugs:**

- Some menus are freezing [\#104](https://github.com/nroduit/Weasis/issues/104)
- The language modules are not load from the server side [\#101](https://github.com/nroduit/Weasis/issues/101)
- Http redirection not working when downloading \(regression\) [\#100](https://github.com/nroduit/Weasis/issues/100)
- Fix covariant return types in ByteBuffer introduced in Java 9 [\#99](https://github.com/nroduit/Weasis/issues/99)
- HTTPS download error: handshake\_failure  [\#98](https://github.com/nroduit/Weasis/issues/98)
- Images blanking out randomly [\#43](https://github.com/nroduit/Weasis/issues/43)

## [3.5.4-rc](https://github.com/nroduit/Weasis/tree/3.5.4-rc) (2019-09-27)

[Full Changelog](https://github.com/nroduit/Weasis/compare/v3.5.3...3.5.4-rc)

## [v3.5.3](https://github.com/nroduit/weasis/tree/v3.5.3) (2019-08-09)

[Full Changelog](https://github.com/nroduit/weasis/compare/v3.5.1...v3.5.3)

**Implemented enhancements:**

- Configuration of proxy server in GUI [\#91](https://github.com/nroduit/Weasis/issues/91) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Weasis binaries does not launch in MacOS Mojave [\#85](https://github.com/nroduit/Weasis/issues/85) [[Type: Security](https://github.com/nroduit/Weasis/labels/Type:%20Security)]
- DICOM send is not able to transcode when the receiver has limited syntaxes [\#82](https://github.com/nroduit/Weasis/issues/82)
- Add median, skewness, kurtosis and entropy for statistics of an area [\#79](https://github.com/nroduit/Weasis/issues/79)
- Supports modality LUT \(table\) when displaying LUT values [\#77](https://github.com/nroduit/Weasis/issues/77)
- Allow to add custom HTTP headers for DICOMWeb nodes [\#74](https://github.com/nroduit/Weasis/issues/74) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Histogram view [\#73](https://github.com/nroduit/Weasis/issues/73) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
-  API for getting a remote configuration at startup \(profile, user, properties...\) [\#66](https://github.com/nroduit/Weasis/issues/66) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- API to handle preferences remotely [\#65](https://github.com/nroduit/Weasis/issues/65) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- HiDPI support [\#64](https://github.com/nroduit/Weasis/issues/64) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Package for installing on Windows, Mac and Linux [\#57](https://github.com/nroduit/Weasis/issues/57) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]

**Fixed bugs:**

- Issues on images with BitsAllocated \(0028,0100\) set to 16 and BitsStored \(0028,0101\) set to 8 [\#94](https://github.com/nroduit/Weasis/issues/94)
- .deb Linux package crash: java.lang.IllegalArgumentException: invalid version [\#93](https://github.com/nroduit/Weasis/issues/93)
- Render issue with MeasurementUnitsCodeSequence in DICOM SR [\#90](https://github.com/nroduit/Weasis/issues/90)
- Issue when reseting a Presentation State [\#87](https://github.com/nroduit/Weasis/issues/87)
- Cannot change W/L when a Presentation LUT is applied [\#86](https://github.com/nroduit/Weasis/issues/86)
- Cannot export DICOM PDF and Video [\#84](https://github.com/nroduit/Weasis/issues/84)
- Cannot render DICOM SR [\#83](https://github.com/nroduit/Weasis/issues/83)
- Exporting jpeg images doesn't work with some patient names [\#81](https://github.com/nroduit/Weasis/issues/81)
- Measurement statistics doesn't use modality LUT \(table\) [\#78](https://github.com/nroduit/Weasis/issues/78)
- Do not apply LUT shape when displaying LUT \(only linear\) [\#76](https://github.com/nroduit/Weasis/issues/76)
- Some numbers doesn't apply the regional format selected in the preferences [\#75](https://github.com/nroduit/Weasis/issues/75)
- The displayed image becomes black after exporting series in jpeg or png [\#72](https://github.com/nroduit/Weasis/issues/72)
- Cannot write MPR images on Windows when the path contain non-ASCII characters [\#69](https://github.com/nroduit/Weasis/issues/69)

## [v3.5.1](https://github.com/nroduit/weasis/tree/v3.5.1) (2019-03-24)

[Full Changelog](https://github.com/nroduit/weasis/compare/3.0.4...v3.5.1)

**Implemented enhancements:**

- The same name of the system process for every platform [\#67](https://github.com/nroduit/Weasis/issues/67)
- A single instance mechanism for all the launch context [\#63](https://github.com/nroduit/Weasis/issues/63) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Allow to enable app sandbox on Mac OS X [\#62](https://github.com/nroduit/Weasis/issues/62)
- Integration of the Mac OS X application menu [\#61](https://github.com/nroduit/Weasis/issues/61) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Improves the traces of the launcher module [\#60](https://github.com/nroduit/Weasis/issues/60)
- Register the DICOM mime type for file association \(for windows .dcm extension\) [\#59](https://github.com/nroduit/Weasis/issues/59) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Register an URI handler for the weasis and weasis-dicomizer scheme [\#58](https://github.com/nroduit/Weasis/issues/58) [[Type: Feature](https://github.com/nroduit/Weasis/labels/Type:%20Feature)]
- Supporting from Java 8 to Java 13 Runtime [\#56](https://github.com/nroduit/Weasis/issues/56)
- Allow to embed JRE for Linux and Mac [\#38](https://github.com/nroduit/Weasis/issues/38) [[Priority: Medium](https://github.com/nroduit/Weasis/labels/Priority:%20Medium)]

**Fixed bugs:**

- 3D Stl file Open Does not work [\#68](https://github.com/nroduit/Weasis/issues/68)
- Open image menu or toolbar should not appear on Dicomizer [\#55](https://github.com/nroduit/Weasis/issues/55)
- W/L issue with MR containing Modality LUT values [\#52](https://github.com/nroduit/Weasis/issues/52) [[Priority: High](https://github.com/nroduit/Weasis/labels/Priority:%20High)]

## [3.0.4](https://github.com/nroduit/weasis/tree/3.0.4) (2018-12-20)

[Full Changelog](https://github.com/nroduit/weasis/compare/3.0.3...3.0.4)

**Implemented enhancements:**

- Allows configuring HTTP timeout of WADO through properties [\#50](https://github.com/nroduit/Weasis/issues/50)
- Cannot display color image with 16-bit per channel [\#49](https://github.com/nroduit/Weasis/issues/49)
- Http redirection not supported when downloading images [\#46](https://github.com/nroduit/Weasis/issues/46) [[Priority: Low](https://github.com/nroduit/Weasis/labels/Priority:%20Low)]
- HTTP read timeout when downloading manifest or images [\#45](https://github.com/nroduit/Weasis/issues/45)

**Fixed bugs:**

- Duplicate presets with DICOM PR and CT [\#48](https://github.com/nroduit/Weasis/issues/48)
- Selected graphic does not get updated [\#44](https://github.com/nroduit/Weasis/issues/44) [[Priority: High](https://github.com/nroduit/Weasis/labels/Priority:%20High)]

**Merged pull requests:**

- Extend GUI for plan/dose selection [\#24](https://github.com/nroduit/Weasis/pull/24) ([toskrip](https://github.com/toskrip))
- Fix isodose rendering [\#23](https://github.com/nroduit/Weasis/pull/23) ([toskrip](https://github.com/toskrip))
- Fix plan initialisation and isodose rendering [\#22](https://github.com/nroduit/Weasis/pull/22) ([toskrip](https://github.com/toskrip))
- Dicom rt plugin [\#21](https://github.com/nroduit/Weasis/pull/21) ([toskrip](https://github.com/toskrip))

## [3.0.3](https://github.com/nroduit/weasis/tree/3.0.3) (2018-11-07)

[Full Changelog](https://github.com/nroduit/weasis/compare/3.0.2...3.0.3)

**Implemented enhancements:**

- Read SCOORD with a reference to an image \(DICOM SR\) [\#41](https://github.com/nroduit/Weasis/issues/41)
- Update to weasis-dicom-tools 5.14.1 [\#40](https://github.com/nroduit/Weasis/issues/40)
- Reduce default margins for standard print [\#35](https://github.com/nroduit/Weasis/issues/35) [[Priority: Low](https://github.com/nroduit/Weasis/labels/Priority:%20Low)]

**Fixed bugs:**

- In Crosshair mode releasing Ctrl do not deactivate W/L \(issue only on Windows\) [\#37](https://github.com/nroduit/Weasis/issues/37) [[Priority: Medium](https://github.com/nroduit/Weasis/labels/Priority:%20Medium)]
- Native decoders only work with recent Linux distributions [\#36](https://github.com/nroduit/Weasis/issues/36) [[Priority: High](https://github.com/nroduit/Weasis/labels/Priority:%20High)]
- TransferSyntaxUID is duplicated when saving generated DICOM files [\#34](https://github.com/nroduit/Weasis/issues/34) [[Priority: High](https://github.com/nroduit/Weasis/labels/Priority:%20High)]
- Weasis 3.0.2 not exporting MPR reformats [\#33](https://github.com/nroduit/Weasis/issues/33) [[Priority: Medium](https://github.com/nroduit/Weasis/labels/Priority:%20Medium)]

## [3.0.2](https://github.com/nroduit/weasis/tree/3.0.2) (2018-09-17)
[Full Changelog](https://github.com/nroduit/weasis/compare/3.0.1...3.0.2)

Bug

-   [[WEA-469](https://dcm4che.atlassian.net/browse/WEA-469)] - Cannot display images with invalid dates
-   [[WEA-470](https://dcm4che.atlassian.net/browse/WEA-470)] - Cannot open several ECGs from the same patient
-   [[WEA-475](https://dcm4che.atlassian.net/browse/WEA-475)] - Cannot build thumbnails (UTF8 path issue on Windows)
-   [[WEA-476](https://dcm4che.atlassian.net/browse/WEA-476)] - Open non-DICOM image issue (UTF8 path issue on Windows)
-   [[WEA-480](https://dcm4che.atlassian.net/browse/WEA-480)] - Filter for non-DICOM images doesn't match to available image decoders
-   [[WEA-483](https://dcm4che.atlassian.net/browse/WEA-483)] - Export the selected view to the clipboard doesn't work

New Feature

-   [[WEA-474](https://dcm4che.atlassian.net/browse/WEA-474)] - Toolbar with import and export DICOM buttons
-   [[WEA-481](https://dcm4che.atlassian.net/browse/WEA-481)] - Allow to open non-DCIOM jpeg2000 images

Improvement

-   [[WEA-472](https://dcm4che.atlassian.net/browse/WEA-472)] - Update to apache felix 6.0.1
-   [[WEA-473](https://dcm4che.atlassian.net/browse/WEA-473)] - Improve DICOM STOW
-   [[WEA-477](https://dcm4che.atlassian.net/browse/WEA-477)] - Add shortcuts for rotation and flip
-   [[WEA-478](https://dcm4che.atlassian.net/browse/WEA-478)] - Update to weasis-dicom-tools 5.13.4
-   [[WEA-479](https://dcm4che.atlassian.net/browse/WEA-479)] - Allows switching from crosshair to W/L (by Ctrl + click)
-   [[WEA-482](https://dcm4che.atlassian.net/browse/WEA-482)] - Right-clicking on the main toolbar to show/hide toolbars

## [3.0.1](https://github.com/nroduit/weasis/tree/3.0.1) (2018-05-14)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.6.3...3.0.1)

Bug

-   [[WEA-458](https://dcm4che.atlassian.net/browse/WEA-458)] - Tool panels are not visible with some screen sizes
-   [[WEA-460](https://dcm4che.atlassian.net/browse/WEA-460)] - Do not update patient age when selecting another study
-   [[WEA-461](https://dcm4che.atlassian.net/browse/WEA-461)] - Cannot launch Weasis (Failed to validate certificate)
-   [[WEA-462](https://dcm4che.atlassian.net/browse/WEA-462)] - Update dcm4che to 5.13.0
-   [[WEA-464](https://dcm4che.atlassian.net/browse/WEA-464)] - Thumbnails cannot be resized
-   [[WEA-465](https://dcm4che.atlassian.net/browse/WEA-465)] - Default Color issue in PR graphics
-   [[WEA-467](https://dcm4che.atlassian.net/browse/WEA-467)] - Re-creates missing download folder when system delete it
-   [[WEA-468](https://dcm4che.atlassian.net/browse/WEA-468)] - Query/Retrieve with C-Move doesn't work (Regression)

Improvement

-   [[WEA-463](https://dcm4che.atlassian.net/browse/WEA-463)] - Display the vertical LUT from the bottom to the top
-   [[WEA-466](https://dcm4che.atlassian.net/browse/WEA-466)] - Update to substance 8.0.2

## [2.6.3](https://github.com/nroduit/weasis/tree/2.6.3) (2018-05-14)
[Full Changelog](https://github.com/nroduit/weasis/compare/3.0.0-RC1...2.6.3)
Bug

-   [[WEA-458](https://dcm4che.atlassian.net/browse/WEA-458)] - Tool panels are not visible with some screen sizes
-   [[WEA-460](https://dcm4che.atlassian.net/browse/WEA-460)] - Do not update patient age when selecting another study
-   [[WEA-461](https://dcm4che.atlassian.net/browse/WEA-461)] - Cannot launch Weasis (Failed to validate certificate)
-   [[WEA-465](https://dcm4che.atlassian.net/browse/WEA-465)] - Default Color issue in PR graphics
-   [[WEA-467](https://dcm4che.atlassian.net/browse/WEA-467)] - Re-creates missing download folder when system delete it
-   [[WEA-468](https://dcm4che.atlassian.net/browse/WEA-468)] - Query/Retrieve with C-Move doesn't work (Regression)

Improvement

-   [[WEA-463](https://dcm4che.atlassian.net/browse/WEA-463)] - Display the vertical LUT from the bottom to the top
-   [[WEA-466](https://dcm4che.atlassian.net/browse/WEA-466)] - Update to substance 8.0.2

## [3.0.0-RC1](https://github.com/nroduit/weasis/tree/3.0.0-RC1) (2018-03-02)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.6.2...3.0.0-RC1)

Bug

-   [[WEA-419](https://dcm4che.atlassian.net/browse/WEA-419)] - Selection of key images doesn't work with mulitframe
-   [[WEA-421](https://dcm4che.atlassian.net/browse/WEA-421)] - Weasis cannot start
-   [[WEA-422](https://dcm4che.atlassian.net/browse/WEA-422)] - Display issue when patient name is different from the one in the xml manifest
-   [[WEA-426](https://dcm4che.atlassian.net/browse/WEA-426)] - Several fixes for being compliant with Java 9
-   [[WEA-440](https://dcm4che.atlassian.net/browse/WEA-440)] - Cannot download images when overriding tags in the xml manifest
-   [[WEA-441](https://dcm4che.atlassian.net/browse/WEA-441)] - Cannot deploy weasis.war in dcm4chee 5.10.6
-   [[WEA-442](https://dcm4che.atlassian.net/browse/WEA-442)] - Fix JWS cache issue. Allow to use jar version in jnlp
-   [[WEA-443](https://dcm4che.atlassian.net/browse/WEA-443)] - Cannot display DICOM LUT with jpeg images
-   [[WEA-444](https://dcm4che.atlassian.net/browse/WEA-444)] - Window/Level gets infinity values with a single value image (other than 0)
-   [[WEA-445](https://dcm4che.atlassian.net/browse/WEA-445)] - Dicom tags view displays information from another view
-   [[WEA-447](https://dcm4che.atlassian.net/browse/WEA-447)] - Force replacing JPEG Baseline RGB color model into YBR_FULL_422 (fixes DICOM error of some constructors)
-   [[WEA-448](https://dcm4che.atlassian.net/browse/WEA-448)] - Fix win/level issue in a series with a full black image
-   [[WEA-451](https://dcm4che.atlassian.net/browse/WEA-451)] - Weasis portable doesn't support Java 9
-   [[WEA-454](https://dcm4che.atlassian.net/browse/WEA-454)] - Bug fix JAI_IMAGEIO_CORE-126 : Under heavy multi-threaded load, native sse2 library randomly fails and brings down the whole JVM
-   [[WEA-455](https://dcm4che.atlassian.net/browse/WEA-455)] - Bug fix JAI_IMAGEIO_CORE-189 : j2k lossy issue
-   [[WEA-456](https://dcm4che.atlassian.net/browse/WEA-456)] - Fix artefact with native jpeg2000 decoder
-   [[WEA-457](https://dcm4che.atlassian.net/browse/WEA-457)] - Fix color model issue of jpeg-ls and jpeg

New Feature

-   [[WEA-423](https://dcm4che.atlassian.net/browse/WEA-423)] - Connection of the dicomizer to a DICOM Worklist
-   [[WEA-452](https://dcm4che.atlassian.net/browse/WEA-452)] - ECG viewer
-   [[WEA-453](https://dcm4che.atlassian.net/browse/WEA-453)] - New image decoding and processing workflow based on OpenCV

Improvement

-   [[WEA-484](https://dcm4che.atlassian.net/browse/WEA-484)] - weasis-portable

## [2.6.2](https://github.com/nroduit/weasis/tree/2.6.2) (2018-03-02)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.6.1...2.6.2)

Bug

-   [[WEA-444](https://dcm4che.atlassian.net/browse/WEA-444)] - Window/Level gets infinity values with a single value image (other than 0)
-   [[WEA-445](https://dcm4che.atlassian.net/browse/WEA-445)] - Dicom tags view displays information from another view
-   [[WEA-446](https://dcm4che.atlassian.net/browse/WEA-446)] - Close Other, from tabs menu leaves selected tab without mouse functions
-   [[WEA-447](https://dcm4che.atlassian.net/browse/WEA-447)] - Force replacing JPEG Baseline RGB color model into YBR_FULL_422 (fixes DICOM error of some constructors)
-   [[WEA-448](https://dcm4che.atlassian.net/browse/WEA-448)] - Fix win/level issue in a series with a full black image
-   [[WEA-449](https://dcm4che.atlassian.net/browse/WEA-449)] - DICOM Listener with C-Move doesn't work
-   [[WEA-450](https://dcm4che.atlassian.net/browse/WEA-450)] - Graphics of DICOM PR stay visible when selected None
-   [[WEA-451](https://dcm4che.atlassian.net/browse/WEA-451)] - Weasis portable doesn't support Java 9

**Merged pull requests:**

- Re-creates missing download folder if needed. [\#29](https://github.com/nroduit/Weasis/pull/29) ([gabibau](https://github.com/gabibau))
- Fix some typos \(found by codespell\) [\#28](https://github.com/nroduit/Weasis/pull/28) ([stweil](https://github.com/stweil))
- Makes Measurements pointers customizable. [\#27](https://github.com/nroduit/Weasis/pull/27) ([gabibau](https://github.com/gabibau))
- Weasis uses PatientPseudoUID to compare different patients [\#26](https://github.com/nroduit/Weasis/pull/26) ([felipefetzer](https://github.com/felipefetzer))
- Number of instances on export checktree [\#25](https://github.com/nroduit/Weasis/pull/25) ([gabibau](https://github.com/gabibau))

## [2.6.1](https://github.com/nroduit/weasis/tree/2.6.1) (2017-12-08)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.6.0...2.6.1)

Bug

-   [[WEA-435](https://dcm4che.atlassian.net/browse/WEA-435)] - Path issue for sub-folder configuration of the portable distribution on Windows
-   [[WEA-437](https://dcm4che.atlassian.net/browse/WEA-437)] - In some cases cross-lines are not displayed
-   [[WEA-438](https://dcm4che.atlassian.net/browse/WEA-438)] - C-Move retrieve issue (cannot start DicomListener)
-   [[WEA-439](https://dcm4che.atlassian.net/browse/WEA-439)] - The LUT palette on the image is inverted
-   [[WEA-440](https://dcm4che.atlassian.net/browse/WEA-440)] - Cannot download images when overriding tags in the xml manifest
-   [[WEA-441](https://dcm4che.atlassian.net/browse/WEA-441)] - Cannot deploy weasis.war in dcm4chee 5.10.6
-   [[WEA-442](https://dcm4che.atlassian.net/browse/WEA-442)] - Fix JWS cache issue. Allow to use jar version in jnlp

Improvement

-   [[WEA-436](https://dcm4che.atlassian.net/browse/WEA-436)] - Change default encoding to ISO_IR 100 (ISO-8859-1)

## [2.6.0](https://github.com/nroduit/weasis/tree/2.6.0) (2017-09-08)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.5.3...2.6.0)

Bug

-   [[WEA-419](https://dcm4che.atlassian.net/browse/WEA-419)] - Selection of key images doesn't work with mulitframe
-   [[WEA-421](https://dcm4che.atlassian.net/browse/WEA-421)] - Weasis cannot start
-   [[WEA-422](https://dcm4che.atlassian.net/browse/WEA-422)] - Display issue when patient name is different from the one in the xml manifest
-   [[WEA-426](https://dcm4che.atlassian.net/browse/WEA-426)] - Several fixes for being compliant with Java 9
-   [[WEA-440](https://dcm4che.atlassian.net/browse/WEA-440)] - Cannot download images when overriding tags in the xml manifest
-   [[WEA-441](https://dcm4che.atlassian.net/browse/WEA-441)] - Cannot deploy weasis.war in dcm4chee 5.10.6
-   [[WEA-442](https://dcm4che.atlassian.net/browse/WEA-442)] - Fix JWS cache issue. Allow to use jar version in jnlp
-   [[WEA-443](https://dcm4che.atlassian.net/browse/WEA-443)] - Cannot display DICOM LUT with jpeg images
-   [[WEA-444](https://dcm4che.atlassian.net/browse/WEA-444)] - Window/Level gets infinity values with a single value image (other than 0)
-   [[WEA-445](https://dcm4che.atlassian.net/browse/WEA-445)] - Dicom tags view displays information from another view
-   [[WEA-447](https://dcm4che.atlassian.net/browse/WEA-447)] - Force replacing JPEG Baseline RGB color model into YBR_FULL_422 (fixes DICOM error of some constructors)
-   [[WEA-448](https://dcm4che.atlassian.net/browse/WEA-448)] - Fix win/level issue in a series with a full black image
-   [[WEA-451](https://dcm4che.atlassian.net/browse/WEA-451)] - Weasis portable doesn't support Java 9
-   [[WEA-454](https://dcm4che.atlassian.net/browse/WEA-454)] - Bug fix JAI_IMAGEIO_CORE-126 : Under heavy multi-threaded load, native sse2 library randomly fails and brings down the whole JVM
-   [[WEA-455](https://dcm4che.atlassian.net/browse/WEA-455)] - Bug fix JAI_IMAGEIO_CORE-189 : j2k lossy issue
-   [[WEA-456](https://dcm4che.atlassian.net/browse/WEA-456)] - Fix artefact with native jpeg2000 decoder
-   [[WEA-457](https://dcm4che.atlassian.net/browse/WEA-457)] - Fix color model issue of jpeg-ls and jpeg

New Feature

-   [[WEA-423](https://dcm4che.atlassian.net/browse/WEA-423)] - Connection of the dicomizer to a DICOM Worklist
-   [[WEA-452](https://dcm4che.atlassian.net/browse/WEA-452)] - ECG viewer
-   [[WEA-453](https://dcm4che.atlassian.net/browse/WEA-453)] - New image decoding and processing workflow based on OpenCV

Improvement

-   [[WEA-484](https://dcm4che.atlassian.net/browse/WEA-484)] - weasis-portable

## [2.5.3](https://github.com/nroduit/weasis/tree/2.5.3) (2017-05-12)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.5.2...2.5.3)

Bug

-   [[WEA-396](https://dcm4che.atlassian.net/browse/WEA-396)] - Changing the selected KO to None will let filter applied
-   [[WEA-400](https://dcm4che.atlassian.net/browse/WEA-400)] - Study date is not updated in dicomizer when an image is removed
-   [[WEA-404](https://dcm4che.atlassian.net/browse/WEA-404)] - Fix canceling DICOM send
-   [[WEA-406](https://dcm4che.atlassian.net/browse/WEA-406)] - Fix the continuous errors of some corrupted images
-   [[WEA-408](https://dcm4che.atlassian.net/browse/WEA-408)] - Fix series sorting issues
-   [[WEA-412](https://dcm4che.atlassian.net/browse/WEA-412)] - Do not allow importing DICOM images in dicomizer
-   [[WEA-414](https://dcm4che.atlassian.net/browse/WEA-414)] - Fix losing spatial unit with DICOM Presentation State
-   [[WEA-416](https://dcm4che.atlassian.net/browse/WEA-416)] - Fix refreshing metada in Dicomizer
-   [[WEA-417](https://dcm4che.atlassian.net/browse/WEA-417)] - Fix the offset position issue of the graphic label after serialization

New Feature

-   [[WEA-152](https://dcm4che.atlassian.net/browse/WEA-152)] - DICOM Query / Retrieve (DICOM import plugin)
-   [[WEA-397](https://dcm4che.atlassian.net/browse/WEA-397)] - Reattempt downloading images after network failure

Task

-   [[WEA-405](https://dcm4che.atlassian.net/browse/WEA-405)] - Upgrade to weasis-dicom-tools 1.0.4
-   [[WEA-413](https://dcm4che.atlassian.net/browse/WEA-413)] - Fix java 9 compilation and execution issues

Improvement

-   [[WEA-398](https://dcm4che.atlassian.net/browse/WEA-398)] - In tile mode, limit the scroll according to the number of images and the layout size
-   [[WEA-401](https://dcm4che.atlassian.net/browse/WEA-401)] - Make more readable the overlays on thumbnails
-   [[WEA-407](https://dcm4che.atlassian.net/browse/WEA-407)] - Allow all the non sequence tags in attributes-view.xml
-   [[WEA-409](https://dcm4che.atlassian.net/browse/WEA-409)] - Read SCOORD from DICOM SR and display graphics on the image
-   [[WEA-410](https://dcm4che.atlassian.net/browse/WEA-410)] - Fix corrupted path for loading images on Windows
-   [[WEA-411](https://dcm4che.atlassian.net/browse/WEA-411)] - Improve DICOM error message
-   [[WEA-415](https://dcm4che.atlassian.net/browse/WEA-415)] - Configure the tags to display, to edit and to be required for publication in Dicomizer

## [2.5.2](https://github.com/nroduit/weasis/tree/2.5.2) (2017-02-22)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.5.0...2.5.2)

Bug

-   [[WEA-385](https://dcm4che.atlassian.net/browse/WEA-385)] - The list of Key Object in explorer is missing
-   [[WEA-386](https://dcm4che.atlassian.net/browse/WEA-386)] - Wrong pixel value with non square pixel image
-   [[WEA-388](https://dcm4che.atlassian.net/browse/WEA-388)] - viewer linux launcher doesn't work
-   [[WEA-390](https://dcm4che.atlassian.net/browse/WEA-390)] - Fix loading native bundles for Windows server 2012 and 2016
-   [[WEA-392](https://dcm4che.atlassian.net/browse/WEA-392)] - Issue for identifying uniquely a patient
-   [[WEA-394](https://dcm4che.atlassian.net/browse/WEA-394)] - DICOM SR are not visible in the explorer
-   [[WEA-395](https://dcm4che.atlassian.net/browse/WEA-395)] - Fix upgrading issue (cache of configuration)

Task

-   [[WEA-393](https://dcm4che.atlassian.net/browse/WEA-393)] - Update Felix framework to 5.6.1

Improvement

-   [[WEA-209](https://dcm4che.atlassian.net/browse/WEA-209)] - Wrong frame rate for multiframe studies
-   [[WEA-387](https://dcm4che.atlassian.net/browse/WEA-387)] - Remove temporary files when closing studies in UI
-   [[WEA-389](https://dcm4che.atlassian.net/browse/WEA-389)] - Follows the POSIX syntax. Allows to run simultaneously different parameter types (Open DICOM)
-   [[WEA-391](https://dcm4che.atlassian.net/browse/WEA-391)] - Menu for exporting studies to Horos on Mac

**Merged pull requests:**

- Rewrite shell script launcher for linux systems [\#12](https://github.com/nroduit/Weasis/pull/12) ([Akronix](https://github.com/Akronix))

## [2.5.0](https://github.com/nroduit/weasis/tree/2.5.0) (2016-11-15)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.7...2.5.0)

Bug

-   [[WEA-342](https://dcm4che.atlassian.net/browse/WEA-342)] - Some DICOM VOI LUTs are not displayed correctly
-   [[WEA-346](https://dcm4che.atlassian.net/browse/WEA-346)] - Artifacts with the pure Java JPEG2000 Decoder
-   [[WEA-352](https://dcm4che.atlassian.net/browse/WEA-352)] - Scroll position of the DICOM metadata view is always on the bottom
-   [[WEA-353](https://dcm4che.atlassian.net/browse/WEA-353)] - Removing a patient do not remove the list of Key Images
-   [[WEA-356](https://dcm4che.atlassian.net/browse/WEA-356)] - Measurement Units are not displayed in DICOM SR view
-   [[WEA-359](https://dcm4che.atlassian.net/browse/WEA-359)] - Cannot export DICOM Mpeg or DICOM pdf
-   [[WEA-361](https://dcm4che.atlassian.net/browse/WEA-361)] - Default AETitle for printer SCU is not valid
-   [[WEA-364](https://dcm4che.atlassian.net/browse/WEA-364)] - Checking "Selected View" option in DICOM Print doesn't work
-   [[WEA-370](https://dcm4che.atlassian.net/browse/WEA-370)] - Close image from the context menu is not working
-   [[WEA-372](https://dcm4che.atlassian.net/browse/WEA-372)] - Sometimes thumbnails of a split series are not displayed
-   [[WEA-374](https://dcm4che.atlassian.net/browse/WEA-374)] - Mermory leak with some views
-   [[WEA-375](https://dcm4che.atlassian.net/browse/WEA-375)] - Build MPR with images from other series
-   [[WEA-376](https://dcm4che.atlassian.net/browse/WEA-376)] - WEASIS close any browser, when opening several large DICOMs
-   [[WEA-378](https://dcm4che.atlassian.net/browse/WEA-378)] - Lens doesn't refresh graphics correctly
-   [[WEA-381](https://dcm4che.atlassian.net/browse/WEA-381)] - Fix patient inconsistency within a study
-   [[WEA-383](https://dcm4che.atlassian.net/browse/WEA-383)] - Green image with YBR_FULL and RLE compression

New Feature

-   [[WEA-337](https://dcm4che.atlassian.net/browse/WEA-337)] - DICOM Send (DICOM export plugin)
-   [[WEA-363](https://dcm4che.atlassian.net/browse/WEA-363)] - New cross-platform decoders: jpeg, jpeg-losseless, jpeg-ls, jpeg2000
-   [[WEA-371](https://dcm4che.atlassian.net/browse/WEA-371)] - Command to open an non DICOM image file or URL
-   [[WEA-373](https://dcm4che.atlassian.net/browse/WEA-373)] - Read new DICOM floating point image pixel
-   [[WEA-377](https://dcm4che.atlassian.net/browse/WEA-377)] - Dicomizer module
-   [[WEA-379](https://dcm4che.atlassian.net/browse/WEA-379)] - New drawing tools
-   [[WEA-380](https://dcm4che.atlassian.net/browse/WEA-380)] - Option for exporting graphics in DICOM Presentation State

Task

-   [[WEA-345](https://dcm4che.atlassian.net/browse/WEA-345)] - Replacing simpleXML (the XML binding library) by jaxb
-   [[WEA-368](https://dcm4che.atlassian.net/browse/WEA-368)] - Build dynamically all the DICOM TagW
-   [[WEA-382](https://dcm4che.atlassian.net/browse/WEA-382)] - Update to felix framework 5.6.0

Improvement

-   [[WEA-343](https://dcm4che.atlassian.net/browse/WEA-343)] - Apply Presentation LUT sequence
-   [[WEA-344](https://dcm4che.atlassian.net/browse/WEA-344)] - Reading Presentation State improvement for overlay, shutter
-   [[WEA-351](https://dcm4che.atlassian.net/browse/WEA-351)] - check open Series' Studies in the Export dialog
-   [[WEA-354](https://dcm4che.atlassian.net/browse/WEA-354)] - Minimal annotations mode available in Display tool
-   [[WEA-355](https://dcm4che.atlassian.net/browse/WEA-355)] - Allow to read DICOM ZIP without DICOMDIR
-   [[WEA-357](https://dcm4che.atlassian.net/browse/WEA-357)] - Display lossy compression information
-   [[WEA-358](https://dcm4che.atlassian.net/browse/WEA-358)] - Allow to open serveral documents of a series (dicom encapsulated document)
-   [[WEA-360](https://dcm4che.atlassian.net/browse/WEA-360)] - Exporting non DICOM files will allow to extract files from DICOM encapsulated document and DICOM video
-   [[WEA-362](https://dcm4che.atlassian.net/browse/WEA-362)] - Allow in preferences to adapt the stacktrace limit in the logger
-   [[WEA-367](https://dcm4che.atlassian.net/browse/WEA-367)] - File configuration of series splitting rules
-   [[WEA-369](https://dcm4che.atlassian.net/browse/WEA-369)] - Image in low resolution with standard print

**Merged pull requests:**

- Change DicomFieldsView and InfoLayer so they work with other Explorer… [\#8](https://github.com/nroduit/Weasis/pull/8) ([gabibau](https://github.com/gabibau))
- Small fixes: replaceView on ImageViewPlugin and a Graphic name. [\#7](https://github.com/nroduit/Weasis/pull/7) ([gabibau](https://github.com/gabibau))
- Fixes Ellipse label preferences. [\#6](https://github.com/nroduit/Weasis/pull/6) ([gabibau](https://github.com/gabibau))

## [2.0.7](https://github.com/nroduit/weasis/tree/2.0.7) (2016-05-20)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.6...2.0.7)

Bug

-   [[WEA-352](https://dcm4che.atlassian.net/browse/WEA-352)] - Scroll position of the DICOM metadata view is always on the bottom
-   [[WEA-353](https://dcm4che.atlassian.net/browse/WEA-353)] - Removing a patient do not remove the list of Key Images
-   [[WEA-356](https://dcm4che.atlassian.net/browse/WEA-356)] - Measurement Units are not displayed in DICOM SR view
-   [[WEA-361](https://dcm4che.atlassian.net/browse/WEA-361)] - Default AETitle for printer SCU is not valid
-   [[WEA-364](https://dcm4che.atlassian.net/browse/WEA-364)] - Checking "Selected View" option in DICOM Print doesn't work

Improvement

-   [[WEA-351](https://dcm4che.atlassian.net/browse/WEA-351)] - check open Series' Studies in the Export dialog
-   [[WEA-355](https://dcm4che.atlassian.net/browse/WEA-355)] - Allow to read DICOM ZIP without DICOMDIR
-   [[WEA-357](https://dcm4che.atlassian.net/browse/WEA-357)] - Display lossy compression information
-   [[WEA-369](https://dcm4che.atlassian.net/browse/WEA-369)] - Image in low resolution with standard print

**Merged pull requests:**

- BasicGraphic sending measurement list by interface. [\#5](https://github.com/nroduit/Weasis/pull/5) ([gabibau](https://github.com/gabibau))
- - Updating surefire plugin version due to Netbeans compatibility issue [\#2](https://github.com/nroduit/Weasis/pull/2) ([pedrobastosz](https://github.com/pedrobastosz))

## [2.0.6](https://github.com/nroduit/weasis/tree/2.0.6) (2016-02-20)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.5...2.0.6)

Bug

-   [[WEA-346](https://dcm4che.atlassian.net/browse/WEA-346)] - Artifacts with the pure Java JPEG2000 Decoder
-   [[WEA-348](https://dcm4che.atlassian.net/browse/WEA-348)] - Cannot display some US images
-   [[WEA-349](https://dcm4che.atlassian.net/browse/WEA-349)] - On Windows 10 and last Java Runtime compressed images cannot be displayed

Improvement

-   [[WEA-350](https://dcm4che.atlassian.net/browse/WEA-350)] - Update to dcm4che 3.3.7 and felix framework 5.4.0

## [2.0.5](https://github.com/nroduit/weasis/tree/2.0.5) (2015-10-05)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.4...2.0.5)

Bug

-   [[WEA-329](https://dcm4che.atlassian.net/browse/WEA-329)] - window/level issue when configured on two or more mouse buttons
-   [[WEA-331](https://dcm4che.atlassian.net/browse/WEA-331)] - Does not support 16-bit Modality LUT on 8-bit monochrome image
-   [[WEA-332](https://dcm4che.atlassian.net/browse/WEA-332)] - dcmview2d:move command do not move the image
-   [[WEA-333](https://dcm4che.atlassian.net/browse/WEA-333)] - Cannot start on Windows 10 and Java 8_60 32-bit
-   [[WEA-334](https://dcm4che.atlassian.net/browse/WEA-334)] - DICOMDIR reader doesn't load DICOM PR and KO
-   [[WEA-335](https://dcm4che.atlassian.net/browse/WEA-335)] - Multiplication of loading progress bars with DICOMDIR reader
-   [[WEA-336](https://dcm4che.atlassian.net/browse/WEA-336)] - Cannot build the distribution with spaces in path on non-Windows systems
-   [[WEA-339](https://dcm4che.atlassian.net/browse/WEA-339)] - Reading color icon of DICOMDIR
-   [[WEA-340](https://dcm4che.atlassian.net/browse/WEA-340)] - Cannot visualize the image with an invalid age value

New Feature

-   [[WEA-330](https://dcm4che.atlassian.net/browse/WEA-330)] - Add the oriented minimum bounding box (OMBB) for polygon (by the minimum rectangle width)

Improvement

-   [[WEA-328](https://dcm4che.atlassian.net/browse/WEA-328)] - Add contextual menu entries for open a series directly on another screen
-   [[WEA-338](https://dcm4che.atlassian.net/browse/WEA-338)] - Show when DICOMDIR is corrupted and suggest reading files
-   [[WEA-341](https://dcm4che.atlassian.net/browse/WEA-341)] - Display the thumbnail of the selected series on the center only when not visible

**Merged pull requests:**

- Move telnet command is working again [\#1](https://github.com/nroduit/Weasis/pull/1) ([alexsierro](https://github.com/alexsierro))

## [2.0.4](https://github.com/nroduit/weasis/tree/2.0.4) (2015-06-23)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.3...2.0.4)

Bug

-   [[WEA-280](https://dcm4che.atlassian.net/browse/WEA-280)] - Sometimes a view disappears when externalizing a window on another screen
-   [[WEA-310](https://dcm4che.atlassian.net/browse/WEA-310)] - Displaying measurement results in pixel is not correct
-   [[WEA-312](https://dcm4che.atlassian.net/browse/WEA-312)] - Missing film size values in DICOM print
-   [[WEA-313](https://dcm4che.atlassian.net/browse/WEA-313)] - Image without a spatial calibration should always show values in pixel unit
-   [[WEA-317](https://dcm4che.atlassian.net/browse/WEA-317)] - Fix several issues with DICOM print
-   [[WEA-320](https://dcm4che.atlassian.net/browse/WEA-320)] - Encoding issue when reading the WADO manifest
-   [[WEA-321](https://dcm4che.atlassian.net/browse/WEA-321)] - Cache images are not removed during Windows shutdown or log off
-   [[WEA-325](https://dcm4che.atlassian.net/browse/WEA-325)] - Fix log file path when using remote preferences

New Feature

-   [[WEA-308](https://dcm4che.atlassian.net/browse/WEA-308)] - Allow to search within DICOM metadata
-   [[WEA-316](https://dcm4che.atlassian.net/browse/WEA-316)] - DICOM Print: allow to print a layout (several images per page)
-   [[WEA-327](https://dcm4che.atlassian.net/browse/WEA-327)] - DICOM Print Film Size options

Improvement

-   [[WEA-307](https://dcm4che.atlassian.net/browse/WEA-307)] - Add a button for abording the import and export tasks
-   [[WEA-309](https://dcm4che.atlassian.net/browse/WEA-309)] - Allow to drag and drop non DICOM images into the central empty area
-   [[WEA-314](https://dcm4che.atlassian.net/browse/WEA-314)] - Update translations for launcher and Java Swing UI
-   [[WEA-315](https://dcm4che.atlassian.net/browse/WEA-315)] - Update OSGI Felix framework to 4.6.1
-   [[WEA-319](https://dcm4che.atlassian.net/browse/WEA-319)] - Update to launch4j 3.8 for building weasis.exe
-   [[WEA-322](https://dcm4che.atlassian.net/browse/WEA-322)] - Display the keyboard shortcut of the measurement selection menu
-   [[WEA-323](https://dcm4che.atlassian.net/browse/WEA-323)] - Add new keyboard shortcuts
-   [[WEA-324](https://dcm4che.atlassian.net/browse/WEA-324)] - Allows to configure the Weasis AETitle for printer at the server side
-   [[WEA-326](https://dcm4che.atlassian.net/browse/WEA-326)] - Show message when MPR cannot build a series without 3D positions

## [2.0.3](https://github.com/nroduit/weasis/tree/2.0.3) (2014-11-24)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.2...2.0.3)

Bug

-   [[WEA-294](https://dcm4che.atlassian.net/browse/WEA-294)] - Thumbnails are not displayed when series are split
-   [[WEA-297](https://dcm4che.atlassian.net/browse/WEA-297)] - Cannot reopen MPR from a generated series
-   [[WEA-299](https://dcm4che.atlassian.net/browse/WEA-299)] - Somtimes start/stop buttons for controling download have no effect
-   [[WEA-300](https://dcm4che.atlassian.net/browse/WEA-300)] - When changing the view selection the preset selection is lost
-   [[WEA-301](https://dcm4che.atlassian.net/browse/WEA-301)] - Issue with the mechanism that clean the bundle cache when there is an error at launch
-   [[WEA-302](https://dcm4che.atlassian.net/browse/WEA-302)] - The lens is not refreshed when panning
-   [[WEA-304](https://dcm4che.atlassian.net/browse/WEA-304)] - Memory issue with some DICOM sequences when reading a DICOM file

New Feature

-   [[WEA-306](https://dcm4che.atlassian.net/browse/WEA-306)] - Support DICOM AU modality with embeded audio player

Improvement

-   [[WEA-295](https://dcm4che.atlassian.net/browse/WEA-295)] - Add PatientAge in attributes-view.xml to be configure by the user
-   [[WEA-296](https://dcm4che.atlassian.net/browse/WEA-296)] - Allow to rebuild a series with MIP
-   [[WEA-298](https://dcm4che.atlassian.net/browse/WEA-298)] - Improve file menu and contextual menu for central view
-   [[WEA-303](https://dcm4che.atlassian.net/browse/WEA-303)] - Build MPR views with non square pixel images
-   [[WEA-305](https://dcm4che.atlassian.net/browse/WEA-305)] - Skip reading private tags larger than 1 KB

## [2.0.2](https://github.com/nroduit/weasis/tree/2.0.2) (2014-08-05)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.1...2.0.2)

Bug

-   [[WEA-286](https://dcm4che.atlassian.net/browse/WEA-286)] - Issue of reset and close command in full screen mode
-   [[WEA-287](https://dcm4che.atlassian.net/browse/WEA-287)] - Patient and study annotations are not displayed with SC modality
-   [[WEA-289](https://dcm4che.atlassian.net/browse/WEA-289)] - Position of dialog windows are not correct with multi-screens
-   [[WEA-290](https://dcm4che.atlassian.net/browse/WEA-290)] - Color images with DICOM overlay are extremely slow to display
-   [[WEA-291](https://dcm4che.atlassian.net/browse/WEA-291)] - Popup dialog does not appear on the right screen
-   [[WEA-293](https://dcm4che.atlassian.net/browse/WEA-293)] - Cannot display non square binary overlay

Improvement

-   [[WEA-288](https://dcm4che.atlassian.net/browse/WEA-288)] - Make telnet available only when all plugin commands are loaded

## [2.0.1](https://github.com/nroduit/weasis/tree/2.0.1) (2014-07-21)
[Full Changelog](https://github.com/nroduit/weasis/compare/2.0.0...2.0.1)

Bug

-   [[WEA-271](https://dcm4che.atlassian.net/browse/WEA-271)] - Cannot split series according to the Frame Type (enhanced DICOM)
-   [[WEA-272](https://dcm4che.atlassian.net/browse/WEA-272)] - Erroneous DICOM presets with MR, PET and XA/XRF images containing Rescale Slope and Rescale Intercept
-   [[WEA-273](https://dcm4che.atlassian.net/browse/WEA-273)] - Downloading progression label stays at 0%
-   [[WEA-274](https://dcm4che.atlassian.net/browse/WEA-274)] - Charset issue when exporting series built by MPR
-   [[WEA-275](https://dcm4che.atlassian.net/browse/WEA-275)] - Cannot read xml manifests from Osirix WEB server
-   [[WEA-278](https://dcm4che.atlassian.net/browse/WEA-278)] - Rebuilding thumbnail from contextual menu does not work correctly
-   [[WEA-279](https://dcm4che.atlassian.net/browse/WEA-279)] - Rendering issue of large rgb images
-   [[WEA-281](https://dcm4che.atlassian.net/browse/WEA-281)] - Preset in MPR views are not initialized correctly
-   [[WEA-282](https://dcm4che.atlassian.net/browse/WEA-282)] - Show pixel information for multi-channel images with channels superior to 8-bit
-   [[WEA-285](https://dcm4che.atlassian.net/browse/WEA-285)] - Refreshing issues with the KO filter mode

Improvement

-   [[WEA-276](https://dcm4che.atlassian.net/browse/WEA-276)] - Read correctly old DICOM date format
-   [[WEA-277](https://dcm4che.atlassian.net/browse/WEA-277)] - Export the slected MPR view to clipboard
-   [[WEA-283](https://dcm4che.atlassian.net/browse/WEA-283)] - Supporting 32-bit images in MPR and MIP
-   [[WEA-284](https://dcm4che.atlassian.net/browse/WEA-284)] - Do not save crosshair tool into preferences when using MPR

## [2.0.0](https://github.com/nroduit/weasis/tree/2.0.0) (2014-06-27)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.9...2.0.0)

Bug

-   [[WEA-167](https://dcm4che.atlassian.net/browse/WEA-167)] - Cannot export to Osirix when it is Osirix MD (or when application is renamed)
-   [[WEA-168](https://dcm4che.atlassian.net/browse/WEA-168)] - Modality LUTs should not be applied in some cases
-   [[WEA-169](https://dcm4che.atlassian.net/browse/WEA-169)] - Very small slider thick with Windows 7 Theme
-   [[WEA-170](https://dcm4che.atlassian.net/browse/WEA-170)] - Pixel statistics on non-square pixel images are erroneous
-   [[WEA-172](https://dcm4che.atlassian.net/browse/WEA-172)] - Do not download DICOM files (cannot read magic number)
-   [[WEA-175](https://dcm4che.atlassian.net/browse/WEA-175)] - Problem of splitting a series containing a video and images
-   [[WEA-178](https://dcm4che.atlassian.net/browse/WEA-178)] - When resizing a view, the image is shifted to the center
-   [[WEA-179](https://dcm4che.atlassian.net/browse/WEA-179)] - Sometimes graphics are not visible when exporting a view through the clipboard
-   [[WEA-180](https://dcm4che.atlassian.net/browse/WEA-180)] - Mixing tmp files when running several instances of Weasis (from different web sources)
-   [[WEA-182](https://dcm4che.atlassian.net/browse/WEA-182)] - Weasis doesn't start on Windows 8
-   [[WEA-190](https://dcm4che.atlassian.net/browse/WEA-190)] - Images are not displayed on Mac with Java 7
-   [[WEA-191](https://dcm4che.atlassian.net/browse/WEA-191)] - Cannot print with annotations for some images
-   [[WEA-194](https://dcm4che.atlassian.net/browse/WEA-194)] - DICOM print issue with some PrintSCP servers
-   [[WEA-197](https://dcm4che.atlassian.net/browse/WEA-197)] - Keep the position of the toolbar after hiding
-   [[WEA-200](https://dcm4che.atlassian.net/browse/WEA-200)] - Some US images (with a color Palette) are displayed in in black and white
-   [[WEA-202](https://dcm4che.atlassian.net/browse/WEA-202)] - Missing images when exporting to JPEG with the option "Keep directory names"
-   [[WEA-210](https://dcm4che.atlassian.net/browse/WEA-210)] - Cannot export big series in jpeg
-   [[WEA-211](https://dcm4che.atlassian.net/browse/WEA-211)] - Error: cannot open more files (Ubuntu is limited by default to 1024)
-   [[WEA-212](https://dcm4che.atlassian.net/browse/WEA-212)] - DICOMDIR Icons are not written correcty
-   [[WEA-213](https://dcm4che.atlassian.net/browse/WEA-213)] - Toolbars have not a fixed position (showing / hiding)
-   [[WEA-215](https://dcm4che.atlassian.net/browse/WEA-215)] - Shutdown of the application hangs sometimes on Windows and with JWS
-   [[WEA-216](https://dcm4che.atlassian.net/browse/WEA-216)] - Do not display some Asiatic characters
-   [[WEA-217](https://dcm4che.atlassian.net/browse/WEA-217)] - weasis2.0.0 can not open dcm file
-   [[WEA-219](https://dcm4che.atlassian.net/browse/WEA-219)] - Random "Printer is not accepting job" PrinterExceptions
-   [[WEA-220](https://dcm4che.atlassian.net/browse/WEA-220)] - The default property "weasis.look" doesn't work.
-   [[WEA-221](https://dcm4che.atlassian.net/browse/WEA-221)] - Cannot display image with Modality LUT sequence and pixels outside the LUT table
-   [[WEA-222](https://dcm4che.atlassian.net/browse/WEA-222)] - Lose W/L values when sorting the series stack
-   [[WEA-229](https://dcm4che.atlassian.net/browse/WEA-229)] - Posterior and anterior annotations are reversed on sagittal images
-   [[WEA-230](https://dcm4che.atlassian.net/browse/WEA-230)] - 3D Reconstruction of CT and MR are splitted in sub-series for each direction
-   [[WEA-231](https://dcm4che.atlassian.net/browse/WEA-231)] - Some presets in DICOM file are not displayed correctly
-   [[WEA-237](https://dcm4che.atlassian.net/browse/WEA-237)] - Security warning popup in Weasis with JRE 1.7.0_40 and JRE 1.7.0_45
-   [[WEA-247](https://dcm4che.atlassian.net/browse/WEA-247)] - Cache issue when creating thumbnails (images cannot be loaded or are loaded two times)
-   [[WEA-252](https://dcm4che.atlassian.net/browse/WEA-252)] - Overlay issue with multiframe
-   [[WEA-253](https://dcm4che.atlassian.net/browse/WEA-253)] - Green border line with YBR_FULL Photometric Interpretation images
-   [[WEA-254](https://dcm4che.atlassian.net/browse/WEA-254)] - Units are not display in DICOM SR view
-   [[WEA-255](https://dcm4che.atlassian.net/browse/WEA-255)] - Cannot load images with Java 8
-   [[WEA-258](https://dcm4che.atlassian.net/browse/WEA-258)] - Cannot resize correctly components in layout on a large screen
-   [[WEA-259](https://dcm4che.atlassian.net/browse/WEA-259)] - Preferences are not saved when Weasis is launched with Java Web Start
-   [[WEA-261](https://dcm4che.atlassian.net/browse/WEA-261)] - Popup message at start are hidden under the main Frame
-   [[WEA-262](https://dcm4che.atlassian.net/browse/WEA-262)] - Download Interruption with https configuration
-   [[WEA-265](https://dcm4che.atlassian.net/browse/WEA-265)] - Preferences of additional plugins are not saved

New Feature

-   [[WEA-17](https://dcm4che.atlassian.net/browse/WEA-17)] - Customization of LUTs, Presets and displayed tags
-   [[WEA-133](https://dcm4che.atlassian.net/browse/WEA-133)] - New window docking system
-   [[WEA-134](https://dcm4che.atlassian.net/browse/WEA-134)] - Read and apply Key Object Selection to a series
-   [[WEA-135](https://dcm4che.atlassian.net/browse/WEA-135)] - Read and apply Presentation State to a series
-   [[WEA-165](https://dcm4che.atlassian.net/browse/WEA-165)] - Orthogonal MPR
-   [[WEA-166](https://dcm4che.atlassian.net/browse/WEA-166)] - MIP(Maximum Intensity Projection) and Min-MIP
-   [[WEA-181](https://dcm4che.atlassian.net/browse/WEA-181)] - Read and export DICOM ZIP file
-   [[WEA-196](https://dcm4che.atlassian.net/browse/WEA-196)] - Display DICOM SR
-   [[WEA-206](https://dcm4che.atlassian.net/browse/WEA-206)] - Draw text annotations
-   [[WEA-207](https://dcm4che.atlassian.net/browse/WEA-207)] - Burn CDs
-   [[WEA-223](https://dcm4che.atlassian.net/browse/WEA-223)] - Changing the unit of the spatial calibration
-   [[WEA-244](https://dcm4che.atlassian.net/browse/WEA-244)] - Crosshair for series sharing the same FrameOfReferenceUID
-   [[WEA-248](https://dcm4che.atlassian.net/browse/WEA-248)] - New API for image processing
-   [[WEA-251](https://dcm4che.atlassian.net/browse/WEA-251)] - Real-world zoom
-   [[WEA-267](https://dcm4che.atlassian.net/browse/WEA-267)] - Allow to embed the viewer in a web page
-   [[WEA-269](https://dcm4che.atlassian.net/browse/WEA-269)] - Pixel probe tool
-   [[WEA-270](https://dcm4che.atlassian.net/browse/WEA-270)] - API for building custom plug-ins (Tool, Toolbar, Import DICOM, Explort DICOM...)
-   [[WEA-292](https://dcm4che.atlassian.net/browse/WEA-292)] - Handle series download globally or individually

Improvement

-   [[WEA-176](https://dcm4che.atlassian.net/browse/WEA-176)] - Space bar shortcut for showing or hiding all the annotations
-   [[WEA-177](https://dcm4che.atlassian.net/browse/WEA-177)] - Allows only a single instance of Weasis (portable) on Windows
-   [[WEA-186](https://dcm4che.atlassian.net/browse/WEA-186)] - Windows portable executable now launches at first 32-bit Java Runtime
-   [[WEA-187](https://dcm4che.atlassian.net/browse/WEA-187)] - Windows portable can incorporate a JVM for DICOM CD
-   [[WEA-192](https://dcm4che.atlassian.net/browse/WEA-192)] - Show the selected Series in the DICOM explorer
-   [[WEA-198](https://dcm4che.atlassian.net/browse/WEA-198)] - Not optimal display of large uncompressed DICOM images
-   [[WEA-199](https://dcm4che.atlassian.net/browse/WEA-199)] - Reading illegal DICOM compressed file (encapsulation is missing)
-   [[WEA-201](https://dcm4che.atlassian.net/browse/WEA-201)] - Improve the selection of thumbnails (same behaviour as a list)
-   [[WEA-203](https://dcm4che.atlassian.net/browse/WEA-203)] - All actions in UI are disabled when the selected view has no image
-   [[WEA-204](https://dcm4che.atlassian.net/browse/WEA-204)] - Can have multiple versions of preferences with the profile name in config.properties
-   [[WEA-205](https://dcm4che.atlassian.net/browse/WEA-205)] - Add a position index and a generic way to configure toolbars (also for new toolbars in a plugin)
-   [[WEA-214](https://dcm4che.atlassian.net/browse/WEA-214)] - Improve interface for building plugins
-   [[WEA-218](https://dcm4che.atlassian.net/browse/WEA-218)] - Preset Creation
-   [[WEA-228](https://dcm4che.atlassian.net/browse/WEA-228)] - Display Date Of Secondary Capture (0018,1012) for SC or OT
-   [[WEA-236](https://dcm4che.atlassian.net/browse/WEA-236)] - Add options for the number of concurrent downloads
-   [[WEA-238](https://dcm4che.atlassian.net/browse/WEA-238)] - Export DICOM KO, PR and SR
-   [[WEA-239](https://dcm4che.atlassian.net/browse/WEA-239)] - Display thumbnails of series as tooltips in Dicom Export
-   [[WEA-240](https://dcm4che.atlassian.net/browse/WEA-240)] - Propagate an error or warning message into Weasis through the manifest
-   [[WEA-241](https://dcm4che.atlassian.net/browse/WEA-241)] - Sort alphabetically items correctly in each language
-   [[WEA-242](https://dcm4che.atlassian.net/browse/WEA-242)] - Better display of DICOM metadata
-   [[WEA-243](https://dcm4che.atlassian.net/browse/WEA-243)] - Display the level of compression in red (IHE BIR RAD TF-­‐2: 4.16.4.2.2.5.8)
-   [[WEA-245](https://dcm4che.atlassian.net/browse/WEA-245)] - Update to dcm4che 3.3
-   [[WEA-249](https://dcm4che.atlassian.net/browse/WEA-249)] - Change the number of concurrent series and image downloads
-   [[WEA-250](https://dcm4che.atlassian.net/browse/WEA-250)] - Improve DICOM metadata display
-   [[WEA-256](https://dcm4che.atlassian.net/browse/WEA-256)] - Typo in DICOM attribute
-   [[WEA-257](https://dcm4che.atlassian.net/browse/WEA-257)] - Improve series sorting
-   [[WEA-260](https://dcm4che.atlassian.net/browse/WEA-260)] - Option for applying windowing on color images
-   [[WEA-263](https://dcm4che.atlassian.net/browse/WEA-263)] - Improve full-screen mode when double clicking on a view
-   [[WEA-264](https://dcm4che.atlassian.net/browse/WEA-264)] - Configuration on server side to customize the Weasis interface
-   [[WEA-266](https://dcm4che.atlassian.net/browse/WEA-266)] - Separate language and the format of date and number in preferences
-   [[WEA-268](https://dcm4che.atlassian.net/browse/WEA-268)] - Weasis-i18n (internationalization) improvements

## [1.2.9](https://github.com/nroduit/weasis/tree/1.2.9) (2014-04-01)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.8...1.2.9)

Bug

-   [[WEA-255](https://dcm4che.atlassian.net/browse/WEA-255)] - Cannot load images with Java 8
-   [[WEA-258](https://dcm4che.atlassian.net/browse/WEA-258)] - Cannot resize correctly components in layout on a large screen
-   [[WEA-259](https://dcm4che.atlassian.net/browse/WEA-259)] - Preferences are not saved when Weasis is launched with Java Web Start

Improvement

-   [[WEA-256](https://dcm4che.atlassian.net/browse/WEA-256)] - Typo in DICOM attribute
-   [[WEA-257](https://dcm4che.atlassian.net/browse/WEA-257)] - Improve series sorting

## [1.2.8](https://github.com/nroduit/weasis/tree/1.2.8) (2013-12-11)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.7...1.2.8)

Bug

-   [[WEA-247](https://dcm4che.atlassian.net/browse/WEA-247)] - Cache issue when creating thumbnails (images cannot be loaded or are loaded two times)

Improvement

-   [[WEA-240](https://dcm4che.atlassian.net/browse/WEA-240)] - Propagate an error or warning message into Weasis through the manifest
-   [[WEA-241](https://dcm4che.atlassian.net/browse/WEA-241)] - Sort alphabetically items correctly in each language

## [1.2.7](https://github.com/nroduit/weasis/tree/1.2.7) (2013-10-30)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.6...1.2.7)

Bug

-   [[WEA-229](https://dcm4che.atlassian.net/browse/WEA-229)] - Posterior and anterior annotations are reversed on sagittal images
-   [[WEA-230](https://dcm4che.atlassian.net/browse/WEA-230)] - 3D Reconstruction of CT and MR are splitted in sub-series for each direction
-   [[WEA-231](https://dcm4che.atlassian.net/browse/WEA-231)] - Some presets in DICOM file are not displayed correctly
-   [[WEA-237](https://dcm4che.atlassian.net/browse/WEA-237)] - Security warning popup in Weasis with JRE 1.7.0_40 and JRE 1.7.0_45

## [1.2.6](https://github.com/nroduit/weasis/tree/1.2.6) (2013-09-05)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.5...1.2.6)

Bug

-   [[WEA-202](https://dcm4che.atlassian.net/browse/WEA-202)] - Missing images when exporting to JPEG with the option "Keep directory names"
-   [[WEA-210](https://dcm4che.atlassian.net/browse/WEA-210)] - Cannot export big series in jpeg
-   [[WEA-211](https://dcm4che.atlassian.net/browse/WEA-211)] - Error: cannot open more files (Ubuntu is limited by default to 1024)
-   [[WEA-212](https://dcm4che.atlassian.net/browse/WEA-212)] - DICOMDIR Icons are not written correcty
-   [[WEA-213](https://dcm4che.atlassian.net/browse/WEA-213)] - Toolbars have not a fixed position (showing / hiding)
-   [[WEA-215](https://dcm4che.atlassian.net/browse/WEA-215)] - Shutdown of the application hangs sometimes on Windows and with JWS
-   [[WEA-216](https://dcm4che.atlassian.net/browse/WEA-216)] - Do not display some Asiatic characters
-   [[WEA-219](https://dcm4che.atlassian.net/browse/WEA-219)] - Random "Printer is not accepting job" PrinterExceptions
-   [[WEA-220](https://dcm4che.atlassian.net/browse/WEA-220)] - The default property "weasis.look" doesn't work.
-   [[WEA-221](https://dcm4che.atlassian.net/browse/WEA-221)] - Cannot display image with Modality LUT sequence and pixels outside the LUT table
-   [[WEA-222](https://dcm4che.atlassian.net/browse/WEA-222)] - Lose W/L values when sorting the series stack

New Feature

-   [[WEA-207](https://dcm4che.atlassian.net/browse/WEA-207)] - Burn CDs

Improvement

-   [[WEA-214](https://dcm4che.atlassian.net/browse/WEA-214)] - Improve interface for building plugins
-   [[WEA-228](https://dcm4che.atlassian.net/browse/WEA-228)] - Display Date Of Secondary Capture (0018,1012) for SC or OT

## [1.2.5](https://github.com/nroduit/weasis/tree/1.2.5) (2013-05-04)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.4...1.2.5)

Bug

-   [[WEA-190](https://dcm4che.atlassian.net/browse/WEA-190)] - Images are not displayed on Mac with Java 7
-   [[WEA-191](https://dcm4che.atlassian.net/browse/WEA-191)] - Cannot print with annotations for some images
-   [[WEA-193](https://dcm4che.atlassian.net/browse/WEA-193)] - Cache images are not deleted when closing the application
-   [[WEA-194](https://dcm4che.atlassian.net/browse/WEA-194)] - DICOM print issue with some PrintSCP servers
-   [[WEA-200](https://dcm4che.atlassian.net/browse/WEA-200)] - Some US images (with a color Palette) are displayed in in black and white

Improvement

-   [[WEA-192](https://dcm4che.atlassian.net/browse/WEA-192)] - Show the selected Series in the DICOM explorer
-   [[WEA-195](https://dcm4che.atlassian.net/browse/WEA-195)] - Upgrade OSGI Felix framework to 4.2.0
-   [[WEA-199](https://dcm4che.atlassian.net/browse/WEA-199)] - Reading illegal DICOM compressed file (encapsulation is missing)
-   [[WEA-201](https://dcm4che.atlassian.net/browse/WEA-201)] - Improve the selection of thumbnails (same behaviour as a list)

## [1.2.4](https://github.com/nroduit/weasis/tree/1.2.4) (2013-01-26)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.3...1.2.4)

Bug

-   [[WEA-167](https://dcm4che.atlassian.net/browse/WEA-167)] - Cannot export to Osirix when it is Osirix MD (or when application is renamed)
-   [[WEA-168](https://dcm4che.atlassian.net/browse/WEA-168)] - Modality LUTs should not be applied in some cases
-   [[WEA-169](https://dcm4che.atlassian.net/browse/WEA-169)] - Very small slider thick with Windows 7 Theme
-   [[WEA-170](https://dcm4che.atlassian.net/browse/WEA-170)] - Pixel statistics on non-square pixel images are erroneous
-   [[WEA-171](https://dcm4che.atlassian.net/browse/WEA-171)] - Cannot export multiframe correctly in JPEG or DICOM
-   [[WEA-172](https://dcm4che.atlassian.net/browse/WEA-172)] - Do not download DICOM files (cannot read magic number)
-   [[WEA-175](https://dcm4che.atlassian.net/browse/WEA-175)] - Problem of splitting a series containing a video and images
-   [[WEA-178](https://dcm4che.atlassian.net/browse/WEA-178)] - When resizing a view, the image is shifted to the center
-   [[WEA-179](https://dcm4che.atlassian.net/browse/WEA-179)] - Sometimes graphics are not visible when exporting a view through the clipboard
-   [[WEA-180](https://dcm4che.atlassian.net/browse/WEA-180)] - Mixing tmp files when running several instances of Weasis (from different web sources)
-   [[WEA-182](https://dcm4che.atlassian.net/browse/WEA-182)] - Weasis doesn't start on Windows 8

New Feature

-   [[WEA-185](https://dcm4che.atlassian.net/browse/WEA-185)] - LUT toolbar, Rotation toolbar and Display Reset button

Improvement

-   [[WEA-173](https://dcm4che.atlassian.net/browse/WEA-173)] - Skip private tags larger than 5 KB
-   [[WEA-174](https://dcm4che.atlassian.net/browse/WEA-174)] - Upgrade to dcm4che 2.0.27
-   [[WEA-176](https://dcm4che.atlassian.net/browse/WEA-176)] - Space bar shortcut for showing or hiding all the annotations
-   [[WEA-177](https://dcm4che.atlassian.net/browse/WEA-177)] - Allows only a single instance of Weasis (portable) on Windows
-   [[WEA-183](https://dcm4che.atlassian.net/browse/WEA-183)] - Upgrade Substance library to 7.2.1
-   [[WEA-184](https://dcm4che.atlassian.net/browse/WEA-184)] - Add Bulgarian language
-   [[WEA-186](https://dcm4che.atlassian.net/browse/WEA-186)] - Windows portable executable now launches at first 32-bit Java Runtime
-   [[WEA-187](https://dcm4che.atlassian.net/browse/WEA-187)] - Windows portable can incorporate a JVM for DICOM CD

## [1.2.3](https://github.com/nroduit/weasis/tree/1.2.3) (2012-10-05)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.2...1.2.3)

Bug

-   [[WEA-164](https://dcm4che.atlassian.net/browse/WEA-164)] - Context menu on the image does not appear on Windows

## [1.2.2](https://github.com/nroduit/weasis/tree/1.2.2) (2012-09-28)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.2.1...1.2.2)

Bug

-   [[WEA-162](https://dcm4che.atlassian.net/browse/WEA-162)] - Any tool stops working when downloading DICOM SR
-   [[WEA-163](https://dcm4che.atlassian.net/browse/WEA-163)] - Sometimes contextual menu on a view disappears after right clicking

## [1.2.1](https://github.com/nroduit/weasis/tree/1.2.1) (2012-09-21)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.1.3...1.2.1)

Bug

-   [[WEA-153](https://dcm4che.atlassian.net/browse/WEA-153)] - Labels on image are not refresh properly when moving them
-   [[WEA-154](https://dcm4che.atlassian.net/browse/WEA-154)] - Unchecking pixel padding option throws errors with some images
-   [[WEA-155](https://dcm4che.atlassian.net/browse/WEA-155)] - Pixel padding values inversion with Monochrome1 images or when LUT is inversed
-   [[WEA-156](https://dcm4che.atlassian.net/browse/WEA-156)] - Do not import DICOM encapsulated document (like pdf) from DICOMDIR
-   [[WEA-157](https://dcm4che.atlassian.net/browse/WEA-157)] - Cannot load images any more when swithing between the version 1.1.2 and 1.1.3
-   [[WEA-158](https://dcm4che.atlassian.net/browse/WEA-158)] - Does not take into account the preferred JRE defined in Mac OS X
-   [[WEA-159](https://dcm4che.atlassian.net/browse/WEA-159)] - Out of memory when downloading very large DICOM files and with the option overrideDicomTagsList
-   [[WEA-161](https://dcm4che.atlassian.net/browse/WEA-161)] - Double click conflict between stopping to draw a polylon and maximize a view in layout
-   [[WEA-188](https://dcm4che.atlassian.net/browse/WEA-188)] - Weasis no longer works for my hospitals images, but works for other hospitals images

Improvement

-   [[WEA-160](https://dcm4che.atlassian.net/browse/WEA-160)] - Upgrade OSGI Felix framework to 4.0.3

## [1.1.3](https://github.com/nroduit/weasis/tree/1.1.3) (2012-07-31)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.1.2...1.1.3)

Bug

-   [[WEA-108](https://dcm4che.atlassian.net/browse/WEA-108)] - Images with extreme default W/L values are displayed in white
-   [[WEA-126](https://dcm4che.atlassian.net/browse/WEA-126)] - Improve stabilization of playback speed in cineloops
-   [[WEA-127](https://dcm4che.atlassian.net/browse/WEA-127)] - Removing patient or study doesn't close DICOM files
-   [[WEA-131](https://dcm4che.atlassian.net/browse/WEA-131)] - Sorting cannot be applied to the same Series in several views
-   [[WEA-137](https://dcm4che.atlassian.net/browse/WEA-137)] - Multiple weasis users on a same linux sytem
-   [[WEA-142](https://dcm4che.atlassian.net/browse/WEA-142)] - Perimeter of the polygon measurement tool is erroneous
-   [[WEA-146](https://dcm4che.atlassian.net/browse/WEA-146)] - Erroneous statistics with ROI on a color image
-   [[WEA-147](https://dcm4che.atlassian.net/browse/WEA-147)] - ext-config.properties file not found in the web distribution
-   [[WEA-148](https://dcm4che.atlassian.net/browse/WEA-148)] - YBR_FULL Images with overlay are not displayed with the right color model
-   [[WEA-149](https://dcm4che.atlassian.net/browse/WEA-149)] - Uncompressed CT images with overlay cannot be displayed on Windows 64 bits
-   [[WEA-150](https://dcm4che.atlassian.net/browse/WEA-150)] - Non square pixel images are not print correctly

New Feature

-   [[WEA-72](https://dcm4che.atlassian.net/browse/WEA-72)] - Apply non linear LUT: VOI LUT from DICOM, sigmoid, log and inverse log
-   [[WEA-130](https://dcm4che.atlassian.net/browse/WEA-130)] - Read modality LUT sequence
-   [[WEA-141](https://dcm4che.atlassian.net/browse/WEA-141)] - New measurement tool: open polyline
-   [[WEA-144](https://dcm4che.atlassian.net/browse/WEA-144)] - DICOM print (experimental)
-   [[WEA-145](https://dcm4che.atlassian.net/browse/WEA-145)] - Display non DICOM images (jpeg, png, tiff...)

Improvement

-   [[WEA-128](https://dcm4che.atlassian.net/browse/WEA-128)] - Commands to set layout, synch and left mouse action from OSGI console
-   [[WEA-129](https://dcm4che.atlassian.net/browse/WEA-129)] - Add progress icon when exporting images
-   [[WEA-132](https://dcm4che.atlassian.net/browse/WEA-132)] - Sort a Series by Content Time and Position/Orientation
-   [[WEA-136](https://dcm4che.atlassian.net/browse/WEA-136)] - Translation in Turkish, Italian, Greek and Russian
-   [[WEA-138](https://dcm4che.atlassian.net/browse/WEA-138)] - Handle Issuer Of PatientID tag to uniquely identify patients
-   [[WEA-139](https://dcm4che.atlassian.net/browse/WEA-139)] - Shortcuts for presets
-   [[WEA-140](https://dcm4che.atlassian.net/browse/WEA-140)] - Automatically clean Weasis cache after it cannot start or it crashes
-   [[WEA-143](https://dcm4che.atlassian.net/browse/WEA-143)] - Display laterality in series annotations
-   [[WEA-151](https://dcm4che.atlassian.net/browse/WEA-151)] - Improve the visibility of annotations when printing

## [1.1.2](https://github.com/nroduit/weasis/tree/1.1.2) (2012-03-01)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.1.1...1.1.2)

Bug

-   [[WEA-100](https://dcm4che.atlassian.net/browse/WEA-100)] - Open series when loading throw an error sometimes
-   [[WEA-101](https://dcm4che.atlassian.net/browse/WEA-101)] - Issue of launching Weasis successively every 1-3 seconds
-   [[WEA-104](https://dcm4che.atlassian.net/browse/WEA-104)] - The clickable area of stop/resume downloads is shifted for non-square thumbnail
-   [[WEA-106](https://dcm4che.atlassian.net/browse/WEA-106)] - Invalid numeric values makes DICOM unreadable
-   [[WEA-107](https://dcm4che.atlassian.net/browse/WEA-107)] - YBR_FULL_420 and YBR_FULL_422 images cannot be displayed
-   [[WEA-114](https://dcm4che.atlassian.net/browse/WEA-114)] - Weasis doesn't start properly when launching it from different locations and having the same version number
-   [[WEA-115](https://dcm4che.atlassian.net/browse/WEA-115)] - Shortcut conflicts
-   [[WEA-116](https://dcm4che.atlassian.net/browse/WEA-116)] - Selecting empty view does not change the patient in DICOM explorer
-   [[WEA-120](https://dcm4che.atlassian.net/browse/WEA-120)] - Wrong file size for multiframe Series
-   [[WEA-121](https://dcm4che.atlassian.net/browse/WEA-121)] - Weasis cannot start on Windows server 2008 R2
-   [[WEA-122](https://dcm4che.atlassian.net/browse/WEA-122)] - Floating Explorer: Sometimes thumbnails of a split series are not displayed
-   [[WEA-123](https://dcm4che.atlassian.net/browse/WEA-123)] - Popup menus throw errors

New Feature

-   [[WEA-28](https://dcm4che.atlassian.net/browse/WEA-28)] - Print capabilities
-   [[WEA-105](https://dcm4che.atlassian.net/browse/WEA-105)] - Read and import images from DICOMDIR
-   [[WEA-110](https://dcm4che.atlassian.net/browse/WEA-110)] - Read DICOM CD
-   [[WEA-118](https://dcm4che.atlassian.net/browse/WEA-118)] - Package bundle in pack200 compression
-   [[WEA-124](https://dcm4che.atlassian.net/browse/WEA-124)] - Export to DICOM (DICOM CD), JPEG, PNG and TIFF

Improvement

-   [[WEA-73](https://dcm4che.atlassian.net/browse/WEA-73)] - When a transfer syntax is not supported, automatically request the uncompressed syntax to the WADO server
-   [[WEA-102](https://dcm4che.atlassian.net/browse/WEA-102)] - Add Reflex angle measurement (360 degrees - angle)
-   [[WEA-103](https://dcm4che.atlassian.net/browse/WEA-103)] - Common behavior for General Preferences configuration
-   [[WEA-109](https://dcm4che.atlassian.net/browse/WEA-109)] - Upgrade to Felix framework 4.0.2
-   [[WEA-111](https://dcm4che.atlassian.net/browse/WEA-111)] - Update to dcm4che 2.0.26
-   [[WEA-112](https://dcm4che.atlassian.net/browse/WEA-112)] - Update to Substance 7.1
-   [[WEA-113](https://dcm4che.atlassian.net/browse/WEA-113)] - Add language packs in Weasis portable
-   [[WEA-117](https://dcm4che.atlassian.net/browse/WEA-117)] - Activation of rolling log in preferences
-   [[WEA-119](https://dcm4che.atlassian.net/browse/WEA-119)] - Displaying date with a short textual representation of a month, three letters in the selected language
-   [[WEA-125](https://dcm4che.atlassian.net/browse/WEA-125)] - German translation

## [1.1.1](https://github.com/nroduit/weasis/tree/1.1.1) (2011-11-21)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.1.0...1.1.1)

Bug

-   [[WEA-60](https://dcm4che.atlassian.net/browse/WEA-60)] - Some DICOM files are not deleted of the temp directory when closing the application
-   [[WEA-77](https://dcm4che.atlassian.net/browse/WEA-77)] - Sometimes thumbnails of a split series are not displayed
-   [[WEA-79](https://dcm4che.atlassian.net/browse/WEA-79)] - Cannot open a large number of DICOM files On Linux
-   [[WEA-81](https://dcm4che.atlassian.net/browse/WEA-81)] - When images have the same patient's name and different patientIDs, only one patient is displayed
-   [[WEA-82](https://dcm4che.atlassian.net/browse/WEA-82)] - Non square pixel images without pixel spacing value (only pixel aspect ratio) are not supported
-   [[WEA-84](https://dcm4che.atlassian.net/browse/WEA-84)] - Rectangular images are shifted with a rotation of 90, 180 or 270 degrees
-   [[WEA-91](https://dcm4che.atlassian.net/browse/WEA-91)] - Image containting DataSet Trailing Padding element (FFFC,FFFC) cannot be read (DCM-478)
-   [[WEA-99](https://dcm4che.atlassian.net/browse/WEA-99)] - Cannot sort correctly some multiframe

New Feature

-   [[WEA-74](https://dcm4che.atlassian.net/browse/WEA-74)] - Cine toolbar and shortcuts to start and stop
-   [[WEA-75](https://dcm4che.atlassian.net/browse/WEA-75)] - Display DICOM shutter
-   [[WEA-87](https://dcm4che.atlassian.net/browse/WEA-87)] - Supported MPEG-4 AVC/H.264 High Profile / Level 4.1

Improvement

-   [[WEA-76](https://dcm4che.atlassian.net/browse/WEA-76)] - Add language packs in Weasis Portable
-   [[WEA-78](https://dcm4che.atlassian.net/browse/WEA-78)] - Keep the path of the last open folder in the import DICOM dialog
-   [[WEA-80](https://dcm4che.atlassian.net/browse/WEA-80)] - Thumbnails are built and displayed asynchronously (do not block UI any more)
-   [[WEA-83](https://dcm4che.atlassian.net/browse/WEA-83)] - All the display parameters are reset when changing the series
-   [[WEA-85](https://dcm4che.atlassian.net/browse/WEA-85)] - Display more accurate information of the image plane orientation
-   [[WEA-86](https://dcm4che.atlassian.net/browse/WEA-86)] - Keeps Look and Feels preference when the version is changing
-   [[WEA-88](https://dcm4che.atlassian.net/browse/WEA-88)] - Extending the provided config.properties file
-   [[WEA-89](https://dcm4che.atlassian.net/browse/WEA-89)] - New command "dicom:close -a", close all patients
-   [[WEA-90](https://dcm4che.atlassian.net/browse/WEA-90)] - Upgrade to Felix framework 4.0.1 (OSGI 4.3)
-   [[WEA-92](https://dcm4che.atlassian.net/browse/WEA-92)] - Display available language packs in preferences
-   [[WEA-93](https://dcm4che.atlassian.net/browse/WEA-93)] - Menu Display to show or hide toolbars and tools
-   [[WEA-94](https://dcm4che.atlassian.net/browse/WEA-94)] - Make the round icon of the mini-tool slider more visible (for substance L&F)
-   [[WEA-95](https://dcm4che.atlassian.net/browse/WEA-95)] - Show or hide DICOM Pixel Padding in display panel.
-   [[WEA-96](https://dcm4che.atlassian.net/browse/WEA-96)] - Adapting SUV Calculation to vendor-neutral pseudo-code
-   [[WEA-97](https://dcm4che.atlassian.net/browse/WEA-97)] - Always detect DICOM files by its magic number
-   [[WEA-98](https://dcm4che.atlassian.net/browse/WEA-98)] - Show a global progression icon for any DICOM import method

## [1.1.0](https://github.com/nroduit/weasis/tree/1.1.0) (2011-10-05)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.0.9...1.1.0)

Bug

-   [[WEA-40](https://dcm4che.atlassian.net/browse/WEA-40)] - Cannot load weasis-i18n with dcm4chee-web3
-   [[WEA-45](https://dcm4che.atlassian.net/browse/WEA-45)] - Accept empty values for IDs in weasis-pacs-connector
-   [[WEA-50](https://dcm4che.atlassian.net/browse/WEA-50)] - Keystrokes for Window/Level (W), Measure (M) and Context Menu (Q) does not work
-   [[WEA-53](https://dcm4che.atlassian.net/browse/WEA-53)] - Cannot resume downloading series after changing priority
-   [[WEA-56](https://dcm4che.atlassian.net/browse/WEA-56)] - Only one series is removed when several are selected
-   [[WEA-57](https://dcm4che.atlassian.net/browse/WEA-57)] - Series progress bar is not always refresh
-   [[WEA-58](https://dcm4che.atlassian.net/browse/WEA-58)] - Weasis Portable hangs when loading images from the directory defined by the property "weasis.portable.dicom.directory"
-   [[WEA-59](https://dcm4che.atlassian.net/browse/WEA-59)] - Weasis Portable does not display split series when loading images of the CD-ROM folder
-   [[WEA-61](https://dcm4che.atlassian.net/browse/WEA-61)] - Error of buiding the views of preferences (OSGI service)
-   [[WEA-70](https://dcm4che.atlassian.net/browse/WEA-70)] - Out Of Memory Error when measuring the whole image (CR)

New Feature

-   [[WEA-43](https://dcm4che.atlassian.net/browse/WEA-43)] - Manage in properties which IDs are allowed and which combination is necessary
-   [[WEA-51](https://dcm4che.atlassian.net/browse/WEA-51)] - Add series to the layout (change the layout if it cannot contain all the series)
-   [[WEA-52](https://dcm4che.atlassian.net/browse/WEA-52)] - New measurements tools (three points circle, polyline, perpendicular, parallel, open anlge, four points angle, Cobb's angle) and more display properties
-   [[WEA-62](https://dcm4che.atlassian.net/browse/WEA-62)] - Cut, copy and paste drawings into another view
-   [[WEA-64](https://dcm4che.atlassian.net/browse/WEA-64)] - Computing SUV for PET images
-   [[WEA-65](https://dcm4che.atlassian.net/browse/WEA-65)] - Anonymize DICOM annotations in display
-   [[WEA-66](https://dcm4che.atlassian.net/browse/WEA-66)] - Add in xsd schema httpTag (add tags in http request) and DirectDownloadFile (direct URL, not WADO)
-   [[WEA-67](https://dcm4che.atlassian.net/browse/WEA-67)] - Export the selected view to clipboard (with all annotations in anonymized mode)
-   [[WEA-68](https://dcm4che.atlassian.net/browse/WEA-68)] - New command for loading a DICOM from an URL (dicom:get -r URL)

Improvement

-   [[WEA-41](https://dcm4che.atlassian.net/browse/WEA-41)] - Overriding jnlp template of weasis-pacs-connector
-   [[WEA-42](https://dcm4che.atlassian.net/browse/WEA-42)] - Add Accession Number as a request ID
-   [[WEA-44](https://dcm4che.atlassian.net/browse/WEA-44)] - Limit access for weasis-pacs-connector
-   [[WEA-46](https://dcm4che.atlassian.net/browse/WEA-46)] - weasis-pacs-connector and weasis can be on different servers
-   [[WEA-47](https://dcm4che.atlassian.net/browse/WEA-47)] - Upgrade to Felix framework 3.2.2
-   [[WEA-48](https://dcm4che.atlassian.net/browse/WEA-48)] - Supports WADO xml references file embedded in Java Web Start (jnlp)
-   [[WEA-49](https://dcm4che.atlassian.net/browse/WEA-49)] - From Weasis 1.1.0 the internationalized package (i18n) can be applied to any version
-   [[WEA-54](https://dcm4che.atlassian.net/browse/WEA-54)] - Shows indeterminate progress bar for large unique file (video, multiframe...) of a series
-   [[WEA-55](https://dcm4che.atlassian.net/browse/WEA-55)] - Images with non-square pixels are rectified and displayed as square pixels
-   [[WEA-63](https://dcm4che.atlassian.net/browse/WEA-63)] - Resize the views of layouts
-   [[WEA-69](https://dcm4che.atlassian.net/browse/WEA-69)] - Contextual menu for drawings (when selected)
-   [[WEA-71](https://dcm4che.atlassian.net/browse/WEA-71)] - Some advanced preferences are accessible only by superuser

## [1.0.9](https://github.com/nroduit/weasis/tree/1.0.9) (2011-05-08)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.0.8...1.0.9)

Bug

-   [[WEA-30](https://dcm4che.atlassian.net/browse/WEA-30)] - Java Version 6 Update 24 Breaks Weasis
-   [[WEA-36](https://dcm4che.atlassian.net/browse/WEA-36)] - Cannot resize Jpeg2000 images with negative values (pixel representation = 1)
-   [[WEA-37](https://dcm4che.atlassian.net/browse/WEA-37)] - Cannot run the Weasis portable distribution if executable path contains space

Improvement

-   [[WEA-38](https://dcm4che.atlassian.net/browse/WEA-38)] - Resizing the magnifying lens does not update the image position
-   [[WEA-39](https://dcm4che.atlassian.net/browse/WEA-39)] - Rebuilding thumbnail manually from first, middle or last stack image

## [1.0.8](https://github.com/nroduit/weasis/tree/1.0.8) (2011-03-10)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.0.7...1.0.8)

Bug

-   [[WEA-8](https://dcm4che.atlassian.net/browse/WEA-8)] - Issues when JAI is already installed
-   [[WEA-16](https://dcm4che.atlassian.net/browse/WEA-16)] - Sometimes a thumbnail in the series list cannot be dragged
-   [[WEA-18](https://dcm4che.atlassian.net/browse/WEA-18)] - Image are not sorted correctly when merge again a split Series
-   [[WEA-19](https://dcm4che.atlassian.net/browse/WEA-19)] - Drawings are preserved when changing image with DICOM multiframe
-   [[WEA-21](https://dcm4che.atlassian.net/browse/WEA-21)] - Context menu resets the pan action
-   [[WEA-24](https://dcm4che.atlassian.net/browse/WEA-24)] - Cannot open images when the viewer preferences contain corrupted values
-   [[WEA-26](https://dcm4che.atlassian.net/browse/WEA-26)] - Measurements are deleted when displaying the same Series int two views
-   [[WEA-27](https://dcm4che.atlassian.net/browse/WEA-27)] - FolderSubmitCtrl2 messes up browser sessions (dcm4chee integration)
-   [[WEA-29](https://dcm4che.atlassian.net/browse/WEA-29)] - Clicking on the start button in the cine toolbar accelerates the cine speed
-   [[WEA-32](https://dcm4che.atlassian.net/browse/WEA-32)] - Does not display the whole image when performing a rotation
-   [[WEA-35](https://dcm4che.atlassian.net/browse/WEA-35)] - The pixel values of Pixel Padding Value (0028,0120) and (0028,0121) are random gray levels (should be always black)

New Feature

-   [[WEA-9](https://dcm4che.atlassian.net/browse/WEA-9)] - Integration of new Look and Feels
-   [[WEA-11](https://dcm4che.atlassian.net/browse/WEA-11)] - A new zooming tool: a magnifying lens
-   [[WEA-12](https://dcm4che.atlassian.net/browse/WEA-12)] - Reading encapsulated DICOM objects and DICOM MPEG
-   [[WEA-14](https://dcm4che.atlassian.net/browse/WEA-14)] - Standalone version of Weasis
-   [[WEA-15](https://dcm4che.atlassian.net/browse/WEA-15)] - Add image filtering capacities (blur, sharpen...) in a view
-   [[WEA-20](https://dcm4che.atlassian.net/browse/WEA-20)] - Exporting images into Osirix
-   [[WEA-25](https://dcm4che.atlassian.net/browse/WEA-25)] - Change or add spatial calibration

Improvement

-   [[WEA-10](https://dcm4che.atlassian.net/browse/WEA-10)] - Handle the option of displaying the confirmation message when closing the application
-   [[WEA-13](https://dcm4che.atlassian.net/browse/WEA-13)] - Changing zoom quality
-   [[WEA-22](https://dcm4che.atlassian.net/browse/WEA-22)] - Display limited DICOM information
-   [[WEA-23](https://dcm4che.atlassian.net/browse/WEA-23)] - Open Series by dragging them into the empty central area
-   [[WEA-31](https://dcm4che.atlassian.net/browse/WEA-31)] - No syncrhonization nor cross reference lines in multiframe series
-   [[WEA-33](https://dcm4che.atlassian.net/browse/WEA-33)] - Improve splitting rules for DICOM Multiframe Series
-   [[WEA-34](https://dcm4che.atlassian.net/browse/WEA-34)] - Pause/Resume a Series download by clicking on the progress bar

## [1.0.7](https://github.com/nroduit/weasis/tree/1.0.7) (2010-11-24)
[Full Changelog](https://github.com/nroduit/weasis/compare/1.0.6...1.0.7)

Bug

-   [[WEA-4](https://dcm4che.atlassian.net/browse/WEA-4)] - Does not display properly 8 bits RGB snapshots
-   [[WEA-6](https://dcm4che.atlassian.net/browse/WEA-6)] - Import and export sub-menus are not displayed
-   [[WEA-7](https://dcm4che.atlassian.net/browse/WEA-7)] - Transfer syntax argument does not appear in the WADO request

Improvement

-   [[WEA-5](https://dcm4che.atlassian.net/browse/WEA-5)] - Japanese translation for weasis.

## [1.0.6](https://github.com/nroduit/weasis/tree/1.0.6) (2010-11-04)
Bug

-   [[WEA-2](https://dcm4che.atlassian.net/browse/WEA-2)] - Does not sort Study (by date) and Series (by series number, uid) sometimes
-   [[WEA-3](https://dcm4che.atlassian.net/browse/WEA-3)] - Cannot display the first image downloaded

Task

-   [[WEA-1](https://dcm4che.atlassian.net/browse/WEA-1)] - Initial import of Weasis code and Maven build scripts


\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
