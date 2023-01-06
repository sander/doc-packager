// #![warn(missing_docs)]

use std::fs;
use std::path::PathBuf;
use std::process::exit;
use std::str::FromStr;

use clap::{arg, Command};
use docpkg::transclusion::transclude;
use log::error;
use log::trace;

use docpkg::packaging::{DocumentationPackagingService, Manifest};
use docpkg::tracking;
use docpkg::tracking::SemanticVersion;

// extern crate lib;

// #[risk("Some risk annotation")]
fn cli() -> Command {
    Command::new("docpkg")
        .about("Documentation Packager")
        .subcommand_required(true)
        .subcommand(
            Command::new("edit")
                .about("Edits a documentation package")
                .arg(arg!(<dir> "The source root path").value_parser(clap::value_parser!(PathBuf)))
                .arg_required_else_help(true),
        )
        .subcommand(
            Command::new("publish")
                .about("Publishes a documentation package")
                .arg(arg!(<dir> "The source root path").value_parser(clap::value_parser!(PathBuf)))
                .arg_required_else_help(true),
        )
}

fn main() {
    pretty_env_logger::init();

    trace!("Starting main application");

    let requirement = SemanticVersion::new("git", 2, 37, 0);
    if !tracking::get_version()
        .filter(|v| requirement.is_met_by(v))
        .is_some()
    {
        error!("Invalid git version, needs at least {}", requirement);
        exit(1);
    }

    let matches = cli().get_matches();
    match matches.subcommand() {
        Some(("edit", sub_matches)) => {
            let path = sub_matches.get_one::<PathBuf>("dir").expect("required");
            let manifest_path = path.join("Docpkg.toml");
            let contents = fs::read_to_string(manifest_path).unwrap();
            let manifest = Manifest::from_str(&contents).unwrap();
            for document in manifest.files {
                transclude(document.0);
            }
        }
        Some(("publish", sub_matches)) => {
            let path = sub_matches.get_one::<PathBuf>("dir").expect("required");
            println!("Publishing {:?}", path);
            let service = DocumentationPackagingService::open(path.clone());
            service.publish();
        }
        _ => unreachable!(),
    }
}
