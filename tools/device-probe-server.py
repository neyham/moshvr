#!/usr/bin/env python3
"""Fixed PTY fixture for DeviceConnectionProbe; never evaluates input as commands."""
import fcntl
import signal
import struct
import sys
import termios

signal.alarm(85)
print("PROBE_READY", flush=True)
for line in sys.stdin:
    command = line.strip()
    if command == "PING 你好 🥽":
        print("PROBE_PONG 你好 🥽", flush=True)
    elif command == "SIZE":
        rows, columns, _, _ = struct.unpack(
            "HHHH", fcntl.ioctl(0, termios.TIOCGWINSZ, bytes(8))
        )
        print(f"PROBE_SIZE {rows} {columns}", flush=True)
    elif command == "QUIT":
        print("PROBE_BYE", flush=True)
        break
    else:
        print("PROBE_UNSUPPORTED", flush=True)
