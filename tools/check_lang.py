#!/usr/bin/env python3
"""Prüft die Sprachdateien: Jede Kennung aus dem Code existiert in beiden Sprachen, gleiche Zahl an Platzhaltern."""
import json, os, re, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lang = os.path.join(root, "src/main/resources/assets/colosseum/lang")
en = json.load(open(os.path.join(lang, "en_us.json"), encoding="utf-8"))
de = json.load(open(os.path.join(lang, "de_de.json"), encoding="utf-8"))
errors = []
for key in sorted(set(en) ^ set(de)):
    errors.append(f"nur in einer Sprache: {key}")
for key in set(en) & set(de):
    if len(re.findall(r"%s", en[key])) != len(re.findall(r"%s", de[key])):
        errors.append(f"Platzhalter passen nicht: {key}")
    for text in (en[key], de[key]):
        if re.search(r"%(?!s)", text.replace("%%", "")):
            errors.append(f"einzelnes Prozentzeichen: {key}")
PREFIX = r"(?:debug|msg|why|title|bar|sign|shop|anvil|hud|mob|powerup|offer|points|perk|door|class|difficulty)\.[a-z0-9_]+"
used = set()
src = os.path.join(root, "src/main/java/de/ecri/colosseum")
for folder, _, files in os.walk(src):
    if "selftest" in folder:
        continue
    for name in files:
        text = open(os.path.join(folder, name), encoding="utf-8").read()
        used |= set(re.findall(r'"(' + PREFIX + r')"', text))
for key in sorted(used):
    if "colosseum." + key not in en:
        errors.append(f"fehlt in den Sprachdateien: {key}")
unused = sorted(k for k in en if k.startswith("colosseum.") and k[len("colosseum."):] not in used)
print(f"{len(en)} Texte, {len(used)} Kennungen im Code, {len(unused)} ungenutzt: {unused[:8]}")
print("\n".join(errors) or "Alles in Ordnung")
sys.exit(1 if errors else 0)
