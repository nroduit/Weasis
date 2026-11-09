# ${artifactId}

A Weasis 5 plugin generated from the `weasis-plugin-dicom-viewer-archetype` archetype: a tool panel (`SampleTool`), a toolbar (`SampleToolBar`) for the DICOM viewer, and a command namespace (`SampleCommands`, `sample:info`).
How plugins work, what they can contribute and how they reach a workstation: `docs/plugins.md` in the Weasis repository.

## Build

The Weasis modules of version `${weasisVersion}` must be in a Maven repository: `mvn -B -ntp install -DskipTests` in the Weasis checkout puts them in the local one.

```bash
mvn package                               # target/${artifactId}-${version}.jar, the tests of the namespace run
```

## Load it in a development Weasis

A source build of Weasis accepts unsigned plugins (`weasis.plugins.policy=any` in `weasis-launcher/conf/base.json`):

```bash
weasis-cli plugins:add --file target/${artifactId}-${version}.jar
weasis-cli -y weasis:quit                 # then start Weasis again: plugins start at launch
weasis-cli plugins:list                   # user / unverified / started
weasis-cli help sample
weasis-cli sample:info --json
```

After a rebuild, `plugins:add` the new jar and restart.

## Ship it

Declare in `pom.xml` the namespaces the plugin registers (`Weasis-Namespaces`) and the capabilities its verbs need (`Weasis-Capabilities`), then sign the jar with the publisher key:

```bash
java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool keygen keys my-publisher
java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool sign target/${artifactId}-${version}.jar keys/my-publisher.key
```

A site that lists the publisher key (`weasis.plugins.publishers`) loads the plugin under the `site` tier; the default policy of the installers (`core-only`) loads the package only.
