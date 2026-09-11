import { useEffect, useMemo, useRef, useState } from "react";
import { ApiFailure, failureMessage, type HostedApi } from "../api/hosted";
import { HostedV3Profiles, type ProfileList, type ProfileRevision } from "../api/hostedV3Profiles";

type State = Readonly<{
  profiles: ProfileList["profiles"] | null;
  selected: ProfileRevision | null;
  busy: boolean;
  error: string;
}>;
const empty: State = { profiles: null, selected: null, busy: false, error: "" };

/** Owned historical reads only; stored publication never authorizes runtime use. */
export function useV3ProfileCatalog(api: HostedApi, enabled: boolean) {
  const owner = useMemo(
    () => ({ client: new HostedV3Profiles(api), active: false, generation: 0 }),
    [api],
  );
  const [state, setState] = useState<State>(empty);
  const latest = useRef(state);
  function replace(next: State) {
    latest.current = next;
    setState(next);
  }
  useEffect(() => {
    owner.active = enabled;
    owner.generation++;
    latest.current = empty;
    setState(empty);
    return () => {
      owner.active = false;
      owner.generation++;
    };
  }, [owner, enabled]);
  const current = (token: number) => owner.active && owner.generation === token;
  function failed(token: number, error: unknown, profiles: State["profiles"]) {
    if (!current(token)) return;
    if (error instanceof ApiFailure && error.code === "SESSION_REQUIRED") {
      owner.active = false;
      owner.generation++;
      replace({ ...empty, error: failureMessage(error) });
    } else replace({ ...empty, profiles, error: failureMessage(error) });
  }
  async function load() {
    if (!owner.active || !enabled) return;
    const token = ++owner.generation;
    replace({ ...empty, busy: true });
    try {
      const value = await owner.client.profiles();
      if (current(token)) replace({ ...empty, profiles: value.profiles });
    } catch (error) {
      failed(token, error, null);
    }
  }
  async function select(objectId: string) {
    if (!owner.active || !enabled) return;
    const profiles = latest.current.profiles;
    const entry = profiles?.find((item) => item.objectId === objectId);
    const token = ++owner.generation;
    if (!entry) {
      replace({ ...empty, profiles, error: "INVALID_REQUEST" });
      return;
    }
    replace({ ...empty, profiles, busy: true });
    try {
      const value = await owner.client.profileRevision(entry.objectId, entry.workspaceRevision);
      if (!current(token)) return;
      if (
        value.projection.model.id !== entry.nativeId ||
        value.projection.model.revision !== entry.nativeRevision ||
        value.sourceDigest !== entry.sourceDigest ||
        value.projection.contentDigest !== entry.contentDigest ||
        value.state !== entry.state ||
        value.definition.objectId !== entry.definition.objectId ||
        value.definition.workspaceRevision !== entry.definition.workspaceRevision
      )
        throw new ApiFailure(409, "CONFLICT");
      replace({ ...empty, profiles, selected: value });
    } catch (error) {
      failed(token, error, profiles);
    }
  }
  return { ...state, load, select };
}
