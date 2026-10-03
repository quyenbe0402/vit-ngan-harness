import sys, time, threading

# A harmless, deterministic, long-lived child used only to prove the transport.
stop = threading.Event()

def heartbeat():
    n = 0
    while not stop.is_set():
        n += 1
        sys.stdout.write("TICK %d\n" % n); sys.stdout.flush()
        time.sleep(0.5)

threading.Thread(target=heartbeat, daemon=True).start()
sys.stderr.write("CHILD_STARTED\n"); sys.stderr.flush()

for line in sys.stdin:
    line = line.rstrip("\n")
    if line == "EXIT":
        sys.stderr.write("CHILD_STOPPING\n"); sys.stderr.flush()
        break
    if line.startswith("ERR"):
        sys.stderr.write("STDERR_FROM_COMMAND\n"); sys.stderr.flush()
    elif line.startswith("BIG"):
        sys.stdout.write("BIG " + ("x" * 200000) + "\n"); sys.stdout.flush()
    else:
        sys.stdout.write("ECHO " + line + "\n"); sys.stdout.flush()
stop.set()