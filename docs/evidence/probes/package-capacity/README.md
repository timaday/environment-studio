# Package capacity probes

These files are generic, independently invented evidence probes used for the
package-read and guarded-package-assembly resource observations. They do not
contain real XML, database models, credentials, locators or transformed customer
configuration.

The probes create mock V3 definitions, mock publication and observation ports,
materialize four hosted plans, prepare a bounded package payload, verify exact
canonical payload bytes, admit the package, and for assembly checks write the
candidate archive to a digesting output stream. The run scripts show the exact
Docker constraints used by the recorded evidence: 1 GiB memory and swap, one CPU,
256 PIDs, no network, read-only container, no new privileges, 65% JVM heap and a
300 second deadline.

`result-summaries.json` records the source revisions, image identifiers, result
hashes, per-shape outcomes and command arrays for the preserved local results.
Full extracted application bundles remain local build artifacts, not repository
content.
