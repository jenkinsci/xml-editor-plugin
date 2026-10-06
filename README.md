# XML Editor Plugin for Jenkins

Query, read, edit and validate XML files from Pipeline and Freestyle jobs **without breaking them**.

Most ways of editing XML in Jenkins (for example `XmlSlurper` + `XmlUtil.serialize` in a Pipeline) rewrite the
whole file: comments disappear, indentation and quotes change, the XML declaration is rewritten, and the diff of a
one-line version bump touches every line. This plugin is **lossless**: everything you do not change stays
byte-for-byte identical, including comments, whitespace, attribute order and quotes, `DOCTYPE`, encoding,
byte order mark and line endings.

## Features

- `xmlQuery`: evaluate XPath and get a `String`, `List`, number or boolean
- `xmlRead`: turn a document (or part of it) into plain maps and lists
- `xmlEdit`: `setText`, `upsert`, `setAttribute`, `removeAttribute`, `addElement`, `remove`, in one pass, all or nothing
- Edit **many files at once** (`files: '**/*.csproj'`), preview with `dryRun`, or edit XML **text in memory**
- `xmlValidate`: well-formedness and XSD validation with line numbers
- Freestyle build steps **Edit XML file** and **Validate XML file**
- Work happens **on the agent** that holds the file, not on the controller
- Plain return values: no `NotSerializableException`, no script approvals
- An XPath that matches nothing **fails** by default instead of silently doing nothing
- Secure by default: no external entities or DTDs (XXE), entity expansion limits, paths confined to the workspace
- New elements get the indentation of their siblings, and a diff of every change is printed in the build log
- Results include the **previous values** of what was changed
- XPath fields are checked **as you type** in the job configuration and in the Snippet Generator

## Pipeline examples

### Maven POM version bump

```groovy
def version = xmlQuery file: 'pom.xml', xpath: '/project/version'
echo "Current version: ${version}"

xmlEdit file: 'pom.xml', operations: [
    setText(xpath: '/project/version', value: '2.0.0')
]
```

The POM's default namespace is handled for you: `/project/version` just works. If you prefer standard XPath
rules, bind a prefix: `xmlQuery file: 'pom.xml', xpath: '/m:project/m:version', namespaces: [m: 'http://maven.apache.org/POM/4.0.0']`.

### .csproj

```groovy
xmlEdit file: 'src/App/App.csproj', operations: [
    setText(xpath: '//PropertyGroup/Version', value: env.BUILD_VERSION),
    addElement(xpath: "//ItemGroup[PackageReference]",
               fragment: '<PackageReference Include="Serilog" Version="3.1.1" />'),
    remove(xpath: "//PackageReference[@Include='Obsolete.Package']", expected: 'ANY')
]
def packages = xmlQuery file: 'src/App/App.csproj', xpath: '//PackageReference/@Include', returnType: 'LIST'
```

### Every project of a .NET solution, creating `<Version>` where it is missing

```groovy
def res = xmlEdit files: '**/*.csproj', excludes: '**/obj/**', dryRun: params.DRY_RUN, operations: [
    upsert(xpath: '/Project/PropertyGroup/Version', value: env.BUILD_VERSION)
]
res.files.each { echo "${it.file}: ${it.operations[0].oldValues} -> ${env.BUILD_VERSION}" }
```

All files are edited in memory first: if an operation fails on any file, **no file is written**.

### XML that is not in a file

```groovy
def response = httpRequest(url: 'https://example.com/config.xml').content
def r = xmlEdit text: response, operations: [setText(xpath: '//timeout', value: '30')]
writeFile file: 'config.xml', text: r.text
def port = xmlQuery text: r.text, xpath: '//port', defaultValue: '8080'
```

### Reading into maps

```groovy
def pom = xmlRead file: 'pom.xml'
echo "${pom.artifactId} ${pom.version}"
pom.dependencies.dependency.each { echo it.artifactId }   // repeated elements become lists
def dep = xmlRead file: 'pom.xml', xpath: "//dependency[artifactId='junit']"
```

Elements with only text become strings, attributes use the key `@name`, text next to child elements uses `#text`.

### Validation

```groovy
xmlValidate file: 'config.xml', schema: 'schema/config.xsd'          // fails the build if invalid
def r = xmlValidate file: 'config.xml', schema: 'schema/config.xsd', failOnError: false
if (!r.valid) { r.errors.each { echo "${it.line}:${it.column} ${it.message}" } }
```

## Reference

### Common parameters

