# NON-PRODUCTION FEASIBILITY PROBE (M0-008H)
# Records, per package, whether it is installed and whether a minimal NATIVE
# operation (not merely an import) succeeds under Chaquopy on Android arm64.

import json, sys, platform, traceback

NATIVE_CHECKS = {
    "pydantic":        ("pydantic", "from pydantic import BaseModel\nclass M(BaseModel):\n  a:int\nassert M(a=1).a==1", "validate model"),
    "pydantic_core":   ("pydantic_core", "import pydantic_core; assert pydantic_core.__version__", "version"),
    "cryptography":    ("cryptography", "from cryptography.hazmat.primitives.ciphers import Cipher,algorithms,modes\nimport os\nk=os.urandom(32); iv=os.urandom(16)\nc=Cipher(algorithms.AES(k),modes.CBC(iv)).encryptor()\nct=c.update(b'0'*16)+c.finalize()\nassert len(ct)==16", "AES-256-CBC encrypt"),
    "orjson":          ("orjson", "import orjson; assert orjson.loads(orjson.dumps({'a':1}))=={'a':1}", "encode/decode"),
    "jiter":           ("jiter", "import jiter; assert jiter.from_json('{\"a\":1}')=={'a':1}", "json parse"),
    "charset_normalizer":("charset_normalizer","from charset_normalizer import from_bytes\nfrom_bytes('hello'.encode())","detect"),
    "httptools":       ("httptools","from httptools import HttpRequestParser\np=HttpRequestParser(); p.feed_data(b'GET / HTTP/1.1\\r\\nHost: x\\r\\n\\r\\n'); p.on_message_begin()","parse request"),
    "watchfiles":      ("watchfiles","import watchfiles; assert hasattr(watchfiles,'watch')","load lib"),
    "rpds_py":         ("rpds_py","import rpds; assert rpds.HashTrie","hash trie"),
    "websockets":      ("websockets","from websockets.asyncio.client import connect\nimport websockets.protocol","protocol import"),
    "PIL":             ("PIL","from PIL import Image\nim=Image.new('RGB',(4,4),(1,2,3))\nassert im.size==(4,4)","create image"),
    "pillow_heif":     ("pillow_heif","import pillow_heif","load lib"),
    "resvg_py":        ("resvg_py","import resvg_py","load lib"),
    "nacl":            ("nacl","from nacl.public import PrivateKey,Box\nk=PrivateKey.generate(); b=Box(k,k)\nassert b.decrypt(b.encrypt(b'hello'))==b'hello'","box encrypt/decrypt"),
    "numpy":           ("numpy","import numpy as np\nassert int(np.array([1,2,3]).sum())==6","sum"),
}

def probe(name):
    mod, code, what = NATIVE_CHECKS[name]
    try:
        if __import__("importlib.util", fromlist=["util"]).find_spec(mod) is None:
            return {"pkg": name, "status": "NOT_INSTALLED", "native": what}
    except Exception:
        pass
    try:
        exec(compile(code, "<probe>", "exec"), {})
        return {"pkg": name, "status": "IMPORT_OK+NATIVE_OK", "native": what}
    except ImportError as e:
        return {"pkg": name, "status": "IMPORT_FAIL", "err": str(e)[:160], "native": what}
    except Exception as e:
        return {"pkg": name, "status": "IMPORT_OK_NATIVE_FAIL", "err": str(e)[:160], "native": what}

def sqlite_wal():
    import os, sqlite3
    d = os.path.join(os.path.dirname(__file__), "probe_state.db")
    con = sqlite3.connect(d)
    mode = con.execute("PRAGMA journal_mode=WAL").fetchone()[0]
    con.execute("CREATE TABLE IF NOT EXISTS t(k TEXT PRIMARY KEY, v TEXT)")
    con.execute("INSERT OR REPLACE INTO t VALUES ('k','v')")
    con.commit(); con.close()
    return {"journal_mode": mode, "wal_file": os.path.exists(d + "-wal"), "shm_file": os.path.exists(d + "-shm")}

def subprocess_check():
    import subprocess, shutil
    return {"node": shutil.which("node"), "sh_present": shutil.which("sh"),
            "busybox": shutil.which("busybox"), "echo_via_sh": subprocess.run(["echo","hi"],capture_output=True,text=True).stdout.strip()}

def main():
    out = {"python": sys.version, "abi": platform.machine(), "packages": []}
    for n in NATIVE_CHECKS:
        out["packages"].append(probe(n))
    try: out["sqlite"] = sqlite_wal()
    except Exception as e: out["sqlite"] = {"error": str(e)[:200]}
    try: out["subprocess"] = subprocess_check()
    except Exception as e: out["subprocess"] = {"error": str(e)[:200]}
    print("PROBE_JSON_BEGIN")
    print(json.dumps(out, indent=1))
    print("PROBE_JSON_END")
    return out

main()