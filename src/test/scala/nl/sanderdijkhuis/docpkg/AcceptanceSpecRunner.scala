package nl.sanderdijkhuis.docpkg

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(plugin = Array("pretty"))
class AcceptanceSpecRunner
