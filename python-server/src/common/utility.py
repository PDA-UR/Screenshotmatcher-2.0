import time
import socket
import os
import threading
import tkinter as tk
import tkinter.simpledialog

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
                return int(_id)
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
    _input = tkinter.simpledialog.askinteger(
        title="Input ID",
        prompt="Please enter your participant ID:"
    )
    if not _input:
        tk.messagebox.showerror("Entry error", "No participant ID entered. Closing.")  

    return _input

# https://stackoverflow.com/a/48741004
class RepeatTimer(threading.Timer):
    def run(self):
        while not self.finished.wait(self.interval):
            self.function(*self.args, **self.kwargs)
