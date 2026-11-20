"""Read-only audit: every @SubscribeEvent listener must reach the event bus."""
import argparse
import os
import re

REG_RE = re.compile(r"EVENT_BUS\s*\.\s*register\s*\(([^;]{0,300}?)\)\s*;", re.S)
TOKEN_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def strip_comments(text: str) -> str:
    """Drop // and /*...*/ comments; string literals follow the same simple rule."""
    out = []
    i = 0
    mode = "code"
    n = len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if mode == "code":
            if ch == "/" and nxt == "/":
                mode = "line"
                i += 2
                continue
            if ch == "/" and nxt == "*":
                mode = "block"
                i += 2
                continue
            out.append(ch)
            i += 1
        elif mode == "line":
            if ch == "\n":
                out.append(ch)
                mode = "code"
            i += 1
        else:
            if ch == "*" and nxt == "/":
                mode = "code"
                i += 2
                continue
            if ch == "\n":
                out.append(ch)
            i += 1
    return "".join(out)


def _java_files(java_dir: str):
    """Return {basename: stripped text}; None when unreadable or empty."""
    if not os.path.isdir(java_dir):
        return None
    files = {}
    try:
        for root, _dirs, names in os.walk(java_dir):
            for name in names:
                if not name.endswith(".java"):
                    continue
                path = os.path.join(root, name)
                with open(path, "r", encoding="utf-8", errors="replace") as handle:
                    files[name[:-5]] = strip_comments(handle.read())
    except OSError:
        return None
    return files or None


def read_listeners(java_dir: str) -> dict:
    files = _java_files(java_dir)
    if files is None:
        return {}
    found = {}
    for name, text in files.items():
        count = text.count("@SubscribeEvent")
        if count:
            found[name] = count
    return dict(sorted(found.items()))


def read_registrations(java_dir: str, listeners: dict) -> set:
    files = _java_files(java_dir)
    if files is None:
        return set()
    scanned = {}
    for name, text in files.items():
        tokens = set()
        for match in REG_RE.finditer(text):
            tokens.update(TOKEN_RE.findall(match.group(1)))
        scanned[name] = (tokens, text)

    registered = set()
    for listener in listeners:
        alias_re = re.compile(r"\b" + re.escape(listener) + r"\s+([a-z][A-Za-z0-9_]*)\b")
        hit = False
        for tokens, text in scanned.values():
            if listener in tokens:
                hit = True
                break
            if any(m.group(1) in tokens for m in alias_re.finditer(text)):
                hit = True
                break
        if not hit:
            own = scanned.get(listener)
            if own is not None and REG_RE.search(own[1]):
                hit = True
        if hit:
            registered.add(listener)
    return registered


def check_registration(listeners: dict, registrations: set) -> list:
    return [
        "事件监听器 %s 有 @SubscribeEvent 但未注册到事件总线" % name
        for name in sorted(listeners)
        if name not in registrations
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", default="src/main/java")
    parser.add_argument("-v", action="store_true")
    args = parser.parse_args()

    if _java_files(args.java) is None:
        print("[问题] 事件总线读取失败")
        print("共 1 份事件总线 · 1 条问题")
        return 1

    listeners = read_listeners(args.java)
    registrations = read_registrations(args.java, listeners)
    problems = check_registration(listeners, registrations)

    if args.v:
        print("已检查 %d 个监听器 · %d 个已注册" % (len(listeners), len(registrations)))
    for problem in problems:
        print("[问题] " + problem)
    print("共 1 份事件总线 · %d 条问题" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
