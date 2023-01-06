use std::{fs, path::Path};

use regex::{Captures, RegexBuilder};
use relative_path::RelativePath;

enum TagInclusion {
    RenderTags,
    HideTags,
}

pub fn transclude<P>(path: P)
where
    P: AsRef<Path>,
{
    let transcluded = transclude_impl(&path, TagInclusion::RenderTags);
    fs::write(&path, transcluded).unwrap();
}

fn transclude_impl<P>(path: P, inclusion: TagInclusion) -> String
where
    P: AsRef<Path>,
{
    const REGEX: &str =
        r"(<!-- *Start transclusion: ?(.*?[^ ]) *-->).*?(<!-- *End transclusion *-->)";
    let content = fs::read_to_string(&path).unwrap();
    let regex = RegexBuilder::new(REGEX)
        .multi_line(true)
        .dot_matches_new_line(true)
        .build()
        .unwrap();
    regex
        .replace_all(&content, |captures: &Captures| {
            let capture = captures.get(2).unwrap().as_str();
            let parent_path = path.as_ref().parent().unwrap();
            let transcluded_path = RelativePath::new(capture).to_path(parent_path);
            let transclusion = transclude_impl(transcluded_path, TagInclusion::HideTags);
            let trimmed_transclusion = transclusion.trim();
            match inclusion {
                TagInclusion::RenderTags => [
                    captures.get(1).unwrap().as_str(),
                    "\n",
                    trimmed_transclusion,
                    "\n",
                    captures.get(3).unwrap().as_str(),
                ]
                .join(""),
                TagInclusion::HideTags => trimmed_transclusion.to_string(),
            }
        })
        .to_string()
}

#[cfg(test)]
mod tests {
    use std::{fs, path::PathBuf};

    use fs_extra::dir::CopyOptions;

    use crate::transclusion::transclude;

    const TEST_PATH: &str = "target/test-transclusion";

    fn set_up() {
        let source_path = [env!("CARGO_MANIFEST_DIR"), "/resources/test/example"].join("");
        fs::create_dir_all(TEST_PATH).unwrap();
        fs_extra::copy_items(&[source_path], TEST_PATH, &CopyOptions::new()).unwrap();
    }

    fn tear_down() {
        fs::remove_dir_all(TEST_PATH).unwrap();
    }

    #[test]
    fn test() {
        set_up();
        let path = PathBuf::from(TEST_PATH).join("example").join("README.md");
        transclude(&path);
        let content = fs::read_to_string(&path).unwrap();
        assert!(content.find("Second example content.").is_some());
        assert!(content
            .find("<!-- Start transclusion: document.md -->")
            .is_some());
        assert!(content
            .find("<!-- Start transclusion: document-2.md -->")
            .is_none());
        tear_down();
    }
}