| Parameter | Description |
|---|---|
| `file` | Path relative to the current directory (`dir {}` is honoured); it cannot leave the workspace |
| `text` | XML text instead of `file` (all steps; `xmlEdit` returns the edited text as `text`). Set exactly one of them: the Snippet Generator flags conflicting fields |
| `namespaces` | Extra prefixes for XPath, e.g. `[m: 'urn:x']`. Prefixes declared in the document work without it (`xmlQuery`, `xmlRead`, `xmlEdit`) |
| `strictNamespaces` | `true` for standard XPath 1.0 namespace rules (default namespace not transparent) (`xmlQuery`, `xmlRead`, `xmlEdit`) |
| `maxSizeMb` | Refuse larger files (default `50`) |

### `xmlQuery`

| Parameter | Default | Description |
|---|---|---|
| `xpath` | | XPath 1.0 expression |
| `returnType` | `STRING` | `STRING`, `LIST`, `NUMBER` or `BOOLEAN` |
| `failIfNotFound` | `true` | with `STRING`, fail when nothing matches (otherwise return `null`) |
| `defaultValue` | | with `STRING`, value returned when nothing matches (instead of failing) |

### `xmlEdit`

| Parameter | Default | Description |
|---|---|---|
| `operations` | | list of operations, applied in order |
| `file` / `files` / `text` | | exactly one: a file, Ant patterns (comma separated, e.g. `'**/*.csproj'`), or XML text |
| `excludes` | | Ant patterns to leave out, with `files` |
| `outputFile` | | write the result here and leave `file` untouched (only with `file`) |
| `showDiff` | `true` | print a unified diff in the build log (disable it for files with secrets) |
| `dryRun` | `false` | run everything and show the diff, but write nothing |

Returns (with `file`):
`[file:, changed:, dryRun:, operations: [[type:, xpath:, matched:, modified:, oldValues: [...]], ...]]`.
With `files`: `[changed:, dryRun:, files: [[file:, changed:, operations: [...]], ...]]`.
With `text`: `[changed:, operations: [...], text: '<the edited XML>']`.
`oldValues` holds the value of each processed node before the change (`null` for a missing attribute).
When nothing changes, the file is not rewritten.

Every operation takes `xpath` and an optional `expected`: `AT_LEAST_ONE` (default), `ONE`, `ANY` or an exact
number such as `'2'`. If one operation's count does not match, the step fails and **the file is not written**.

| Operation | Parameters | Effect |
|---|---|---|
| `setText` | `xpath`, `value` | Text of elements without child elements, value of attributes, or a text node. Comments inside the element are kept, CDATA stays CDATA when possible |
| `upsert` | `xpath`, `value` | Like `setText`, but when nothing matches it **creates** the missing elements (and a final `@attribute`) under the deepest existing one. The XPath must then be a simple absolute path such as `/Project/PropertyGroup/Version`, optionally with `[@attr='value']` predicates. `expected` defaults to `ANY` |
| `setAttribute` | `xpath`, `name`, `value` | Adds or updates an attribute; new attributes follow the existing layout |
| `removeAttribute` | `xpath`, `name` | Removes an attribute when present |
| `addElement` | `xpath`, `fragment`, `position` | Inserts XML as `LAST_CHILD` (default), `FIRST_CHILD`, `BEFORE` or `AFTER`, indented like its siblings |
| `remove` | `xpath` | Removes elements, attributes, comments or text; a node alone on its line takes the line with it |

### `xmlValidate`

| Parameter | Default | Description |
|---|---|---|
| `schema` | | XSD in the workspace; includes/imports are only read from the workspace |
| `failOnError` | `true` | fail the build when invalid |

Returns `[valid: true|false, errors: [[line:, column:, severity:, message:], ...]]`.

## Freestyle jobs

Add the build steps **Edit XML file** or **Validate XML file**. Environment variables such as `$BUILD_NUMBER`
are expanded in every field. **Edit XML file** also supports file patterns, excludes, dry run and namespace
prefixes (one `prefix=uri` per line).

## Security

- External entities, external DTDs and remote schemas are never loaded; entity expansion is limited.
- A `DOCTYPE` is accepted (many real files have one) and kept as is.
- Paths are resolved inside the workspace, including through symbolic links.
- Files are written atomically (temporary file, then rename).

## Limitations

- XPath 1.0 (the JDK engine). XSLT and XPath 2.0+ are not supported yet.
- Custom entities declared in the internal DTD subset are kept in the file; XPath sees their value only when it is
  plain text.
- `xmlQuery`, `xmlRead` and `xmlValidate` work on one file at a time (`xmlEdit` supports patterns).

## Development

```bash
./mvnw verify      # build, tests, SpotBugs, formatting check
./mvnw hpi:run     # Jenkins with the plugin on http://localhost:8080/jenkins
```

Requires JDK 17+ to build (JDK 21 recommended). Licensed under the [MIT License](LICENSE.md).
