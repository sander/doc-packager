use std::path::PathBuf;
use std::process::Command;
use std::str;
use std::str::FromStr;

use regex::Regex;

trait ContentTrackingService {
    fn initialize(&self);
    fn get_current_branch_name(&self) -> BranchName;

    // Risk: origin could be anything, not per se a valid worktree.
    // fn clone(&self, origin: &Path);

    // fn add_file(&self, source: &Path, target: &Path);
    // fn add_current_worktree(&self);
    // fn add_worktree(&self, path: &Path, name: BranchName);
    // fn remove_work_tree(&self, path: &Path);
    // fn create_branch<P: Point>(&self, name: BranchName, point: P);

    // Risk: the path could be anything, not per se a valid worktree.
    // fn commit(&self, message: CommitMessage) -> Option<CommitId>;

    // fn commit_tree(&self, name: ObjectName) -> CommitId;
    // fn make_tree(&self) -> ObjectName;
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
    fn new(worktree: PathBuf) -> Git {
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
        let path = PathBuf::from("target/test-tracking");
        let _ = fs::remove_dir_all(path.clone());
        let _ = fs::create_dir_all(path.clone());
        let git = Git::new(path.clone());
        git.initialize();
        assert_eq!(git.get_current_branch_name().0, INITIAL_BRANCH_NAME);
        let _ = fs::remove_dir_all(path.clone());
    }
}
