#!/bin/bash
#export KEYSTORE_LOCATION=""
#export CERT_ALIAS=""
#export KEYSTORE_PASSWORD=""

mvn clean install
mvn install:install-file -Dfile=./google-demo-client/target/google-demo-service-0.0.1-SNAPSHOT-jar-with-dependencies.jar -DgroupId=com.codeminders -DartifactId=google-demo-service-jar-with-dependencies -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar

cd ./weasis-distributions
mvn clean package -Djarsigner.alias="${CERT_ALIAS}" -Djarsigner.storepass="${KEYSTORE_PASSWORD}" -Djarsigner.keystore="${KEYSTORE_LOCATION}" -Dportable=true

unzip ./target/portable-dist/weasis-portable.zip -d ./target/portable-dist/
mkdir -p ../demo-server/src/main/resources/public/weasis/
cp ./target/resources.zip ../demo-server/src/main/resources/public/weasis/
cp ./target/portable-dist/weasis/*.jar ../demo-server/src/main/resources/public/weasis/
cp -r ./target/portable-dist/weasis/conf ../demo-server/src/main/resources/public/weasis/
cp -r ./target/portable-dist/weasis/bundle ../demo-server/src/main/resources/public/weasis/
cp -r ./client_secrets.json ../demo-server/src/main/resources/public/weasis/

cd ../demo-server/
mvn clean install