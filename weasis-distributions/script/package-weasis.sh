#!/bin/bash
# This script makes the multiplatform build with jpackage
#
# Initial script by Nicolas Roduit

# Build Parameters
REVISON_INC="1"
PACKAGE=YES

# The JDK modules and the launch options are defined in launch-options.sh, shared with the
# GitHub workflow building the macOS and Windows installers.
NAME="Weasis"
IDENTIFIER="org.weasis.launcher"

# Aux functions:
die ( ) {
  echo
  echo -e "ERROR: $*"
  exit 1
}

POSITIONAL=()
while [[ $# -gt 0 ]]
do
  key="$1"

  case $key in
    -h|--help)
echo "Usage: package-weasis.sh <options>"
echo "Sample usages:"
echo "    Build an installer for the current platform with the minimal required parameters"
echo "        package-weasis.sh --jdk /home/user/jdk-25"
echo ""
echo "Options:"
echo " --help -h
Print the usage text with a list and description of each valid
option the output stream, and exit"
echo " --input -i
Path of the weasis-native directory"
echo " --output -o
Path of the base output directory.
Default value is the current directory"
echo " --jdk -j
Path of the jdk with the jpackage module"
echo " --temp
Path of the temporary directory during build"
echo " --no-installer
Build only the native binaries not the final installer"
echo " --mac-signing-key-user-name
Key user name of the certificate to sign the bundle"
exit 0
;;
-j|--jdk)
JDK_PATH_UNIX="$2"
shift # past argument
shift # past value
;;
-i|--input)
INPUT_PATH="$2"
shift # past argument
shift # past value
;;
-o|--output)
OUTPUT_PATH="$2"
shift # past argument
shift # past value
;;
--temp)
TEMP_PATH="$2"
shift # past argument
shift # past value
;;
--no-installer)
PACKAGE="NO"
shift # past argument
;;
--mac-signing-key-user-name)
CERTIFICATE="$2"
shift # past argument
shift # past value
;;
*)    # unknown option
POSITIONAL+=("$1") # save it in an array for later
shift # past argument
;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters


curPath=$(dirname "$(readlink -f "$0")")
rootdir="$(dirname "$curPath")"
rootdir="$(dirname "$rootdir")"

echo "rootdir: $rootdir"

if [ ! -d "${INPUT_PATH}" ] ; then
  INPUT_PATH="${rootdir}/bin-dist"
  if [ ! -d "${INPUT_PATH}" ] ; then
    INPUT_PATH="${rootdir}/weasis-distributions/target/native-dist/weasis-native/bin-dist"
  fi
fi

if [ ! -d "${INPUT_PATH}" ] ; then
  die "The input path ${INPUT_PATH} doesn't exist, provide a valid value for --input"
fi

# weasis-core-img is a plain jar of the launcher set (launcher/), renamed *.jar.embedded on a re-run
CORE_IMG=$(ls "$INPUT_PATH"/weasis/launcher/weasis-core-img-*.jar* 2>/dev/null | head -n 1)
[ -n "$CORE_IMG" ] || die "The launcher set (launcher/weasis-core-img-*.jar) is missing from $INPUT_PATH"
ARC_OS=$("$JDK_PATH_UNIX/bin/java" -cp "$CORE_IMG" org.weasis.opencv.natives.NativeLibrary)
if [ -z "$ARC_OS" ] ; then
  die "Cannot get Java system architecture"
fi
machine=$(echo "${ARC_OS}" | cut -d'-' -f1)
arc=$(echo "${ARC_OS}" | cut -d'-' -f2-3)

# Set the JDK modules and the launch options (JDK_MODULES, customOptions, commonOptions)
# shellcheck source=launch-options.sh
source "${curPath}/launch-options.sh" "$machine" || die "Cannot read the launch options"

echo "Platform: $machine"
echo "JDK Modules: $JDK_MODULES"

if [ "$machine" = "windows" ] ; then
  INPUT_PATH_UNIX=$(cygpath -u "$INPUT_PATH")
  OUTPUT_PATH_UNIX=$(cygpath -u "$OUTPUT_PATH")
  RES="${curPath}\resources\\${machine}"
else
  INPUT_PATH_UNIX="$INPUT_PATH"
  OUTPUT_PATH_UNIX="$OUTPUT_PATH"
  RES="${curPath}/resources/$machine"
fi

