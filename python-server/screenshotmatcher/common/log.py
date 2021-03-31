import requests
from common.config import Config

class Logger():
    def __init__(self):
        self.value_pairs = {}

    def save_log(self):
        # TODO
        pass

    def send_log(self):
        requests.post(url=Config.LOG_HOST, json=self.value_pairs, verify=False)
