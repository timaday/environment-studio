# Core guidance

Framework-free Java only. Domain/application packages cannot import JDBC, Spring,
HTTP, XML parser or filesystem APIs. Add a port when there is an actual external
boundary. Keep collections immutable, ordering explicit and errors typed.

Write the failing behavior test before implementation. Test missing evidence,
stale input, ambiguous mappings and graph edge cases. Do not merely assert that
an implementation returns its own calculated expected result. The starter gate
does not replace definition-specific rules or the future validated-plan boundary.
