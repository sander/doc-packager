extern crate proc_macro;

use proc_macro::TokenStream;

#[proc_macro_attribute]
pub fn risk(attr: TokenStream, item: TokenStream) -> TokenStream {
    println!("risk: {}", attr);
    item
}