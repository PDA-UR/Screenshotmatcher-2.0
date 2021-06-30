import time
import socket
import os
import tkinter as tk
import tkinter.ttk as ttk

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
    _input = tk.simpledialog.askinteger(
        title="Input ID",
        prompt="Please enter your participant ID:"
    )
    if not _input:
        tk.messagebox.showerror("Entry error", "No participant ID entered. Closing.")  

    return _input

def is_device_allowed(current_setting, device_id, device_name):
    if current_setting == 1:    # allow all
        return True
    if current_setting == 2:    # block all
        return False
    if is_device_in_list(device_id, "whitelist.txt"):
        return True
    if is_device_in_list(device_id, "blacklist.txt"):
        return False
    if current_setting == 0:    # ask permission
        allowed = request_permission_for_device(device_id, device_name)
        return allowed
    

def is_device_in_list(device_id, filename):
    if not os.path.isfile(filename):
        return False

    with open(filename, "w+") as f:
        for line in f.readlines():
            if line == device_id:
                return True
        return False

def add_device_to_list(device_id, filename, user_response):
    if not os.path.isfile(filename):
        with open(filename, "w+") as f:
            pass
    
    with open(filename, "a+") as f:
        f.write(device_id + "\n")

    if filename == "whitelist.txt":
        user_response = "allow"
    elif filename == "blacklist.txt":
        user_response = "block"

def set_user_response(var, val):
    var = val

def request_permission_for_device(device_id, device_name):
    user_response = ""
    prompt_text = "The device \"{}\" is asking for permission to connect to ScreenshotMatcher.\n(ID: {})".format(device_name, device_id)
    ROOT = tk.Tk()

    style = ttk.Style()
    style.theme_use("vista")
    style.configure(
        "Label.TLabel",
        font=("Arial", 10),
        background="#f0f0f0"

    )
    style.configure(
        "Frame.TFrame",
        background="#f0f0f0"
    )
    style.configure(
        "Button.TButton",
        font=("Arial", 9),
        background="#f0f0f0"
    )

    label_frame = ttk.Frame(
        ROOT,
        style="Frame.TFrame"
    )
    label = ttk.Label(
        label_frame,
        text=prompt_text,
        style="Label.TLabel"
    )

    button_frame = ttk.Frame(
        ROOT,
        style="Frame.TFrame"
    )
    btn_allow = ttk.Button(
        button_frame,
        text="Allow",
        command=add_device_to_list(device_id, "whitelist.txt", user_response),
        style="Button.TButton"
    )
    btn_allow_once = ttk.Button(
        button_frame,
        text="Allow once",
        command=set_user_response(user_response, "allow once"),
        style="Button.TButton"
    )
    btn_block = ttk.Button(
        button_frame,
        text="Block",
        command=add_device_to_list(device_id, "blacklist.txt", user_response),
        style="Button.TButton"
    )

    label_frame.pack(fill="both", padx=10, pady=5)
    button_frame.pack(fill="both", anchor="center")
    label.pack()
    btn_allow.pack(side="left", anchor="center", padx=5, pady=5)
    btn_allow_once.pack(side="left", anchor="center", padx=5)
    btn_block.pack(side="left", anchor="center", padx=5)

    ROOT.mainloop()
