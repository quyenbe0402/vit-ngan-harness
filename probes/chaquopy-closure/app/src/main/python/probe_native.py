# NON-PRODUCTION feasibility probe (M0-008I).
# Imports a self-rebuilt Android arm64 cp314 native wheel and runs a real
# native operation inside it, not merely an import.
import sys


def run():
    res = ["python=" + sys.version.split()[0]]
    try:
        import pydantic_core
    except BaseException as e:
        res.append("IMPORT=FAIL %s: %s" % (type(e).__name__, str(e)[:400]))
        return "\n".join(res)

    res.append("IMPORT=OK")
    res.append("version=" + str(getattr(pydantic_core, "__version__", "?")))
    try:
        gen = pydantic_core.schema_generator.generate_schema(int)
        res.append("SCHEMA_TYPE=" + str(gen["type"]))
        v = pydantic_core.SchemaValidator(int, {})
        res.append("VALIDATE=" + str(v.validate_python(42)))
        res.append("NATIVE_OP=OK")
    except BaseException as e:
        res.append("NATIVE_OP=FAIL %s: %s" % (type(e).__name__, str(e)[:400]))
    return "\n".join(res)
