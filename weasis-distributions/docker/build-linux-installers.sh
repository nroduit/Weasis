#!/bin/bash

##### Prerequisites for building multi-architecture binaries #####
# Required a linux system with x86_64 or amd64 architecture (examples based on Ubuntu)
# Required Docker and qemu (see https://docs.docker.com/desktop/multi-arch/)
# Required XFS driver for simulating 32-bit file system
# The script must be executed from weasis-native folder, see Build Weasis: https://nroduit.github.io/en/getting-started/building-weasis/

##### Install prerequisites #####
# Install docker: https://docs.docker.com/engine/install/
# Install the required qemu packages: 
### sudo apt-get install qemu binfmt-support qemu-user-static
### docker run --rm --privileged multiarch/qemu-user-static --reset -p yes
# Install the required XFS packages:
### sudo apt-get install xfsprogs

##### Update docker images
# docker image rm -f weasis/builder:latest
# docker buildx build --platform linux/amd64,linux/arm64,linux/arm/v7 -t weasis/builder:latest .
# docker buildx build --load --platform linux/amd64 -t weasis/builder:latest .
# sudo ./build-linux-installers.sh -a linux/amd64
# docker buildx build --load --platform linux/arm64 -t weasis/builder:latest .
# sudo ./build-linux-installers.sh -a linux/arm64
# docker buildx build --load --platform linux/arm/v7 -t weasis/builder:latest .
# sudo ./build-linux-installers.sh -a linux/arm/v7

# Aux functions:
die ( ) {
  echo -e "ERROR: $*" >&2
  exit 1
}

# This script must be executed with sudo
if ! [ "$(id -u)" = 0 ]; then
   die "The script need to be run as root."
   exit 1
fi

declare -a supportedArc=("linux/amd64" "linux/arm64" "linux/arm/v7")

if [ "$SUDO_USER" ]; then
    real_user=$SUDO_USER
else
    real_user=$(whoami)
fi

POSITIONAL=()
while [[ $# -gt 0 ]]
do
  key="$1"

  case $key in
    -h|--help)
echo "Usage: build-all-linux.sh <options>"
echo "Sample usages:"
echo "    Build an installer for the arm platforms"
echo "        $ sudo ./build-linux-installers.sh -a linux/arm/v7,linux/arm64"
echo ""
echo "Options:"
echo " --help -h
    Print the usage text with a list and description of each valid
    option the output stream, and exit"
echo " --architecture -a
    List of Docker architecture (values separated by a comma).
    Example: $ sudo ./build-linux-installers.sh -a linux/amd64,linux/arm64"
exit 0
;;
-a|--architecture)
ARC_LIST="$2"
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

if [[ ! "$ARC_LIST" ]]; then
  die "Missing argument '-a', see --help"
fi


# Build multi-arch local docker images
#docker buildx build --platform "$ARC_LIST" -t weasis/builder:latest .

PWD=$(pwd)
DISK="disk-jfs.img"

# Build a docker volume: file system for 32 and 64-bit:
if [[ ! -f "$DISK" ]]; then
  sudo -u "$real_user" fallocate -l 2G "$DISK"
  sudo -u "$real_user" mkfs.jfs "$DISK" -q
fi

DISK_FOLDER="disk"
# Build a docker volume: file system for 32 and 64-bit:
if [[ ! -d "$DISK_FOLDER" ]]; then
  sudo -u "$real_user" mkdir "$DISK_FOLDER"
fi

mount -o loop "$DISK" "$DISK_FOLDER"

rm -Rf "$DISK_FOLDER/script"
mkdir -p "$DISK_FOLDER/installer"
cp -f target/native-dist/weasis-native.zip "$DISK_FOLDER"/
cp -Rf script "$DISK_FOLDER"/


IFS=',' read -ra ARCS <<< "$ARC_LIST"
for arc in "${ARCS[@]}"; do
  if [[ ! " ${supportedArc[*]} " == *"$arc"* ]]; then
    echo "$arc is not supported!"
    continue
  fi

  rm -Rf "$DISK_FOLDER/weasis-native"
  unzip -o "$DISK_FOLDER/weasis-native.zip" -d "$DISK_FOLDER/weasis-native"

  # Load the local images
  # docker buildx build --load --platform "$arc" -t weasis/builder:latest .
  if [[ "$arc" == *"64"* || "$arc" = "linux/s390x" ]]; then
    docker run --platform "$arc" -it --rm -v "$PWD/$DISK_FOLDER":/work weasis/builder:latest bash -c "export JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=vfork; cd /work/installer; /work/script/package-weasis.sh --input /work/weasis-native --jdk /opt/java/openjdk/ --temp /work/temp"
  else
    echo "32-bit needs to copy jdk on 32-bit file system"
    docker run --platform "$arc" -it --rm -v "$PWD/$DISK_FOLDER":/work weasis/builder:latest bash -c "cp -r /opt/java/openjdk /work/; export JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=vfork; cd /work/installer; /work/script/package-weasis.sh --input /work/weasis-native --jdk /work/openjdk/ --temp /work/temp"
  fi
done

sudo -u "$real_user" mkdir -p "target/installer"
sudo -u "$real_user" cp -Rf "$DISK_FOLDER/installer/"* "target/installer"

umount "$DISK_FOLDER"
rmdir "$DISK_FOLDER"
rm -f "$DISK"
