const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname);
const port = Number(process.argv[2]) || 5188;
const host = "127.0.0.1";
const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml; charset=utf-8",
};

const server = http.createServer((request, response) => {
  let route;
  try {
    route = decodeURIComponent(request.url.split("?")[0]);
  } catch {
    response.writeHead(400);
    response.end("Bad request");
    return;
  }
  if (route === "/") {
    route = "/index.html";
  }

  const filePath = path.resolve(root, "." + route);
  const relative = path.relative(root, filePath);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    response.writeHead(403);
    response.end("Forbidden");
    return;
  }

  fs.readFile(filePath, (error, data) => {
    if (error) {
      response.writeHead(404);
      response.end("Not found");
      return;
    }

    response.writeHead(200, {
      "Content-Type": types[path.extname(filePath)] || "application/octet-stream",
    });
    response.end(data);
  });
});

server.listen(port, host, () => {
  console.log(`独数一帜 running at http://${host}:${port}/`);
});
