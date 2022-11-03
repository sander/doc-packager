use std::collections::HashSet;
use std::{env, fs};
use std::path::PathBuf;
use std::str::FromStr;

use regex::Regex;
use serde_derive::Deserialize;

use crate::tracking::{BranchName, CommitId, CommitMessage, ContentTrackingService};

#[derive(PartialOrd, PartialEq, Debug, Eq, Hash)]
struct FileDescription(PathBuf);

#[derive(PartialOrd, PartialEq, Debug)]
struct PackageId(String);

impl PackageId {
    fn from(s: &str) -> Option<PackageId> {
        let re = Regex::new(r"^[a-z][a-z-/0-9]{0,19}$").unwrap();
        re.is_match(s).then_some(PackageId(s.to_string()))
    }
}

#[derive(PartialOrd, PartialEq, Debug)]
struct PackageName(String);

#[derive(Deserialize, Debug)]
struct ManifestDto {
    id: String,
    name: String,
    files: Vec<PathBuf>,
}

#[derive(Debug)]
pub struct Manifest {
    id: PackageId,
    name: PackageName,
    files: HashSet<FileDescription>,
}

impl FromStr for Manifest {
    type Err = ();

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let dto: ManifestDto = toml::from_str(s).or(Err(()))?;
        let id = PackageId::from(&dto.id).ok_or(())?;
        Ok(Manifest {
            id,
            name: PackageName(dto.name),
            files: dto.files.into_iter().map(|f| FileDescription(f)).collect(),
        })
    }
}

struct DocumentationPackagingService {
    content: ContentTrackingService,
    target_content: ContentTrackingService,
    target_branch_name: BranchName,
    manifest: Manifest,
}

static RELATIVE_TARGET_PATH: &str = "target/docpkg";

impl DocumentationPackagingService {
    /// # Risks
    /// - User data lost when creating a work tree when one already exists.
    fn open(path: PathBuf) -> Self {
        let contents = fs::read_to_string(path.join("Docpkg.toml")).unwrap();
        let manifest: Manifest = Manifest::from_str(&contents).unwrap();
        let content = ContentTrackingService::new(path.clone());
        let target_content = ContentTrackingService::new(path.join(RELATIVE_TARGET_PATH));
        let original_branch_name = content.get_current_branch_name().or(env::var("BRANCH_NAME").ok().and_then(|s| BranchName::from_str(&s).ok())).unwrap();
        let target_branch_name = BranchName::from_str(&format!("docpkg/{}/{}", manifest.name.0, original_branch_name.to_string())).unwrap();
        let service = Self { content, target_content, target_branch_name, manifest };
        service.create_worktree();
        service
    }

    /// # Risks
    /// - User has the origin configured not as `origin`.
    fn ensure_branch_exists_with_default_commit(&self, name: &BranchName, id: CommitId) {
        let point = BranchName::from_str(&format!("origin/{}", name.to_string())).unwrap();
        self.content.create_branch(&name, point);
        self.content.create_branch(&name, id);
    }

    fn create_initial_commit(&self) -> CommitId {
        let tree_id = self.content.make_tree();
        self.content.commit_tree(tree_id)
    }

    fn create_worktree(&self) {
        fs::remove_dir_all(self.content.worktree()).unwrap();
        let commit_id = self.create_initial_commit();
        self.ensure_branch_exists_with_default_commit(&self.target_branch_name, commit_id);
        self.content.add_worktree(PathBuf::from(RELATIVE_TARGET_PATH), &self.target_branch_name);
    }

    fn publish(&self) {
        self.manifest.files.iter().for_each(|f| self.target_content.add_file(self.content.worktree().join(f.0.clone()), f.0.clone()));
        self.target_content.commit(CommitMessage::from_str("docs: new package").unwrap());
        self.content.push_to_origin(&self.target_branch_name);
    }
}

impl Drop for DocumentationPackagingService {
    fn drop(&mut self) {
        self.content.remove_work_tree(PathBuf::from(RELATIVE_TARGET_PATH));
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashSet;
    use std::path::PathBuf;
    use std::str::FromStr;

    use crate::packaging::{FileDescription, Manifest};

    #[test]
    fn parse_manifest() {
        let input = "id = \"docpkg\"\n\
                     name = \"Documentation Packager\"\n\
                     files = [\n\
                       \"README.md\"\n\
                     ]\n\
                     ";
        let manifest = Manifest::from_str(input).unwrap();
        assert_eq!(manifest.id.0, "docpkg");
        assert_eq!(manifest.name.0, "Documentation Packager");
        assert_eq!(manifest.files, HashSet::from([FileDescription(PathBuf::from("README.md"))]));
    }
}