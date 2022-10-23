use regex::Regex;
use std::process::Command;
use std::str;
use std::str::FromStr;

#[derive(Debug)]
pub struct SemanticVersion(String, u16, u16, u16);

impl FromStr for SemanticVersion {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let re = Regex::new(r"([^ ]+) version (\d+)\.(\d+)\.(\d+)(?: \(.*\))?").unwrap();
        let mat = re.captures(s).ok_or("Could not capture")?;
        Ok(SemanticVersion(
            (&mat[1]).to_string(),
            (&mat[2]).parse::<u16>().unwrap(),
            (&mat[3]).parse::<u16>().unwrap(),
            (&mat[4]).parse::<u16>().unwrap(),
        ))
    }
}

pub fn get_version() -> Option<SemanticVersion> {
    let output = Command::new("git").args(["version"]).output().ok()?;
    str::from_utf8(&output.stdout).ok()?.parse().ok()
}
