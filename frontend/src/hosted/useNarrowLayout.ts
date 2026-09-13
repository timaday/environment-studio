import { useSyncExternalStore } from "react";

const query = "(max-width: 800px)";
function subscribe(changed: () => void) {
  const media = window.matchMedia?.(query);
  media?.addEventListener("change", changed);
  return () => media?.removeEventListener("change", changed);
}
const snapshot = () => window.matchMedia?.(query).matches ?? false;

/** Presentation only; responsive composition never changes operation scope. */
export function useNarrowLayout() {
  return useSyncExternalStore(subscribe, snapshot, () => false);
}
