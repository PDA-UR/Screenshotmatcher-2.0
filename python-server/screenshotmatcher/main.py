#!/usr/bin/python3
# -*- coding: utf-8 -*-

# Import third party modules
import threading
import logging
import time

# Import app config
from common.config import Config

# Import app parts
import gui.tray
from common.utils import print_banner
from matching.matcher import Matcher
from server.server import Server

def main():
  # Init Server
  server = Server()

  # Init Tray
  tray = gui.tray.Tray(Config.APP_NAME)

  # Print Banner
  print_banner( Config.APP_NAME, Config.CURRENT_VERSION, Config.IDENTIFIER )

  # Start server in different thread
  x = threading.Thread(target=server.start, args=(), daemon=True)
  x.start()

  # Start Tray event loop
  tray.run()

main()