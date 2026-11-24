# Perspective DOM

[![Ignition 8.3](https://img.shields.io/badge/Ignition-8.3-blue.svg)](https://www.docs.inductiveautomation.com/docs/8.3/intro)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.txt)

An Ignition Perspective module that brings JavaScript-style DOM lookups to Perspective scripting. Find components by `meta.domId`, by name or with CSS-like selectors, the way `document.getElementById` and `document.querySelector` work in the browser.

The `system.dom` functions search Perspective's component tree on the Gateway, not the browser's HTML DOM.

Instead of navigating the component tree by name:

```python
self.parent.getChild("FlexContainer").getChild("FlexContainer").getChild("example-button").props.text
```

give the component a `domId` and look it up directly:

```python
system.dom.getElementById("example-button").props.text
```

Renaming or moving the component no longer breaks the script.

Runs on Ignition 8.3 Standard and **Maker Edition** (use the signed module on Maker, which doesn't allow unsigned modules). It is a free module and needs no license.

## Scripting Functions

### Lookups by ID
- `system.dom.getElementById(id)`: Returns the first matching component in the **current view**, or `None`.
- `system.dom.getPageElementById(id)`: Returns the first matching component across the **whole page** (current view first, then other views).
- `system.dom.getElementsById(id)`: Returns a Python list of all matching components in the **current view**.
- `system.dom.getPageElementsById(id)`: Returns a Python list of all matching components across the **whole page**.

### Lookups by Name (`meta.name`)
*(Fast: a component's name is held in memory, so matching needs no property-tree reads)*
- `system.dom.getElementByName(name)`: Returns the first component matching `meta.name` in the current view.
- `system.dom.getPageElementByName(name)`: Returns the first component matching `meta.name` across the page.
- `system.dom.getElementsByName(name)`: Returns a Python list of all matching components in the current view.
- `system.dom.getPageElementsByName(name)`: Returns a Python list of all matching components across the page.

### Query Selectors (CSS-like)
Supports the universal selector (`'*'`), types (`'button'`, `'label'`, or a full `'ia.input.button'`), IDs (`'#statusLabel'`), one or more classes (`'.warning'`, `'.warning.big'`), attributes (`'[name="Submit"]'`, `'[domId="foo"]'`, `'[type="ia.input.button"]'`), compound selectors (`'button#saveBtn'`, `'label.error'`, `'ia.display.label.error'`), and comma-separated unions (`'button, label'`):
- `system.dom.querySelector(selector)`: Returns the first matching component in the current view, or `None`.
- `system.dom.querySelectorAll(selector)`: Returns a Python list of all matching components in the current view.
- `system.dom.getPageQuerySelector(selector)`: Returns the first matching component across the whole page.
- `system.dom.getPageQuerySelectorAll(selector)`: Returns a Python list of all matching components across the whole page.

All single-component functions return the same kind of object as `self` (`ComponentModelScriptWrapper`), so `.props`, `.custom`, `.meta` and custom methods all work, including writes:

```python
# Look up by domId
lbl = system.dom.getElementById("statusLabel")
lbl.props.text = "Saved"

# Batch update all buttons
for btn in system.dom.querySelectorAll("button"):
    btn.props.enabled = False

# Fast lookup by component name
save_btn = system.dom.getElementByName("SaveButton")
```

## Limitations & Scope

### Query Selector Limitations
`querySelector` and `querySelectorAll` provide a lightweight, token-based selector engine tailored for Perspective. Be aware of the following CSS syntax differences:
- **No Combinators**: Hierarchical combinators like direct child (`>`), descendant (space), adjacent sibling (`+`), and general sibling (`~`) are **not supported** (e.g. `FlexContainer > button` or `card label` will not work). Every selector clause evaluates against individual components.
- **No Pseudo-Classes**: Pseudo-classes such as `:first-child`, `:last-child`, `:nth-child(n)`, `:has(...)`, `:not(...)`, or `:empty` are **not supported**.
- **Attribute Matching**: Exact equality `[attr="value"]` or `[attr=value]` is supported for `name`, `domId`, and `type` only. Substring operators (`*=`, `^=`, `$=`) and other attributes are not supported.
- **Types**: A short type matches the last part of the component type (`'button'` matches `ia.input.button`). A dotted word is read as a full type only when it has three parts, so `'ia.display.label.error'` is type `ia.display.label` plus class `error`, while `'label.error'` is type `label` plus class `error`.
- **Unsupported Syntax Raises an Error**: Anything outside the syntax above (combinators, pseudo-classes, other attributes or operators, empty clauses) raises an `IllegalArgumentException` naming the problem, rather than being ignored. A typo can never silently widen a match to every component. Note that in Jython a Java exception is not caught by `except Exception:`; use `except java.lang.IllegalArgumentException:` (after `import java`) or a bare `except:`.

### Runtime Scope Limitations
- **Perspective Scripts Only**: Functions require Perspective's thread-local session context (`ViewModel.VIEW` and `PageModel.PAGE`). They work in component event scripts, property change scripts, message handlers, and custom methods. Calling them from **Gateway Event Scripts, Tag Change Scripts, or the Designer Script Console** will raise an `IllegalStateException`.
- **View Isolation**: Functions without the `getPage` prefix search **only the calling view**. Embedded views (including rows in Flex Repeaters), docked views, and popups live in their own `ViewModel` and are ignored. Use the `getPage*` variants if you explicitly need to cross view boundaries.
- **Duplicate ID Shadowing**: `domId` values should ideally be unique within a view. If duplicates exist, single-match functions (`getElementById`, `querySelector('#id')`) return the **first match in document order**; all subsequent duplicates are shadowed. Use `getElementsById` to retrieve all matching instances.
- **Reference Persistence**: Component wrappers returned by these functions are live Java objects, not JSON values, so don't store them in `view.custom` or `session.custom`. Don't cache them in project script library globals either: views unmount when closed, leaving stale references and keeping the old view in memory.

## Performance & Considerations

- **Lookup cost depends on what you match on**: Matching on `meta.domId` or `props.style.classes` needs a read from the component's property tree, which means a hop to Perspective's execution queue (roughly $35 - 45\ \mu\text{s}$ per component checked). Matching on name or type uses values held in memory and needs no hop.
- **ID lookups on a ~120-component view**: about $0.6\text{ ms}$ for an early hit and $2 - 5\text{ ms}$ for a deep hit or a miss. Measurements vary by ±50% between runs.
- **Store the reference, but reads still cost**: Keeping the result in a local variable avoids repeating the search. Each `.props` / `.custom` read or write on it still goes through the property-tree queue, exactly like the same access on `self` (about $45\ \mu\text{s}$ measured).
- **Scope**: `domId` values should be unique within a view. If there are duplicates, the first match in document order wins; `getPageElementById` prefers matches in the calling view.

👉 See [**PERFORMANCE.md**](PERFORMANCE.md) for the cost model, benchmark results and best practices.

## Building

Requires Java 17, Node.js and Docker.

```bash
cp gradle.properties.template gradle.properties
./gradlew build
```

The unsigned module is written to `build/Perspective-DOM.unsigned.modl`. Install it on an Ignition 8.3 gateway that allows unsigned modules.

Signed builds for the [releases](https://github.com/NathanMoore4472/perspective-dom/releases) are made by passing a version and the signing details as properties (the keystore stays outside the repository):

```bash
./gradlew -Pversion=0.1.0 -PsignModule=true \
  -Pignition.signing.keystoreFile=/path/to/keystore.jks -Pignition.signing.keystorePassword=... \
  -Pignition.signing.certFile=/path/to/chain.p7b -Pignition.signing.certAlias=... -Pignition.signing.certPassword=... \
  clean build
```

This writes the signed module to `build/Perspective-DOM.modl`. Always include `clean` when changing the version.

## Development gateway

```bash
./gradlew build
docker compose -f docker/docker-compose.yml up -d
```

The gateway runs at http://localhost:9088 (`admin` / `password`) with the module installed. Ignition 8.3 loads modules only at startup, so after changing Java code rebuild and restart:

```bash
./gradlew build
docker compose -f docker/docker-compose.yml restart gateway
```

## Project layout

| Folder | Scope | Contents |
|---|---|---|
| `common` | Gateway + Designer | Script function signatures and docs |
| `gateway` | Gateway | `system.dom` implementation, selector parser, module hook |
| `designer` | Designer | Script autocomplete registration, module hook |
| `web` | Browser | Perspective component bundle |
| `docker` | – | Development gateway |

## License

MIT, see [LICENSE.txt](LICENSE.txt). Built from an MIT-licensed Perspective component module template.
