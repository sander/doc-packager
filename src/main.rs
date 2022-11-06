// #![warn(missing_docs)]

use std::fs;
use std::path::PathBuf;
use std::process::exit;
use std::str::FromStr;

use clap::{arg, Command};
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

    let contents = fs::read_to_string("Docpkg.toml").unwrap();
    let value: Manifest = Manifest::from_str(&contents).unwrap();
    println!("Value: {:?}", value);

    println!("Version {:?}", tracking::get_version().unwrap());
    let matches = cli().get_matches();
    match matches.subcommand() {
        Some(("publish", sub_matches)) => {
            let path = sub_matches.get_one::<PathBuf>("dir").expect("required");
            println!("Publishing {:?}", path);
            let service = DocumentationPackagingService::open(path.clone());
            service.publish();
        }
        _ => unreachable!(),
    }
}
