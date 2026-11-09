# Plugin archetypes

Two Maven archetypes that generate a Weasis 5 plugin: a tool panel and a toolbar (`InsertableFactory` extensions), a command namespace (`SampleCommands`, verb `sample:info`) with its namespace test, the manifest headers the plugin manager reads (`Weasis-Namespaces`, `Weasis-Capabilities`) and a README with the development loop.

- `weasis-plugin-base-viewer-archetype`: the tool and the toolbar attach to the non-DICOM image viewer (`weasis-base-viewer2d`).
- `weasis-plugin-dicom-viewer-archetype`: the same for the DICOM viewer (`weasis-dicom-viewer2d`).

## Install the archetypes locally

From this directory, with the Weasis modules installed (`mvn -B -ntp install -DskipTests` at the root):

```bash
mvn install
```

## Generate a plugin

```bash
mvn archetype:generate -DarchetypeCatalog=local
```

Pick the archetype, then answer `groupId`, `artifactId`, `version`, `package` and `weasisVersion` (the Weasis version the plugin is built against, default `5.0.0-SNAPSHOT`).
Without prompts:

```bash
mvn archetype:generate -B -DarchetypeCatalog=local \
  -DarchetypeGroupId=org.weasis.samples -DarchetypeArtifactId=weasis-plugin-dicom-viewer-archetype -DarchetypeVersion=5.0.0-SNAPSHOT \
  -DgroupId=com.example -DartifactId=my-plugin -Dversion=1.0.0-SNAPSHOT -Dpackage=com.example.myplugin -DweasisVersion=5.0.0-SNAPSHOT
```

`mvn package` in the generated project builds the plugin jar and runs the test of its namespace; its README says how to load it in a development Weasis (`plugins:add`), and how to sign it for a site.
The guide is [docs/plugins.md](../docs/plugins.md).

## Writing an image operation

A display operation extends `AbstractOp`; its contract (revision, `invalidate()`, output ownership) is described in [Display Op Chain](../weasis-core/docs/Display-Op-Chain.md).
