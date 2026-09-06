# Recipe Signature Contract

The signature API no longer enumerates object fields. This prevents dedicated
server class loading failures from client-only fields inside recipe objects and
prevents rendering state or lookup caches from affecting recipe validity.

## Supported Values

`RecipeSnapshotCompiler.semanticSignature(Object)` accepts:

- Exact built-in recipe, input and output types listed in `BuiltinRecipeSignatureData`.
- `String`, `Character`, `Boolean`, boxed primitive numbers, `BigInteger`,
  `BigDecimal`, enums and null. Arbitrary `Number` or `CharSequence` implementations
  are not treated as scalars.
- Item, gas, fluid, block and infusion resource identities. Registry objects are
  represented by their IDs, without traversing their implementation fields.
- Exact `ItemStack`, `GasStack`, `FluidStack`, `InfuseStorage` and standard NBT types.
- Core `ImmutableResourceSnapshot`, `RecipeResourceFlow`, `RecipeSemanticsSnapshot`
  and `FarmChanceOutput` values.
- Arrays, iterables, sets and maps containing supported values only.
- Objects explicitly implementing `mekanism.api.recipes.IRecipeSignatureSource`.

Unknown objects, unadapted subclasses of built-in recipe/input/output classes,
unsupported nested values, cycles and nesting beyond 128 levels throw
`UnsupportedRecipeSignatureException`. The error identifies the type and value
path. Unknown objects are not reflected or converted with `toString()`, and no
partial signature or placeholder is returned.

Map, set and NBT compound order does not matter. List and array order does matter,
including lane ordering. Shared references and independent equal copies have
equal signatures. Length framing prevents delimiters in data from creating
ambiguous encodings. Built-in recipe time, energy and probabilities are included;
input lookup caches and rendering metadata are excluded.

Signatures now start with `recipe-signature-v2:`. Treat them as opaque comparison
values, not stable serialized identifiers across core versions. Rebuild any
external persisted signature caches when upgrading.

## Adapting a Third-Party Recipe

Resolve the third-party recipe through its supported API on the server thread.
Do not pass an arbitrary `IRecipe` object into the one-argument method.

```java
Map<String, Object> data = new LinkedHashMap<>();
data.put("recipeId", recipeId);
data.put("inputs", resolvedInputs);
data.put("output", resolvedOutput);
data.put("remainders", resolvedRemainders);
data.put("ticks", requiredTicks);
data.put("energy", requiredEnergy);
String signature = RecipeSnapshotCompiler.semanticSignature("myaddon:crafting_v1", data);
```

Use a stable, nonempty namespaced schema ID and include all relevant semantics:
counts, NBT, ordering, probabilities, custom requirements and operation modes as
applicable. The two-argument method validates the entire data graph; putting the
original recipe, a handler or a client object into the Map is still rejected.
Snapshots must be fresh or defensively copied and must not mutate during capture.

An owned recipe class or wrapper may instead implement `IRecipeSignatureSource`:
`getRecipeSignatureType()` returns the same schema ID, and
`getRecipeSignatureData()` returns the same data Map. Both entry points produce
the same signature for the same schema and data. No undeclared fields of the
adapter are inspected, but the adapter itself must use server-safe code.

Built-in recipe subclasses are not automatically trusted: a subclass may add
requirements which a base-class projection would omit. Explicitly include those
requirements through the adapter. Signature extraction no longer calls `copy()`
first, since an inherited copy implementation could erase the subclass.

## Validation and Failure

This API only supplies a signature. It neither makes third-party callbacks safe
on workers nor extends `RecipeSemanticsCompiler` or the worker value whitelist.
Keep matching, third-party callbacks and semantic extraction on the server thread.
Workers receive detached core values and compute plans; commits remain on the
server thread.

At commit time, verify machine/configuration versions, recipe generation and
recipe identity, then re-resolve and compare all relevant semantics. A registry
name alone cannot detect an in-place recipe edit or a changed dynamic result.
Do not remove signature checks, compare only IDs, reuse stale resolved data, or
replace failures with a constant signature.

The base machine scheduler skips processing when signature capture is unsupported.
If a snapshot/plan already exists, validation or calculation failures discard it
and release the pending reservation. It does not run the old recipe loop as an
automatic fallback. Identical signature warnings for a tile are suppressed until
a successful commit; capture retries continue so a supported recipe can recover.
Direct callers of the compiler receive the exception and must explicitly handle
unsupported recipes without consuming resources or committing an incomplete plan.

## Addon Migration

Existing callers using supported core recipe types continue to work. Callers
passing arbitrary third-party `IRecipe` objects must migrate to explicit semantic
data before deploying this core version with those machines. In particular,
ExtendedCrafting's direct source-signature calls require adaptation.

Use the new core dev Jar for compilation and the new production core Jar at
runtime. Reusing the same filename does not imply that an older local dev Jar
contains the new interface or overload.

Verify dedicated-server class isolation, complete semantic invalidation, nested
unsupported objects, cycles, lane order, blocked outputs, failure without partial
resource changes and recovery. Unit tests do not substitute for actual server,
cross-mod or long-running tests.
