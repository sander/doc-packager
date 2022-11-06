# Documentation Packager

## Context

**Documentation Packager** enables product developers to combine evergreen and living documentation into human-readable packages, in order to collaborate effectively with stakeholders on complex systems.

## How to install it

Run:

```
cargo install --git https://github.com/sander/docpkg
```

Optionally add a `--rev` parameter to pin the commit to use.

## How it works

Create a manifest file `Docpkg.toml` in your project root directory. In your continuous integration workflow, call `docpkg publish .`.

This publishes the three listed files from your working directory to the origin repository on branch `docpkg/my-package-name/<branch-name>`, where `<branch-name>` is the name of your current working branch. This enables easy access to documentation generated during continuous integration.

For a functional example, see the [Docpkg.toml](https://github.com/sander/docpkg/blob/main/Docpkg.toml) script and Documentation Packager’s own [published documentation](https://github.com/sander/docpkg/blob/docpkg/docpkg/main/README.md#readme). For a workflow example, see the GitHub Actions workflow [example.yml](https://github.com/sander/docpkg/blob/main/.github/workflows/example.yml) and [its runs](https://github.com/sander/docpkg/actions/workflows/example.yml).

For help, call `docpkg help`.

## Maintenance guide

Build this project using [Cargo](https://doc.rust-lang.org/cargo/).
