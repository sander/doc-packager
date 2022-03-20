const { Given, When, Then } = require("@cucumber/cucumber");
const assert = require("assert").strict;
const fs = require("fs/promises");
const package = require("../../package.json");
const util = require("util");
const exec = util.promisify(require("child_process").exec);

Given("a", () => {
  console.log("given");
});

When("b", () => {
  console.log("when");
});

Then("c", () => {
  console.log("then");
  assert.equal(1, 1);
});

Given("a project", () => {});

When("I execute the program", () => {});

Then("I get a PDF", () => {
  assert.equal(2, 2);
});

Given("a project with a feature file", async function (featureFile) {
  this.featureFile = featureFile;
  await fs.mkdir("out/features/step_definitions", { recursive: true });
  await fs.writeFile("out/features/example.feature", featureFile + "\n");
  await fs.writeFile(
    "out/features/step_definitions/steps.js",
    `require("@cucumber/cucumber").defineStep(/(.*)/, function(s) {})`
  );
  await fs.writeFile(
    "out/cucumber.js",
    `module.exports = { default: '--publish-quiet' }`
  );
  //
  // }));
});

When("I run the test suite", async function () {
    await exec("npx cucumber-js", { cwd: "out" });
})

When("I package the documentation", async function () {

})

Then("the package contains the lines from this feature file", async function() {
  
})