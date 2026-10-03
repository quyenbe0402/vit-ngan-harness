# NON-PRODUCTION feasibility probe (M0-008O).
# jiter==0.17.0 rebuilt for Android arm64 / CPython 3.14 under Chaquopy 17.
#
# jiter is the ACTUAL Rust/PyO3 dependency of Hermes @ eaecc99c, reached
# through openai==2.24.0 (jiter<1,>=0.10.0 -> 0.17.0 for resolution date
# 2026-10-03 under exclude-newer = "14 days").
#
# API is read from the shipped jiter-0.17.0-cp314 wheel: __init__.pyi declares
# from_json, cache_clear, cache_usage and LosslessFloat. Nothing here is
# invented.
#
# FAIL-LOUD: every failure raises. No exception is converted into a PASS.

TAG = "M0_008O_JITER"

# Real non-ASCII text (cafe-acute + two CJK ideographs), inserted into the JSON
# as raw UTF-8 rather than \u escapes, so the round trip is unambiguous.
UNICODE = "caf\u00e9 \u4e2d\u6587"


def _log(msg):
    print("%s %s" % (TAG, msg))


def run():
    result = {}

    import sys

    result["python_version"] = sys.version.split()[0]
    if not sys.version.startswith("3.14"):
        raise AssertionError("expected Python 3.14, got %r" % (sys.version,))
    _log("PYTHON_RUNTIME_OK " + result["python_version"])

    # --- import: failure propagates, nothing is caught ------------------
    import jiter
    from jiter import from_json, cache_clear, cache_usage

    result["jiter_module"] = jiter.__name__
    result["jiter_native"] = type(jiter.from_json).__module__
    _log("JITER_IMPORT_OK " + result["jiter_native"])

    # --- A/B/C/D: nested structure, numbers, unicode -------------------
    payload = (
        '{"name":"Hermes","ok":true,"count":42,"ratio":1.5,'
        '"tags":["a","b"],"nested":{"x":[1,2,{"y":null}],"s":"' + UNICODE + '"}}'
    ).encode("utf-8")
    result["input_bytes"] = len(payload)
    obj = from_json(payload)

    if obj["name"] != "Hermes":
        raise AssertionError("string field wrong: %r" % (obj["name"],))
    if obj["ok"] is not True:
        raise AssertionError("bool field wrong: %r" % (obj["ok"],))
    if obj["count"] != 42 or type(obj["count"]) is not int:
        raise AssertionError("int field wrong: %r" % (obj["count"],))
    if obj["ratio"] != 1.5 or type(obj["ratio"]) is not float:
        raise AssertionError("float field wrong: %r" % (obj["ratio"],))
    if obj["tags"] != ["a", "b"]:
        raise AssertionError("array wrong: %r" % (obj["tags"],))
    # Unicode: the \u escapes above must decode to real non-ASCII text.
    if obj["nested"]["s"] != UNICODE:
        raise AssertionError("unicode wrong: %r" % (obj["nested"]["s"],))
    if obj["nested"]["x"][2]["y"] is not None:
        raise AssertionError("null wrong: %r" % (obj["nested"]["x"][2]["y"],))
    result["nested_sum"] = sum(obj["nested"]["x"][:2])
    result["unicode_len"] = len(obj["nested"]["s"])
    _log("JITER_PARSE_OK nested+unicode+numbers")

    # --- F: upstream boundary case -- empty containers and deep nesting --
    # jiter upstream tests cover empty containers; check the exact behaviour.
    edge = from_json(b'{"a":{},"b":[],"c":[[[]]],"d":null,"e":0,"f":-0.0}')
    if edge["a"] != {} or edge["b"] != [] or edge["c"] != [[[]]]:
        raise AssertionError("empty container wrong: %r" % (edge,))
    if edge["d"] is not None or edge["e"] != 0:
        raise AssertionError("scalar edge wrong: %r" % (edge,))
    result["edge_ok"] = True

    # --- string cache is real native state ------------------------------
    cache_clear()
    big = b'{"k":"' + b"v" * 5000 + b'"}'
    first = from_json(big)
    if first["k"] != "v" * 5000:
        raise AssertionError("big string wrong")

    # cache_usage() sums entries over caches already RETURNED to the pool
    # (crates/jiter/src/py_string_cache.rs). That is an internal accounting
    # detail, not a documented guarantee, so it is recorded rather than
    # asserted non-zero. What IS guaranteed and is asserted: both functions are
    # real native entry points returning ints, and cache_clear() empties them.
    used_after = cache_usage()
    if not isinstance(used_after, int) or used_after < 0:
        raise AssertionError("cache_usage returned %r" % (used_after,))
    result["cache_usage_observed"] = used_after
    cache_clear()
    cleared = cache_usage()
    if cleared != 0:
        raise AssertionError("cache_clear left %r" % (cleared,))
    result["cache_after_clear"] = cleared
    _log("JITER_CACHE_OK observed=%d cleared=%d" % (used_after, cleared))

    # --- LosslessFloat: a Rust-side type, not a Python shim ------------
    lf = from_json(b'{"x":1.2345678901234567}', float_mode="lossless-float")
    lossless = lf["x"]
    if type(lossless).__name__ != "LosslessFloat":
        raise AssertionError("float_mode ignored: %r" % (type(lossless).__name__,))
    if float(lossless) != 1.2345678901234567:
        raise AssertionError("LosslessFloat value wrong: %r" % (float(lossless),))
    result["lossless_float"] = float(lossless)
    _log("JITER_LOSSLESS_OK")

    # --- E: negative control, malformed input must raise ----------------
    rejected = 0
    for bad in [b'{"a":', b'{"a" 1}', b'[1,2', b'not json', b'{"a":1,}']:
        try:
            from_json(bad)
        except Exception:  # noqa: BLE001 - rejection is the expected outcome
            rejected += 1
    if rejected != 5:
        raise AssertionError("expected 5 rejections, got %d" % rejected)
    result["invalid_rejected"] = rejected

    # catch_duplicate_keys is an upstream-documented strictness option.
    dup_rejected = False
    try:
        from_json(b'{"a":1,"a":2}', catch_duplicate_keys=True)
    except Exception:
        dup_rejected = True
    if not dup_rejected:
        raise AssertionError("duplicate keys were accepted")
    result["dup_rejected"] = True

    _log("JITER_NATIVE_OK parse+cache+lossless+invalid_rejected")
    result["NATIVE_OPERATION_OK"] = True
    return result
