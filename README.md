# TheSiftPaperPlugin

**Benötigt:** Paper 26.3 (getestet mit Build 227), Java 25. Jede Version des Plugins ist für genau
eine Minecraft-Version gebaut; auf anderen Versionen bleibt es mit einer Meldung im Log deaktiviert.

Paper-Plugin und Server-Resource-Pack für **Lama Kingdom** (LamaMC), das
*Mielon's The Sift* nachbildet: eigene Dimension, Siftit-Ausrüstung, Erze, Portale.

Inoffiziell. Das Original ist die Fabric-Mod *Mielon's The Sift* von Mielon (MIT).
Quellbasis der Referenz: [Arta48/Mielons-The-Sift](https://github.com/Arta48/Mielons-The-Sift).
Dieses Projekt ist keine Portierung, sondern eine Neuumsetzung für Paper
(ohne Client-Mod, mit Resource Pack). Texturen, Modelle, Sounds und Daten der
Original-Mod werden unter der MIT-Lizenz mit Namensnennung wiederverwendet.

Status: Phase 1 in Arbeit (Dimension, eigene Blöcke und Resource Pack laufen).

## Aufbau

- `packgen/`: Import-Tool. Liest das Jar der Original-Mod und erzeugt `pack-src/`
  (Datapack mit Dimension und Worldgen, Assets für das Resource Pack). Eigene Blöcke werden dabei
  auf ungenutzte Vanilla-Blockzustände abgebildet (`packgen/src/main/resources/block-mapping.yml`).
  Neu importieren: `./gradlew :packgen:importSift` (optional `-PsiftJar=<pfad>`).
- `pack-src/`: generierte Pack-Quellen, eingecheckt; `IMPORT-REPORT.md` listet, was entfallen ist.
  `resourcepack-overlay/` enthält Handgeschriebenes (z. B. `de_de.json`).
- `./gradlew :packgen:resourcePack`: baut das Resource Pack (`packgen/build/pack/`) samt SHA-1.
- `plugin/`: Paper-Plugin. Liefert das Datapack aus und setzt die Welt-Config der Sift.
- `docs/ADMIN.md`: Installation, Konfiguration, Befehle.

## Bauen und testen

```
scripts/setup-jdk.sh
./gradlew build
./gradlew :plugin:runServer
```
