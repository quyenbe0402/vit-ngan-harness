# NON-PRODUCTION diagnostic probe (M0-008J).
# Determines how Chaquopy exposes CPython C-API symbols to separately
# dlopen()ed extension modules. Diagnostic experiment only.
import ctypes
import ctypes.util
import os
import sys

SYMS = ["PyLong_Type", "PyObject_Type", "PyUnicode_Type",
        "PyErr_SetString", "Py_IsInitialized", "PyModule_Create2"]

RTLD_LAZY, RTLD_NOW = 0x00001, 0x00002
RTLD_GLOBAL, RTLD_LOCAL = 0x00100, 0x00000
RTLD_NOLOAD = 0x00004

out = []


def say(s):
    out.append(s)


def probe(handle, name):
    try:
        return ctypes.cast(getattr(ctypes, "c_void_p"), ctypes.c_void_p) is not None
    except Exception:
        return False


def dlsym(handle, name):
    lib = ctypes.CDLL(None, mode=ctypes.RTLD_GLOBAL) if handle is None else None
    return None


def run():
    say("=== M0-008J Chaquopy native loader diagnostic ===")
    say("PYTHON_RUNTIME_OK version=" + sys.version.split()[0])
    say("platform=" + sys.platform)

    libc = ctypes.CDLL("libc.so", use_errno=True)
    _dlopen = libc.dlopen
    _dlopen.restype = ctypes.c_void_p
    _dlopen.argtypes = [ctypes.c_char_p, ctypes.c_int]
    _dlsym = libc.dlsym
    _dlsym.restype = ctypes.c_void_p
    _dlsym.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    _dlerror = libc.dlerror
    _dlerror.restype = ctypes.c_char_p

    def sym(handle, name):
        p = _dlsym(handle, name.encode())
        return ("RESOLVED" if p else "NOT_FOUND"), p

    say("--- 1. dlsym(RTLD_DEFAULT=0) in the current process ---")
    for s in SYMS:
        st, _ = sym(None, s)
        say("  SYMBOL_%s %s" % (st, s))

    say("--- 2. dlopen libpython3.14.so RTLD_NOLOAD|RTLD_LOCAL ---")
    h_local = _dlopen(b"libpython3.14.so", RTLD_NOLOAD | RTLD_LOCAL)
    say("  handle=%s dlerror=%s" % ("NON_NULL" if h_local else "NULL", _dlerror()))
    if h_local:
        for s in SYMS:
            st, _ = sym(h_local, s)
            say("  SYMBOL_%s %s" % (st, s))

    say("--- 3. dlopen libpython3.14.so RTLD_NOLOAD|RTLD_GLOBAL ---")
    h_global = _dlopen(b"libpython3.14.so", RTLD_NOLOAD | RTLD_GLOBAL)
    say("  handle=%s dlerror=%s" % ("NON_NULL" if h_global else "NULL", _dlerror()))
    if h_global:
        for s in SYMS:
            st, _ = sym(h_global, s)
            say("  SYMBOL_%s %s" % (st, s))

    say("--- 4. load the Python runtime globally, then re-probe default scope ---")
    h_force = _dlopen(b"libpython3.14.so", RTLD_NOW | RTLD_GLOBAL)
    say("  forced handle=%s dlerror=%s" % ("NON_NULL" if h_force else "NULL", _dlerror()))
    for s in SYMS:
        st, _ = sym(None, s)
        say("  AFTER_GLOBAL SYMBOL_%s %s" % (st, s))

    say("--- 5. does a Chaquopy-shipped extension dlopen? ---")
    root = os.environ.get("HOME") or os.getcwd()
    cands = []
    for dirpath, dirnames, filenames in os.walk(root):
        if "bootstrap-native" in dirpath and dirpath.count(os.sep) < 12:
            for f in filenames:
                if f.endswith(".so") and f.startswith(("_bz2", "zlib", "math")):
                    cands.append(os.path.join(dirpath, f))
        if len(cands) >= 3:
            break
    for path in sorted(cands):
        h = _dlopen(path.encode(), RTLD_NOW | RTLD_LOCAL)
        say("  %s -> %s" % (os.path.basename(path),
                            "LOADED" if h else "FAIL " + str(_dlerror())))
        if h:
            base_name = os.path.basename(path).split(".")[0]
            st, _ = sym(h, "PyInit_" + base_name)
            say("     PyInit_%s=%s" % (base_name, st))

    say("--- 6. does OUR rebuilt extension dlopen? ---")
    found = []
    for dirpath, dirnames, filenames in os.walk(root):
        for f in filenames:
            if f == "_pydantic_core.so":
                found.append(os.path.join(dirpath, f))
        if len(found) >= 2:
            break
    for path in sorted(found):
        say("  file: %s" % path)
        for label, flags in (("LOCAL", RTLD_NOW | RTLD_LOCAL),
                             ("GLOBAL", RTLD_NOW | RTLD_GLOBAL)):
            h = _dlopen(path.encode(), flags)
            say("    mode=%s -> %s" % (label, "LOADED" if h else "FAIL " + str(_dlerror())))
            if h:
                st, _ = sym(h, "PyInit__pydantic_core")
                say("      PyInit__pydantic_core=%s" % st)

    say("--- 7. import path result ---")
    try:
        import pydantic_core
        say("IMPORT=OK version=" + str(getattr(pydantic_core, "__version__", "?")))
        try:
            gen = pydantic_core.schema_generator.generate_schema(int)
            say("SCHEMA_TYPE=" + str(gen["type"]))
            v = pydantic_core.SchemaValidator(int, {})
            say("VALIDATE=" + str(v.validate_python(42)))
            say("NATIVE_OPERATION_OK")
        except BaseException as e:
            say("NATIVE_OPERATION_FAIL %s: %s" % (type(e).__name__, str(e)[:200]))
    except BaseException as e:
        say("IMPORT=FAIL %s: %s" % (type(e).__name__, str(e)[:200]))


    found = []
    for dirpath, dirnames, filenames in os.walk(root):
        for f in filenames:
            if f == "_pydantic_core.so":
                found.append(os.path.join(dirpath, f))
        if len(found) >= 2:
            break
    say("--- 8. DECISIVE: same .so, but placed in Chaquopy's own bootstrap dir ---")
    import shutil
    for src in sorted(found):
        try:
            boot_dir = None
            for dirpath, dirnames, filenames in os.walk(root):
                if "bootstrap-native" in dirpath and dirpath.count(os.sep) < 12:
                    boot_dir = dirpath
                    break
            if not boot_dir:
                say("  bootstrap-native dir not found")
                break
            target = os.path.join(boot_dir, "_pydantic_core.cpython-314-aarch64-linux-android.so")
            shutil.copyfile(src, target)
            say("  copied to: %s" % target)
            h = _dlopen(target.encode(), RTLD_NOW | RTLD_LOCAL)
            say("  dlopen in bootstrap dir -> %s" % ("LOADED" if h else "FAIL " + str(_dlerror())))
            if h:
                st, _ = sym(h, "PyInit__pydantic_core")
                say("  PyInit__pydantic_core=%s" % st)
            os.remove(target)
        except BaseException as e:
            say("  copy experiment EX %s: %s" % (type(e).__name__, e))

    say("=== DIAG END ===")
    return "\n".join(out)
