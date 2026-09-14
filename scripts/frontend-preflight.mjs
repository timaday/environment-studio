import os from "node:os";
import process from "node:process";
import { spawnSync } from "node:child_process";

const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const npmVersion = spawnSync(npm, ["--version"], { encoding: "utf8" });
const nodeMajor = Number(process.versions.node.split(".")[0]);
const qualified = nodeMajor >= 24 && nodeMajor < 27;

console.log(`platform=${process.platform} ${os.release()} ${os.arch()}`);
console.log(`node=${process.version}`);
console.log(`npm=${npmVersion.status === 0 ? npmVersion.stdout.trim() : "unavailable"}`);
console.log(`cwd=${process.cwd()}`);
console.log(`frontendNodeQualified=${qualified ? "yes" : "no"}`);

if (!qualified) {
  console.log("Environment Studio frontend is qualified for Node 24–26. If build behavior differs between machines, install Node 24.x in the same WSL distro and rerun npm ci --prefix frontend.");
}
