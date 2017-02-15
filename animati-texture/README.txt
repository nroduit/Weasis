﻿**COPYRIGHT INFO********************************************************************************


ANIMATI SISTEMAS DE  INFORMATICA LTDA
CNPJ No. 09.504.718/0001-90,
Av. Nossa Senhora Medianeira, n. 1321,
Pavimento 2, Sala 105,
Santa Maria/RS - Brazil
CEP: 97.060-003


Copyright 2015 - All Rights Reserved.
No part of this publication or included files in this project may be reproduced, distributed, transmitted or published in any form or by any means, including photocopying, recording, or other electronic or mechanical methods, without the prior written permission of the publisher.


********************************************************************************************************




DOC VERSION 1.0 


### Animati TextureDicom-Weasis Demo ###


Supported Video Cards:
nVidia: GeForce 8000 or 9000, or 200 ou higher
AMD ATI: Radeon HD (2000 ou higher)


### Build and Run Instructions ###


- Build and Run the same way you do with Weasis, the only diference is that you have to install TextureDicom manually.
The .jar is on /animati-texture/texture/animati-lib/br/com/animati/texturedicom_pg folder. If you use Netbeans, you just have open de Dependency folder, click with the right button on the TextureDicom entry and chose Instal manually. Or you can do it using maven command line.

ex.: mvn install:install-file -Dfile=TextureDicom_1110.jar -DgroupId=br.com.animati -DartifactId=texturedicom_pg -Dversion=1.1.10 -Dpackaging=jar

@NOTE : this shouldn't be necessary anymore since animati-texture artifact use now a local file repository

### About the modules ###


## JOGL ##


This module just includes all needed dependencies for TextureDicom.
We don't know if it is the best way to do this, but this is the way we made is works. It is tested for windows, Mac and Ubuntu.


## texture ##


Includes the .jar itself, and the codec is responsible to make dicom opened in weasis to became texture data.
ImageSeriesFactory is responsible to build the texture object.
ImageDicomSeries is the object that represents all the data needed to show one dicom-series as a texture on video card.


##  Animati-mpr3d ##


Animati-mpr3d is our first attempt of using texture and weasis together.
As the panel that shows texture has to extend JGLPanel, we cannot use DefaultView2d, so we had to build almost everything. As we did it, we started using an interface for the viewers (GridElement), so its possible to have different types of viewer on a same grid (or ViewPlugin).
The graphics were the worst to adapt. As you can see, my fix looks ugly - but I don't like the alternative of re-do everything. Maybe we can do something about that together.
You will also notice that we use a different Events-system. It leaves the implementation of the actions more to the viewers, and the events-handler (EventPublisher) just handles the events.




## Observations  ##


We have inserted a basic DRM (Digital Rights Management) in the project. So, every time the module is launched, the application performs a server check in your host. If the test passes all the features are released for use.


In this first release we have implemented user interfaces for the features below:
- Stack navigation/Scroll;
- Multiplanar Reformatting and Oblique Axis;
- Intensity Projection: MIP, MinIP and AIP;
- Filters application (with previous defined Transfer Function);
- Basic VR view (need change layout to 2x2 to display it);


Still planning the user interfaces and the best way for do it inside Weasis:
- Curved MPR
- Image and Video Rendering and its export
- Volume rendering with transfer function specification, selection clipping, and so;
- 4D Image Series