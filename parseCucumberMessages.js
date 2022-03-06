const fs = require("fs");
const util = require("util");

// https://github.com/cucumber/common/tree/main/messages


const tRef = Symbol("ref");
const ref = (...path) => [tRef, ...path];
const isRef = (v) => Array.isArray(v) && v[0] === tRef;
const refPath = ([tRef, ...path]) => path;
const refLookup = ([tRef, ...path], tree) => {
  const lookup = ([first, ...rest], v) => {
    return rest.length > 0 ? lookup(rest, v[first]) : v[first];
  };
  return lookup(path, tree);
};

const project = (messages) =>
  messages.reduce(
    (d, m) => {
      if (m.meta) return { ...d, meta: m.meta };
      if (m.source) {
        const { uri, ...source } = m.source;
        return {
          ...d,
          sourcesByUri: { ...d.sourcesByUri, [uri]: source },
          sources: [...d.sources, ref("sourcesByUri", uri)],
        };
      }
      if (m.gherkinDocument) {
        const {
          uri,
          feature: { children, ...feature },
          ...gherkinDocument
        } = m.gherkinDocument;
        return {
          ...d,
          gherkinDocumentsByUri: {
            ...d.gherkinDocumentsByUri,
            [uri]: {
              ...gherkinDocument,
              feature: {
                ...feature,
                scenarios: children.map(({ scenario: { id } }) =>
                  ref("scenariosById", id)
                ),
              },
            },
          },
          gherkinDocuments: [
            ...d.gherkinDocuments,
            ref("gherkinDocumentsByUri", uri),
          ],
          scenariosById: {
            ...d.scenariosById,
            ...Object.fromEntries(
              children
                .map((c) => c.scenario)
                .filter((c) => !!c)
                .map(({ id, steps, ...scenario }) => [
                  id,
                  {
                    ...scenario,
                    steps: steps.map((s) => ref("gherkinStepsById", s.id)),
                  },
                ])
            ),
          },
          gherkinStepsById: {
            ...d.gherkinStepsById,
            ...Object.fromEntries(
              children
                .flatMap(({ scenario: { steps } }) => steps)
                .map(({ id, ...step }) => [id, step])
            ),
          },
        };
      }
      if (m.pickle) {
        const { id, uri, steps, astNodeIds, ...pickle } = m.pickle;
        const scenariosById = Object.entries(d.scenariosById).map(([k, v]) => {
          return astNodeIds.indexOf(k) !== -1
            ? [
                k,
                {
                  ...v,
                  pickles: [...(v.pickles || []), ref("picklesById", id)],
                },
              ]
            : [k, v];
        });
        return {
          ...d,
          scenariosById: Object.fromEntries(scenariosById),
          picklesById: {
            ...d.picklesById,
            [id]: {
              ...pickle,
              source: ref("sourcesByUri", uri),
              steps: steps.map(({ id }) => ref("pickleStepsById", id)),
            },
          },
          pickles: [...d.pickles, ref("picklesById", id)],
          pickleStepsById: {
            ...d.pickleStepsById,
            ...Object.fromEntries(steps.map(({ id, ...step }) => [id, step])),
          },
        };
      }
      if (m.stepDefinition) {
        const { id, ...stepDefinition } = m.stepDefinition;
        return {
          ...d,
          stepDefinitionsById: {
            ...d.stepDefinitionsById,
            [id]: stepDefinition,
          },
        };
      }
      if (m.testRunStarted)
        return { ...d, testRun: { ...d.testRun, started: m.testRunStarted } };
      if (m.testCase) {
        const { id, pickleId, testSteps } = m.testCase;
        const pickle = d.picklesById[pickleId];
        return {
          ...d,
          picklesById: {
            ...d.picklesById,
            [pickleId]: {
              ...pickle,
              testCases: [
                ...(pickle.testCases || []),
                ref("testCasesById", id),
              ],
            },
          },
          testCasesById: {
            ...d.testCasesById,
            [id]: {
              // pickle: ref("picklesById", pickleId),
              testSteps: testSteps.map(({ id }) => ref("testStepsById", id)),
            },
          },
          testStepsById: {
            ...d.testStepsById,
            ...Object.fromEntries(
              testSteps.map(
                ({ id, pickleStepId, stepDefinitionIds, ...testStep }) => [
                  id,
                  {
                    pickleStep: ref("pickleStepsById", pickleStepId),
                    stepDefinitions: stepDefinitionIds.map((id) =>
                      ref("stepDefinitionsById", id)
                    ),
                    ...testStep,
                  },
                ]
              )
            ),
          },
        };
      }
      if (m.testCaseStarted) {
        const { id, testCaseId, ...testCaseStarted } = m.testCaseStarted;
        const testCase = d.testCasesById[testCaseId];
        return {
          ...d,
          testCasesById: {
            ...d.testCasesById,
            [testCaseId]: { ...testCase, runs: [ref("testCaseRunsById", id)] },
          },
          testCaseRunsById: {
            ...d.testCaseRunsById,
            [id]: { started: testCaseStarted, steps: {} },
          },
        };
      }
      if (m.testStepStarted) {
        const { testCaseStartedId, testStepId, ...testStepStarted } =
          m.testStepStarted;
        const { steps, ...testCaseRun } = d.testCaseRunsById[testCaseStartedId];
        return {
          ...d,
          testCaseRunsById: {
            ...d.testCaseRunsById,
            [testCaseStartedId]: {
              ...testCaseRun,
              steps: {
                ...steps,
                [testStepId]: [
                  ...(steps[testStepId] || []),
                  { started: testStepStarted },
                ],
              },
            },
          },
        };
      }
      if (m.testStepFinished) {
        const {
          testCaseStartedId,
          testStepId,
          testStepResult,
          ...testStepFinished
        } = m.testStepFinished;
        const { steps, ...testCaseRun } = d.testCaseRunsById[testCaseStartedId];
        const testStepRuns = steps[testStepId].slice(0, -1);
        const testStepRun = steps[testStepId][steps[testStepId].length - 1];
        return {
          ...d,
          testCaseRunsById: {
            ...d.testCaseRunsById,
            [testCaseStartedId]: {
              ...testCaseRun,
              steps: {
                ...steps,
                [testStepId]: [
                  ...testStepRuns,
                  {
                    ...testStepRun,
                    finished: testStepFinished,
                    result: testStepResult,
                  },
                ],
              },
            },
          },
        };
      }
      if (m.testCaseFinished) {
        const { testCaseStartedId, ...testCaseFinished } = m.testCaseFinished;
        const testCaseRun = d.testCaseRunsById[testCaseStartedId];
        return {
          ...d,
          testCaseRunsById: {
            ...d.testCaseRunsById,
            [testCaseStartedId]: {
              ...testCaseRun,
              finished: testCaseFinished,
            },
          },
        };
      }
      if (m.testRunFinished)
        return { ...d, testRun: { ...d.testRun, finished: m.testRunFinished } };
      return d;
    },
    { sources: [], gherkinDocuments: [], pickles: [] }
  );


// const messages = fs
//   .readFileSync("out.json")
//   .toString()
//   .trim()
//   .split("\n")
//   .map(JSON.parse);
// const result = project(messages);
// console.log(util.inspect(result, { depth: 100 }));

const denormalize = (value, tree) => {
  if (typeof value === "object" && !Array.isArray(value) && value !== null) {
    return {
      ...Object.fromEntries(
        Object.entries(value).map(([k, v]) => [k, denormalize(v, tree)])
      ),
    };
  }
  if (isRef(value)) {
    return denormalize(refLookup(value, tree), tree);
  }
  if (Array.isArray(value)) {
    return value.map((v) => denormalize(v, tree));
  }
  return value;
};

// const denormalized = denormalize(result.gherkinDocuments, result);

// console.log(util.inspect(denormalized, { depth: 100 }));

// TODO: (1) denormalize gherkinDocuments, (2) render to TeX

module.exports = {
  project,
  denormalize
}
