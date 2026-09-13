# Standalone guarded supervisor candidate

Build from the repository root with the pinned Java 21 / Maven 3.9.16 toolchain:

```
mvn -B -ntp -f backend/pom.xml verify
```

The versioned directory and ZIP are under this module's `target/`. The directory
contains an executable launcher, an explicit fixed JAR set, dependency license
notices and `SHA256SUMS`. The ZIP is a portable content archive; installation must
restore the launcher's executable mode and independently verify its inventory.
No native client, Java runtime, trust configuration or package is bundled.

The ordinary entry point has an empty compiled runtime registry and an unavailable
console/native-runtime composition. It refuses before credential entry or client
launch. There is no test flag, execution override, environment credential or HTTP
route. Package admission, regenerated SQL, transcript framing, commit/acknowledgement
and owned-process mechanisms are exercised through package-private test ports.
Those tests do not authorize a runtime combination.

`ProcessBuilder` is confined to this tools module. The server dependency is its
explicit thin `classes` classifier, containing classes and schemas, with all
transitive dependencies excluded. This module enumerates the required dependencies
and assembly refuses extra JARs, Boot archives and static UI resources. It does not
ship Spring, servlet/security libraries, JDBC drivers or SQLite.

Concrete console echo/input-drain/restoration and Oracle certificate-only wallet
preparation remain unavailable pending exact native qualification. Native TLS,
startup/EOF/death/commit-fault tests and full target/rollback database witnesses
belong to the next qualification slice. The web application remains unable to
launch or execute this supervisor.
