# Doc Packager

**Doc Packager** is a tool that enables packaging both evergreen and living documentation into human-readable packages.

How it works:

1. Define your project in a `README.md` file and in [Gherkin](https://cucumber.io/docs/gherkin/reference/) executable specifications.
2. Set the `"documentation"` configurations in your `package.json` manifest file to point to the output of previous step.
3. Run `node main` to process the output into a TeX source file.
4. Run the LuaLaTeX processor to generate a PDF.

In the current example project, steps 3 and 4 are automated in GitHub Actions. This way, each commit leads to a new PDF explaining the project in the [Package docs](https://github.com/sander/doc-packager/actions/workflows/package.yml) workflow history.

It is currently an experiment, not useable in production. Contact [@sander](https://github.com/sander) if you have questions or want to collaborate.
