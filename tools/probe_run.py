"""S7 独立探针跑手：编译 build/probe/<Name>.java 并运行 main。

用法：
    python tools/probe_run.py S7DashProbe [更多探针名...]

要点（前几轮踩过的坑）：
  * classpath 复用 offline_check.classpath()（服务端 srg jar 必须排最前）；
  * javac/java 用 subprocess 传 list，**不走 bash 字符串**，避免 MSYS 把 `;` 当分隔符吃掉（R27）；
  * 产物落 build/probe/out/<Name>/，不入库。
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import offline_check  # noqa: E402

ROOT = offline_check.ROOT
PROBE = os.path.join(ROOT, "build", "probe")
JAR = os.path.join(ROOT, "build", "libs", "redtale_project-0.1.0.jar")


def run(name):
    src = os.path.join(PROBE, name + ".java")
    if not os.path.exists(src):
        print(f"MISSING {src}")
        return 2
    out = os.path.join(PROBE, "out", name)
    os.makedirs(out, exist_ok=True)
    cp = offline_check.classpath()
    c = subprocess.run(["javac", "-encoding", "UTF-8", "-nowarn", "-proc:none",
                        "-d", out, "-cp", JAR + ";" + cp, src],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if c.returncode != 0:
        print(c.stdout)
        print(c.stderr)
        print(f"COMPILE_FAIL {name}")
        return 2
    r = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", out + ";" + JAR + ";" + cp, name],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(r.stdout.strip())
    if r.returncode != 0:
        print(r.stderr.strip()[:2000])
    return r.returncode


if __name__ == "__main__":
    rc = 0
    for n in sys.argv[1:]:
        print(f"== {n}")
        rc |= run(n)
    sys.exit(rc)
