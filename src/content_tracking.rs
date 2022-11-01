use std::path::PathBuf;
use std::process::{Command, ExitStatus, Stdio};
use std::{fs, str};
use std::os::unix::prelude::ExitStatusExt;
use std::str::FromStr;

use regex::Regex;

trait ContentTrackingService {

    fn initialize(&self);

    fn get_current_branch_name(&self) -> BranchName;

    /// Risk: origin could be anything, not per se a valid worktree.
    fn clone(&self, origin: PathBuf);

    fn add_file(&self, source: PathBuf, target: PathBuf);

    fn add_current_worktree(&self);

    fn add_worktree(&self, path: PathBuf, name: BranchName);

    fn remove_work_tree(&self, path: PathBuf);

    fn create_branch(&self, name: BranchName, point: impl Point);

    /// Risk: the path could be anything, not per se a valid worktree.
    fn commit(&self, message: CommitMessage) -> Option<CommitId>;

    fn commit_tree(&self, name: ObjectName) -> CommitId;

    fn make_tree(&self) -> ObjectName;

    // fn publish(&self, name: BranchName);
}

trait Point {
    fn reference(&self) -> &str;
}

#[derive(Debug, PartialEq)]
struct BranchName(String);

impl Point for BranchName {
    fn reference(self: &Self) -> &str {
        &self.0
    }
}

#[derive(Debug)]
struct CommitId(String);

impl Point for CommitId {
    fn reference(self: &Self) -> &str {
        &self.0
    }
}

struct ObjectName(String);

struct CommitMessage(String);

#[derive(Debug)]
struct SemanticVersion {
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

fn get_version() -> Option<SemanticVersion> {
    let output = Command::new("git").args(["version"]).output().ok()?;
    str::from_utf8(&output.stdout).ok()?.parse().ok()
}

#[derive(Debug)]
struct Git {
    worktree: PathBuf,
}

impl Git {
    fn new(worktree: PathBuf) -> impl ContentTrackingService {
        Git { worktree }
    }
}

static INITIAL_BRANCH_NAME: &str = "main";

impl ContentTrackingService for Git {
    fn initialize(&self) {
        Command::new("git").args(["init", &format!("--initial-branch={}", INITIAL_BRANCH_NAME)]).current_dir(&self.worktree).output().unwrap();
    }

    fn get_current_branch_name(&self) -> BranchName {
        let out = Command::new("git").args(["branch", "--show-current"]).current_dir(&self.worktree).output().unwrap().stdout;
        BranchName(str::from_utf8(&out).unwrap().to_string().replace("\n", ""))
    }

    fn clone(&self, origin: PathBuf) {
        Command::new("git").args(["clone", origin.to_str().unwrap()]).current_dir(&self.worktree).output().unwrap();
    }

    fn add_file(&self, source: PathBuf, target: PathBuf) {
        let target = self.worktree.join(target);
        fs::create_dir_all(target.parent().unwrap()).unwrap();
        fs::copy(source, target.clone()).unwrap();
        Command::new("git").args(["add", target.to_str().unwrap()]).current_dir(&self.worktree).output().unwrap();
    }

    fn add_current_worktree(&self) {
        Command::new("git").args(["add", "."]).current_dir(&self.worktree).output().unwrap();
    }

    fn add_worktree(&self, path: PathBuf, name: BranchName) {
        Command::new("git").args(["worktree", "add", "--force", path.to_str().unwrap(), &name.0]).current_dir(&self.worktree).output().unwrap();
    }

    fn remove_work_tree(&self, path: PathBuf) {
        Command::new("git").args(["worktree", "remove", path.to_str().unwrap()]).current_dir(&self.worktree).output().unwrap();
    }

    fn create_branch(&self, name: BranchName, point: impl Point) {
        Command::new("git").args(["branch", &name.0, point.reference()]).current_dir(&self.worktree).output().unwrap();
    }

    fn commit(&self, message: CommitMessage) -> Option<CommitId> {
        let result = Command::new("git").args(["commit", "-m", &message.0]).current_dir(&self.worktree).output().unwrap();
        if result.status.success() {
            let out = Command::new("git").args(["rev-parse", "HEAD"]).current_dir(&self.worktree).output().unwrap().stdout;
            let id = CommitId(str::from_utf8(&out).unwrap().to_string().replace("\n", ""));
            Some(id)
        } else if result.status == ExitStatus::from_raw(1) {
            None
        } else {
            panic!("Unexpected status");
        }
    }

    fn commit_tree(&self, name: ObjectName) -> CommitId {
        let message = "build: new documentation package";
        let command = Command::new("git").args(["commit-tree", &name.0, "-m", message]).current_dir(&self.worktree).output().unwrap().stdout;
        CommitId(str::from_utf8(&command).unwrap().to_string())
    }

    fn make_tree(&self) -> ObjectName {
        let command = Command::new("git").args(["mktree"]).current_dir(&self.worktree).stdin(Stdio::null()).output().unwrap().stdout;
        ObjectName(str::from_utf8(&command).unwrap().to_string().replace("\n", ""))
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

        let git = Git::new(path.clone());
        git.initialize();
        assert_eq!(git.get_current_branch_name().0, INITIAL_BRANCH_NAME);

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

        let git = Git::new(content_path.clone());
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

        let git = Git::new(main_path.clone());
        assert_eq!(git.make_tree().0, "4b825dc642cb6eb9a060e54bf8d69288fbee4904");

        fs::remove_dir_all(main_path.clone()).unwrap();
    }
}
