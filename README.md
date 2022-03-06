# Doc Packager

**Doc Packager** is a tool that enables packaging both evergreen and living documentation into human-readable packages.

## Running

```
./node_modules/.bin/cucumber-js --format=message:out.json

node parse.js > in.tex && /Library/TeX/texbin/lualatex in
```