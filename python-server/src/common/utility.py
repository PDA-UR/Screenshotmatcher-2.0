import time
import socket
import os
import tkinter as tk
from tkinter import simpledialog, messagebox

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

def set_participant_id():
    if not os.path.isfile("pid.txt"):
        with open("pid.txt", "w+") as f:
            pass

    with open("pid.txt", "r") as f:
        _id = f.readline()
        if _id:
            try:
                int(_id)
                return _id
            except ValueError:
                pass

    _input = ask_for_id()
    if not _input:
        return None
    
    with open("pid.txt", "w") as f:
        f.write(str(_input))
        return _input

def ask_for_id():
    ROOT = tk.Tk()
    ROOT.withdraw()
    _input = simpledialog.askinteger(
        title="Input ID",
        prompt="Please enter your participant ID:"
    )
    if not _input:
        messagebox.showerror("Entry error", "No participant ID entered. Closing.")  

    return _input

def is_device_allowed(current_setting, MAC_addr, device_name):
    if current_setting == 1:    # allow all
        return True
    if current_setting == 2:    # block all
        return False
    if is_device_in_list(MAC_addr, "whitelist.txt"):
        return True
    if is_device_in_list(MAC_addr, "blacklist.txt"):
        return False
    if current_setting == 0:    # ask permission
        allowed = request_permission_for_device(MAC_addr, device_name)
        return allowed
    

def is_device_in_list(MAC_addr, filename):
    if not os.path.isfile(filename):
        return False

    with open(filename, "w+") as f:
        for line in f.readlines():
            if line == MAC_addr:
                return True
        return False

def add_device_to_list(MAC_addr, filename, user_response):
    if not os.path.isfile(filename):
        with open(filename, "w+") as f:
            pass
    
    with open(filename, "a+") as f:
        f.write(MAC_addr + "\n")

    if filename == "whitelist.txt":
        user_response = "allow"
    elif filename == "blacklist.txt":
        user_response = "block"

def request_permission_for_device(MAC_addr, device_name):
    user_response = ""
    prompt_text = "The device {} is asking for permission to connect to ScreenshotMatcher.\n (MAC address: {})".format(device_name, MAC_addr)
    ROOT = tk.Tk()

    label = tk.Label(
        ROOT,
        text=prompt_text
    )
    btn_allow = tk.Button(
        ROOT,
        text="Allow",
        command=add_device_to_list(MAC_addr, "whitelist.txt", user_response)
    )
    btn_allow_once = tk.Button(
        ROOT,
        text="Allow once",
        command=set_user_response(user_response, "allow once")
    )
    btn_block = tk.Button(
        ROOT,
        text="Block",
        command=add_device_to_list(MAC_addr, "blacklist.txt", user_response)
    )

    label.pack(side="TOP")
    btn_allow.pack(side="LEFT")
    btn_allow_once.pack()
    btn_block.pack(side="RIGHT")

    ROOT.mainloop()