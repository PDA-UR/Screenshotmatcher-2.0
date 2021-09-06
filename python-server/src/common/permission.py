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

def request_permission_for_device(device_id, device_name, queue):
    global prompt_open
    if prompt_open: # prevent multiple popups
        return ""
    user_input = ""

    # put a command into the queue. will be called by App
    queue.put_nowait(
        ("permission_request", (device_name, device_id))
    )

    # wait for a response.
    # put the item back last in the queue if it is not what we need
    while True:
        if queue.empty():
            continue

        # prevent going out of range, when another thread pops the item at indexing time
        try:
            last_item_key = queue.queue[0][0]
        except IndexError:
            continue
        if last_item_key == "permission_response":
            item = queue.get_nowait()
            user_input = item[1]
            break

    # white-/blacklist if necessary
    if user_input == "allow":
        add_device_to_list(device_id, "whitelist.txt")
    elif user_input == "block":
        add_device_to_list(device_id, "blacklist.txt")

    prompt_open = False
    return user_input

def on_permission_response(user_input):
    if user_input == "allow":
        add_device_to_list(device_id, "whitelist.txt")
    elif user_input == "block":
        add_device_to_list(device_id, "blacklist.txt")