# Set custom JDK path (>= JDK 11)
export JAVA_HOME=$JDK_PATH_UNIX

WEASIS_VERSION=$(grep -i "weasis.version=" "${curPath}/build.properties" | sed 's/^.*=//')

echo System        = "${ARC_OS}"
echo JDK path        = "${JDK_PATH_UNIX}"
echo Weasis version  = "${WEASIS_VERSION}"
echo Input path      = "${INPUT_PATH}"
if [ "$machine" = "windows" ]
then
  echo Input unix path      = "${INPUT_PATH_UNIX}"
fi

# Specify the required Java version.
# Only major version is checked. Minor version or any other version string info is left out.
REQUIRED_TEXT_VERSION=$(grep -i "jdk.version=" "${curPath}/build.properties" | sed 's/^.*=//')
# Extract major version number for comparisons from the required version string.
# In order to do that, remove leading "1." if exists, and minor and security versions.
REQUIRED_MAJOR_VERSION=$(echo $REQUIRED_TEXT_VERSION | sed -e 's/^1\.//' -e 's/\..*//')

# Check jlink command.
if [ -x "$JDK_PATH_UNIX/bin/jpackage" ] ; then
  JPKGCMD="$JDK_PATH_UNIX/bin/jpackage"
  JAVACMD="$JDK_PATH_UNIX/bin/java"
else
  die "JAVA_HOME is not set and no 'jpackage' command could be found in your PATH. Specify a jdk path >=$REQUIRED_TEXT_VERSION."
fi

# Then, get the installed version
INSTALLED_VERSION=$($JAVACMD -version 2>&1 | awk '/version [0-9]*/ {print $3;}')
echo "Found java version $INSTALLED_VERSION"
echo "Java command path: $JAVACMD"

# Remove double quotes, remove leading "1." if it exists and remove everything apart from the major version number.
INSTALLED_MAJOR_VERSION=$(echo "$INSTALLED_VERSION" | sed -e 's/"//g' -e 's/^1\.//' -e 's/\..*//' -e 's/-.*//')
echo "Java major version: $INSTALLED_MAJOR_VERSION"
if (( INSTALLED_MAJOR_VERSION < REQUIRED_MAJOR_VERSION )) ; then
  die "Your version of java is too low to run this script.\nPlease update to $REQUIRED_TEXT_VERSION or higher"
fi

if [ -z "$OUTPUT_PATH" ] ; then
  OUTPUT_PATH="weasis-$ARC_OS-jdk$INSTALLED_MAJOR_VERSION-$WEASIS_VERSION"
  OUTPUT_PATH_UNIX="$OUTPUT_PATH"
fi


if [ "$machine" = "windows" ] ; then
  INPUT_DIR="$INPUT_PATH\weasis"
  IMAGE_PATH="$OUTPUT_PATH\\${NAME}"
  # JDK 26+ requires WiX 4+ (wix.exe) for MSI packaging.
  if ! command -v wix &> /dev/null ; then
    die "WiX 4+ is required for Windows packaging with JDK 26+.\nInstall the latest version from https://github.com/wixtoolset/wix/releases\nor run: dotnet tool install --global wix"
  fi
else
  IMAGE_PATH="$OUTPUT_PATH/$NAME"
  INPUT_DIR="$INPUT_PATH_UNIX/weasis"
fi

WEASIS_CLEAN_VERSION=$(echo "$WEASIS_VERSION" | sed -e 's/"//g' -e 's/-.*//' -e 's/\(\([0-9]\+\.\)\{2\}[0-9]\+\)\.[0-9]\+/\1/')

# A version with a suffix (5.0.0-beta.1, 5.0.0-SNAPSHOT) is a pre-release. jpackage gets the clean
# version (the MSI and the bundle need x.y.z), the installer files carry the full version and the
# Linux packages take a release below 1 (5.0.0-0.beta.1 sorts before 5.0.0-1), so that the release
# upgrades the preview on every platform; the weasis:// scheme stays with the installed release
# (prerelease-resources.sh). Same rules as the GitHub workflow, see docs/Native-Build-Overview.md.
case "$WEASIS_VERSION" in
  *-*) WEASIS_SUFFIX="${WEASIS_VERSION#*-}" ;;
  *)   WEASIS_SUFFIX="" ;;
