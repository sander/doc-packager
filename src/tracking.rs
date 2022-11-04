use std::{fs, str};
use std::os::unix::prelude::ExitStatusExt;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Stdio};
use std::str::FromStr;

use regex::Regex;

pub trait Point {
    fn reference(&self) -> &str;
}

#[derive(Debug, PartialEq)]
pub struct BranchName(String);

impl Point for BranchName {
    fn reference(self: &Self) -> &str {
        &self.0
    }
}

impl ToString for BranchName {
    fn to_string(&self) -> String {
        self.0.clone()
    }
}

impl FromStr for BranchName {
    type Err = ();

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(BranchName(s.to_string()))
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

impl FromStr for CommitMessage {
    type Err = ();

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(CommitMessage(s.to_string()))
    }
}

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

impl SemanticVersion {
    fn is_met_by(self: &Self, candidate: SemanticVersion) -> bool {
        let name_matches = self.name == candidate.name;
        let compatible_design = self.major == candidate.major && self.minor <= candidate.minor;
        name_matches && compatible_design && (self.minor < candidate.minor || self.patch <= candidate.patch)
    }

    fn new(name: &str, major: u16, minor: u16, patch: u16) -> SemanticVersion {
        SemanticVersion { name: name.to_string(), major, minor, patch }
    }
}

pub fn get_version() -> Option<SemanticVersion> {
    let output = Command::new("git").args(["version"]).output().ok()?;
    str::from_utf8(&output.stdout).ok()?.parse().ok()
}

#[derive(Debug)]
pub struct ContentTrackingService {
    worktree: PathBuf,
}

impl ContentTrackingService {}

static INITIAL_BRANCH_NAME: &str = "main";

impl ContentTrackingService {
    fn command(&self) -> Command {
        let mut command = Command::new("git");
        command.current_dir(&self.worktree);
        command
    }

    pub fn new(worktree: PathBuf) -> Self {
        Self { worktree }
    }

    pub fn worktree(&self) -> &Path {
        &self.worktree
    }

    pub fn initialize(&self) {
        self.command().args(["init", &format!("--initial-branch={}", INITIAL_BRANCH_NAME)]).output().unwrap();
    }

    pub fn get_current_branch_name(&self) -> Option<BranchName> {
        let out = self.command().args(["branch", "--show-current"]).output().unwrap().stdout;
        let str = str::from_utf8(&out).unwrap().to_string().replace("\n", "");
        if str == "" {
            None
        } else {
            Some(BranchName(str))
        }
    }

    /// Risk: origin could be anything, not per se a valid worktree.
    pub fn clone_to(&self, target: PathBuf) {
        let result = Command::new("git").args(["clone", self.worktree.to_str().unwrap(), target.to_str().unwrap()]).output().unwrap();
        println!("Clone result: {:?}", result);
    }

    pub fn add_file(&self, source: PathBuf, target: PathBuf) {
        println!("Adding {:?} as {:?}", source, target);
        let full_target_path = self.worktree.join(&target);
        fs::create_dir_all(full_target_path.parent().unwrap()).unwrap();
        fs::copy(source, full_target_path.clone()).unwrap();
        let result = self.command().args(["add", target.to_str().unwrap()]).output().unwrap();
        println!("Add result: {:?}", result);
    }

    pub fn add_current_worktree(&self) {
        self.command().args(["add", "."]).output().unwrap();
    }

    pub fn add_worktree(&self, path: PathBuf, name: &BranchName) {
        let result = self.command().args(["worktree", "add", "--force", path.to_str().unwrap(), &name.0]).output().unwrap();
        println!("Add worktree result: {:?}", result);
    }

    pub fn remove_work_tree(&self, path: PathBuf) {
        self.command().args(["worktree", "remove", path.to_str().unwrap()]).output().unwrap();
    }

    pub fn create_branch(&self, name: &BranchName, point: impl Point) {
        let result = self.command().args(["branch", &name.0, point.reference()]).output().unwrap();
        println!("Create branch result: {:?}", result);
    }

