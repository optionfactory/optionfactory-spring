# 0011. Request parameters bind to fields directly

Status: accepted

## Context

Applications used to write their DTOs as classes with public fields, not
JavaBeans. Jackson reads and writes public fields, so `@RequestBody` and
responses worked; spring's data binder does not. It binds request parameters
(query strings, forms: `@ModelAttribute` and undeclared complex handler
arguments) through bean properties only, and a public field without a setter
is not a property. The failure is silent: the parameter is ignored, the field
stays `null`, and no binding error is reported. Verified on spring 7.0:
`?name=alice&age=42` bound to `class Form { public String name; public Integer
age; }` yields `name=null age=null` and an empty binding result.

## Decision

`@EnableCustomWebMvc`, used in place of `@EnableWebMvc`, turns on direct field
access for every data binder (`ConfigurableWebBindingInitializer.setDirectFieldAccess(true)`):
request parameters are written straight into fields, public or not, setters
or not. `@EmbeddedTomcatWebMvcApplication` uses it.

## What it protects from, and what it costs

It protects field-only DTO classes bound from request parameters from being
silently left empty. It does nothing for `@RequestBody` (Jackson),
`@RequestParam`, `@PathVariable` or responses, which never went through the
data binder's property access.

It costs, verified on spring 7.0 with mock mvc:

- **records cannot be bound from request parameters.** Spring constructs the
  record from the parameters, then, with direct field access, also tries to
  write each parameter into the record's final fields; that fails with "Field
  is not accessible" and the request is answered `400`. With plain
  `@EnableWebMvc` the same record binds correctly. A class with a nested record
  property fails with a `500` instead (the nested path cannot be auto-grown).
- **every field is bindable**, private ones without setters included: a
  request parameter named after an internal field writes it. Binding is no
  longer limited to what the DTO chose to expose, so field-bound DTOs must not
  carry fields a client must not set.

## Consequences

- Field-only DTO classes bound from request parameters work, and records bound
  from request parameters do not: the two DTO styles cannot both be bound from
  query strings and forms under the same configuration. JSON bodies work with
  both.
- Direct field access can be dropped once no type bound from request
  parameters relies on fields without setters, that is when every such type is
  a record or a JavaBean. JSON-only DTOs do not count. Dropping it changes how
  existing applications bind, so it follows 0002.
- The costs are documented where the setting is enabled
  (`@EnableCustomWebMvc`'s javadoc, the context-web readme) and where it is
  imported implicitly (`@EmbeddedTomcatWebMvcApplication`).
