# NON-PRODUCTION feasibility probe (M0-008M).
# httptools==0.8.0 rebuilt for Android arm64 / CPython 3.14 under Chaquopy 17.
#
# httptools 0.8.0 API (verified from the shipped .pyx/.pyi and the upstream
# tests): HttpRequestParser takes a protocol object, and every callback is a
# method on that object. It does NOT accept zero arguments and it does NOT
# have attribute assignment for callbacks.
#
# FAIL-LOUD: every failure raises. No exception is converted into a PASS.

TAG = "M0_008M_HTTPTOOLS"


def _log(msg):
    print("%s %s" % (TAG, msg))


class RequestProtocol(object):
    """Protocol object handed to HttpRequestParser.

    Each callback is invoked from inside the rebuilt C extension, so a value
    recorded here proves that native code ran and called back into Python.
    """

    def __init__(self):
        self.events = []
        self.url = b""
        self.method = b""
        self.body = b""
        self.chunks = 0

    def on_message_begin(self):
        self.events.append("begin")

    def on_url(self, u):
        self.url = u
        self.events.append("url:" + u.decode())

    def on_header(self, name, value):
        self.events.append("hdr:%s=%s" % (name, value))

    def on_headers_complete(self):
        self.events.append("headers_complete")

    def on_body(self, b):
        self.body += b

    def on_message_complete(self):
        self.events.append("complete")

    def on_chunk_complete(self):
        self.chunks += 1
        self.events.append("chunk")


def run():
    result = {}

    import sys

    result["python_version"] = sys.version.split()[0]
    if not sys.version.startswith("3.14"):
        raise AssertionError("expected Python 3.14, got %r" % (sys.version,))
    _log("PYTHON_RUNTIME_OK " + result["python_version"])

    # --- import: failure propagates, nothing is caught ------------------
    import httptools
    from httptools.parser import url_parser as ht_url

    result["httptools_version"] = httptools.__version__
    _log("HTTPTOOLS_IMPORT_OK " + result["httptools_version"])

    result["url_module"] = type(ht_url.URL).__module__

    # --- real native operation 1: HTTP request parsing -----------------
    # httptools.parser.parser is the llhttp binding. A completed parse with
    # callbacks fired means llhttp ran inside the Android .so.
    proto = RequestProtocol()
    p = httptools.HttpRequestParser(proto)
    p.feed_data(
        b"POST /v1/messages?x=1 HTTP/1.1\r\n"
        b"Host: example.invalid\r\n"
        b"Content-Length: 5\r\n"
        b"\r\n"
        b"hello"
    )
    if "complete" not in proto.events:
        raise AssertionError("parse did not complete: %r" % (proto.events,))
    if proto.url != b"/v1/messages?x=1":
        raise AssertionError("on_url wrong: %r" % (proto.url,))
    # 0.8.0 has no on_method callback; the method is exposed by get_method().
    if p.get_method() != b"POST":
        raise AssertionError("get_method wrong: %r" % (p.get_method(),))
    proto.method = p.get_method()
    if proto.body != b"hello":
        raise AssertionError("on_body wrong: %r" % (proto.body,))
    result["parse_events"] = proto.events
    result["parse_method"] = proto.method.decode()
    result["parse_url"] = proto.url.decode()
    result["parse_body"] = proto.body.decode()
    _log("HTTPTOOLS_PARSE_OK " + proto.method.decode())

    # --- native operation 2: chunked transfer decoding -----------------
    proto2 = RequestProtocol()
    p2 = httptools.HttpRequestParser(proto2)
    p2.feed_data(
        b"POST /c HTTP/1.1\r\nHost: h\r\n"
        b"Transfer-Encoding: chunked\r\n\r\n"
        b"5\r\nhello\r\n0\r\n\r\n"
    )
    if proto2.chunks < 1:
        raise AssertionError("chunked decode failed: %r" % (proto2.events,))
    result["chunked_count"] = proto2.chunks
    _log("HTTPTOOLS_CHUNKED_OK chunks=%d" % proto2.chunks)

    # --- native operation 3: response parsing --------------------------
    proto3 = RequestProtocol()
    p3 = httptools.HttpResponseParser(proto3)
    p3.feed_data(
        b"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n"
    )
    if "complete" not in proto3.events:
        raise AssertionError("response parse did not complete: %r" % (proto3.events,))

    # --- native operation 4: URL parsing (separate extension) ----------
    # httptools.parser.url_parser is the http-parser binding.
    u = ht_url.parse_url(b"http://example.invalid:8080/a/b?q=1#frag")
    result["url_schema"] = u.schema.decode()
    result["url_host"] = u.host.decode()
    result["url_path"] = u.path.decode()
    result["url_query"] = u.query.decode()
    if u.schema != b"http" or u.path != b"/a/b" or u.query != b"q=1":
        raise AssertionError("URL parse wrong: %r" % (result,))
    if u.host != b"example.invalid" or u.port != 8080:
        raise AssertionError("URL host/port wrong: %r" % (result,))
    result["url_ok"] = True

    # --- negative control: malformed request must be rejected ----------
    # httptools rejects the malformed token itself (HttpParserError) before any
    # completion callback, so any exception raised from feed_data is a pass;
    # silence would be the failure.
    proto4 = RequestProtocol()
    bad = httptools.HttpRequestParser(proto4)
    rejected = False
    try:
        bad.feed_data(b"GET /x HTTP/1.1\r\nHost: h\r\n\x00\x01\x02")
    except Exception as exc:  # noqa: BLE001 - rejection is the expected outcome
        rejected = True
        result["invalid_error_type"] = type(exc).__name__
        result["invalid_error_msg"] = str(exc)[:80]
    if not rejected:
        raise AssertionError("malformed request was NOT rejected")
    if "complete" in proto4.events:
        raise AssertionError("parser reported completion for a malformed request")
    result["invalid_rejected"] = True

    _log("HTTPTOOLS_NATIVE_OK parse+chunked+response+url+invalid_rejected")
    result["NATIVE_OPERATION_OK"] = True
    return result
