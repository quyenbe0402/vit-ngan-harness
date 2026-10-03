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
    """Full pydantic-core validation cycle through the rebuilt native .so.

    The package is imported normally. Nothing is mocked: schema creation,
    SchemaValidator construction and validate_python all enter the Rust
    extension compiled for Android arm64 CPython 3.14.
    """
    out = []
    try:
        import pydantic_core
    except BaseException as e:
        return "NATIVE_IMPORT_RESULT=FAIL %s: %s" % (type(e).__name__, str(e)[:300])

    out.append("NATIVE_IMPORT_RESULT=OK")
    out.append("  package_version = " + str(pydantic_core.__version__))
    out.append("  SchemaValidator = " + str(pydantic_core.SchemaValidator))

    from pydantic_core import core_schema

    try:
        schema = core_schema.int_schema()
        validator = pydantic_core.SchemaValidator(schema)
        out.append("  SCHEMA_BUILT = " + str(schema["type"]))
        out.append("  VALIDATOR_BUILT = " + type(validator).__name__)

        good = validator.validate_python(42)
        out.append("  VALIDATE_VALID_42 = %r (%s)" % (good, type(good).__name__))
        if good != 42 or type(good) is not int:
            out.append("NATIVE_OPERATION_RESULT=FAIL validator returned unexpected value")
            return "\n".join(out)

        rejected = False
        try:
            validator.validate_python("not-an-int")
        except pydantic_core.ValidationError as e:
            rejected = True
            out.append("  INVALID_REJECTED = yes")
            out.append("  error_type = " + str(e.errors()[0].get("type")))
        except BaseException as e:
            out.append("  INVALID_REJECTED = wrong-exception %s" % type(e).__name__)
        if not rejected:
            out.append("NATIVE_OPERATION_RESULT=FAIL invalid input was NOT rejected")
            return "\n".join(out)

        # Serialisation is also native; proves more of the Rust surface.
        ser = pydantic_core.SchemaSerializer(core_schema.int_schema())
        js = ser.to_json(42)
        out.append("  SERIALIZE = " + str(js))
        out.append("  ROUNDTRIP = %r" % (pydantic_core.from_json(js),))
        out.append("NATIVE_OPERATION_RESULT=OK")
    except BaseException as e:
        out.append("NATIVE_OPERATION_RESULT=FAIL %s: %s" % (type(e).__name__, str(e)[:300]))
    return "\n".join(out)
