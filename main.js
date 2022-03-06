const fs = require("fs");
const process = require("process");
const documentation = require("./package.json").documentation;

const writePackage = require("./writePackage");

const readmeSource = fs.readFileSync(documentation.readme).toString();

const messages = fs
  .readFileSync(documentation.cucumberMessages)
  .toString()
  .trim()
  .split("\n")
  .map(JSON.parse);

const out = process.stdout;

writePackage(documentation.title, readmeSource, messages, process.stdout);
