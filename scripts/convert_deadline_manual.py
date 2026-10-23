#!/usr/bin/env python3
"""ML-002's manual straggler pass: the parameterless settle helpers (their
clock enters through a new parameter) and the direct in-method sites the
sweeper could not clock-resolve. Table-driven, one explicit row per site —
docs/TEST_DEADLINE_CENSUS.md's conversion ledger closes on these."""

from __future__ import annotations

import re
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CORE = "zamin-core/src/test/java/net/zaminmc/torch/server"
IMPORT_LINE = "import net.zaminmc.torch.testing.EngineAwaits;"

# (file, kind, payload...) — kind: 'helper' rewrites a parameterless
# try/sleep/catch helper to take the clock; 'site' swaps one line.
HELPER = {
    f"{CORE}/BeaconSystemTest.java": ("settle", "EngineServer server",
                                      "server.world()", 6, "the settle"),
    f"{CORE}/BlockEntityApiAcceptanceTest.java": ("awaitSettled", "EngineServer server",
                                                  "server.world()", 6,
                                                  "the submit drains at the head of the next tick"),
    f"{CORE}/ContainerApiAcceptanceTest.java": ("awaitSettled", "EngineServer server",
                                                 "server.world()", 6,
                                                 "the submit drains at the head of the next tick"),
    f"{CORE}/EnderChestAcceptanceTest.java": ("settle", "EngineServer server",
                                               "server.world()", 6, "the settle"),
    f"{CORE}/ExplosionTntRedstoneApiAcceptanceTest.java": ("awaitSettled", "EngineServer server",
                                                           "server.world()", 8,
                                                           "the submit drains at the head of the next tick"),
    f"{CORE}/HopperWindowAcceptanceTest.java": ("awaitSettled", "EngineServer server",
                                                "server.world()", 6,
                                                "the submit drains at the head of the next tick"),
    f"{CORE}/ScheduledUpdatesApiAcceptanceTest.java": ("awaitSettled", "EngineServer server",
                                                       "server.world()", 6,
                                                       "the submit drains at the head of the next tick"),
}

SITES = [
    (f"{CORE}/EnderExitAcceptanceTest.java", "Thread.sleep(300); // let the submit drain",
     'EngineAwaits.awaitTicks(server.world(), 6, "let the submit drain");'),
    (f"{CORE}/WeatherSystemTest.java", "Thread.sleep(300); // a few ticks run",
     'EngineAwaits.awaitTicks(third.world(), 6, "a few ticks run");'),
    (f"{CORE}/brewing/ContainerPersistenceTest.java", "Thread.sleep(300);",
     'EngineAwaits.awaitTicks(one.world(), 6, "a few ticks run (the brewing cycle arms)");'),
    (f"{CORE}/entity/vehicle/ChestMinecartTest.java", "Thread.sleep(300);",
     'EngineAwaits.awaitTicks(server.world(), 6, "the far click refused (the settle)");'),
    (f"{CORE}/PeacefulDifficultyTest.java", "Thread.sleep(500); // ten seconds of engine ticks",
     'EngineAwaits.awaitTicks(server.world(), 10,\n                "the night passes empty-handed (a peaceful world never spawns)");'),
    (f"{CORE}/SpawnEggTest.java", "Thread.sleep(150); // a few ticks: nothing may arrive late",
     'EngineAwaits.awaitTicks(server.world(), 3, "a few ticks: nothing may arrive late");'),
    (f"{CORE}/entity/SummonNbtTest.java", "Thread.sleep(150);",
     'EngineAwaits.awaitTicks(server.world(), 3, "the submit drains (the refused wound)");'),
    (f"{CORE}/entity/SummonNbtTest.java", "Thread.sleep(1_000); // 20 ticks",
     'EngineAwaits.awaitTicks(server.world(), 20, "the NoAI pin: the mind walk never runs");'),
    (f"{CORE}/entity/SummonNbtTest.java", "Thread.sleep(400);",
     'EngineAwaits.awaitTicks(server.world(), 8, "the settle");'),
    (f"{CORE}/SchedulerApiAcceptanceTest.java", "Thread.sleep(250); // several further ticks pass",
     'EngineAwaits.awaitTicks(server.world(), 5, "several further ticks pass");'),
    (f"{CORE}/SchedulerApiAcceptanceTest.java", "Thread.sleep(1500); // ~30 ticks at 20Hz",
     'EngineAwaits.awaitTicks(server.world(), 30, "~30 ticks at 20Hz");'),
    (f"{CORE}/SchedulerApiAcceptanceTest.java", "Thread.sleep(400);",
     'EngineAwaits.awaitTicks(server.world(), 8, "the settle");'),
    (f"{CORE}/SchedulerApiAcceptanceTest.java", "Thread.sleep(600);",
     'EngineAwaits.awaitTicks(server.world(), 12, "the settle");'),
    (f"{CORE}/SchedulerApiAcceptanceTest.java", "Thread.sleep(1100); // a full second of ticks",
     'EngineAwaits.awaitTicks(server.world(), 22, "a full second of ticks");'),
    (f"{CORE}/entity/vehicle/VehiclePersistenceTest.java", "Thread.sleep(600);",
     'EngineAwaits.awaitTicks(one.world(), 12,\n                "the bodies settle (the carts ride their physics onto the ground)");'),
    (f"{CORE}/entity/vehicle/VehiclePersistenceTest.java", "Thread.sleep(400); // the settle",
     'EngineAwaits.awaitTicks(one.world(), 8, "the settle");'),
]

