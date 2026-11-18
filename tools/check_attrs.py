"""80 条属性表三向对账（T-0056）：docs §2 ↔ AttributeSpecs.java ↔ lang；只读，退出码 0/1。"""
from __future__ import annotations
import argparse, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOC_RE = re.compile(r"^\|\s*(\d+)\s*\|\s*`([^`]+)`\s*\|", re.M)
SEC_RE = re.compile(r"^### ([A-F])\..*?(\d+)", re.M)
CODE_RE = re.compile(r'^\s+(v|c|cb)\("([^"]+)",', re.M)
EXP_RE = re.compile(r"EXPECTED_COUNT\s*=\s*(\d+)")
LANGS = ("zh_cn", "en_us")


def abs_of(p):
    return p if os.path.isabs(p) else os.path.join(ROOT, p)


def read(path):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except Exception:
        return None


def parse_doc(text, probs):
    """返回 ([(序号, id)], [(段字母, 标题条数)], {字母: 实际条数})。"""
    if text is None or "## 2." not in text or "## 3." not in text:
        probs.append("文档缺 §2 或 §3 标题，无法定位属性表")
        return [], [], {}
    sec = text.split("## 2.")[1].split("## 3.")[0]
    rows = [(int(m.group(1)), m.group(2)) for m in DOC_RE.finditer(sec)]
    secs = [(m.group(1), int(m.group(2))) for m in SEC_RE.finditer(sec)]
    counts, letter = {}, None
    for line in sec.splitlines():
        m = SEC_RE.match(line)
        if m:
            letter = m.group(1)
            continue
        if DOC_RE.match(line) and letter:
            counts[letter] = counts.get(letter, 0) + 1
    return rows, secs, counts


def parse_java(text, probs):
    """返回 ([(op, id)], EXPECTED_COUNT 或 None)。"""
    if text is None:
        probs.append("代码文件读不到")
        text = ""
    pairs = [(m.group(1), m.group(2)) for m in CODE_RE.finditer(text)]
    m = EXP_RE.search(text)
    if not m:
        probs.append("代码里读不到 EXPECTED_COUNT")
        return pairs, None
    return pairs, int(m.group(1))


def load_lang(folder, probs):
    out = {}
    for name in LANGS:
        try:
            with open(os.path.join(folder, name + ".json"), "r", encoding="utf-8") as f:
                out[name] = json.load(f)
        except Exception as ex:
            probs.append(name + ".json 无法解析：" + str(ex))
            out[name] = None
    return out


def dupes(ids, side, probs):
    seen = {}
    for ident in ids:
        seen[ident] = seen.get(ident, 0) + 1
        if seen[ident] > 1:
            probs.append("重复 id：%s（%s 第 %d 次）" % (ident, side, seen[ident]))


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="80 条属性表三向对账")
    ap.add_argument("--doc", default="docs/ATTRIBUTE-REGISTRY.md")
    ap.add_argument("--java", default="src/main/java/com/redtale/project/core/attr/AttributeSpecs.java")
    ap.add_argument("--lang", default="src/main/resources/assets/redtale_project/lang")
    args = ap.parse_args()

    probs = []
    dtext = read(abs_of(args.doc))
    if dtext is None:
        probs.append("文档文件读不到：" + args.doc)
    rows, secs, counts = parse_doc(dtext, probs)
    pairs, expected = parse_java(read(abs_of(args.java)), probs)
    langs = load_lang(abs_of(args.lang), probs)
    doc_ids = [ident for _, ident in rows]
    code_ids = [ident for _, ident in pairs]

    if not (len(rows) == len(pairs) == expected):
        probs.append("条数不一致：文档 %d / 代码 %d / EXPECTED_COUNT %s" %
                     (len(rows), len(pairs), "缺失" if expected is None else expected))
    for letter, total in secs:
        if counts.get(letter, 0) != total:
            probs.append("段落 %s 计数不符：标题 %d / 实际 %d" % (letter, total, counts.get(letter, 0)))

    dset, cset = set(doc_ids), set(code_ids)
    for ident in doc_ids:
        if ident not in cset:
            probs.append("文档有、代码无：" + ident)
    for ident in code_ids:
        if ident not in dset:
            probs.append("代码有、文档无：" + ident)

    ops = {}
    for op, ident in pairs:
        ops.setdefault(ident, op)
    for ident in doc_ids:
        op = ops.get(ident)
        want = "v" if ident.startswith("minecraft:") else "自建"
        if op is None or (op == "v") != (ident.startswith("minecraft:")):
            probs.append("落点不一致：%s（文档 %s / 代码 %s）" %
                         (ident, "原版" if want == "v" else "自建", op if op else "无"))

    for i, (a, b) in enumerate(zip(doc_ids, code_ids), 1):
        if a != b:
            probs.append("顺序不一致：第 %d 位 文档 %s / 代码 %s" % (i, a, b))
            break

    dupes(doc_ids, "文档", probs)
    dupes(code_ids, "代码", probs)
    for i, (num, _) in enumerate(rows, 1):
        if num != i:
            probs.append("序号不连续：第 %d 行是 %d" % (i, num))

    for ident in doc_ids:
        if ident.startswith("minecraft:"):
            continue
        key = "attribute.redtale_project." + ident.split(":", 1)[-1]
        for name in LANGS:
            data = langs[name]
            if isinstance(data, dict) and key not in data:
                probs.append("%s 缺属性键：%s" % (name, key))

    for p in probs:
        print("[问题] " + p)
    print("共 3 份来源 · %d 条问题" % len(probs))
    return 1 if probs else 0


if __name__ == "__main__":
    sys.exit(main())
