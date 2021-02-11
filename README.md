# Aktueller Stand

 * Screenshot erstellen indem man Bildschirm fotografiert
 * BA Hartmann: App + Server
   * App: Expo-Framework (React-basiert, Cross-Platform)
   * Server: Python, läuft als "daemon" (auch Cross-Platform!)
   * Kommunikation: HTTP
   * Authentifizierung: QR-Code

# Probleme

 * ist noch viel langsamer als es sein sollte (liegt vermutlich nicht am Matching!)
 * Authentifizierung sollte langfristig implizit passieren
 * bisher: beim Wechseln des Wifi-Netzwerks ist neue Authentifizierung nötig
 * Server läuft in einem Terminal, das sollte verschwinden
 * Installation relativ umständlich
 * Logging-Prozess nur serverseitig

# Baustellen

 * Zeiten anständig loggen (auch von der aktuellen Version) -> vermutlich muss das noch ins Paper
 * Latenz verringern
   * wo ist das Bottleneck?
   * geht TCP schneller als HTTP?
   * kann man über eine native App was rausholen?
   * Ziel: < 1 Sekunde
 * App überarbeiten/neu implementieren
   * etwas hübscher machen
   * mehr Optionen zum Logging (auch im Hinblick auf eventuelle Studie)
   * ggf Portierung auf TCP
   * modular gestalten und gut dokumentieren: Vermutlich werden andere Anwendungen darauf aufgebaut (zB Videostream)
 * Authentifizierung verbessern
   * Nutzer sollte im Idealfall nix mitbekommen
   * Authentifizierung anhand von Gerät, nicht IP
   * Beispiel: KDEconnect
   * ganz verrückte Idee: Integration in KDEconnect?
 * Server soll komplett im Hintergrund laufen
 * Installationsprozess verbessern

# Vorgehen

 * Termine:
   * 18.02. Acceptance Notification
   * 25.02. Camera-Ready-Deadline
 * Besprechung (machen wir gerade)
 * Repo erstellen und Issues anlegen (mache ich, hoffentlich heute)
 * TF und AW haben genug Studen (AW ab 18. mehr Zeit)
