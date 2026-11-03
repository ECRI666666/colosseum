# Colosseum of Experience: Aufbau und Plan

Erster Teil des eigenen Modpacks. Minecraft 26.3, NeoForge 26.3.0.101, Java 25.
Jeder Spieler braucht die Mod auch im Client, weil Portalblock und Dimension neu sind.

## Paketstruktur

```
de.ecri.colosseum
├── Colosseum                  Einstieg, meldet alles an
├── registry                   Portalblock, Daten am Spieler
├── dimension
│   ├── ColosseumDimension     Schlüssel der Arena
│   ├── ArenaLayout            Alle Maße des Bauwerks
│   ├── ArenaBuilder           Baut Kolosseum und Lobby, steuert Tore
│   └── ArenaProtection        Kein Abbauen, Platzieren, Sprengen
├── portal                     Rahmen erkennen, zünden, reisen
├── player                     Sichern, zurückgeben, Rückkehrpunkt
├── lobby/LobbyController      Knöpfe und Hebel
├── run
│   ├── ArenaRun               Ablauf: Lobby, Tor, Countdown, Welle, Pause, Ende
│   ├── Difficulty             Normal, Schwer, Blutgott
│   └── PlayerClass            Gladiator, Schütze, Seuchen-Doktor
├── wave
│   ├── WaveComposer           Welche Mobs in welcher Runde
│   └── MobFactory             Ausrüstung, Effekte, Jockeys
├── board/Leaderboard          Bestenliste, Datei und Tafel
├── shop                       Kaufstellen, Mystery Box, Perks, Amboss, Punktekonto, Punkte-Shop
├── loot/SupplyChests          Zwei Kisten pro Pause, drei Loot-Stufen
└── reward                     Abholkonto für Gekauftes
```

Der Selbsttest liegt in `selftest` und kommt nicht in die fertige Jar.

## Festgelegte Spielregeln

Diese Entscheidungen gehen vor Konzept und Prompt.

- Belohnung sind Shop-Punkte auf einem festen Konto, unabhängig von den Punkten im Durchlauf: Nach n geschafften Runden sind es 100 * n * (n + 5), mal Schwierigkeitsfaktor. Der Tod in Runde 11 bringt also genau 15.000, und das kosten 30 Level, 16 Diamanten oder ein Netherit-Barren. Mit 15 geschafften Runden sind es 30.000. Gekauft wird im Punkte-Shop an der Südwand der Halle (`shop/PointsShop.java`), die Ware kommt beim Gang durchs Portal ins Inventar. `tools/simulate_player.py` zeigt, was das pro Stunde bedeutet.
- Letzter Tod in der Arena: Der Spieler erscheint in der Halle. Der Ertrag bleibt voll, das eigene Inventar gibt es beim Gang durchs Portal dort.
- Solange der Partner lebt: Der Gefallene verliert seine Arena-Ausrüstung, schaut als Zuschauer zu und kommt zu Beginn der nächsten Runde ohne Ausrüstung und mit halbem Leben zurück. Für die Runde, in der er fiel, bekommt er keinen Rundenbonus. Erst wenn niemand mehr lebt, ist der Durchlauf vorbei.
- Exfil: Das freie Portal nach jeder 5. Runde gibt es nicht mehr. In der Pause nach jeder 5. Runde steht oben auf dem Mittelteil ein Knopf, einer reicht zum Rufen. Dann kommt statt der nächsten Runde eine schwere Exfil-Runde (8 + 2,5 × Runde Mobs mal Spielerfaktor, höchstens 32). Wer sie überlebt und durchs Portal geht, bekommt den Ertrag des Durchlaufs 1,5-fach aufs Konto. Gefallene sind wieder dabei, das Portal brennt 30 Sekunden. Wer nicht hindurchgeht, spielt mit der nächsten Runde weiter.
- Punkte: Kill 50 plus 5 pro Runde (Reiter 100, Spinnen und Ertrunkene 80, Verwüster 300 als Basis), geschaffte Runde 50 plus 10 pro Runde. Sie gelten nur im Durchlauf, was übrig bleibt, verfällt. Gefallene behalten sie. Gekauft wird an 24 Wandknöpfen (`shop/Shop.java`), die Klassen starten bewusst klein.
- Bereiche wie bei CoD: Start im Vorhof, Türen kosten Punkte (Nordhof 750, Säulenhalle 1500, Grube 3000, Abkürzungen je 500, erst wenn beide Seiten offen sind). Mobs kommen nur aus offenen Bereichen. Das Exfil-Tor steht im Vorhof, damit es immer erreichbar ist. Gefallene kommen im Vorhof hinter dem Tor zurück. Die Türen schließen sich mit jedem neuen Durchlauf wieder. Geschlossene Türen halten durch ihre Gitter auf, es gibt keine Teleportation an den Bereichsgrenzen.
- Bestenliste an der Hallenwand: links meiste Runden, rechts meiste Punkte (alle verdienten, auch ausgegebene). Sie liegt in `colosseum_bestenliste.json` im Weltordner.
- Am Boden statt sofort Zuschauer: Wer fällt und einen lebenden Partner hat, liegt 45 Sekunden am Boden (langsam, unverwundbar, kann nichts kaufen). Der Partner rettet ihn, indem er 3 Sekunden daneben schleicht: halbes Leben, Gear bleibt, Retter +200 Punkte. Ohne Rettung gilt die alte Regel: Zuschauer, Gear weg, nächste Runde im Vorhof zurück.
- Power-ups: 4 % pro Kill, 20 Sekunden am Boden, Doppelte Punkte und Ein-Treffer-Kill je 30 Sekunden, Munition, Atombombe (+400 Punkte, trifft keinen Boss). Der Boss lässt immer eins fallen.
- Mystery Box 950 Punkte, zieht nach 6 Benutzungen in einen anderen offenen Bereich um.
- Strom: Schalter in der Grube, gilt bis zum Ende des Durchlaufs. Perks und der Amboss brauchen ihn. Es gibt Zähigkeit, Tempo, Kraft, Scharfschütze, Schnell-Retten, Doppelschlag (nur Nahkampf), Vampir und Zweite Chance (einmal pro Durchlauf, fängt den Tod ab). Man kann höchstens 4 gleichzeitig haben, sie stehen in der Seitenleiste rechts. Der Amboss hat drei Stufen pro Waffe (2500, 5000, 7500): Man wählt im Menü aus bis zu drei zufälligen Verzauberungen, neue kommen auf die halbe Stufe (aufgerundet, Schärfe III), die vorherige Wahl wird voll. Nach der dritten Stufe ist alles auf der Waffe voll. Perks gehen beim Fallen und Verlassen verloren.
- Boss alle 10 Runden zusätzlich: Arenameister, 1000 Punkte plus 50 pro Runde, garantierte Kisten.
- Punkte teilen: Knopf im Vorhof gibt 500 an den Partner mit den wenigsten.
- Skelette pro Welle: zufällig von null bis zur halben Rundenzahl (abgerundet), auch mit zwei Spielern. Reiter auf Spinnen zählen mit, der Rest kommt als Spinne ohne Reiter. Der Boss zählt nicht.
- Spieler dürfen in die Gänge der Mob-Tore. Schließt ein Tor, stehen sie direkt davor im Sand. In die Mitte geholt wird nur, wer über der Mauer oder ganz draußen landet. Enderperlen gibt es nicht.
- Versorgungskisten kommen nur alle 3 Runden, Rüstung macht rund 7 % des Loots aus.
- Alles, was man in der Arena von der Klasse oder aus Kisten bekommt, ist unzerstörbar.
- Arena und Oberwelt haben getrennte Inventare. Ausrüstung aus der Arena bleibt dort und verschwindet beim Verlassen. Mit kommen nur die im Punkte-Shop gekauften Sachen.
- Runde 20 und später: schwere Jockeys laut Konzept (Verwüster mit Reitern, dazu Ertrunkene mit Dreizack). Hühner-Jockeys nur in Runde 4 und 12 und nur wenige (ein Viertel von Rundenzahl plus 5, dazu doppelt so viele Zombies), Spinnen-Jockeys in Runde 8 und 16.

