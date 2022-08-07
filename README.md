# Documentation Packager

## Context

**Documentation Packager** enables product developers to combine evergreen and living documentation into human-readable packages, in order to collaborate effectively with stakeholders on complex systems.

## How to use it

To run acceptance and unit tests, use [Scala CLI](https://scala-cli.virtuslab.org):

```
scala-cli test src/main acceptance
```

To just run unit tests:

```
scala-cli test src/main
```

## Maintenance guide

The [scripts](scripts/) directory contains several self-documenting scripts. They can be run using [Scala CLI](https://scala-cli.virtuslab.org).
