const sharp = require("sharp");
const path = require("path");
const fs = require("fs");

const root = path.join(__dirname, "..");
const source = path.join(root, "assets", "mascot-reference.svg");
const outDir = path.join(root, "build");
const output = path.join(outDir, "icon.png");

async function main() {
  fs.mkdirSync(outDir, { recursive: true });
  await sharp(source, { density: 300 })
    .resize(512, 512)
    .png()
    .toFile(output);
  console.log(`icon written: ${output}`);
}

main().catch((error) => {
  console.error("icon generation failed:", error);
  process.exit(1);
});
