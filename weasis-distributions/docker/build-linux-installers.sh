#!/bin/bash

##### Prerequisites for building multi-architecture binaries #####
# Required a linux system with amd64 architecture (examples based on Ubuntu)
# Required Docker and qemu (see https://docs.docker.com/desktop/multi-arch/)
# The script must be executed after downloading or building weasis-native.zip

##### Install prerequisites #####
# Install docker: https://docs.docker.com/engine/install/
# Install the required qemu packages: 
### sudo apt-get install qemu binfmt-support qemu-user-static
### docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

##### Update docker images
# unzip weasis-native.zip
# cd weasis-native/build/docker
# docker image rm -f weasis/builder:latest
# docker buildx build --load --platform linux/amd64 -t weasis/builder:latest .
# sudo ./build-linux-installers.sh -a linux/amd64
# docker buildx build --load --platform linux/arm64 -t weasis/builder:latest .
# sudo ./build-linux-installers.sh -a linux/arm64

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

declare -a supportedArc=("linux/amd64" "linux/arm64")

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
echo "        $ sudo ./build-linux-installers.sh -a linux/arm64"
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

IFS=',' read -ra ARCS <<< "$ARC_LIST"
for arc in "${ARCS[@]}"; do
  if [[ ! " ${supportedArc[*]} " == *"$arc"* ]]; then
    echo "$arc is not supported!"
    continue
  fi

  # Must be copied for every build as some binaries are remove by script
  rm -Rf "bin-dist"
  cp -Rf ../../bin-dist "bin-dist"

  # Load the local images
  # docker buildx build --load --platform "$arc" -t weasis/builder:latest .
  docker run --platform "$arc" -it --rm -v "$PWD":/work weasis/builder:latest bash -c "export JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=vfork; cd /work/installer; /work/build/script/package-weasis.sh --jdk /opt/java/openjdk --temp /work/temp"
done

sudo -u "$real_user" mkdir -p "output-dist"
sudo -u "$real_user" cp -Rf "installer/"* "output-dist"