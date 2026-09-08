# Adapter guidance

Implement inbound/outbound adapters here; semantics belong in core. Authenticate
and authorize real-data APIs before accepting any DB credentials. Keep the demo
mutation-denial policy until that behavior is implemented and tested.

Do not add a datasource pool, JPA entity or Flyway migration for managed XML.
Read adapters own a single bounded authenticated connection. SQL writers return
artifacts/refusals and have no connection port. XML uploads never instantiate
Spring beans. HTTP error messages contain safe codes, not raw exception payloads.

Adapters are generic engine/storage/XML mechanisms driven by external declarations.
Do not encode real table/column locators, application types, relationships or rules
in Java/resources. Test only independently invented mock database/configuration
cases; private Q feedback must follow the root information boundary.
