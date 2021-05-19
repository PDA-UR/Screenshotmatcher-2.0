#!/usr/bin/python3
# -*- coding: utf-8 -*-

# Import third party modules
import threading

# Import app config
from common.config import Config

# Import app parts
import gui.tray
import server.server_discovery as discovery
from matching.matcher import Matcher
from server.server import Server

def main():
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
    main()
