import time
import socket
import os
import uuid
import tkinter as tk
from common.config import Config
from configparser import ConfigParser

def get_current_ms():
    return round(time.time() * 1000)

def get_current_ip_address():
    s_ip = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)     # socket for finding local network IP
    s_ip.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # reuse address if already in use
    ip = ''
    try:
        s_ip.connect(('10.255.255.255', 1))
        ip = s_ip.getsockname()[0]
    except Exception as e:
        print('Exception caught while trying to determine server IP')
        print(e)
    finally:
        s_ip.close()
    return ip

# Read the file containing user settings
# Create with default values, if no file exists yet
def read_user_config():
    if not os.path.isfile("config.ini"):
        with open("config.ini", "w") as f:
            pass

    config = ConfigParser()
    config.read("config.ini")
    if not config.has_section("main"):
        config.add_section("main")

    # ID
    if config.has_option("main", "uuid"):
        _id = config.get("main", "uuid")
    else:
        _id = str(uuid.uuid4())
        config.set("main", "uuid", _id)
    Config.ID = _id
 
    # Unknown devices
    if config.has_option("main", "unknown_device_handling"):
        Config.UNKNOWN_DEVICE_HANDLING = config.getint("main", "unknown_device_handling")
    else:
        config.set("main", "unknown_device_handling", "0")
    
    # Full screenshots
    if config.has_option("main", "FULL_SCREENSHOTS_ENABLED"):
        Config.FULL_SCREENSHOTS_ENABLED = config.getboolean("main", "FULL_SCREENSHOTS_ENABLED")
    else:
        config.set("main", "FULL_SCREENSHOTS_ENABLED", "0")

    # Save
    with open("config.ini", "w") as f:
        config.write(f)

def set_user_config_option(option, value):
    config = ConfigParser()
    config.read("config.ini")
    config.set("main", option, value)
    with open("config.ini", "w") as f:
        config.write(f)

def update_user_config():
    config = ConfigParser()
    config.read("config.ini")
    config.set("main", "unknown_device_handling", str(Config.UNKNOWN_DEVICE_HANDLING))
    config.set("main", "FULL_SCREENSHOTS_ENABLED", str(Config.FULL_SCREENSHOTS_ENABLED))
    with open("config.ini", "w") as f:
        config.write(f)