esac
WEASIS_FILE_VERSION="$WEASIS_CLEAN_VERSION${WEASIS_SUFFIX:+-$WEASIS_SUFFIX}"
if [ -n "$WEASIS_SUFFIX" ] ; then
  LINUX_RELEASE="0.$(echo "$WEASIS_SUFFIX" | sed 's/[^A-Za-z0-9.]/./g')"
  RES_UNIX="${curPath}/resources/$machine"
  PRERELEASE_RES="$(mktemp -d)/resources"
  bash "${curPath}/prerelease-resources.sh" "$RES_UNIX" "$PRERELEASE_RES" || die "Cannot prepare the pre-release resources"
  if [ "$machine" = "windows" ] ; then
    RES=$(cygpath -w "$PRERELEASE_RES")
  else
    RES="$PRERELEASE_RES"
  fi
  echo "Pre-release $WEASIS_VERSION: package version $WEASIS_CLEAN_VERSION, installer files $WEASIS_FILE_VERSION, Linux release $LINUX_RELEASE, weasis:// not registered"
else
  LINUX_RELEASE="$REVISON_INC"
fi


# Remove pack jar for launcher
rm -f "$INPUT_DIR"/*.jar.pack.gz

# Remove the unrelated native packages
find "$INPUT_DIR"/bundle/weasis-opencv-core-* -type f ! -name '*-'"${ARC_OS}"'-*'  -exec rm -f {} \;

# Embedded launcher set. jpackage adds every plain *.jar found under --input to the class path of
# the native launchers. On Linux and Windows that class path must carry weasis-bootstrap.jar only:
# stage 1 (the bootstrap) builds the class path of stage 2 itself from launcher/, so the set is
# renamed *.jar.embedded to stay off the jpackage class path. On macOS the set runs in the launcher
# process (open-file events reach the first process): it keeps its plain names so that jpackage
# puts it on that class path, where the AOT cache covers it. The sha256sum manifest keeps its
# name. pf4j plugins stay .jar.xz.
for f in "$INPUT_DIR"/launcher/*.jar "$INPUT_DIR"/launcher/*.jar.embedded; do
  [ -f "$f" ] || continue
  if [ "$machine" = "macosx" ] ; then
    case "$f" in *.embedded) mv "$f" "${f%.embedded}" ;; esac
    echo "launcher set on the class path: $(basename "${f%.embedded}")"
  else
    case "$f" in *.embedded) ;; *) mv "$f" "$f.embedded" ;; esac
    echo "embedded set: $(basename "${f%.embedded}").embedded"
  fi
done
[ -f "$INPUT_DIR/weasis-bootstrap.jar" ] || die "weasis-bootstrap.jar is missing from $INPUT_DIR"
rm -f "$INPUT_DIR"/weasis-launcher.jar
# Safety: any other plain jar under --input would be added to the stage-1 class path by jpackage
if [ "$machine" = "macosx" ] ; then
  OTHER_JARS=$(find "$INPUT_DIR" -name '*.jar' ! -path "$INPUT_DIR/weasis-bootstrap.jar" ! -path "$INPUT_DIR/launcher/*")
else
  OTHER_JARS=$(find "$INPUT_DIR" -name '*.jar' ! -path "$INPUT_DIR/weasis-bootstrap.jar")
fi
if [ -n "$OTHER_JARS" ]; then
  die "Plain jars besides weasis-bootstrap.jar under $INPUT_DIR (build weasis-distributions with -P compressXZ):\n$OTHER_JARS"
fi

# Optional deployment specific preparation of the input directory, absent from the public distribution
siteHook="${curPath}/prepare-input-site.sh"
if [[ -f "$siteHook" ]] ; then
  bash "$siteHook" "$INPUT_PATH_UNIX/weasis" || die "Cannot prepare the input directory"
fi

# Pre-sign ALL native libraries embedded in JARs (macOS only).
# jpackage --mac-sign will sign the extracted content of the app image,
# but dylibs INSIDE jars must be signed before jpackage repackages them.
if [ "$machine" = "macosx" ] && [[ -n "$CERTIFICATE" ]] ; then
    for jar_file in "$INPUT_DIR"/*.jar "$INPUT_DIR"/launcher/*.jar "$INPUT_DIR"/launcher/*.jar.embedded "$INPUT_DIR"/bundle/*.jar; do
      [ -f "$jar_file" ] || continue
      if unzip -l "$jar_file" 2>/dev/null | grep -qE '\.(dylib|jnilib)$'; then
        echo "--- Signing native libs in: $jar_file"
        tmpdir=$(mktemp -d)
        unzip -q "$jar_file" -d "$tmpdir"
        while IFS= read -r -d '' lib; do
          echo "  Signing: $lib"
          codesign --force --options runtime --timestamp \
            --sign "$CERTIFICATE" "$lib"
        done < <(find "$tmpdir" \( -name "*.dylib" -o -name "*.jnilib" \) -print0)
        jar_name=$(basename "$jar_file")
        (cd "$tmpdir" && jar cf "/tmp/$jar_name" .)
        mv -f "/tmp/$jar_name" "$jar_file"
        rm -rf "$tmpdir"
      fi
    done
fi

# Remove previous package
if [ -d "${OUTPUT_PATH}" ] ; then
  rm -rf "${OUTPUT_PATH}"
fi

if [ -z "$TEMP_PATH" ] ; then
  declare -a tmpArgs=()
else
  declare -a tmpArgs=("--temp" "$TEMP_PATH")
fi

if [ -d "${TEMP_PATH}" ] ; then
  rm -rf "${TEMP_PATH}"
fi

declare -a signArgs=()
if [ "$machine" = "windows" ] ; then
  DICOMIZER_CONFIG="Dicomizer=$RES\dicomizer-launcher.properties"
  CLI_CONFIG="weasis-cli=$RES\weasis-cli-launcher.properties"
else
  DICOMIZER_CONFIG="Dicomizer=$RES/dicomizer-launcher.properties"
  CLI_CONFIG="weasis-cli=$RES/weasis-cli-launcher.properties"
  if [ "$machine" = "macosx" ] ; then
    if [[ -n "$CERTIFICATE" ]] ; then
      signArgs=("--mac-package-identifier" "$IDENTIFIER" "--mac-signing-key-user-name" "$CERTIFICATE" "--mac-sign")
    else
      signArgs=("--mac-package-identifier" "$IDENTIFIER")
    fi
  fi
fi

# On Linux the deb/rpm are built in a single jpackage step from --input (see below), so the
# standalone app-image is only needed for the other platforms or for --no-installer builds.
# --jlink-options replaces jpackage's defaults on purpose: the default set includes
# --strip-native-commands, but bin/java must remain in the runtime because the AOT cache
# assembly phase (-XX:AOTCacheOutput) spawns <java.home>/bin/java at exit.
# --generate-cds-archive ships a base CDS archive that speeds up the stage-1 bootstrap JVM and
# the first (untrained) launch of stage 2.
JLINK_OPTIONS="--strip-debug --no-man-pages --no-header-files --generate-cds-archive"

if [ "$machine" != "linux" ] || [ "$PACKAGE" = "NO" ] ; then
  # shellcheck disable=SC2154 # customOptions and commonOptions come from launch-options.sh
  $JPKGCMD --type app-image --input "$INPUT_DIR" --dest "$OUTPUT_PATH" --name "$NAME" \
  --jlink-options "$JLINK_OPTIONS" \
  --main-jar weasis-bootstrap.jar --main-class org.weasis.bootstrap.Bootstrap --add-modules "$JDK_MODULES" \
  --add-launcher "${DICOMIZER_CONFIG}" --add-launcher "${CLI_CONFIG}" --resource-dir "$RES"  --app-version "$WEASIS_CLEAN_VERSION" \
  "${tmpArgs[@]}" --verbose "${signArgs[@]}" "${customOptions[@]}" "${commonOptions[@]}"
fi


if [ "$PACKAGE" = "YES" ] ; then
  VENDOR="Weasis Team"
  COPYRIGHT="© 2009-2026 Weasis Team"
  if [ "$machine" = "windows" ] ; then
    [ "$arc" = "aarch64" ]  && UPGRADE_UID="3aedc24e-48a8-4623-ab39-0c3c01c7383c" || UPGRADE_UID="3aedc24e-48a8-4623-ab39-0c3c01c7383a"
    $JPKGCMD --type "msi" --app-image "$IMAGE_PATH" --dest "$OUTPUT_PATH" --name "$NAME" --resource-dir "$RES/msi/${arc}" \
    --license-file "$INPUT_PATH\Licence.txt" --description "Weasis DICOM viewer" --win-upgrade-uuid "$UPGRADE_UID"  \
    --win-menu --win-menu-group "$NAME" --copyright "$COPYRIGHT" --app-version "$WEASIS_CLEAN_VERSION" \
    --vendor "$VENDOR" --file-associations "${curPath}\file-associations.properties" "${tmpArgs[@]}" --verbose
    mv "$OUTPUT_PATH_UNIX/$NAME-$WEASIS_CLEAN_VERSION.msi" "$OUTPUT_PATH_UNIX/$NAME-$WEASIS_FILE_VERSION-${arc}.msi"
  elif [ "$machine" = "linux" ] ; then
    declare -a installerTypes=("deb" "rpm")
    for installerType in "${installerTypes[@]}"; do
      if [ "${installerType}" = "rpm" ]; then
        DEPENDENCIES=""
        declare -a typeArgs=("--linux-rpm-license-type" "EPL-2.0")
      else
        DEPENDENCIES="libstdc++6, libgcc1"
        declare -a typeArgs=("--linux-deb-maintainer" "Nicolas Roduit")
      fi
      # Build the deb/rpm in a single step directly from --input (not from a prebuilt --app-image).
      # On JDK 26+ the two-step "--type deb --app-image <image>" path fails to resolve the launcher
      # icons from --resource-dir and falls back to the default JavaApp.png, so both the main and the
      # Dicomizer launchers end up with the generic Java icon. The single-step build resolves them.
      # shellcheck disable=SC2154 # customOptions and commonOptions come from launch-options.sh
      $JPKGCMD --type "$installerType" --input "$INPUT_DIR" --dest "$OUTPUT_PATH" --name "$NAME" \
      --jlink-options "$JLINK_OPTIONS" \
      --main-jar weasis-bootstrap.jar --main-class org.weasis.bootstrap.Bootstrap --add-modules "$JDK_MODULES" \
      --add-launcher "${DICOMIZER_CONFIG}" --add-launcher "${CLI_CONFIG}" --resource-dir "$RES" \
      --license-file "$INPUT_PATH/Licence.txt" --description "Weasis DICOM viewer" --vendor "$VENDOR" \
      --copyright "$COPYRIGHT" --app-version "$WEASIS_CLEAN_VERSION" --file-associations "${curPath}/file-associations.properties" \
      --linux-app-release "$LINUX_RELEASE" --linux-package-name "weasis" \
      "${typeArgs[@]}" \
      --linux-menu-group "Viewer;MedicalSoftware;Graphics;" --linux-app-category "science" --linux-package-deps "${DEPENDENCIES}" \
      --linux-shortcut "${customOptions[@]}" "${commonOptions[@]}" "${tmpArgs[@]}" --verbose
      if [ -d "${TEMP_PATH}" ] ; then
        rm -rf "${TEMP_PATH}"
      fi
    done
  elif [ "$machine" = "macosx" ] ; then
    # Do NOT pass --mac-sign here: the app bundle was already signed with proper
    # --options runtime + --timestamp in the app-image step above.
    # Using --mac-sign at the pkg stage causes jpackage to re-sign every dylib
    # without --timestamp, which breaks Apple notarization.
    $JPKGCMD --type "pkg" --app-image "$IMAGE_PATH.app" --dest "$OUTPUT_PATH" --name "$NAME" --resource-dir "$RES" \
    --license-file "$INPUT_PATH/Licence.txt" --copyright "$COPYRIGHT" --app-version "$WEASIS_CLEAN_VERSION" \
    --mac-package-identifier "$IDENTIFIER" "${tmpArgs[@]}" --verbose

    UNSIGNED_PKG="$OUTPUT_PATH/$NAME-$WEASIS_CLEAN_VERSION.pkg"
    SIGNED_PKG="$OUTPUT_PATH/$NAME-$WEASIS_FILE_VERSION-${arc}.pkg"
    if [[ -n "$CERTIFICATE" ]] ; then
      # Sign the unsigned .pkg with the Developer ID Installer certificate.
      # productsign only wraps the outer package; it does not touch the app contents.
      INSTALLER_ID=$(security find-identity -v -p basic \
        | grep "Developer ID Installer" | head -1 | awk -F'"' '{print $2}')
      echo "Signing pkg with installer identity: $INSTALLER_ID"
      productsign --sign "$INSTALLER_ID" --timestamp "$UNSIGNED_PKG" "$SIGNED_PKG"
      rm -f "$UNSIGNED_PKG"
    else
      mv "$UNSIGNED_PKG" "$SIGNED_PKG"
    fi
  fi
fi
