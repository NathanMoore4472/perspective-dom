# Performance Considerations for Perspective DOM

This document covers the cost model, measured benchmarks and best practices for the **Perspective DOM** module (`system.dom.*`).

---

## 1. Summary

- **Design trade-off**: `system.dom` trades some lookup speed for scripts that survive renaming and restructuring views.
- **What a lookup costs depends on what it matches on**:
  - **ID and class** (`getElementById`, `#id`, `[domId=...]`, `.class`): each component checked needs a read from its property tree, which is a hop to Perspective's execution queue (roughly **35–45 µs per component**).
  - **Name and type** (`getElementByName`, `[name=...]`, `button`, `ia.input.button`): a component's name and type are plain fields in memory, so matching needs **no queue hop**.
- **Reads after the lookup are not free**: the returned object is the same kind of wrapper as `self`. Every `.props` / `.custom` read or write on it goes through the property-tree queue, just as it does on `self` (about **45 µs** per read, measured). Storing the reference saves repeating the *search*, not the reads.
- **In practice**: on a ~120-component view, ID lookups take about **0.6 ms** (early hit) to **5 ms** (deep hit or miss). That is imperceptible for a button click, but adds up in scripts that run often or loop.

---

## 2. Cost Model

### Native Ignition (`getChild` / `getSibling`)
```python
self.parent.getChild("Container").getChild("Label").props.text = "Hello"
```
- **Lookup**: at each level, finds the child with that name among the container's children. Only the components along the path are visited, and names are held in memory.
- **Queue hops**: none for the navigation itself. The final `.props.text` access costs one, as any property access does.

### Lookups by ID or class
```python
lbl = system.dom.getElementById("statusLabel")
```
- **Search**: depth-first, in document order, over the components in scope (the calling view, or the whole page for `getPage*`).
- **The queue hop**: Perspective only allows a property tree to be read on its own execution queue. The script thread is not on it, so each `meta.domId` (or `props.style.classes`) read is handed to the queue and the script waits:
  ```java
  meta.getQueue().runOrSubmit(() -> meta.read("domId")...).get();
  ```
  This is the same mechanism Perspective's own script wrappers use for `self.props.x`.
- **Total cost** ≈ components checked × (queue hop + read). A hit stops early; a miss checks every component in scope. Multi-match functions (`getElementsById`, `querySelectorAll`) always check every component.

### Lookups by name or type
```python
btn = system.dom.getElementByName("SubmitButton")
buttons = system.dom.querySelectorAll("button")
```
- `ComponentModel.getName()` and `getType()` return plain fields on the component. Matching needs no property-tree read and no queue hop.
- The cost still grows with the number of components walked, but each check is cheap.

### Compound selectors
Within a selector clause, cheap checks run first (type, name), and property-tree reads (domId, classes) only happen for components that pass them. So `button#save` only reads `domId` on buttons, and is much cheaper than `#save` on a view with few buttons.

---

## 3. Benchmarks (Ignition 8.3.9, Docker, Apple Silicon Mac)

Measured with the `HeavyTest` (129 components, 7 levels of nesting, 100 iterations) and `BenchmarkSuite` (106 components, 50 iterations) views in the example project. Ranges cover three runs. Treat them as orders of magnitude, not exact figures:

- Results vary by up to ±50% between runs (the native baseline alone measured 70–220 µs).
- Jython's `time.time()` has millisecond resolution, so per-iteration figures under ~50 µs are coarse.
- Rows marked † include one `.props.text` read on the result (about 45 µs) inside the timed loop.

### Heavy view (129 components)

| Operation | Components checked | Measured |
| :--- | :--- | :--- |
| Native `getChild` chain, 7 levels † | 9 (by name) | 70–220 µs |
| `getElementById`, early hit † | ~15 | ~0.6 ms |
| `getElementById`, deep hit † | ~65 | 2.3–2.8 ms |
| `getElementById`, near the end † | ~125 | 4.5–4.8 ms |
| `getElementById`, miss | 129 | 2.8–4.9 ms |
| `getPageElementById`, miss | 129+ (all views) | 4.4–5.1 ms |

### Benchmark suite (106 components)

| Operation | Queue hops | Measured |
| :--- | :--- | :--- |
| `getElementByName` † | none | 60–80 µs |
| `getElementsByName` (10 matches) | none | 100–340 µs |
| `querySelector('button')` † | none | 60–160 µs |
| `querySelectorAll('button')` | none | 180–860 µs |
| `querySelector('[name="..."]')` † | none | 40–80 µs |
| `getElementById('deepButton')` † | one per component checked | 1.8–2.1 ms |
| `getElementsById('sharedCard')` | one per component | 3.4–3.6 ms |
| `querySelector('button#deepButton')` † | one per button checked | 0.5–0.6 ms |
| `querySelectorAll('.card-item')` | one per component | 3.3–3.8 ms |

### Property access on a stored reference

