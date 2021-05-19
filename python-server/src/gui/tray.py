from PIL import Image, ImageDraw
from pystray import Icon, Menu as menu, MenuItem as item
from io import BytesIO
import os
import subprocess
import platform
import base64
import common.utils
from common.config import Config

class Tray():
    def __init__(self, app_name):
        self.app_name = app_name

        self.ALGORITHM_STATE = Config.CURRENT_ALGORITHM

        self.icon = Icon(
            self.app_name,
            icon=self.get_icon(),
            menu=menu(
                # item(
                #     'Show QR code',
                #     lambda icon: self.onclick_qr()),
                item(
                    'Open Results Dir',
                    lambda icon: self.onclick_results()),
                menu.SEPARATOR,
                item(
                    'Matching Algorithm',
                    lambda icon: None,
                    enabled=False),
                item(
                    'SURF (Precise)',
                    self.set_state('SURF'),
                    checked=self.get_state('SURF'),
                    radio=True),
                item(
                    'ORB (Fast)',
                    self.set_state('ORB'),
                    checked=self.get_state('ORB'),
                    radio=True),
                menu.SEPARATOR,
                item(
                    'Quit',
                    lambda icon: self.onclick_quit())))

    def set_state(self, v):
        def inner(icon, item):
            self.ALGORITHM_STATE = v
            print('Switching Algorithm to %s' % self.ALGORITHM_STATE)
            Config.CURRENT_ALGORITHM = self.ALGORITHM_STATE
        return inner

    def get_state(self, v):
        def inner(item):
            return self.ALGORITHM_STATE == v
        return inner

    def get_icon(self):
        return Image.open(Config.ICON_PATH)

    def run(self):
        self.icon.run(self.setup)

    def onclick_quit(self):
        self.icon.stop()

    def setup(self, icon):
        self.icon.visible = True
