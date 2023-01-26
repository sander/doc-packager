# How to track compliance data?

## Introduction

Use Documentation Packager to keep track of *standards*, their *requirements*, *audits* on compliance with these requirements, verifying *controls* over the course of several *releases* that your documentation package describes.

In this compliance management context, we assume that the documentation package is about some system. This system could for example be a standalone application or a collection of components providing some service. Furthermore, we mean by:

- *Release*: a first or substantially changed version of the system that is subject to documentation.
- *Requirement*: an identifiable statement that the documented system could be verified to comply to.
- *Standard*: any externally recognized document that states requirements for such a system, such as a regulation, industry standard, or internal architecture standard.
- *Control*: a practice that is (to be) designed and (to be) implemented to contribute to meeting a requirement.
- *Audit*: a process for obtaining and evaluating evidence to determine if the controls are adequately designed, implemented, and in some cases effective to meet the requirements in one or more applicable standards.

Record these as structured data in `Docpkg.toml` and use the Documentation Packager functions to generate and combine documentation systematically.

## How to record the data

Start with a `Docpkg.toml` definition to describe your system’s documentation in [TOML](https://toml.io/) syntax. For example:

```toml
[package]
id = "example-id"
name = "Example System"
files = [
]
```

Then, for each relevant release, append a `release` table like this:

```toml
[[release]]
id = "release-1"

[[release]]
id = "release-2"
```

> **Note**: In the context of auditing the design and implementation of systems, typically only the last audited and subsequent releases are relevant to maintain. Rely on version control instead of extending `Docpkg.toml` to keep historical data around.

For each applicable standard, append a `standard` table like this:

```toml
[[standard]]
id = "owasp-top10:2021"
uri = "https://owasp.org/Top10/A00_2021_Introduction/"
title = "OWASP Top 10 - 2021"
```

If the system has already been audited against this standard, add a `last_audit` sub-table like this:

```toml
[standard.last_audit]
date = "2023-01-26"
report = "https://example.local/audit-report-001.pdf"
adequate = true
```

For each requirement in the standard, declare the ID and optionally an annotation to better understand the requirement in the context of your specific system:

```toml
[[standard.requirement]]
id = "A01:2021"
annotation = "In Example System, access control is only relevant for resources classified as Protected or higher. This requirement is about (1) defining and (2) enforcing correct policies for operating on these resources."
```

> **Note**: Do not add the requirement text verbatim to `Docpkg.toml`. It could create copyright issues and out of its context requirements easily get misinterpreted. Instead, make the original source easy to access and read next to your system’s documentation.

Now, in the context of this requirement, document the timeline of how the system was designed to meet this requirement. The timeline is defined by events, which identified as either:

- a `control` event: the introduction of one or more changed controls, or
- an `out` event: the requirement has gone out of its scope.

For example, say that for the documented requirement `A01:2021` the system was out of scope at release `release-1`, but came in scope at release `release-2`. Furthermore, at that release, two controls were designed to meet the requirement. Then define the timeline as follows:

```toml
[standard.requirement.since."release-1".out]
# Note: use single brackets since this line cannot be repeated

[[standard.requirement.since."release-2".control]]
code = "path/to/program/code/of/control_1.c"
annotation = "Defines the policy. In particular, pay attention to §2 of this document."

[[standard.requirement.since."release-2".control]]
doc = "path/to/documentation/of/control-2.md"
annotation = "Describes how the policy is enforced."
```

Each control design is evidenced by either `code` or a `doc`. The `annotation` is optional but often helpful to describe how to interpret the evidence with respect to the requirement.

> **Note**: The `code` and `doc` paths do not need to be present in `package.files`.

For each control, you may also define how the implementation can be evidenced in one or more demonstrations. Each demo is performed by one or more roles (`person`), using one or more assets (`thing`), performing one or more actions (`instruction`). For example:

```toml
[[standard.requirement.since."release-2".control.demo]]
person = [ "Privileged User", "Unprivileged User", "System Administrator" ]
thing = [ "Server logs", "Client application" ]
instruction = [
  "Privileged User creates a protected resource.",
  "Privileged User tries to read the protected resource in the app and succeeds.",
  "System Administrator observices in the server logs that access was granted.",
  "Unprivileged User tries to read the protected resource in the app and fails.",
  "System Administrator observices in the server logs that access was denied.",
]
```

Now you have a complete package definition of:

- 1 `package`, containing
  - ≥ 0 `release`s, containing
    - 1 `id`: string
  - ≥ 0 `standard`s, containing
    - 1 `id`: string
    - 1 `uri`: string (URI)
    - 1 `title`: string
    - 0–1 `last_audit`, containing
      - 1 `date`: string (RFC 8601 date)
      - 1 `report`: string (URL)
      - 1 `adequate`: boolean
      - ≥ 0 `requirement`s, containing
        - 1 `id`: string
        - 0–1 `annotation`: string
        - ≥ 0 `since`, containing
          - ≥ 0 reference to `release` by `id`, containing
            - either
              - ≥ 0 `control`, containing
                - 0–1 `annotation`: string
                - either
                  - 1 `code`: path
                  - 1 `doc`: path
                  - nothing
                - ≥ 0 `demo`, containing
                  - ≥ 1 `person`
                  - ≥ 1 `thing`
                  - ≥ 1 `instruction`
              - 1 `out`, empty

This package definition alone can already be useful in helping you collect relevant compliance data. But Documentation Packager provides functions to specifically process these to create audit and compliance tables.

## How to process the data

Ensure Documentation Packager is properly installed. Run `docpkg help` to be sure.

To process compliance data, run:

```
docpkg audit .
```

assuming that you are working in the directory that contains `Docpkg.toml`. This command will automatically generate two tables as [comma-separated values (CSV)](https://en.wikipedia.org/wiki/Comma-separated_values) files:

- `table/audits.csv`: an overview of documented last audits
- `table/compliance.csv`: a requirements compliance matrix listing for each requirement the optional annotation and the evidence per release

Both tables can be imported to any spreadsheet software for further processing. For example, use the second table as input for an audit work program.
