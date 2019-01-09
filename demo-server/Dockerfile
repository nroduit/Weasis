FROM openjdk:8-jre

COPY target/demo-server-0.0.1-SNAPSHOT.jar /usr/local/weasis/weasis-server.jar

WORKDIR /usr/local/weasis/

CMD ["java", "-jar", "weasis-server.jar"]
