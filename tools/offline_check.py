"""
离线编译校验 + 手工打包（Gradle 构建被 maven.neoforged.net 阻断时的替代路径）。

它**不是**完整构建：不做 ModDevGradle 的 neoform 反编译/补丁流程，只做
「用测试服已有的 NeoForge 运行时 jar 当 classpath 做 javac」。

用法：
    python tools/offline_check.py            # 只编译校验
    python tools/offline_check.py --package  # 编译 + 打 build/libs/*.jar

前提：E:/server-test/libraries 存在（NeoForge 21.1.257 运行时）。
"""
import os
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIBS = r"E:/server-test/libraries"
NEO = os.path.join(LIBS, "net", "neoforged", "neoforge", "21.1.257")
SERVER_JAR = os.path.join(NEO, "neoforge-21.1.257-server.jar")
UNIVERSAL_JAR = os.path.join(NEO, "neoforge-21.1.257-universal.jar")
GSON = (r"C:\Users\AkiFish3\.gradle\caches\modules-2\files-2.1\com.google.code.gson"
        r"\gson\2.10.1\b3add478d4382b78ea20b1671390a858002feb6c\gson-2.10.1.jar")
OUT_CLASSES = os.path.join(ROOT, "build", "manualcheck")


def classpath():
    # 服务端 jar 必须排最前：libraries 里存在同名但映射不同的 MinecraftServer，会遮蔽它
    jars = [SERVER_JAR, UNIVERSAL_JAR]
    for root, _dirs, files in os.walk(LIBS):
        for f in files:
            if f.endswith(".jar"):
                p = os.path.join(root, f).replace("/", "\\")
                if p not in jars:
                    jars.append(p)
    if os.path.exists(GSON):
        jars.append(GSON)
    return ";".join(jars)


def sources():
    out = []
    for root, _dirs, files in os.walk(os.path.join(ROOT, "src", "main", "java")):
        for f in files:
            if f.endswith(".java"):
                out.append(os.path.join(root, f).replace("/", "\\"))
    return out


def compile_all():
    os.makedirs(OUT_CLASSES, exist_ok=True)
    srcs = sources()
    cmd = ["javac", "-encoding", "UTF-8", "-nowarn", "-proc:none",
           "-d", OUT_CLASSES, "-cp", classpath()] + srcs
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("sources:", len(srcs), "exit:", p.returncode)
    if p.stdout.strip():
        print(p.stdout)
    if p.stderr.strip():
        print(p.stderr)
    return p.returncode == 0


def read_props():
    props = {}
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def package_jar():
    props = read_props()
    with open(os.path.join(ROOT, "src", "main", "resources", "META-INF", "neoforge.mods.toml"),
              encoding="utf-8") as fh:
        toml = fh.read()
    for k in ("mod_id", "mod_name", "mod_version", "mod_group"):
        toml = toml.replace("${%s}" % k, props.get(k, ""))

    out = os.path.join(ROOT, "build", "libs",
                       "%s-%s.jar" % (props["mod_id"], props["mod_version"]))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _dirs, files in os.walk(OUT_CLASSES):
            for f in files:
                p = os.path.join(root, f)
                z.write(p, os.path.relpath(p, OUT_CLASSES).replace("\\", "/"))
        z.writestr("META-INF/neoforge.mods.toml", toml)
        res = os.path.join(ROOT, "src", "main", "resources")
        for root, _dirs, files in os.walk(res):
            for f in files:
                p = os.path.join(root, f)
                rel = os.path.relpath(p, res).replace("\\", "/")
                if rel == "META-INF/neoforge.mods.toml":
                    continue
                z.write(p, rel)
    print("jar:", out, os.path.getsize(out), "bytes")
    return out


if __name__ == "__main__":
    ok = compile_all()
    if ok and "--package" in sys.argv:
        package_jar()
    sys.exit(0 if ok else 1)
