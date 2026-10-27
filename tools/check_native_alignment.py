"""Check ELF LOAD alignment in an APK; ZIP alignment must also be checked with zipalign -P 16."""
import argparse, json, struct, zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("apk",type=Path)
p.add_argument("--output",type=Path,required=True)
a=p.parse_args()
rows=[]
with zipfile.ZipFile(a.apk) as z:
    for name in z.namelist():
        if not(name.startswith("lib/") and name.endswith(".so")): continue
        b=z.read(name)
        if b[:4]!=b"\x7fELF" or b[4]!=2 or b[5]!=1: raise ValueError("Unsupported ELF "+name)
        offset=struct.unpack_from("<Q",b,32)[0]
        entrysize,count=struct.unpack_from("<HH",b,54)
        loads=[]
        for i in range(count):
            kind,flags,fo,va,pa,fs,ms,align=struct.unpack_from("<IIQQQQQQ",b,offset+i*entrysize)
            if kind==1: loads.append({"align":align,"offset":fo,"vaddr":va,"compatible":align>=16384 and fo%16384==va%16384})
        rows.append({"library":name,"compatible":bool(loads) and all(p["compatible"] for p in loads),"loads":loads})
report={"apk":str(a.apk),"libraries":rows,"all_elf_16k_compatible":bool(rows) and all(r["compatible"] for r in rows)}
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(report,indent=2)+"\n")
print(json.dumps({"libraries":len(rows),"elf_16k":report["all_elf_16k_compatible"],"failures":[r["library"] for r in rows if not r["compatible"]]},indent=2))
raise SystemExit(0 if report["all_elf_16k_compatible"] else 1)
