# Build project from Maven Archetype #

## Install Maven Archetype locally ##
- From the root directory of an archetype execute: **mvn install**

## Generate a sample project ##
- Execute the following command: **mvn archetype:generate -DarchetypeCatalog=local**
- Select the archetype:
    * weasis-plugin-base-viewer-archetype (example of a toolbar and a tool for the non DICOM viewer)
    * weasis-plugin-dicom-viewer-archetype (example of a toolbar and a tool for the DICOM viewer)