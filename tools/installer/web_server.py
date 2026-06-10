from http.server import BaseHTTPRequestHandler, HTTPServer
import time
from pathlib import Path
from urllib.parse import unquote, urlparse

RATE_LIMIT = 1000 * 1024 * 1024
CHUNK_SIZE = 80 * 1024

# Directory whose files are served (path is URL suffix, e.g. /LinkMind.jar); relative to cwd
SERVE_DIR = Path("lagi-web/target")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        rel = unquote(urlparse(self.path).path).lstrip("/")
        if not rel or rel.endswith("/"):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found")
            return

        root = SERVE_DIR.resolve()
        try:
            file_path = (root / rel).resolve()
            file_path.relative_to(root)
        except ValueError:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found")
            return

        if not file_path.is_file():
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found")
            return

        file_size = file_path.stat().st_size

        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(file_size))
        self.end_headers()

        with open(file_path, "rb") as f:
            while True:
                chunk = f.read(CHUNK_SIZE)
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()

                time.sleep(CHUNK_SIZE / RATE_LIMIT)


server = HTTPServer(("0.0.0.0", 8000), Handler)
print("Serving on http://0.0.0.0:8000")
print(f"Root: {SERVE_DIR.resolve()}")
server.serve_forever()
