#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
import org.weasis.core.api.command.Since;
import picocli.CommandLine.Command;

/**
 * The command namespace of this plugin: every feature is a verb first, the UI (the tool and the
 * toolbar) calls the same code. The verbs are reachable from launch arguments, weasis:// links,
 * weasis-cli, scripts and AI agents; "help sample" describes them. The namespace must be listed in
 * the Weasis-Namespaces header of the manifest (pom.xml), and a verb that reads or writes files or
 * the network declares it with @Needs(Capability.X), listed in Weasis-Capabilities.
 */
@Extension
@Command(
    name = "sample",
    description = "The sample plugin: what it is and the tool it adds.",
    subcommands = {SampleCommands.Info.class})
@Since("1.0")
public class SampleCommands implements CommandExtension {

  public static final String NAMESPACE = "sample";

  @Override
  public String getNamespace() {
    return NAMESPACE;
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** A verb: options and parameters as Picocli fields, the work in run(), the answer declared. */
  @Command(name = "info", description = "The name of this plugin and the id of its tool panel.")
  @Since("1.0")
  static class Info extends AbstractVerb implements Returns {

    @Override
    protected void run() {
      JsonObject result =
          Json.createObjectBuilder()
              .add("name", SampleTool.BUTTON_NAME)
              .add("namespace", NAMESPACE)
              .add("tool", SampleTool.TOOL_ID)
              .build();
      if (isJson()) {
        print(result.toString());
      } else {
        print(result.getString("name") + " (tool " + result.getString("tool") + ")");
      }
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("name", String.class, "The name of the plugin")
          .field("namespace", String.class, "The command namespace of the plugin")
          .field("tool", String.class, "The id of the tool panel the plugin adds")
          .build();
    }
  }
}
