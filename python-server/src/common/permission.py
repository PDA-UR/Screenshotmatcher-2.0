import os
import time
import threading
import uuid
import PySimpleGUI as sg

prompt_open = False
user_response = ""
permission_tokens = []
TOKEN_TIMEOUT_S = 60

def is_device_allowed(current_setting, device_id, device_name, token):
    if current_setting == 1:    # allow all
        return 1
    if current_setting == 2:    # block all
        return -1
    if is_device_in_list(device_id, "whitelist.txt"):
        return 1
    if is_device_in_list(device_id, "blacklist.txt"):
        return -1
    if token in permission_tokens:
        permission_tokens.remove(token)
        return 1
    return 0   # client needs to request permission

def is_device_in_list(device_id, filename):
    if not os.path.isfile(filename):
        return False

    with open(filename, "r") as f:
        for line in f.readlines():
            if line[:-1] == device_id:  # remove \n for comparison
                return True
        return False

def add_device_to_list(device_id, filename):
    if not os.path.isfile(filename):
        with open(filename, "w+") as f:
            f.write("# File must end with an empty line")
            f.write("\n")
    
    with open(filename, "a+") as f:
        f.write(device_id)
        f.write("\n")

def set_user_response(val):
    global user_response, prompt_open
    user_response = val
    prompt_open = False

def create_single_match_token():
    token = uuid.uuid4().hex
    global permission_tokens
    permission_tokens.append(token)
    t_token = threading.Thread(target=set_token_timeout, args=(token, ))
    t_token.start()
    return token

# remove the token after a certain amount of time has passed
def set_token_timeout(token):
    global permission_tokens
    time.sleep(TOKEN_TIMEOUT_S)
    try:
        permission_tokens.remove(token)
    except ValueError:
        pass

def request_permission_for_device(device_id, device_name):
    global prompt_open
    user_response = ""
    if prompt_open: # prevent multiple popups
        return ""

    prompt_text = "The device \"{}\" is asking for permission to connect to ScreenshotMatcher.\n(ID: {})".format(device_name, device_id)
    prompt_open = True

    layout = [
        [sg.Text(prompt_text)],
        [sg.Button('Allow'), sg.Button("Allow Once"), sg.Button("Block")],
    ]

    window = sg.Window('ScreenshotMatcher Permission Request', layout)
                                                    
    # Display and interact with the window
    while True:
        event, values = window.read()
        if event == sg.WINDOW_CLOSED:
            break
        elif event == "Allow":
            user_response = "allow"
            break
        elif event == "Allow Once":
            user_response = "allow once"
            break
        elif event == "Block":
            user_response = "block"
            break

    window.close()
    prompt_open = False

    return user_response