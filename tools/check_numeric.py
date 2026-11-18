"""数值面四份配置起飞前对账（T-0057）：只读，--dir/--java 可指临时目录，退出码 0=干净 1=有问题。"""
import argparse, json, math, os, re, sys
from check_progress import clean_json5

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = ("attack_speed", "crit", "damage", "progression")
ATTR_JAVA = "src/main/java/com/redtale/project/core/attr/AttributeSpecs.java"
ATTR_RE = re.compile(r'^\s+(v|c|cb)\("([^"]+)",', re.M)   # (op, id)：只取 id 作白名单
SINGLE_KEY = re.compile(r"'([^'\n]+)'(\s*:)")
AS_KEYS = ("haste_attack_speed_per_level", "effect_attack_speed", "soft_cap", "min_attack_delay_ticks")
AS_NUM = ("haste_attack_speed_per_level", "soft_cap", "min_attack_delay_ticks")
CRIT_KEYS = ("enabled", "chance_attribute", "power_attribute", "apply_to_vanilla_crit", "force_on_roll", "max_chance")
CRIT_BOOL = ("enabled", "apply_to_vanilla_crit", "force_on_roll")
CAP_KEYS = ("percentCap", "condCap", "condCount", "damageCap")
ATTRS, ATTR_OK, P = set(), True, []

def num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)

def prob(name, msg):
    P.append((name, msg))

def known(token):
    """token 合法口径：自建裸名 / redtale_project:裸名 / 原版 minecraft:全名。"""
    if not isinstance(token, str): return False
    t = token.strip()
    if t in ATTRS: return True
    if t.startswith("redtale_project:") and t[len("redtale_project:"):] in ATTRS: return True
    return False

def load(root, name):
    path = os.path.join(root, name + ".json5")
    if not os.path.isfile(path): return None, "解析失败：找不到 " + name + ".json5"
    with open(path, "r", encoding="utf-8", errors="replace") as f: text = f.read()
    try: data = json.loads(clean_json5(SINGLE_KEY.sub(r'"\1"\2', text)))
    except Exception as ex: return None, "解析失败：" + str(ex)
    if not isinstance(data, dict): return None, "顶层不是对象"
    return data, None

def check_as(d):
    for k in AS_KEYS:
        if k not in d: prob("attack_speed", "attack_speed 缺键：" + k)
    for k in AS_NUM:
        if k in d and not num(d[k]): prob("attack_speed", "attack_speed." + k + " 必须是数字")
    v = d.get("min_attack_delay_ticks")
    if num(v) and v < 1.0: prob("attack_speed", "attack_speed.min_attack_delay_ticks = %s 低于物理地板 1.0（delay 到 0 会让 getAttackCooldownProgress 除零 → 每 tick 出手）" % v)
    v = d.get("haste_attack_speed_per_level")
    if num(v) and v < 0: prob("attack_speed", "attack_speed.haste_attack_speed_per_level 不能为负：" + str(v))
    v = d.get("soft_cap")
    if num(v) and v < 0: prob("attack_speed", "attack_speed.soft_cap 不能为负：" + str(v) + "（0 = 不饱和）")
    ea = d.get("effect_attack_speed")
    if not isinstance(ea, dict): prob("attack_speed", "attack_speed.effect_attack_speed 必须是对象"); return
    for eid, ev in ea.items():
        if not isinstance(ev, dict): prob("attack_speed", "attack_speed.effect_attack_speed." + eid + " 的值必须是对象（{flat|per_level}）"); continue
        for kk in ev:
            if kk not in ("flat", "per_level"): prob("attack_speed", "attack_speed.effect_attack_speed." + eid + " 有未知键：" + kk)
        for kk in ("flat", "per_level"):
            if kk in ev and not num(ev[kk]): prob("attack_speed", "attack_speed.effect_attack_speed." + eid + "." + kk + " 必须是数字")
    h = ea.get("minecraft:haste")
    if isinstance(h, dict) and any(num(h.get(k)) and h[k] != 0 for k in ("per_level", "flat")):
        prob("attack_speed", "attack_speed.effect_attack_speed 里的 minecraft:haste 必须为 0（急迫只走 haste_attack_speed_per_level，运行期会忽略该条并告警）")

def check_crit(d):
    for k in CRIT_KEYS:
        if k not in d: prob("crit", "crit 缺键：" + k)
    for k in CRIT_BOOL:
        if k in d and not isinstance(d[k], bool): prob("crit", "crit." + k + " 必须是布尔值")
    for k in ("chance_attribute", "power_attribute"):
        if k not in d: continue
        v = d[k]
        if not isinstance(v, str) or not v.strip(): prob("crit", "crit." + k + " 必须是非空字符串")
        elif ATTR_OK and not known(v): prob("crit", "crit." + k + " 不是已注册属性：" + v)
    if "max_chance" in d:
        v = d["max_chance"]
        if not num(v): prob("crit", "crit.max_chance 必须是数字")
        elif v < 0: prob("crit", "crit.max_chance 不能为负：" + str(v) + "（0 = 不限；负值运行期会被静默当 0）")

