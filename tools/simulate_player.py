#!/usr/bin/env python3
"""Simuliert Spieler im Colosseum und zeigt, was sie pro Stunde für den Punkte-Shop erspielen.

Aufruf:  python3 tools/simulate_player.py [--hours 400] [--seed 1]

Fest aus dem Spiel übernommen (Stand 0.8): Wellen, Punkte, Preise, Türen, Pausen.
Annahmen über den Menschen stehen in PROFILES: Sie sind geschätzt. "mittel" ist so eingestellt, dass
er allein zu den echten Läufen aus colosseum_bestenliste.json passt (etwa 3 bis 5 Runden im Schnitt,
bester um 9, rund 23.000 verdiente Punkte pro Stunde, gemessen über rund 48 Minuten Anmeldezeit). Der Kampf ist ein grobes Modell, kein Nachbau der Mob-KI.

Die Shop-Punkte hängen nur an den geschafften Runden: 100 * n * (n + 5), siehe PointsAccount.
Das Modell kennt keine Pack-a-Punch-Stufen und keine Mystery Box.
"""
import argparse
import math
import random
import statistics
from dataclasses import dataclass

# --- Aus dem Spiel: Zeiten in Sekunden (ArenaRun)
COUNTDOWN = 5
PAUSE = 25
EXFIL_CALL = 5
SPAWN_INTERVAL = 0.4
MAX_MOBS = 40
ROUND_TEN = 15000


def total_for(rounds):
    """PointsAccount.totalFor"""
    return 100 * rounds * (rounds + 5)


# Rüstung: (Rüstungspunkte, Härte, Schutzwirkung als Anteil)
ARMOR = {
    "start": (3, 0, 0), "leather": (7, 0, 0), "chain": (12, 0, 0), "iron": (15, 0, 0),
    "diamond": (20, 8, 0.32), "netherite": (20, 12, 0.64),
}
# Schwerter: Schaden pro Treffer
WEAPON = {"stone": 5.0, "iron": 6.0, "diamond": 8.5, "netherite": 11.0}

# Kaufreihenfolge: Name, Preis, was es bewirkt. Türen schalten den nächsten Abschnitt frei.
UPGRADES = [
    ("Lederrüstung", 150, ("armor", "leather")),
    ("Schild", 300, ("block", 0.15)),
    ("Tür Nordhof", 750, ("door", 2)),
    ("Eisenschwert", 500, ("weapon", "iron")),
    ("Kettenrüstung", 500, ("armor", "chain")),
    ("Eisenrüstung", 1200, ("armor", "iron")),
    ("Tür Säulenhalle", 1500, ("door", 3)),
    ("Diamantschwert", 2000, ("weapon", "diamond")),
    ("Diamantrüstung", 3000, ("armor", "diamond")),
    ("Tür Grube", 3000, ("door", 4)),
    ("Netherit-Schwert", 4500, ("weapon", "netherite")),
    ("Netherit-Rüstung", 6000, ("armor", "netherite")),
]
HEAL_PRICE, HEAL_AMOUNT = 250, 8


@dataclass(frozen=True)
class Profile:
    name: str
    hits_per_second: float  # Treffer pro Sekunde auf das Ziel
    walk_seconds: float     # Weg zum nächsten Ziel
    hit_rate: float         # Treffer pro Sekunde, die ein anliegender Mob wirklich landet
    max_contact: int        # So viele Mobs erreichen ihn gleichzeitig
    regen: float            # Herzen-Halbe pro Sekunde durch Essen im Kampf
    pause_heal: float       # Heilung in der Pause
    powerup_catch: float    # So oft sammelt er ein Power-up ein
    exfil_after: int        # Ab dieser Runde ruft er den Exfil (0 = nie)
    lobby_seconds: float    # Zeit in der Halle zwischen zwei Durchläufen
    active_share: float     # Anteil der Stunde, in dem wirklich gespielt wird


PROFILES = {
    "anfaenger": Profile("anfaenger", 0.65, 1.6, 0.07, 4, 0.10, 8, 0.5, 0, 60, 0.80),
    "mittel": Profile("mittel", 0.90, 1.1, 0.045, 3, 0.15, 11, 0.7, 10, 45, 0.90),
    "gut": Profile("gut", 1.15, 0.8, 0.03, 2, 0.20, 14, 0.9, 15, 30, 0.95),
}


