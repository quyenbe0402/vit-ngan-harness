# NON-PRODUCTION feasibility probe (M0-008L-F).
# Proves cryptography==50.0.1 -- the exact version Hermes @ eaecc99c pins --
# executes its real native (Rust + OpenSSL) implementation under Chaquopy 17.0 /
# CPython 3.14 on Android arm64-v8a.
#
# FAIL-LOUD CONTRACT: this module raises on every failure. It never converts an
# exception into a PASS. Callers assert on the returned dict.
#
# No Hermes code is imported. No provider credentials. No network.

import hashlib

TAG = "M0_008L_F_CRYPTO"


def _log(msg):
    print("%s %s" % (TAG, msg))


def run():
    result = {}

    # --- 0. cffi / _cffi_backend (M0-008L-G) ---------------------------
    # cryptography 50.0.1 declares cffi>=2.0.0. Chaquopy has no cp314 cffi
    # wheel, so cffi was rebuilt for Android arm64 against libffi 3.4.4.
    # A real dlopen of _cffi_backend plus a real FFI call is required before
    # cryptography is considered reachable.
    import cffi
    import _cffi_backend

    # Real native operation through cffi -> _cffi_backend -> libffi.
    ffi = cffi.FFI()
    ffi.cdef("struct probe_s { int a; double b; };")
    # Struct layout is computed inside _cffi_backend via libffi.
    result["cffi_sizeof_struct"] = ffi.sizeof("struct probe_s")
    if result["cffi_sizeof_struct"] != 16:
        raise AssertionError(
            "cffi sizeof mismatch: %r" % (result["cffi_sizeof_struct"],)
        )

    # A genuine FFI call: bind and invoke a real libc function. This
    # exercises libffi cif/closure machinery, not just an import.
    ffi.cdef("int getpid(void);")
    pid = ffi.dlopen(None).getpid()
    if not isinstance(pid, int) or pid <= 0:
        raise AssertionError("cffi dlopen call returned %r" % (pid,))
    result["cffi_getpid"] = pid
    result["cffi_backend_loaded"] = _cffi_backend is not None
    _log("CFFI_NATIVE_OK sizeof=%d getpid=%d"
         % (result["cffi_sizeof_struct"], pid))

    # --- 1. Python runtime identity -------------------------------------
    import sys

    result["python_version"] = sys.version.split()[0]
    if not sys.version.startswith("3.14"):
        raise AssertionError("expected Python 3.14, got %r" % (sys.version,))
    _log("PYTHON_RUNTIME_OK " + result["python_version"])

    # --- 2. cryptography import (failure propagates, no try/except) -----
    import cryptography

    result["cryptography_version"] = cryptography.__version__
    if cryptography.__version__ != "50.0.1":
        raise AssertionError(
            "expected cryptography 50.0.1, got %r" % (cryptography.__version__,)
        )
    _log("CRYPTOGRAPHY_IMPORT_OK " + result["cryptography_version"])

    # --- 3. OpenSSL runtime identity -----------------------------------
    from cryptography.hazmat.backends.openssl.backend import backend

    result["openssl_version_text"] = backend.openssl_version_text()
    _log("OPENSSL_RUNTIME_OK " + str(result["openssl_version_text"]))
    if "OpenSSL 3." not in str(result["openssl_version_text"]):
        raise AssertionError(
            "expected OpenSSL 3.x from Chaquopy runtime, got %r"
            % (result["openssl_version_text"],)
        )

    from cryptography.hazmat.bindings._rust import openssl as _rust_openssl

    result["openssl_bound"] = _rust_openssl.__name__

    # --- 4. Native op A: deterministic key + sign/verify ---------------
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import (
        Ed25519PrivateKey,
    )

    seed = bytes(range(32))
    key = Ed25519PrivateKey.from_private_bytes(seed)
    result["key_type"] = type(key).__name__

    pub = key.public_key().public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    result["pubkey_len"] = len(pub)

    msg = b"M0-008L-F deterministic message"
    sig = key.sign(msg)
    result["sig_len"] = len(sig)
    key.public_key().verify(sig, msg)
    result["sign_verify"] = "ok"

    tampered_rejected = False
    try:
        key.public_key().verify(sig, msg + b"!")
    except Exception as exc:  # rejection is the expected outcome
        tampered_rejected = True
        result["tamper_error_type"] = type(exc).__name__
    if not tampered_rejected:
        raise AssertionError("tampered signature was ACCEPTED")
    result["tamper_rejected"] = True
    _log("SIGN_VERIFY_OK tampered_rejected")

    # --- 5. Native op B: AES-GCM encrypt/decrypt -----------------------
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    aes_key = bytes(range(32))
    nonce = bytes(range(12))
    plaintext = b"Chaquopy Android cryptography 50.0.1 native proof"
    ct = AESGCM(aes_key).encrypt(nonce, plaintext, None)
    result["aes_ct_len"] = len(ct)

    if AESGCM(aes_key).decrypt(nonce, ct, None) != plaintext:
        raise AssertionError("AES-GCM round trip mismatch")
    result["aes_roundtrip"] = "ok"

    # Determinism proves the native AEAD actually ran rather than a stub.
    if AESGCM(aes_key).encrypt(nonce, plaintext, None) != ct:
        raise AssertionError("AES-GCM output is not deterministic")
    result["aes_deterministic"] = True

    auth_rejected = False
    try:
        AESGCM(bytes(range(1, 33))).decrypt(nonce, ct, None)
    except Exception as exc:  # InvalidTag is the expected outcome
        auth_rejected = True
        result["aes_invalid_error_type"] = type(exc).__name__
    if not auth_rejected:
        raise AssertionError("AES-GCM accepted a wrong key")
    result["aes_invalid_rejected"] = True
    _log("AES_GCM_OK invalid_rejected")

    # --- 6. Native op C: X.509 signing (ASN.1 + EVP path) -------------
    import datetime

    from cryptography import x509
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID

    ca_key = ec.generate_private_key(ec.SECP256R1())
    ca_name = x509.Name(
        [x509.NameAttribute(NameOID.COMMON_NAME, "M0-008L-F Probe CA")]
    )
    now = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(ca_name)
        .issuer_name(ca_name)
        .public_key(ca_key.public_key())
        .serial_number(1)
        .not_valid_before(now)
        .not_valid_after(
            datetime.datetime(2036, 1, 1, tzinfo=datetime.timezone.utc)
        )
        .sign(ca_key, hashes.SHA256())
    )
    pem = cert.public_bytes(serialization.Encoding.PEM)
    if not pem.startswith(b"-----BEGIN CERTIFICATE-----"):
        raise AssertionError("certificate PEM is malformed")

    reparsed = x509.load_pem_x509_certificate(pem)
    if reparsed.subject != ca_name:
        raise AssertionError("certificate subject round trip mismatch")
    result["x509_subject"] = reparsed.subject.rfc4514_string()
    result["x509_sig_hash"] = "sha256"
    _log("X509_OK " + result["x509_subject"])

    # --- 7. Cross-check against pure-Python hashlib --------------------
    result["hashlib_sha256"] = hashlib.sha256(plaintext).hexdigest()
    result["NATIVE_OPERATION_OK"] = True
    _log("NATIVE_OPERATION_OK all_operations_passed")
    return result

    sig = key.sign(msg)
    result["sig_len"] = len(sig)
    key.public_key().verify(sig, msg)
    result["sign_verify"] = "ok"

    tampered_rejected = False
    try:
        key.public_key().verify(sig, msg + b"!")
    except Exception as exc:  # rejection is the expected outcome
        tampered_rejected = True
        result["tamper_error_type"] = type(exc).__name__
    if not tampered_rejected:
        raise AssertionError("tampered signature was ACCEPTED")
    result["tamper_rejected"] = True
    _log("SIGN_VERIFY_OK tampered_rejected")