def check_damage(d):
    caps = d.get("caps")
    if not isinstance(caps, dict): prob("damage", "damage.caps 缺失或不是对象")
    else:
        for k in CAP_KEYS:
            if k not in caps: prob("damage", "damage.caps 缺键：" + k); continue
            v = caps[k]
            if k == "condCount":
                if isinstance(v, bool) or not isinstance(v, int) or v < 0: prob("damage", "damage.caps.condCount 必须是 ≥ 0 的整数：" + str(v))
            elif not (num(v) and v >= 0): prob("damage", "damage.caps." + k + " 必须 ≥ 0：" + str(v))
    san = d.get("sanity")
    if san is None: prob("damage", "damage.sanity 缺失")
    elif isinstance(san, dict):
        if "negativeGuard" in san and not isinstance(san["negativeGuard"], bool): prob("damage", "damage.sanity.negativeGuard 必须是布尔值")
        if "hardCeiling" in san:
            v = san["hardCeiling"]
            if not (num(v) and 0 < v <= 1e9): prob("damage", "damage.sanity.hardCeiling 必须在 (0, 1e9] 内：" + str(v))
        if "logThreshold" in san:
            v = san["logThreshold"]
            if not (num(v) and v > 0): prob("damage", "damage.sanity.logThreshold 必须 > 0：" + str(v))
        if "logSampleRate" in san:
            v = san["logSampleRate"]
            if not (num(v) and 0 <= v <= 1): prob("damage", "damage.sanity.logSampleRate 必须在 [0,1] 内：" + str(v))
    mul = d.get("multiplier")
    if not isinstance(mul, dict): prob("damage", "damage.multiplier 缺失或不是对象"); return
    dt = mul.get("damage_types")
    if not isinstance(dt, list) or not dt: prob("damage", "damage.multiplier.damage_types 必须是非空数组")
    else:
        for t in dt:
            if not isinstance(t, str) or not t.strip(): prob("damage", "damage.multiplier.damage_types 元素必须是非空字符串")
            elif t == "*": prob("damage", 'damage.multiplier.damage_types 不许写 "*"（会把别人的伤害也乘一遍）')
    if "passthrough_external" in mul and not isinstance(mul["passthrough_external"], bool): prob("damage", "damage.multiplier.passthrough_external 必须是布尔值")
    pa = mul.get("percent_attributes")
    if not isinstance(pa, list): prob("damage", "damage.multiplier.percent_attributes 必须是数组")
    elif ATTR_OK:
        for t in pa:
            if not known(t): prob("damage", "damage.multiplier.percent_attributes 含未知属性：" + str(t))
    ca = mul.get("category_attributes")
    if not isinstance(ca, list): prob("damage", "damage.multiplier.category_attributes 必须是数组")
    else:
        seen = set()
        for i, it in enumerate(ca):
            if not isinstance(it, dict): prob("damage", "damage.multiplier.category_attributes[%d] 不是对象" % i); continue
            typ = it.get("type")
            if not isinstance(typ, str) or not typ.strip(): prob("damage", "damage.multiplier.category_attributes[%d] 缺非空 type" % i); typ = None
            elif typ in seen: prob("damage", "damage.multiplier.category_attributes type 重复：" + typ)
            if typ: seen.add(typ)
            attrs = it.get("attributes")
            if not isinstance(attrs, list): prob("damage", "damage.multiplier.category_attributes[%d].attributes 必须是数组" % i)
            elif ATTR_OK:
                for t in attrs:
                    if not known(t): prob("damage", "damage.multiplier.category_attributes[%s] 含未知属性：%s" % (typ, t))
    cond = mul.get("conditional_attributes")
    if not isinstance(cond, dict): prob("damage", "damage.multiplier.conditional_attributes 必须是对象")
    else:
        for k, v in cond.items():
            if k not in ("pvp", "pve", "undead"): prob("damage", "damage.multiplier.conditional_attributes 未知键：" + k + "（只能是 pvp / pve / undead，运行期会跳过）")
            if not isinstance(v, list): prob("damage", "damage.multiplier.conditional_attributes." + k + " 必须是数组"); continue
            if ATTR_OK:
                for t in v:
                    if not known(t): prob("damage", "damage.multiplier.conditional_attributes." + k + " 含未知属性：" + str(t))

def check_prog(d):
    if "mob_exp_multiplier" not in d: prob("progression", "progression 缺键：mob_exp_multiplier"); return
    v = d["mob_exp_multiplier"]
    if not num(v): prob("progression", "progression.mob_exp_multiplier 必须是数字")
    elif v < 0: prob("progression", "progression.mob_exp_multiplier 不能为负：" + str(v))
    elif v > 1e9: prob("progression", "progression.mob_exp_multiplier 超过硬顶 1e9：" + str(v))

CHECKS = {"attack_speed": check_as, "crit": check_crit, "damage": check_damage, "progression": check_prog}

def main():
    global ATTRS, ATTR_OK
    try: sys.stdout.reconfigure(encoding="utf-8")
    except Exception: pass
    ap = argparse.ArgumentParser(description="数值面配置对账")
    ap.add_argument("--dir", default="src/main/resources/redtale/defaults")
    ap.add_argument("--java", default=ATTR_JAVA)
    a = ap.parse_args()
    os.chdir(ROOT)
    jp = os.path.abspath(a.java)
    if os.path.isfile(jp):
        with open(jp, "r", encoding="utf-8", errors="replace") as f:
            ATTRS = set(m.group(2) for m in ATTR_RE.finditer(f.read()))
        ATTR_OK = bool(ATTRS)
    else:
        ATTR_OK = False
    if not ATTR_OK: prob("-", "属性表读不到：" + a.java)
    root, datas = os.path.abspath(a.dir), {}
    for name in FILES:
        d, err = load(root, name)
        if err: prob(name, err); datas[name] = None
        else: datas[name] = d
    for name in FILES:
        if datas[name]: CHECKS[name](datas[name])
    for name, msg in P: print("[问题] " + name + " · " + msg)
    print("共 %d 个文件 · %d 条问题" % (len(FILES), len(P)))
    return 1 if P else 0

if __name__ == "__main__":
    sys.exit(main())
