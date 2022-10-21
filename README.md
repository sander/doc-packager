# Documentation Packager

## Context

**Documentation Packager** enables product developers to combine evergreen and living documentation into human-readable packages, in order to collaborate effectively with stakeholders on complex systems.

## How it works

In your continuous integration workflow, call:

```scala
DocumentationPackager.publish("my-package-name",
  "docs/doc1.md",
  "docs/doc2.md",
  "out/test-results.md")
```

This publishes the three listed files from your working directory to the origin repository on branch `docpkg/my-package-name/<branch-name>`, where `<branch-name>` is the name of your current working branch. This enables easy access to documentation generated during continuous integration.

For an example, see the [package.sc](scripts/package.sc) script and Documentation Packager’s own [published documentation](https://github.com/sander/docpkg/blob/docpkg/docpkg/main/README.md#readme).

## Maintenance guide

Build this project using [Maven](https://maven.apache.org/).
