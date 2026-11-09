#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.command.CommandVocabulary;

/**
 * The contract of the namespace, pinned without a running application: the vocabulary rules, the
 * declared results, the documented command lines, and what a verb answers.
 */
class SampleCommandsTest {

  @Test
  void theNamespaceFollowsTheVocabularyAndDeclaresItsResults() {
    Object model = new SampleCommands();
    assertAll(
        () -> assertEquals(List.of(), CommandVocabulary.violations("sample", model)),
        () -> assertEquals(List.of(), CommandVocabulary.resultViolations("sample", model)));
  }

  @Test
  void theDocumentedCommandLinesParse() {
    Object model = new SampleCommands();
    assertAll(
        () -> assertDoesNotThrow(() -> CommandModel.parse(model, "sample:info")),
        () -> assertDoesNotThrow(() -> CommandModel.parse(model, "sample:info --json")));
  }

  @Test
  void infoAnswersTheNameAndTheTool() {
    CommandResult result =
        CommandModel.execute(new SampleCommands(), "info", new String[] {"--json"});
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status()),
        () -> assertTrue(result.body().contains("\"tool\":\"sample\""), result.body()));
  }
}
