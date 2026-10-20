#!/usr/bin/env python3
"""
Figma API Client & CLI tool for CodeUnlock.
Extracts designs, layout nodes, design tokens, and rendered images directly from Figma.
"""

import sys
import os
import re
import json
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

ENV_PATH = Path(__file__).resolve().parent.parent / ".env"

def load_token() -> str:
    token = os.environ.get("FIGMA_ACCESS_TOKEN") or os.environ.get("FIGMA_API_KEY")
    if token:
        return token.strip()
    
    if ENV_PATH.exists():
        with open(ENV_PATH, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("FIGMA_ACCESS_TOKEN=") or line.startswith("FIGMA_API_KEY="):
                    return line.split("=", 1)[1].strip().strip('"').strip("'")
    
    raise ValueError("Figma token not found in environment or .env file.")

def extract_file_key(file_or_url: str) -> str:
    """Extracts 22-character Figma file key from URL or raw key."""
    file_or_url = file_or_url.strip()
    # Pattern: figma.com/(design|file)/<key>/...
    m = re.search(r'figma\.com/(?:design|file)/([a-zA-Z0-9_-]+)', file_or_url)
    if m:
        return m.group(1)
    # Check if raw key format
    if re.match(r'^[a-zA-Z0-9_-]+$', file_or_url):
        return file_or_url
    raise ValueError(f"Could not parse Figma file key from: {file_or_url}")

def figma_api_request(endpoint: str, params: dict = None) -> dict:
    token = load_token()
    url = f"https://api.figma.com/v1/{endpoint.lstrip('/')}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    
    req = urllib.request.Request(
        url,
        headers={
            "X-Figma-Token": token,
            "User-Agent": "CodeUnlock-Figma-Tool/1.0"
        }
    )
    
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            err_json = json.loads(body)
            msg = err_json.get("err") or err_json.get("message") or body
        except Exception:
            msg = body
        raise RuntimeError(f"Figma API Error (HTTP {e.code}): {msg}")

def cmd_whoami():
    user = figma_api_request("me")
    print(f"Connected to Figma!")
    print(f"User:   {user.get('handle')}")
    print(f"Email:  {user.get('email')}")
    print(f"ID:     {user.get('id')}")

def cmd_file_summary(file_arg: str):
    file_key = extract_file_key(file_arg)
    print(f"Fetching Figma file: {file_key}...")
    data = figma_api_request(f"files/{file_key}", {"depth": "2"})
    name = data.get("name")
    last_mod = data.get("lastModified")
    doc = data.get("document", {})
    pages = doc.get("children", [])
    
    print(f"\n--- Figma File: {name} ---")
    print(f"Last Modified: {last_mod}")
    print(f"Pages: {len(pages)}")
    for p in pages:
        p_name = p.get("name")
        frames = p.get("children", [])
        print(f"  * Page: '{p_name}' ({len(frames)} top-level frames/components)")
        for f in frames[:10]:
            print(f"      - [{f.get('type')}] '{f.get('name')}' (ID: {f.get('id')})")
        if len(frames) > 10:
            print(f"      ... and {len(frames) - 10} more items.")

def cmd_export_images(file_arg: str, node_ids: list, out_dir: str = "figma_exports", img_format: str = "png"):
    file_key = extract_file_key(file_arg)
    ids_str = ",".join(node_ids)
    print(f"Rendering images for nodes [{ids_str}] from file {file_key}...")
    res = figma_api_request(f"images/{file_key}", {
        "ids": ids_str,
        "format": img_format,
        "scale": "2"
    })
    
    images = res.get("images", {})
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    
    for node_id, img_url in images.items():
        if not img_url:
            print(f"Warning: Node {node_id} could not be rendered.")
            continue
        safe_name = node_id.replace(":", "_") + f".{img_format}"
        file_dest = out_path / safe_name
        req = urllib.request.Request(img_url, headers={"User-Agent": "CodeUnlock-Figma-Tool/1.0"})
        with urllib.request.urlopen(req) as r, open(file_dest, "wb") as f:
            f.write(r.read())
        print(f"Exported: {file_dest}")

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help", "help"):
        print("Usage:")
        print("  python scripts/figma_client.py whoami")
        print("  python scripts/figma_client.py file <file_key_or_url>")
        print("  python scripts/figma_client.py export <file_key_or_url> <node_id> [output_dir] [format]")
        sys.exit(0)
    
    cmd = sys.argv[1]
    if cmd == "whoami":
        cmd_whoami()
    elif cmd == "file":
        if len(sys.argv) < 3:
            print("Error: Missing file key or URL.")
            sys.exit(1)
        cmd_file_summary(sys.argv[2])
    elif cmd == "export":
        if len(sys.argv) < 4:
            print("Error: Missing file key and node ID.")
            sys.exit(1)
        file_arg = sys.argv[2]
        node_id = sys.argv[3]
        out_dir = sys.argv[4] if len(sys.argv) > 4 else "figma_exports"
        img_format = sys.argv[5] if len(sys.argv) > 5 else "png"
        cmd_export_images(file_arg, [node_id], out_dir, img_format)
    else:
        print(f"Unknown command: {cmd}")