| Operation | Measured |
| :--- | :--- |
| `ref.props.text` read (`ref` from `getElementById`) | ~45 µs |
| `self.meta.name` read | ~45 µs |

---

## 4. Multi-Element Lookups (`getElementsById`, `querySelectorAll`)

- **Single-element functions** (`getElementById`, `getElementByName`, `querySelector`) stop at the **first match** in document order.
- **Multi-element functions** (`getElementsById`, `getElementsByName`, `querySelectorAll`) walk the whole view (or page) and return **all matches** as a Python list.

```python
# Disable every button in the view:
for btn in system.dom.querySelectorAll("button"):
    btn.props.enabled = False

# Reset every component sharing a domId:
for lbl in system.dom.getElementsById("statusLabel"):
    lbl.props.text = "Ready"
```

Collecting all matches costs about the same as a miss, since both walk the whole tree. Each write in the loop then costs a queue hop of its own.

---

## 5. Query Selectors (`querySelector` / `querySelectorAll`)

| Selector | Matches | Example | Queue hops |
| :--- | :--- | :--- | :--- |
| **Universal** | Every component | `'*'` | None |
| **Type** | Last part of the type, or a full three-part type | `'button'`, `'ia.input.button'` | None |
| **Name** | `meta.name` | `'[name="Save"]'`, `'[name=Cancel]'` | None |
| **ID** | `meta.domId` | `'#submitBtn'`, `'[domId="statusLabel"]'` | One per component checked |
| **Class** | `props.style.classes` (all listed classes must be present) | `'.warning'`, `'.warning.big'` | One per component checked |
| **Compound** | All criteria | `'button#saveBtn'`, `'label.error'`, `'ia.display.label.error'` | Only for components passing the cheap checks |
| **Union** | Any clause | `'button, label, #headerTitle'` | Depends on clauses |

```python
# No queue hops: all labels by type
all_labels = system.dom.querySelectorAll("label")

# No queue hops: a button by name
submit = system.dom.querySelector("button[name='SubmitAction']")

# Reads domId only on buttons
btn = system.dom.querySelector("button#saveButton")
```

> [!NOTE]
> **Unsupported syntax raises an error.** Combinators (space, `>`, `+`, `~`), pseudo-classes (`:first-child`, `:not()`, ...), attributes other than `name` / `domId` / `type`, and operators other than `=` raise an `IllegalArgumentException` that names the problem. They are never silently ignored, so a mistyped selector can't widen a match to every component.

---

## 6. Store the Reference, Not the Search

Each lookup repeats the search, so look a component up once per script and keep it in a local variable.

### ✅ Recommended
```python
target = system.dom.getElementById("tankLevelGauge")   # search: ~2 ms on a large view

if target is not None:
    target.props.value = 84.5          # each access: one queue hop (~45 µs)
    target.props.style.stroke = "#28a745"
    target.custom.lastUpdated = system.date.now()
```

### ❌ Avoid
```python
# Searches the view three times
system.dom.getElementById("tankLevelGauge").props.value = 84.5
system.dom.getElementById("tankLevelGauge").props.style.stroke = "#28a745"
system.dom.getElementById("tankLevelGauge").custom.lastUpdated = system.date.now()
```

---

## 7. Best Practices

1. **Prefer name or type matching when it's precise enough.** `getElementByName("MyBtn")` and `querySelectorAll("button")` need no queue hops. Names are less stable than `domId` under refactoring, though, so choose per use case.
2. **Narrow ID/class selectors with a type.** `button#save` only reads `domId` on buttons.
3. **Use view-scoped lookups** (`getElementById` over `getPageElementById`) unless you need to reach docks, popups or embedded views.
4. **Avoid lookups in loops.** Resolve components once, outside the loop.
5. **Don't store references in `view.custom` / `session.custom`.** These hold JSON-style values that sync to the browser, not live Java objects.
6. **Don't store references in project script library globals.** Views are discarded when closed. A global reference goes stale and keeps the old view in memory.

---

## 8. Why No Cache (Yet)

Caching `domId → component` could make repeat lookups much faster, but it has costs:

| Challenge | Impact |
| :--- | :--- |
| **Components come and go** | Flex Repeaters, embedded views and popups create and discard components at runtime. A cached component may no longer be on screen. |
| **`domId` can change** | A binding or script can change `meta.domId`, so a cached entry can point at the wrong component. |
| **Memory** | Strong references keep discarded views alive. A cache would need weak references or cleanup when views shut down. |
| **Validation** | Every cache hit must still check that the component is running and still has that `domId`. That is **one** queue hop per lookup instead of one per component checked, a large saving on big views, but it adds complexity. |

### Possible Future Optimisation: One Queue Hop Per View
Instead of one queue hop per component, a lookup could run its whole search for a view as a single task on that view's queue. This replaces N hops with one, while still reading N components (cheaply, since the reads happen on the queue). The cost would still grow with view size, just far more slowly.

This assumes all components in a view share one execution queue, which has not been verified yet. It also means one long task occupies that queue for the whole search, delaying other updates to the view.
