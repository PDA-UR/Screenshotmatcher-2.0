#!/usr/bin/python3
import sys
import psutil
import threading
import ctypes
import queue
import server.server_discovery as discovery
from common.config import Config
from common.utility import read_user_config, get_current_ip_address
from matching.matcher import Matcher
from server.server import Server
from gui.app import App

def main():
    # Queue used for cross-thread communication. Only put (key, value) tuples in there.
    app_queue = queue.PriorityQueue()

    # Read user settings
    read_user_config()

    # Set the current host IP
    Config.HOST = get_current_ip_address()
    
    # Init Server
    server = Server(queue=app_queue)
    
    # Start server in different thread
    x = threading.Thread(target=server.start, args=(), daemon=True)
    x.start()
    
    # Start server discovery via UDP socket in different thread
    udp_t = threading.Thread(target=discovery.start, args=(), daemon=True)
    udp_t.start()
    
    # Start the GUI
    app = App(queue=app_queue)
    app.main_loop()

if __name__ == "__main__":
    # program already running. abort.
    # check causes issues with virtual environments.
    procs = [p for p in psutil.process_iter() if ('python.exe' in p.name() and __file__ in p.cmdline()) or 'Screenshotmatcher.exe' in p.name()]
    if len(procs) > 1:
        # EXE
        ctypes.windll.user32.MessageBoxW(0, "Screenshotmatcher.exe is already running.", "Screenshotmatcher.exe", 0)
        # otherwise
        print("Screenshotmatcher.py is already running.")
        sys.exit(1)

    main()
