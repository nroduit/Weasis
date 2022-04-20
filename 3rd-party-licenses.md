# License overview of included 3rd party libraries

Weasis and the accompanying materials are made available under the terms of
the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0) or
the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

However, Weasis includes several third-party Open-Source libraries, which are licensed under their
own Open Source licenses.

## Libraries or code directly included in Weasis

The licenses of the libraries used in Weasis are visible in the [Maven dependencies](https://github.com/nroduit/Weasis/network/dependencies).


## OpenJDK (Java Runtime)

The OpenJDK provided with the native installer is the adoptium distribution, see the [related licenses](https://projects.eclipse.org/projects/adoptium.temurin).

## Icons and resources

### JetBrains icons list

License: Apache 2.0 license  
https://jetbrains.design/intellij/resources/icons_list/

### Icons by Icons8

License: MIT 
https://github.com/icons8/welovesvg/blob/master/LICENSE.md

### Other icons and resources

Licenses: Apache 2.0 license

## Native library based on OpenCV for DICOM image processing and compression

### weasis-core-img
This project [weasis-core-img](https://github.com/nroduit/weasis-core-img) is the Java wrapper of the native library.

Licenses: Eclipse Public License 2. or Apache 2.0 license

### OpenCV

License: Apache 2.0 license
https://opencv.org/license/

### DICOM implementation in OpenCV

The implementation for reading, writing and transcoding DICOM Images has been developed by [Nicolas Roduit](https://github.com/nroduit) with the same license as the OpenCV project (Apache 2.0).

The specific codecs used with this implementation are:

- [OpenJPEG](https://github.com/uclouvain/openjpeg/blob/master/LICENSE) with BSD License
- [CharLS](https://github.com/team-charls/charls/blob/master/LICENSE.md) with BSD License
- IJG version 6b license:
    ```
    The authors make NO WARRANTY or representation, either express or implied,
    with respect to this software, its quality, accuracy, merchantability, or
    fitness for a particular purpose. This software is provided "AS IS", and you,
    its user, assume the entire risk as to its quality and accuracy.
    
    This software is copyright (C) 1991-1998, Thomas G. Lane.
    All Rights Reserved except as specified below.
    
    Permission is hereby granted to use, copy, modify, and distribute this
    software (or portions thereof) for any purpose, without fee, subject to these
    conditions:
    (1) If any part of the source code for this software is distributed, then this
    README file must be included, with this copyright and no-warranty notice
    unaltered; and any additions, deletions, or changes to the original files
    must be clearly indicated in accompanying documentation.
    (2) If only executable code is distributed, then the accompanying
    documentation must state that "this software is based in part on the work of
    the Independent JPEG Group".
    (3) Permission for use of this software is granted only if the user accepts
    full responsibility for any undesirable consequences; the authors accept
    NO LIABILITY for damages of any kind.
    
    These conditions apply to any software derived from or based on the IJG code,
    not just to the unmodified library. If you use our work, you ought to
    acknowledge us.
    
    Permission is NOT granted for the use of any IJG author's name or company name
    in advertising or publicity relating to this software or products derived from
    it. This software may be referred to only as "the Independent JPEG Group's
    software".
    
    We specifically permit and encourage the use of this software as the basis of
    commercial products, provided that all warranty or liability claims are
    assumed by the product vendor.
    ```
