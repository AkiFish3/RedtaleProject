"""
极简 RCON 客户端（测试服验收入口用）。密码不写死：
读环境变量 RCON_PASSWORD，或 --server 指向的 server.properties 里的 rcon.password。

用法：
    python tools/rcon.py "redtale status"
    python tools/rcon.py --server E:/server-test "redtale reload"
"""
import argparse
import os
import re
import socket
import struct


def packet(rid, ptype, body):
    data = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", 4 + 4 + len(data), rid, ptype) + data


def read_packet(sock):
    raw = sock.recv(4)
    if len(raw) < 4:
        return None, None, ""
    (length,) = struct.unpack("<i", raw)
    buf = b""
    while len(buf) < length:
        chunk = sock.recv(length - len(buf))
        if not chunk:
            break
        buf += chunk
    rid, ptype = struct.unpack("<ii", buf[:8])
    return rid, ptype, buf[8:-2].decode("utf-8", "replace")


def password_from_properties(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = re.match(r"\s*rcon\.password\s*=\s*(.*)", line)
            if m:
                return m.group(1).strip()
    return ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("command")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25577)
    ap.add_argument("--server", default="E:/server-test")
    args = ap.parse_args()

    password = os.environ.get("RCON_PASSWORD")
    if not password:
        password = password_from_properties(os.path.join(args.server, "server.properties"))
    if not password:
        raise SystemExit("没有 RCON 密码：设置 RCON_PASSWORD 或检查 server.properties")

    sock = socket.create_connection((args.host, args.port), timeout=10)
    sock.sendall(packet(1, 3, password))
    rid, _ptype, _body = read_packet(sock)
    if rid == -1:
        raise SystemExit("RCON 认证失败")
    sock.sendall(packet(2, 2, args.command))
    _rid, _ptype, body = read_packet(sock)
    sock.close()
    print(body.strip())


if __name__ == "__main__":
    main()
