"""Check the optimized JVM names looked up literally by LiteRT-LM 0.18.0 JNI.

This catches the observed nativeComputeEmbedding ClassNotFoundException before
installation. It complements actual model inference, not replaces runtime tests.
"""
import argparse
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser()
parser.add_argument("mapping", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()

# Inspected in liblitertlm_jni.so from the resolved 0.18.0 AAR. The base type
# additionally occurs in the nativeComputeEmbedding array method signature.
required = ["InputData", "InputData$Text", "InputData$Image", "InputData$Audio",
            "EmbeddingResponse", "BenchmarkInfo", "LiteRtLmJniException"]
prefix = "com.google.ai.edge.litertlm."
classes = {}
with args.mapping.open(encoding="utf-8") as source:
    for line in source:
        match = re.match(r"^(\S+) -> (\S+):$", line.rstrip())
        if match:
            classes[match[1]] = match[2]
rows = [{"class": prefix + name, "optimized_name": classes.get(prefix + name),
         "retained": classes.get(prefix + name) == prefix + name} for name in required]
report = {"all_jni_names_retained": all(r["retained"] for r in rows), "classes": rows}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
raise SystemExit(0 if report["all_jni_names_retained"] else 1)
