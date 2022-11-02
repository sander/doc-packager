use std::collections::HashSet;
use std::path::PathBuf;
use std::str::FromStr;
use regex::Regex;
use serde_derive::Deserialize;
use crate::tracking::{BranchName, CommitId, ContentTrackingService};

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
            files: dto.files.into_iter().map(|f| FileDescription(f)).collect()
        })
    }
}

struct DocumentationPackagingService {
    content: ContentTrackingService,
    target_branch_name: BranchName,
    manifest: Manifest,
}

static RELATIVE_TARGET_PATH: &str = "target/docpkg";

impl DocumentationPackagingService {
    /// # Risks
    /// - User data lost when creating a work tree when one already exists.
    fn open(content: ContentTrackingService, manifest: Manifest) -> Self {
        todo!()
    }

    /// # Risks
    /// - User has the origin configured not as `origin`.
    fn ensure_branch_exists_with_default_commit(&self, name: &BranchName, id: CommitId) {
        let point = BranchName::from_str(&format!("origin/{}", name.to_string())).unwrap();
        self.content.create_branch(&name, point);
        self.content.create_branch(&name, id);
    }

    fn create_initial_commit() -> CommitId {
        todo!()
    }

    fn create_worktree() {
        todo!()
    }

    fn publish() {
        todo!()
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