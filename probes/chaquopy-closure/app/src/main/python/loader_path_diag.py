# NON-PRODUCTION diagnostic probe (M0-008K).
# libc dlopen/dlsym side of the System.load vs dlopen comparison.
import ctypes
import os
import sys

RTLD_NOW, RTLD_LAZY = 0x00001, 0x00002
RTLD_GLOBAL, RTLD_LOCAL = 0x00100, 0x00000
RTLD_NOLOAD = 0x00004

_libc = ctypes.CDLL("libc.so", use_errno=True)
_libc.dlopen.restype = ctypes.c_void_p
_libc.dlopen.argtypes = [ctypes.c_char_p, ctypes.c_int]
_libc.dlsym.restype = ctypes.c_void_p
_libc.dlsym.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
_libc.dlerror.restype = ctypes.c_char_p


def err():
    e = _libc.dlerror()
    return e.decode("utf8", "replace") if e else "None"


def dlopen_one(path):
    h = _libc.dlopen(path.encode(), RTLD_NOW | RTLD_LOCAL)
    if not h:
        return "dlopen -> FAIL: " + err()
    init = _libc.dlsym(h, ("PyInit_" + os.path.basename(path).split(".")[0]).encode())
    return "dlopen -> LOADED  PyInit=%s" % ("RESOLVED" if init else "NOT_FOUND")


def probe_symbols():
    out = ["  dlsym probe (RTLD_DEFAULT):"]
    for s in ("PyLong_Type", "PyObject_Type", "PyErr_SetString"):
        p = _libc.dlsym(None, s.encode())
        out.append("    SYMBOL_%s %s" % ("RESOLVED" if p else "NOT_FOUND", s))
    return "\n".join(out)


def run(path):
    out = ["  path  = " + path]
    out.append("  exists= %s size=%d" % (os.path.exists(path),
                                       os.path.getsize(path) if os.path.exists(path) else -1))
    out.append("  python= %s" % sys.version.split()[0])
    for label, flags in (("NOW|LOCAL", RTLD_NOW | RTLD_LOCAL),
                         ("NOW|GLOBAL", RTLD_NOW | RTLD_GLOBAL),
                         ("LAZY|LOCAL", RTLD_LAZY | RTLD_LOCAL)):
        h = _libc.dlopen(path.encode(), flags)
        if h:
            out.append("  dlopen %-11s -> LOADED   (dlerror=%s)" % (label, err()))
        else:
            out.append("  dlopen %-11s -> FAIL     dlerror=%s" % (label, err()))
    return "\n".join(out)


def final_import():
    """Exercise the rebuilt native extension.

    The .so is loaded straight from disk with ExtensionFileLoader so that the
    probe wheel's own __init__.py shim is bypassed entirely. The wheel binary
    is NOT modified.
    """
    out = []
    path = None
    root = os.environ.get("HOME") or os.getcwd()
    stack = [root]
    while stack:
        d = stack.pop()
        try:
            kids = os.listdir(d)
        except OSError:
            continue
        for f in kids:
            fp = os.path.join(d, f)
            if os.path.isdir(fp):
                if len(fp.split(os.sep)) < 14:
                    stack.append(fp)
            elif f == "_pydantic_core.so":
                path = fp
        if path:
            break
    if not path:
        return "NATIVE_IMPORT_RESULT=FAIL could not locate _pydantic_core.so"
    out.append("  loading: " + path)
    try:
        import importlib.util
        from importlib.machinery import ExtensionFileLoader
        loader = ExtensionFileLoader("_pydantic_core", path)
        spec = importlib.util.spec_from_file_location("_pydantic_core", path, loader=loader)
        core = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(core)
        out.append("NATIVE_IMPORT_RESULT=OK module=_pydantic_core")
        out.append("  has_schema_validator=" + str(hasattr(core, "SchemaValidator")))
        out.append("  version=" + str(getattr(core, "__version__", "?")))
        schema = core.build_schema([core.CoreSchema.int_schema()])
        v = core.SchemaValidator(schema)
        out.append("  VALIDATE_42=" + str(v.validate_python(42)))
        try:
            v.validate_python("not-an-int")
            out.append("  REJECT_BAD=NO")
        except Exception:
            out.append("  REJECT_BAD=YES")
        out.append("NATIVE_OPERATION_RESULT=OK")
    except BaseException as e:
        out.append("NATIVE_IMPORT_RESULT=FAIL %s: %s" % (type(e).__name__, str(e)[:300]))
    return "\n".join(out)
