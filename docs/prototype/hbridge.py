import os, socket, subprocess, sys, threading, json, time

TOKEN = sys.argv[1]; SP = int(sys.argv[2]); CP = int(sys.argv[3]); CHILD = sys.argv[4:]
HOME = "/data/data/com.termux/files/home"
log = open(HOME + "/hbridge.log", "a", buffering=1)
def say(m): log.write("%.3f %s\n" % (time.time(), m))

lock = threading.Lock()
last = {"session": None}

def listen(port, name):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", port)); s.listen(8); s.settimeout(120)
    say("%s listening %d" % (name, port))
    open(HOME + "/hbridge.%s.port" % name, "w").write(str(port))
    return s

def auth(conn):
    conn.settimeout(30); buf = b""
    while not buf.endswith(b"\n"):
        c = conn.recv(1)
        if not c: return False
        buf += c
    if buf.decode(errors="replace").strip() != "AUTH " + TOKEN:
        conn.sendall(b"AUTH_FAIL\n"); say("auth rejected"); return False
    conn.sendall(b"AUTH_OK\n"); conn.settimeout(None); return True

def stdio_loop():
    srv = listen(SP, "stdio")
    while True:
        conn, _ = srv.accept()
        if not auth(conn): conn.close(); continue
        sid = "s%d" % int(time.time()*1000)
        child = subprocess.Popen(CHILD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with lock: last["session"] = (sid, child)
        say("session %s child pid=%d" % (sid, child.pid))
        def pump_out():
            try:
                for raw in child.stdout: conn.sendall(raw)
            except Exception as e: say("stdout end %r" % e)
        def pump_err():
            try:
                for raw in child.stderr:
                    with lock: tgt = last["ctrl"]
                    if tgt: tgt.sendall(json.dumps({"session": sid, "stderr": raw.decode(errors="replace")}).encode() + b"\n")
            except Exception: pass
        threading.Thread(target=pump_out, daemon=True).start()
        threading.Thread(target=pump_err, daemon=True).start()
        try:
            while True:
                d = conn.recv(4096)
                if not d: break
                child.stdin.write(d); child.stdin.flush()
        except Exception as e: say("stdin end %r" % e)
        say("session %s client closed" % sid)
        try: child.stdin.close()
        except Exception: pass
        try: child.terminate()
        except Exception: pass
        try: conn.close()
        except Exception: pass

def ctrl_loop():
    srv = listen(CP, "ctrl")
    while True:
        conn, _ = srv.accept()
        if not auth(conn): conn.close(); continue
        with lock: last["ctrl"] = conn
        say("ctrl attached")
        try:
            while True:
                d = conn.recv(4096)
                if not d: break
        except Exception: pass
        with lock: last["ctrl"] = None
        conn.close()

threading.Thread(target=ctrl_loop, daemon=True).start()
stdio_loop()