Summary: APPLICATION_DESCRIPTION
Name: APPLICATION_PACKAGE
Version: APPLICATION_VERSION
Release: 1
License: APPLICATION_LICENSE_TYPE
Vendor: APPLICATION_VENDOR
Prefix: INSTALLATION_DIRECTORY
Provides: APPLICATION_PACKAGE
Autoprov: 0
Autoreq: 0
PACKAGE_DEPENDENCIES

#avoid ARCH subfolder
%define _rpmfilename %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm

#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but 
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

%description
APPLICATION_DESCRIPTION

%prep

%build

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}INSTALLATION_DIRECTORY
cp -r %{_sourcedir}/APPLICATION_FS_NAME %{buildroot}INSTALLATION_DIRECTORY

%files
APPLICATION_LICENSE_FILE
INSTALLATION_DIRECTORY/APPLICATION_FS_NAME

%post
if [ "CREATE_JRE_INSTALLER" != "true" ]; then
    ADD_LAUNCHERS_INSTALL
    xdg-desktop-menu install --novendor INSTALLATION_DIRECTORY/APPLICATION_FS_NAME/Dicomizer.desktop	
    xdg-desktop-menu install --novendor INSTALLATION_DIRECTORY/APPLICATION_FS_NAME/APPLICATION_LAUNCHER_FILENAME.desktop
    FILE_ASSOCIATION_INSTALL
fi

%preun
if [ "CREATE_JRE_INSTALLER" != "true" ]; then
    ADD_LAUNCHERS_REMOVE
    xdg-desktop-menu uninstall --novendor INSTALLATION_DIRECTORY/APPLICATION_FS_NAME/Dicomizer.desktop
    xdg-desktop-menu uninstall --novendor INSTALLATION_DIRECTORY/APPLICATION_FS_NAME/APPLICATION_LAUNCHER_FILENAME.desktop
    FILE_ASSOCIATION_REMOVE
fi

%clean
