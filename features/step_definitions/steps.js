const { Given, When, Then } = require("@cucumber/cucumber");
const assert = require("assert").strict;

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

Given("a project", () => {})

When("I execute the program", () => {})

Then("I get a PDF", () => { assert.equal(2,2)})