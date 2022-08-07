# Documentation Packager

## Context

**Documentation Packager** enables product developers to combine evergreen and living documentation into human-readable packages, in order to collaborate effectively with stakeholders on complex systems.

## How to use it

Build this project using [sbt](https://www.scala-sbt.org). Run `sbt tasks` to get an overview of possible tasks.

To run acceptance specs, use [Scala CLI](https://scala-cli.virtuslab.org):

```
scala-cli test src/main acceptance
```

## Maintenance guide

The [scripts](scripts/) directory contains several self-documenting scripts. They can be run using [Scala CLI](https://scala-cli.virtuslab.org).
