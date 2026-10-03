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
        # pydantic-core 2.46.4 API: build the schema through core_schema, then
        # construct a real SchemaValidator. There is no top-level
        # generate_schema / schema_generator in this version.
        from pydantic_core import core_schema

        schema = core_schema.int_schema()
        v = pydantic_core.SchemaValidator(schema)
        res.append("SCHEMA_TYPE=" + str(schema["type"]))
        res.append("VALIDATOR=" + type(v).__name__)
        res.append("VALIDATE=" + str(v.validate_python(42)))
        try:
            v.validate_python("not-an-int")
            res.append("INVALID_REJECT=NO")
        except pydantic_core.ValidationError as e:
            res.append("INVALID_REJECT=yes")
            res.append("INVALID_ERROR_TYPE=" + str(e.errors()[0].get("type")))
        ser = pydantic_core.SchemaSerializer(schema)
        res.append("SERIALIZE=" + str(ser.to_json(42)))
        res.append("NATIVE_OP=OK")
    except BaseException as e:
        res.append("NATIVE_OP=FAIL %s: %s" % (type(e).__name__, str(e)[:400]))
    return "\n".join(res)
