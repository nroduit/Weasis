mvn install -pl weasis-dicom/weasis-dicom-viewer2d -am -DskipTests -q
cd weasis-distributions && mvn clean package -DskipTests -q
pkill -f "AppLauncher" || true; sleep 1
rm -rf /tmp/weasis-build; mkdir -p /tmp/weasis-build
unzip -o target/native-dist/weasis-native.zip -d /tmp/weasis-build/ > /dev/null
export JAVA_HOME=$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home
cd /tmp/weasis-build/bin-dist/weasis
$JAVA_HOME/bin/java -cp weasis-launcher.jar:felix.jar org.weasis.launcher.AppLauncher >weasis-stdout.log 2>weasis-stderr.log &
