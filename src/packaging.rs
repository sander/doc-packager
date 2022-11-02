use std::collections::HashSet;
use std::path::PathBuf;
use std::str::FromStr;
use regex::Regex;
use serde_derive::Deserialize;

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

struct Manifest {
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

// trait DocumentationPackagingService {
//     fn publish(files: impl IntoIterator);
// }

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