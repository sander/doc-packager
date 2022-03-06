const { marked } = require("marked");
const util = require("util");
const { project, denormalize } = require("./parseCucumberMessages");

const escape = (s) =>
  s
    .replace(/\\/g, "\\\\")
    .replace(/_/g, "\\_")
    .replace(/\{/g, "\\{")
    .replace(/\}/g, "\\}");

module.exports = (title, readmeSource, messages, out) => {
  const tokens = marked.lexer(readmeSource);

  out.write(`\\documentclass{article}

\\usepackage[active,tightpage]{preview}
\\usepackage{fontspec}

\\setmainfont{IBM Plex Sans}
%\\setmainfont{iA Writer Quattro S}

\\usepackage{setspace}
\\setstretch{1.3}

\\renewcommand{\\PreviewBorder}{0.5in}

\\usepackage[]{hyperref}

\\hypersetup{
  pdftitle={my title},
  pdfauthor={my author},
  pdfsubject={my subject},
  pdfkeywords={keyword1, keyword2},
  pdfpagemode=UseOutlines,
  bookmarksnumbered=true,
  bookmarksopen=true,
  bookmarksopenlevel=1,
  colorlinks=true,
  pdfstartview=Fit,
  allcolors=blue
}

\\usepackage{hypcap}

\\begin{document}

\\begin{preview}

\\section*{${escape(title)}}

\\end{preview}

\\begin{preview}

`);

  for (const token of tokens) {
    if (token.type === "heading") {
      out.write(`\\section*{${token.text}}\n`);
    } else if (token.type === "paragraph") {
      for (const t of token.tokens) {
        if (t.type === "strong") {
          out.write(`\\textbf{${t.text}}`);
        } else if (t.type === "text") {
          out.write(t.text);
        }
      }
    }
  }

  out.write(`

\\end{preview}

`);

  const result = project(messages);
  const denormalized = denormalize(result.gherkinDocuments, result);

  for (const doc of denormalized) {
    out.write(`
\\begin{preview}

\\section*{${escape(doc.feature.keyword)}: ${escape(doc.feature.name)}}
`);

    for (const scenario of doc.feature.scenarios) {
      out.write(`
\\subsection*{${escape(scenario.keyword)}: ${escape(scenario.name)}}
`);

      out.write(
        scenario.steps
          .map((step) => `\\textit{${step.keyword}}${step.text}`)
          .join("\\\\")
      );
    }

    out.write(`
\\end{preview}
`);
  }

  out.write(`

\\end{document}
`);
};
