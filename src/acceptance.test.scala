//> using lib "com.github.sbt:junit-interface:0.13.3"
//> using lib "io.cucumber::cucumber-scala:8.7.0"
//> using lib "io.cucumber:cucumber-junit:7.6.0"
//> using lib "junit:junit:4.13.2"
//> using lib "org.scalameta::munit:0.7.29"
// Do not update to 1.0.0-M6: https://github.com/scalameta/munit/issues/540
//> using testFramework "com.novocode.junit.JUnitFramework"
//> using resourceDirs "../features"

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(plugin = Array("pretty"), tags = "not (@planned or @wip)")
class AcceptanceSpecRunner