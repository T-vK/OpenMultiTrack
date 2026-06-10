#!/usr/bin/env python3
"""Relay TCP connections (used when AP isolation blocks tablet-to-tablet traffic)."""
from __future__ import annotations

import socket
import sys
import threading


def pipe(src: socket.socket, dst: socket.socket) -> None:
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    finally:
        for sock, how in ((src, socket.SHUT_RD), (dst, socket.SHUT_WR)):
            try:
                sock.shutdown(how)
            except OSError:
                pass


def handle(client: socket.socket, target_host: str, target_port: int) -> None:
    remote = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    remote.connect((target_host, target_port))
    t1 = threading.Thread(target=pipe, args=(client, remote), daemon=True)
    t2 = threading.Thread(target=pipe, args=(remote, client), daemon=True)
    t1.start()
    t2.start()
    t1.join()
    t2.join()
    client.close()
    remote.close()


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(f"usage: {sys.argv[0]} LISTEN_HOST LISTEN_PORT TARGET_PORT")
    listen_host = sys.argv[1]
    listen_port = int(sys.argv[2])
    target_port = int(sys.argv[3])
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((listen_host, listen_port))
    server.listen()
    print(f"bridge {listen_host}:{listen_port} -> 127.0.0.1:{target_port}", flush=True)
    while True:
        client, _ = server.accept()
        threading.Thread(
            target=handle,
            args=(client, "127.0.0.1", target_port),
            daemon=True,
        ).start()


if __name__ == "__main__":
    main()
