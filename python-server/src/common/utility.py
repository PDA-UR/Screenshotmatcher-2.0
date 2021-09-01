import time
import socket
import os
import uuid
import tkinter as tk

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

def get_id():
    if not os.path.isfile("uuid.txt"):
        with open("uuid.txt", "w+") as f:
            _id = str(uuid.uuid4())
            f.write(_id)
            return _id

    with open("uuid.txt", "r") as f:
        _id = f.readline()
        print(_id)
        if _id:
            try:
                uuid.UUID(_id, version=4)
                return _id
            except ValueError:
                return None

    # _input = ask_for_id()
    # if not _input:
    #     return None
    
    # with open("pid.txt", "w") as f:
    #     f.write(str(_input))
    #     return _input