<?xml version="1.0" encoding="UTF-8"?>

<jnlp spec="1.6+" version="" codebase="">
    <information>
        <title>Weasis</title>
        <vendor>Weasis Team</vendor>

        <description>DICOM images viewer</description>
        <description kind="short">An application to visualize and analyze DICOM images.</description>

        <description kind="one-line">DICOM images viewer</description>
        <description kind="tooltip">Weasis</description>
    </information>

    <security>
        <all-permissions/>
    </security>

    <resources>
        <!-- Requires Java SE 8 for Weasis 2.5 and superior -->
        <java version="9+" href="http://java.sun.com/products/autodl/j2se"
              java-vm-args="--add-modules java.xml.bind --add-exports=java.base/sun.net.www.protocol.http=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.https=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.file=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.ftp=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.jar=ALL-UNNAMED --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens=java.desktop/javax.imageio=ALL-UNNAMED --add-opens=java.desktop/com.sun.awt=ALL-UNNAMED"
              initial-heap-size="${ihs}" max-heap-size="${mhs}"/>
        <java version="9+"
              java-vm-args="--add-modules java.xml.bind --add-exports=java.base/sun.net.www.protocol.http=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.https=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.file=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.ftp=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.jar=ALL-UNNAMED --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens=java.desktop/javax.imageio=ALL-UNNAMED --add-opens=java.desktop/com.sun.awt=ALL-UNNAMED"
              initial-heap-size="${ihs}" max-heap-size="${mhs}"/>
        <j2se version="1.8+" href="http://java.sun.com/products/autodl/j2se" initial-heap-size="${ihs}"
              max-heap-size="${mhs}"/>
        <j2se version="1.8+" initial-heap-size="${ihs}" max-heap-size="${mhs}"/>

        <jar href="${cdb}/weasis-launcher.jar" main="true"/>
        <jar href="${cdb}/felix.jar"/>
        <jar href="${cdb}/substance.jar"/>
        <jar href="${cdb}/google-demo.jar"/>

        <!-- Avoiding Unnecessary Update Checks -->
        <property name="jnlp.versionEnabled" value="${jnlp_jar_version}"/>

        <!-- Allows to get files in pack200 compression, only since Weasis 1.1.2 -->
        <property name="jnlp.packEnabled" value="false"/>

        <!-- ================================================================================================================= -->
        <!-- Security Workaround. Add prefix "jnlp.weasis" for having a fully trusted application without signing jnlp (only since
            weasis 1.2.9), http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6653241 -->

        <!-- Required parameter. Define the location of config.properties (the OSGI configuration and the list of plug-ins to install/start) -->
        <property name="jnlp.weasis.felix.config.properties" value="${cdb}/conf/config.properties"/>

    <#--<!-- Optional parameter. Define the location of ext-config.properties (extend/override config.properties) &ndash;&gt;-->
    <#--<property name="jnlp.weasis.felix.extended.config.properties" value="${cdb-ext}/conf/ext-config.properties" />-->

        <!-- Required parameter. Define the code base of Weasis for the JNLP -->
        <property name="jnlp.weasis.weasis.codebase.url" value="${cdb}"/>

        <!-- Optional parameter. Define the code base ext of Weasis for the JNLP -->
    <#--<property name="jnlp.weasis.weasis.codebase.ext.url" value="${cdb-ext}" />-->

        <!-- Required parameter. OSGI console parameter -->
        <property name="jnlp.weasis.gosh.args" value="-sc telnetd -p 17179 start"/>

        <!-- Optional parameter. Allows to have the Weasis menu bar in the top bar on Mac OS X (works only with the native Aqua
            look and feel) -->
        <property name="jnlp.weasis.apple.laf.useScreenMenuBar" value="true"/>

    <#--<!-- Optional parameter. Allows to get plug-ins translations &ndash;&gt;-->
    <#--<property name="jnlp.weasis.weasis.i18n" value="${cdb}/../weasis-i18n" />-->

        <!-- Optional Weasis Documentation -->
        <!-- <property name="jnlp.weasis.weasis.help.url" value="${cdb}/../weasis-doc" /> -->
    </resources>

    <application-desc main-class="org.weasis.launcher.WebstartLauncher">
        <argument>$dicom:get</argument>
        <argument>--portable</argument>
    </application-desc>

</jnlp>