use docpkg::risk;
use regex::Regex;
use std::path::Path;
use std::process::Command;
use std::str;
use std::str::FromStr;

trait ContentTrackingService {
    fn initialize(&self, worktree: Path);

    #[risk("Origin could be anything, not per se a valid worktree")]
    fn clone(&self, origin: Path, worktree: Path);

    fn add_file(&self, worktree: Path, source: Path, target: Path);
    fn add_current_worktree(&self, worktree: Path);
    fn add_worktree(&self, worktree: Path, path: Path, name: BranchName);
    fn remove_work_tree(&self, worktree: Path, path: Path);
    fn get_current_branch_name(&self, worktree: Path) -> BranchName;
    fn create_branch<P: Point>(&self, worktree: Path, name: BranchName, point: P);

    #[risk("The path could be anything, not per se a valid worktree")]
    fn commit(&self, worktree: Path, message: CommitMessage) -> Option<CommitId>;

    fn commit_tree(&self, worktree: Path, name: ObjectName) -> CommitId;
    fn make_tree(&self, worktree: Path) -> ObjectName;
    fn publish(&self, worktree: Path, name: BranchName);
}

trait Point {
    fn reference(&self) -> &str;
}

#[derive(Debug)]
pub struct BranchName(String);

impl Point for BranchName {
    fn reference(self: &Self) -> &str {
        &self.0
    }
}

#[derive(Debug)]
pub struct CommitId(String);

impl Point for CommitId {
    fn reference(self: &Self) -> &str {
        &self.0
    }
}

pub struct ObjectName(String);

pub struct CommitMessage(String);

#[derive(Debug)]
pub struct SemanticVersion {
    name: String,
    major: u16,
    minor: u16,
    patch: u16,
}

impl FromStr for SemanticVersion {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let re = Regex::new(r"([^ ]+) version (\d+)\.(\d+)\.(\d+)(?: \(.*\))?").unwrap();
        let mat = re.captures(s).ok_or("Could not capture")?;
        Ok(SemanticVersion {
            name: (&mat[1]).to_string(),
            major: (&mat[2]).parse::<u16>().unwrap(),
            minor: (&mat[3]).parse::<u16>().unwrap(),
            patch: (&mat[4]).parse::<u16>().unwrap(),
        })
    }
}

pub fn get_version() -> Option<SemanticVersion> {
    let output = Command::new("git").args(["version"]).output().ok()?;
    str::from_utf8(&output.stdout).ok()?.parse().ok()
}