HELPER_BODY = (
    r"(private\s+(?:static\s+)?void\s+%s\(\)\s*\{\s*try\s*\{\s*)"
    r"Thread\.sleep\([0-9_]+\);"
    r"(\s*\}\s*catch\s*\(InterruptedException\s+\w+\)\s*\{[^}]*\}\s*)\}"
)


def add_import(text: str) -> str:
    if IMPORT_LINE in text:
        return text
    lines = text.splitlines(keepends=True)
    imports = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if imports:
        lines.insert(imports[-1] + 1, IMPORT_LINE + "\n")
    else:
        pkg = [i for i, l in enumerate(lines) if l.startswith("package ")]
        lines.insert(pkg[-1] + 1 if pkg else 0, IMPORT_LINE + "\n")
    return "".join(lines)


def main() -> None:
    for path, (name, param, clock, ticks, what) in HELPER.items():
        p = Path(REPO, path)
        text = p.read_text(encoding="utf-8")
        pattern = re.compile(HELPER_BODY % name, re.MULTILINE)
        m = pattern.search(text)
        assert m, f"helper body not found: {path}:{name}"
        body = (f"private void {name}({param}) throws InterruptedException {{\n"
                f"        EngineAwaits.awaitTicks({clock}, {ticks}, \"{what}\");\n"
                f"    }}")
        text = pattern.sub(body.replace("\\", "\\\\"), text, count=1)
        # call sites gain the clock argument
        text = re.sub(rf"(?<![\w.]){name}\(\);", f"{name}({param.split()[-1]});", text)
        text = add_import(text)
        p.write_text(text, encoding="utf-8")
        print(f"helper {path}:{name}")

    for path, old, new in SITES:
        p = Path(REPO, path)
        text = p.read_text(encoding="utf-8")
        indent_m = re.search(r"^([ \t]*)" + re.escape(old.splitlines()[0]), text, re.MULTILINE)
        assert indent_m, f"site not found: {path}: {old[:40]}"
        indent = indent_m.group(1)
        new_indented = new.replace("\n", "\n" + indent)
        text = text.replace(indent + old, indent + new_indented, 1)
        text = add_import(text)
        p.write_text(text, encoding="utf-8")
        print(f"site   {path}: {old[:44]}")


if __name__ == "__main__":
    main()