def compose(round_no, players, rng):
    """WaveComposer.compose"""
    party = 1.0 + 0.5 * max(0, players - 1)
    wave = []
    budget = rng.randrange(round_no // 2 + 1)
    if round_no % 4 != 0:
        n = min(MAX_MOBS, round((4 + 2 * round_no) * party))
        skeletons = min(n, budget)
        wave += ["skeleton"] * skeletons
        wave += ["zombie"] * (n - skeletons)
    elif round_no >= 20:
        wave += ["ravager"] * min(8, round((1 + (round_no - 20) // 8) * party))
        wave += ["drowned"] * min(MAX_MOBS, round((4 + round_no // 4) * party))
    elif round_no % 8 == 4:
        jockeys = max(1, round((5 + round_no) * party / 4))
        wave += ["chicken"] * jockeys + ["zombie"] * (2 * jockeys)
    else:
        n = min(MAX_MOBS, round((3 + round_no // 2) * party))
        riders = min(n, budget)
        wave += ["spider_jockey"] * riders + ["spider"] * (n - riders)
    if round_no % 10 == 0:
        wave.append("boss")
    return wave


def compose_exfil(round_no, players, rng):
    """WaveComposer.composeExfil"""
    party = 1.0 + 0.5 * max(0, players - 1)
    n = min(32, round((8 + 2.5 * round_no) * party))
    skeletons = rng.randrange(round_no // 2 + 1)
    wave = []
    for _ in range(n):
        roll = rng.randrange(100)
        if 58 <= roll < 78:
            wave.append("skeleton" if skeletons > 0 else "zombie")
            skeletons -= 1
        elif roll >= 85:
            wave.append("spider_jockey" if skeletons > 0 else "spider")
            skeletons -= 1
        else:
            wave.append("chicken" if roll >= 82 else "zombie")
    wave += ["ravager"] * max(1, round_no // 10)
    return wave


def mob_stats(kind, round_no, rng):
    """Leben, Schaden, Fernkampf, Punkte nach MobFactory."""
    chance = min(0.9, round_no * 0.06)
    armor_by_round = 0 if round_no < 4 else 9 if round_no < 7 else 12 if round_no < 12 else 14 if round_no < 18 else 18
    armor = armor_by_round * chance
    sword = (0 if rng.random() > chance else 4 if round_no < 6 else 5 if round_no < 14 else 6)
    strength = 0
    if round_no >= 10 and rng.random() < min(0.5, (round_no - 9) * 0.04):
        strength = 3 if round_no < 20 else 6
    table = {
        "zombie": (20, 3 + sword, False, 50),
        "skeleton": (20, 3.5 + max(0, round_no - 9) * 0.3, True, 50),
        "chicken": (20, 3 + sword, False, 100),
        "spider": (14, 2.5, False, 80),
        "spider_jockey": (20 + 14, 3.5, True, 180),
        "drowned": (20, 9, True, 80),
        "ravager": (120, 12, False, 400),
        "boss": (20 * (10 + round_no / 5), 5 * (1.5 + round_no * 0.05) + 9, False, 1000 + 50 * round_no - 5 * round_no),
    }
    hp, damage, ranged, base = table[kind]
    return hp, damage + strength, ranged, base + 5 * round_no, armor if kind != "boss" else 8


def after_armor(damage, armor_key):
    points, toughness, protection = ARMOR[armor_key]
    effective = min(20, max(points / 5, points - damage / (2 + toughness / 4)))
    return damage * (1 - effective / 25) * (1 - protection)


class Player:
    def __init__(self, profile):
        self.profile = profile
        self.hp = 20.0
        self.alive = True
        self.points = 0
        self.earned = 0
        self.spent_on_gear = 0
        self.weapon = "stone"
        self.armor = "start"
        self.block = 0.0
        self.heals = 0
        self.bought = 0
        self.bank = 0

    def add(self, amount):
        self.points += amount
        self.earned += amount

    def shop(self, doors):
        """Kauft das Nächste auf der Liste. Sparen bringt nichts mehr, der Shop zahlt nach Runden."""
        while self.bought < len(UPGRADES):
            _, price, (what, value) = UPGRADES[self.bought]
            if what == "door" and value in doors:
                self.bought += 1
                continue
            if self.points < price:
                break
            self.points -= price
            self.spent_on_gear += price
            self.bought += 1
            if what == "door":
                doors.add(value)
            elif what == "weapon":
                self.weapon = value
            elif what == "armor":
                self.armor = value
            elif what == "block":
                self.block = value
        while self.heals < 2 and self.points >= HEAL_PRICE:
            self.points -= HEAL_PRICE
            self.spent_on_gear += HEAL_PRICE
            self.heals += 1


def fight(team, wave, round_no, rng):
    """Eine Welle in Sekundenschritten. Gibt die Dauer zurück."""
    pending = list(wave)
    rng.shuffle(pending)
    arrival = [(8 + i * SPAWN_INTERVAL, mob_stats(kind, round_no, rng)) for i, kind in enumerate(pending)]
    field = []  # [Leben, Schaden, Fernkampf, Punkte, Rüstung]
    target = {id(p): None for p in team}
    cooldown = {id(p): 0.0 for p in team}
    double_until = insta_until = -1.0
    t = 0.0
    while True:
        t += 1.0
        while arrival and arrival[0][0] <= t:
            _, stats = arrival.pop(0)
            field.append(list(stats))
        living = [p for p in team if p.alive]
        if not living:
            return t
        if not field and not arrival:
            return t

        for player in living:
            p = player.profile
            if cooldown[id(player)] > 0:
                cooldown[id(player)] -= 1.0
                continue
            if not field:
                continue
            mob = target[id(player)]
            if mob is None or mob not in field:
                mob = field[0] if rng.random() < 0.7 else rng.choice(field)
                target[id(player)] = mob
            damage = WEAPON[player.weapon] * p.hits_per_second * (1 - min(20, mob[4]) / 25 * 0.6)
            mob[0] -= 10_000 if t < insta_until else damage
            if mob[0] <= 0:
                field.remove(mob)
                player.add(mob[3] * (2 if t < double_until else 1))
                cooldown[id(player)] = p.walk_seconds * (2.5 if mob[2] else 1.0)
                if rng.random() < 0.04 and rng.random() < p.powerup_catch:
                    kind = rng.randrange(4)
                    if kind == 0:
                        double_until = t + 30
                    elif kind == 1:
                        insta_until = t + 30
                    elif kind == 3:
                        keep = [m for m in field if m[3] >= 1000]
                        field[:] = keep
                        for q in living:
                            q.add(400)

        # Schaden: Fernkämpfer treffen immer, Nahkämpfer nur, wenn sie herankommen
        melee = [m for m in field if not m[2]]
        ranged = [m for m in field if m[2]]
        for player in list(living):
            p = player.profile
            share = len(living)
            for mob in melee[: p.max_contact * share]:
                if rng.random() < p.hit_rate / share * (1 - player.block):
                    player.hp -= after_armor(mob[1], player.armor)
            for mob in ranged:
                if rng.random() < p.hit_rate * 0.7 / share * (1 - player.block):
                    player.hp -= after_armor(mob[1], player.armor)
            player.hp = min(20.0, player.hp + p.regen)
            if player.hp < 7 and player.heals > 0:
                player.heals -= 1
                player.hp = min(20.0, player.hp + HEAL_AMOUNT)
            if player.hp <= 0:
                partner = [q for q in living if q is not player and q.alive]
                if partner and rng.random() < 0.5:
                    player.hp = 10.0  # gerettet
                else:
                    player.alive = False
                    player.weapon, player.armor, player.block, player.bought = "stone", "start", 0.0, 0
                    player.heals = 0
        if t > 900:  # Kein Lauf hängt ewig, das Modell bricht hier ab
            for player in team:
                player.alive = False
            return t


def play_run(profile, players, rng):
    """Ein Durchlauf bis Tod oder Exfil. Gibt Dauer, Runden und pro Spieler Konto-Plus und Punkte zurück."""
    team = [Player(profile) for _ in range(players)]
    doors = set()
    elapsed = profile.lobby_seconds + COUNTDOWN
    round_no = 0
    exfil = False
    while True:
        round_no += 1
        elapsed += fight(team, compose(round_no, players, rng), round_no, rng)
        if not any(p.alive for p in team):
            round_no -= 1
            break
        for player in team:
            if player.alive:
                player.add(50 + 10 * round_no)
                player.bank += total_for(round_no) - total_for(round_no - 1)
                player.hp = min(20.0, player.hp + profile.pause_heal)
            else:  # Gefallene sind nächste Runde wieder da, mit halbem Leben und ohne Ausrüstung
                player.alive = True
                player.hp = 10.0
            player.shop(doors)
        if round_no % 3 == 0:  # Kisten: ab und zu eine bessere Waffe
            for player in team:
                if player.weapon == "stone" and rng.random() < 0.5:
                    player.weapon = "iron"
        call = profile.exfil_after and round_no % 5 == 0 and round_no >= profile.exfil_after
        if call:
            elapsed += EXFIL_CALL
            elapsed += fight(team, compose_exfil(round_no, players, rng), round_no, rng)
            exfil = any(p.alive for p in team)
            if exfil:
                for player in team:
                    player.add(100 + 20 * round_no)
                elapsed += 10
            break
        elapsed += PAUSE
    results = []
    for player in team:
        bank = player.bank
        if exfil and player.alive:
            bank += bank // 2
        results.append((bank, player.earned))
    return elapsed, round_no, exfil, results


def simulate(profile, players, hours, seed):
    rng = random.Random(seed)
    budget = hours * 3600 * profile.active_share
    spent = 0.0
    bank = earned = 0
    rounds = []
    exfils = 0
    while spent < budget:
        duration, round_no, exfil, results = play_run(profile, players, rng)
        spent += duration
        rounds.append(round_no)
        exfils += exfil
        bank += sum(r[0] for r in results) / players
        earned += sum(r[1] for r in results) / players
    return {
        "bank_per_hour": bank / hours,
        "earned_per_hour": earned / hours,
        "minutes_per_run": spent / 60 / len(rounds),
        "rounds_median": statistics.median(rounds),
        "rounds_mean": statistics.mean(rounds),
        "rounds_p90": sorted(rounds)[int(len(rounds) * 0.9)],
        "exfil_share": exfils / len(rounds),
        "runs_per_hour": len(rounds) / hours,
    }


def nice(value, step):
    return max(step, int(round(value / step)) * step)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hours", type=int, default=400, help="simulierte Spielstunden pro Fall")
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()

    print("Echte Läufe vom Server (bestenliste, 0.6/0.7, ein Spieler):")
    real = [(9, 9580), (5, 4215), (3, 1610), (3, 1180), (2, 940), (1, 335), (0, 110), (0, 110)]
    print(f"  {len(real)} Läufe, Runden im Schnitt {statistics.mean(r[0] for r in real):.1f}, beste {max(r[0] for r in real)},"
          f" Punkte verdient {sum(r[1] for r in real)}\n")

    header = f"{'Profil':10} {'Spieler':>7} {'Runden Median':>13} {'Schnitt':>7} {'Top 10 %':>8} {'Exfil':>6} {'Min/Lauf':>8} {'verdient/h':>10} {'Konto/h':>8}"
    print(header)
    print("-" * len(header))
    base = {}
    for name, profile in PROFILES.items():
        for players in (1, 2):
            r = simulate(profile, players, args.hours, args.seed)
            base[(name, players)] = r
            print(f"{name:10} {players:7} {r['rounds_median']:13.0f} {r['rounds_mean']:7.1f} {r['rounds_p90']:8d} {r['exfil_share']:6.0%}"
                  f" {r['minutes_per_run']:8.1f} {r['earned_per_hour']:10.0f} {r['bank_per_hour']:8.0f}")

    print("\nPreise: 30 Level, 16 Diamanten oder 1 Netherit-Barren kosten je", ROUND_TEN, "(Tod in Runde 11, Normal)")
    print("Was das pro Stunde ist (Stunden bis zum ersten Mal 30 Level kaufen):")
    for (name, players), r in base.items():
        print(f"  {name:10} {players} Spieler: {r['bank_per_hour'] / ROUND_TEN * 16:5.1f} Diamanten pro Stunde, {ROUND_TEN / r['bank_per_hour']:4.1f} h")


if __name__ == "__main__":
    main()
