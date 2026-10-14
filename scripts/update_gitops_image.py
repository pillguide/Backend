"""Update one Deployment image, preserving its independently managed replica count."""
import os
from pathlib import Path
import re

environment = os.environ["DEPLOY_ENV"]
if environment not in {"dev", "production"}:
    raise SystemExit("Unknown deployment environment")
digest = os.environ["IMAGE_DIGEST"]
if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
    raise SystemExit("Build did not return an immutable image digest")
image = f'{os.environ["IMAGE_NAME"]}@{digest}'
path = Path("manifest") / environment / "deployment.yaml"
original = path.read_text()
if "name: pillguide-backend" not in original or f"namespace: {environment}" not in original:
    raise SystemExit("Unexpected Deployment target")
updated, count = re.subn(r"^(\s*image:)[^\r\n]*", lambda m: f"{m[1]} {image}", original, flags=re.MULTILINE)
if count != 1:
    raise SystemExit(f"Expected one image field; found {count}")
path.write_text(updated)
print(f"Updated {path} to {image}; replica count unchanged")
