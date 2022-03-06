const fs = require("fs");
const process = require("process");

const writePackage = require("./writePackage");

const readmeSource = fs.readFileSync("README.md").toString();


  const messages = fs
    .readFileSync("out.json")
    .toString()
    .trim()
    .split("\n")
    .map(JSON.parse);

const out = process.stdout;

writePackage(readmeSource, messages, process.stdout);
