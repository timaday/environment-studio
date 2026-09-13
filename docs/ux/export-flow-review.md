# Export guarded package approval packet

Status: approved with less-is-more correction on 2026-09-13. The generated desktop and narrow images
are recorded in `reference/export-flow-approval.json`. They use the approved
Midnight shell references and show only the blocked/unavailable guarded package
candidate state.

The proposal keeps export authority with the backend. It does not show a digest,
byte count, successful download, production readiness, Deploy, Run SQL, Execute
or Commit action. The package-candidate action remains disabled while validation
checks or client/content/review evidence are unresolved. External supervisor
usage is shown as reference guidance only and remains outside the hosted app.

Implementation must use actual backend package-candidate responses and reduce repeated explanatory copy where possible. A future
success/download state requires actual route evidence and separate approval if
it materially changes the screen.
Implementation note, 13 September 2026: Tim approved the view direction with “less is more where possible”. The implemented copy removes repeated backend explanation while preserving the unqualified-candidate and external-execution boundary.
