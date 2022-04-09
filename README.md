# Doc Packager

**Doc Packager** is a tool that enables packaging both evergreen and living documentation into human-readable packages.

Make sure to `npm install` first.

How it works:

1. Define your project in a `README.md` file and in [Gherkin](https://cucumber.io/docs/gherkin/reference/) executable specifications.
2. Set the documentation configurations in your package manifest file to point to the output of previous step.
3. Run `clojure -X:main` to process the output into a PDF file.

In the current example project, step 3 is automated in GitHub Actions. This way, each commit leads to a new PDF explaining the project. This PDF is accessible in the [Package docs](https://github.com/sander/doc-packager/actions/workflows/package.yml) workflow history and [in the repository’s GitHub Pages](https://sander.github.io/docpkg/docpkg-main.pdf).

It is currently an experiment, not useable in production. Contact [Sander](https://github.com/sander) if you have questions or want to collaborate.
