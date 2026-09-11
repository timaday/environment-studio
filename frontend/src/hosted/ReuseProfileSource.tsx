import type { ProfileRevision } from "../api/hostedV3Profiles";
export function ReuseProfileSource({
  source,
  heading = true,
}: {
  source: ProfileRevision;
  heading?: boolean;
}) {
  return (
    <div className="reuse-source">
      {heading && <strong>{source.projection.model.id}</strong>}
      {heading && (
        <p>
          Profile revision {source.projection.model.revision} · Saved revision{" "}
          {source.workspaceRevision} · {source.state === "published" ? "Published" : "Draft"}
        </p>
      )}
      <details>
        <summary>Source details</summary>
        <p>Profile revision identifies the version of the profile structure.</p>
        <p>
          Saved revision identifies the immutable workspace version. Published indicates its
          historical state; the backend checks current admission separately.
        </p>
        <dl>
          <dt>Workspace object</dt>
          <dd>{source.objectId}</dd>
          <dt>Definition</dt>
          <dd>
            {source.definition.objectId} · revision {source.definition.workspaceRevision}
          </dd>
        </dl>
      </details>
    </div>
  );
}
