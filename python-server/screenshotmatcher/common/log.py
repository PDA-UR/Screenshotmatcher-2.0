import requests
from common.config import Config

class Logger():
    def __init__(self):
        self.value_pairs = {}

    def save_log(self):
        # TODO
        pass

    def send_log(self):
        print('sending log data:')
        print(self.value_pairs)
        requests.post(url=Config.LOG_HOST, json=self.value_pairs, verify=False)

# function decorator to log time needed to execute a function
# seems better than the log singleton
# has to be adjusted to work with the current infrastructure
# usage:
#
# @log_time('save_screenshot')
# def save_screenshot():
#     do_stuff()
#
def log_time(key):
    def decorator(function):
        def wrapper(*args, **kwargs):
            #log.value_pairs['{}_start'.format(key)] = get_current_ms()
            print('{}_start'.format(key))
            result = function(*args, **kwargs)
            #log.value_pairs['{}_end'.format(key)] = get_current_ms()
            print('{}_end'.format(key))
        return wrapper
    return decorator