- Mobs der Wellen lassen weder Items noch XP fallen. Bosse haben eine Lebensleiste oben am Bildschirm.
- Alle Texte im Spiel kommen über `Lang.t` aus den Sprachdateien (Englisch und Deutsch), `tools/check_lang.py` prüft sie. Jeder Client zeigt seine eigene Sprache, Standard ist Englisch. Der Name Pack-a-Punch kommt im Spiel nicht vor (Marke von CoD), dort heißt es Arena Anvil.

- Lock-Creeper (1500 Punkte, Vorhof): Rechtsklick wirft ihn. Wo er landet, sitzt 8 Sekunden ein kleiner Creeper, der Mobs im Umkreis von 16 Blöcken anlockt. Er ist halb so groß wie ein normaler Creeper, leuchtet nicht, tippelt auf der Stelle und lässt sich nicht wegschieben. Dann explodiert er: 22,5 Schaden an Mobs im Umkreis von 5 Blöcken, keine Spieler, keine Blöcke, die Punkte gehen an den Werfer.

- Leere Flaschen bleiben nach dem Trinken in der Arena nicht im Inventar.
- Cheat-Menü: `/colosseum debug`, nur für Operatoren und nur in der Arena. Durchläufe mit Cheats kommen nicht auf die Bestenliste.

## Technische Regeln

- Sicherung und Inventar liegen in derselben Spielerdatei und werden zusammen gespeichert.
- Der Durchlauf lebt nur im Speicher. Nach einem Neustart beginnt alles in der Lobby, der Ertrag des Durchlaufs kommt beim nächsten Anmelden aufs Konto.
- Es gibt einen einzigen Prüfpunkt für alle Wege (Portal, `/tp`, Tod, Neuanmeldung), siehe `ArenaTransfer`.
- Nur Spieler reisen. Items und Mobs können nichts aus der Arena schmuggeln.
- Vor einem Minecraft-Update müssen alle Spieler die Arena verlassen haben. Die Sicherung wird beim Update nicht mitkonvertiert.

## Bauen und testen

```sh
rm -rf run/world          # frische Testwelt
./gradlew runServer       # Selbsttest, Ergebnis in den Zeilen mit COLTEST
./gradlew build           # Jar in build/libs
```

Das Hineinlaufen ins Portal ersetzt der Selbsttest durch den Aufruf derselben Block-Methode.
Aussehen, Himmel und echtes Laufen lassen sich nur im Spiel prüfen.
