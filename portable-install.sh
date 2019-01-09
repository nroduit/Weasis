#!/bin/bash

mvn clean install
mvn install:install-file -Dfile=./google-demo-client/target/google-demo-service-0.0.1-SNAPSHOT-jar-with-dependencies.jar -DgroupId=com.codeminders -DartifactId=google-demo-service-jar-with-dependencies -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar

cd ./weasis-distributions
mvn clean package -Dportable=true -P pack200