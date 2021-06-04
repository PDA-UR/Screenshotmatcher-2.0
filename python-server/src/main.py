#!/usr/bin/python3
import sys
import psutil
import threading
import ctypes

import gui.tray
import server.server_discovery as discovery
from common.config import Config
from common.utility import set_participant_id
from matching.matcher import Matcher
from server.server import Server

def main():
    # Set the participant ID
    Config.PARTICIPANT_ID = set_participant_id()
    
    # Init Server
    server = Server()
    
    # Init Tray
    tray = gui.tray.Tray(Config.APP_NAME)
    
    # Start server in different thread
    x = threading.Thread(target=server.start, args=(), daemon=True)
    x.start()
    
    # Start server discovery via UDP socket in different thread
    udp_t = threading.Thread(target=discovery.start, args=(), daemon=True)
    udp_t.start()
    
    # Start Tray event loop
    tray.run()

if __name__ == "__main__":
    # program already running. abort.
    procs = [p for p in psutil.process_iter() if ('python.exe' in p.name() and __file__ in p.cmdline()) or 'Screenshotmatcher.exe' in p.name()]
    if len(procs) > 1:
        # EXE
        ctypes.windll.user32.MessageBoxW(0, "Screenshotmatcher.exe is already running.", "Screenshotmatcher.exe", 0)
        # otherwise
        print("Screenshotmatcher.py is already running.")
        sys.exit(1)

    main()
