package docpkg.StepDefinitions;

import clojure.lang.IFn;
import clojure.java.api.Clojure;
import io.cucumber.java8.En;

import static clojure.java.api.Clojure.read;
import static clojure.java.api.Clojure.var;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

public class StepDefinitions implements En {
  public StepDefinitions() {
    Given("a BPMN model", () ->
      assertTrue(new File("processes/hello.bpmn").exists())
    );

    When("I render it to PDF", () -> {
      var("clojure.core", "require").invoke(read("docpkg.main"));
      var("docpkg.main", "render-bpmn").invoke();
    });

    Then("I have a PDF file", () ->
      assertTrue(new File("out/hello.pdf").exists())
    );
  }
}
