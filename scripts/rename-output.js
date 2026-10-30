// Rename the portable artifact to a clean Chinese filename.
const fs = require("fs");
const path = require("path");

const dist = path.join(__dirname, "..", "dist");
const from = path.join(dist, "独数一帜 1.0.0.exe");
const to = path.join(dist, "独数一帜.exe");

const MIN_VALID_BYTES = 50 * 1024 * 1024; // a real payload is ~70MB; truncated builds are ~22MB

if (fs.existsSync(from)) {
  fs.renameSync(from, to);
  console.log(`renamed to ${to}`);
} else if (fs.existsSync(to)) {
  console.log(`already named ${to}`);
} else {
  console.warn("portable artifact not found, skipping rename");
  process.exit(1);
}

const size = fs.statSync(to).size;
if (size < MIN_VALID_BYTES) {
  console.error(
    `WARNING: ${path.basename(to)} is only ${size} bytes — smaller than the expected ~70MB. ` +
      `The 7za archive step likely hit a locked Stand-Out-By-Logic.exe and skipped it. ` +
      `Rebuild (npm run dist) before shipping.`
  );
  process.exit(1);
}
console.log(`OK: ${path.basename(to)} is ${size} bytes`);
