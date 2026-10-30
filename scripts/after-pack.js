// After packaging hook: apply icon + version metadata to the app exe.
// electron-builder's own rcedit step is disabled (signAndEditExecutable: false)
// because the bundled rcedit chokes on empty-string args and non-ASCII values.
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const EXE_NAME = "Stand-Out-By-Logic.exe";

function findRcedit() {
  const base = path.join(process.env.LOCALAPPDATA || "", "electron-builder", "Cache", "winCodeSign");
  if (!fs.existsSync(base)) {
    return null;
  }
  let best = null;
  for (const dir of fs.readdirSync(base)) {
    const candidate = path.join(base, dir, "rcedit-x64.exe");
    if (fs.existsSync(candidate)) {
      best = candidate;
    }
  }
  return best;
}

function pngToIco(pngPath, icoPath) {
  const png = fs.readFileSync(pngPath);
  const header = Buffer.alloc(22);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(1, 4); // count
  header.writeUInt8(0, 6); // width (0 = 256)
  header.writeUInt8(0, 7); // height (0 = 256)
  header.writeUInt8(0, 8); // palette
  header.writeUInt8(0, 9); // reserved
  header.writeUInt16LE(1, 10); // planes
  header.writeUInt16LE(32, 12); // bit count
  header.writeUInt32LE(png.length, 14); // bytes in image
  header.writeUInt32LE(22, 18); // image offset
  fs.writeFileSync(icoPath, Buffer.concat([header, png]));
}

async function patchExe(context) {
  const appOutDir = context.appOutDir;
  const exePath = path.join(appOutDir, EXE_NAME);
  const rcedit = findRcedit();
  if (!rcedit) {
    console.warn("after-pack: rcedit not found, skipping icon patch");
    return;
  }

  const projectRoot = path.join(__dirname, "..");
  const pngPath = path.join(projectRoot, "build", "icon.png");
  const icoPath = path.join(projectRoot, "build", "icon.ico");
  if (fs.existsSync(pngPath)) {
    pngToIco(pngPath, icoPath);
  }
  if (!fs.existsSync(icoPath)) {
    console.warn("after-pack: icon.ico not found, skipping icon patch");
    return;
  }

  // Run each rcedit operation as a separate invocation. The bundled rcedit
  // fails with "Unable to commit changes" when several options are combined,
  // but each option succeeds on its own.
  const operations = [
    ["--set-icon", icoPath],
    ["--set-file-version", "1.0.0.0"],
    ["--set-product-version", "1.0.0.0"],
    ["--set-version-string", "FileDescription", "Stand Out by Logic - Sudoku Game"],
    ["--set-version-string", "ProductName", "Stand Out by Logic"],
    ["--set-version-string", "LegalCopyright", "Copyright 2026 Stand Out by Logic"],
    ["--set-version-string", "InternalName", EXE_NAME],
    ["--set-version-string", "OriginalFilename", EXE_NAME],
  ];
  // Run each rcedit operation as a separate invocation with retries: the
  // freshly written exe is briefly locked (antivirus / file flush), and the
  // bundled rcedit reports that as "Unable to commit changes".
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  let failed = false;
  for (const op of operations) {
    let ok = false;
    for (let attempt = 0; attempt < 15 && !ok; attempt += 1) {
      const result = spawnSync(rcedit, [exePath, ...op], { encoding: "utf8" });
      if (result.status === 0) {
        ok = true;
      } else {
        await sleep(400);
      }
    }
    if (!ok) {
      failed = true;
      console.error("after-pack: rcedit failed for", op[0]);
    }
  }
  if (!failed) {
    console.log("after-pack: icon and version metadata applied");
  }
}

exports.default = async function afterPack(context) {
  // Must await: without it, afterPack resolves while rcedit is still holding
  // the exe open, and the subsequent 7za archive step locks/fails on the exe,
  // silently producing a truncated portable build.
  await patchExe(context);
};
