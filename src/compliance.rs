use std::collections::HashMap;
use std::fmt::Write;
use std::path::PathBuf;

use serde_derive::Deserialize;

#[derive(Deserialize, Debug)]
pub struct ReleaseDto {
    id: String,
}

#[derive(Deserialize, Debug)]
pub struct StandardDto {
    id: Option<String>,
    title: Option<String>,
    uri: Option<String>,
    requirement: Vec<RequirementDto>,
}

#[derive(Deserialize, Debug)]
pub struct RequirementDto {
    id: String,
    annotation: Option<String>,
    since: Option<HashMap<String, ApplicabilityDto>>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct ApplicabilityDto {
    control: Option<Vec<ControlDto>>,
    out: Option<OutOfScopeDto>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct OutOfScopeDto {}

#[derive(Deserialize, Debug, Clone)]
#[serde(rename_all = "lowercase")]
pub struct ControlDto {
    doc: Option<PathBuf>,
    code: Option<PathBuf>,
    annotation: Option<String>,
    demo: Option<Vec<DemoDto>>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct DemoDto {
    person: Vec<String>,
    thing: Vec<String>,
    instruction: Vec<String>,
}

#[derive(Debug)]
pub struct ComplianceMatrix {
    releases: Vec<String>,
    standards: Vec<Standard>,
}

#[derive(Debug)]
pub struct Standard {
    id: Option<String>,
    title: Option<String>,
    uri: Option<String>,
    requirements: Vec<Requirement>,
}

#[derive(Debug)]
pub struct Requirement {
    id: String,
    annotation: Option<String>,
    timeline: Vec<Applicability>,
}

#[derive(Debug, Clone)]
pub enum Applicability {
    Undefined,
    NotApplicable,
    Applicable(Vec<Control>),
}

#[derive(Debug, Clone)]
pub struct Control {
    design_evidence: ControlDesignEvidence,
    annotation: Option<String>,
    demos: Vec<Demo>,
}

#[derive(Debug, Clone)]
pub struct Demo {
    people: Vec<String>,
    things: Vec<String>,
    instructions: Vec<String>,
}

#[derive(Debug, Clone)]
pub enum ControlDesignEvidence {
    Document(PathBuf),
    Code(PathBuf),
}

pub fn compliance_matrix(release: Vec<ReleaseDto>, standard: Vec<StandardDto>) -> ComplianceMatrix {
    let releases: Vec<String> = release.iter().map(|r| {
        r.id.clone()
    }).collect();
    let standards = standard.iter().map(|s| {
        if s.id.is_none() && s.uri.is_none() && s.title.is_none() {
            panic!("invalid standard definition");
        }
        let requirements = s.requirement.iter().map(|requirement| {
            let mut timeline: Vec<Applicability> = vec!();
            let mut last = Applicability::Undefined;
            for release in releases.iter() {
                let since = requirement.since.clone().unwrap_or(HashMap::new());
                let result = since.get(release);
                let applicability = match result {
                    Some(ApplicabilityDto { out: Some(_), control: None }) => Applicability::NotApplicable,
                    Some(ApplicabilityDto { out: None, control: Some(control) }) => {
                        let controls = control.iter().map(|c| {
                            let demos = c.demo.clone().unwrap_or(Vec::new()).iter().map(|d| {
                                Demo {
                                    people: d.person.clone(),
                                    things: d.thing.clone(),
                                    instructions: d.instruction.clone(),
                                }
                            }).collect();
                            Control {
                                design_evidence: match (&c.code, &c.doc) {
                                    (Some(code), None) => ControlDesignEvidence::Code(code.clone()),
                                    (None, Some(doc)) => ControlDesignEvidence::Document(doc.clone()),
                                    _ => panic!("invalid design evidence"),
                                },
                                annotation: c.annotation.clone(),
                                demos,
                            }
                        }).collect();
                        Applicability::Applicable(controls)
                    }
                    None => last.clone(),
                    _ => panic!("invalid applicability"),
                };
                last = applicability.clone();
                timeline.push(applicability);
            }
            Requirement {
                id: requirement.id.clone(),
                annotation: requirement.annotation.clone(),
                timeline,
            }
        }).collect();
        Standard {
            id: s.id.clone(),
            title: s.title.clone(),
            uri: s.uri.clone(),
            requirements,
        }
    }).collect();
    ComplianceMatrix { releases, standards }
}

fn escape(s: &str) -> String {
    format!("\"{}\"", s.replace("\"", "\\\""))
}

fn write_row<W: Write>(w: &mut W, r: &Vec<String>) {
    let escaped: Vec<String> = r.iter().map(|r| escape(r)).collect();
    write!(w, "{}\n", escaped.join(",")).unwrap();
}

impl Standard {
    fn name(&self) -> String {
        self.id.clone().or(self.title.clone()).or(self.uri.clone()).unwrap()
    }
}

impl ComplianceMatrix {
    pub fn to_csv<W: Write>(&self, w: &mut W) {
        let mut header: Vec<String> = vec!("Standard", "Requirement", "Annotation").iter().map(|s| s.to_string()).collect();
        for r in &self.releases {
            header.push(format!("{}: Design", &r));
        }
        for r in &self.releases {
            header.push(format!("{}: Demo – who", &r));
            header.push(format!("{}: Demo – what", &r));
            header.push(format!("{}: Demo – how", &r));
        }
        write_row(w, &header);
        for s in &self.standards {
            for r in &s.requirements {
                let mut row = vec!(s.name(), r.id.clone(), r.annotation.clone().unwrap_or("N/A".to_string()));
                for t in &r.timeline {
                    let applicability = match t {
                        Applicability::Undefined => "?".to_string(),
                        Applicability::NotApplicable => "N/A".to_string(),
                        Applicability::Applicable(c) => {
                            let controls: Vec<String> = c.iter().map(|control| {
                                let mut s = String::new();
                                match &control.design_evidence {
                                    ControlDesignEvidence::Code(path) => write!(s, "Code: {}", path.to_string_lossy()),
                                    ControlDesignEvidence::Document(path) => write!(s, "Document: {}", path.to_string_lossy()),
                                }.unwrap();
                                match &control.annotation {
                                    Some(a) => write!(s, "\n{}", a).unwrap(),
                                    None => (),
                                };
                                s
                            }).collect();
                            controls.join("\n\n")
                        }
                    };
                    row.push(applicability.to_string());
                }
                for t in &r.timeline {
                    match t {
                        Applicability::Applicable(c) => {
                            let demos: Vec<&Demo> = c.iter().flat_map(|c| &c.demos).collect();
                            let who: Vec<String> = demos.iter().flat_map(|d| d.people.clone()).collect();
                            let what: Vec<String> = demos.iter().flat_map(|d| d.things.clone()).collect();
                            let how: Vec<String> = demos.iter().flat_map(|d| d.instructions.clone()).collect();

                            row.push(who.join("\n\n"));
                            row.push(what.join("\n\n"));
                            row.push(how.join("\n\n"));
                        }
                        _ => {
                            for _ in 0..3 {
                                row.push("N/A".to_string());
                            }
                        }
                    }
                }
                write_row(w, &row);
            }
        }
    }
}