    /// Risk: the path could be anything, not per se a valid worktree.
    pub fn commit(&self, message: CommitMessage) -> Option<CommitId> {
        let result = self.command().args(["commit", "-m", &message.0]).output().unwrap();
        if result.status.success() {
            let out = self.command().args(["rev-parse", "HEAD"]).output().unwrap().stdout;
            let id = CommitId(str::from_utf8(&out).unwrap().to_string().replace("\n", ""));
            Some(id)
        } else if result.status.code().filter(|c| c == &1).is_some() {
            None
        } else {
            panic!("Unexpected status {:?}", result.status.code());
        }
    }

    pub fn commit_tree(&self, name: ObjectName) -> CommitId {
        let message = "build: new documentation package";
        let command = self.command().args(["commit-tree", &name.0, "-m", message]).output().unwrap().stdout;
        CommitId(str::from_utf8(&command).unwrap().to_string().replace("\n", ""))
    }

    pub fn make_tree(&self) -> ObjectName {
        let command = self.command().args(["mktree"]).stdin(Stdio::null()).output().unwrap().stdout;
        ObjectName(str::from_utf8(&command).unwrap().to_string().replace("\n", ""))
    }

    pub fn push_to_origin(&self, name: &BranchName) {
        self.command().args(["push", "origin", &name.0]).output().unwrap();
    }
}

#[cfg(test)]
mod tests {
    use std::fs;

    use super::*;

    #[test]
    fn get_version_assuming_git_2_is_installed() {
        let version = get_version();
        assert!(version.is_some());
        assert_eq!(version.unwrap().major, 2);
    }

    #[test]
    fn semantic_version_comparison() {
        let requirement = SemanticVersion::new("git", 2, 37, 0);
        let installed_correct = SemanticVersion::new("git", 2, 37, 3);
        let installed_incorrect = SemanticVersion::new("git", 3, 37, 3);
        assert!(requirement.is_met_by(installed_correct));
        assert!(!requirement.is_met_by(installed_incorrect));
    }

    #[test]
    fn semantic_version_parsing() {
        let correct_lines = ["git version 2.37.0 (Apple Git-136)", "git version 2.37.0"];
        for line in correct_lines {
            assert!(SemanticVersion::from_str(line).is_ok());
        }
    }

    #[test]
    fn get_current_branch_name() {
        let path = PathBuf::from("target/test-tracking-get-current-branch-name");

        fs::remove_dir_all(path.clone()).ok();
        fs::create_dir_all(path.clone()).unwrap();

        let git = ContentTrackingService::new(path.clone());
        git.initialize();
        assert_eq!(git.get_current_branch_name().unwrap().0, INITIAL_BRANCH_NAME);

        fs::remove_dir_all(path.clone()).unwrap();
    }

    #[test]
    fn add_file() {
        let main_path = PathBuf::from("target/test-tracking-add-file");
        let file_path = main_path.clone().join("file");
        let content_path = main_path.clone().join("content");
        let target_path = PathBuf::from("foo/bar");
        let content = "Hello World!";

        fs::remove_dir_all(main_path.clone()).ok();
        fs::create_dir_all(content_path.clone()).unwrap();
        fs::write(file_path.clone(), content).unwrap();

        let git = ContentTrackingService::new(content_path.clone());
        git.initialize();
        git.add_file(file_path.clone(), target_path.clone());

        assert_eq!(fs::read_to_string(content_path.clone().join(target_path)).unwrap(), content);

        fs::remove_dir_all(main_path.clone()).unwrap();
    }

    #[test]
    fn make_tree() {
        let main_path = PathBuf::from("target/test-tracking-make-tree");

        fs::remove_dir_all(main_path.clone()).ok();
        fs::create_dir_all(main_path.clone()).unwrap();

        let git = ContentTrackingService::new(main_path.clone());
        assert_eq!(git.make_tree().0, "4b825dc642cb6eb9a060e54bf8d69288fbee4904");

        fs::remove_dir_all(main_path.clone()).unwrap();
    }
}
