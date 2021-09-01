from PIL import Image, ImageDraw
from pystray import Icon, Menu as menu, MenuItem as item
from io import BytesIO
import os
import subprocess
import platform
import base64
import common.utility
import webbrowser
import PySimpleGUIWx as sg
from common.config import Config

class OldTray():
    def __init__(self, app_name):
        self.app_name = app_name

        self.icon = Icon(
            self.app_name,
            icon=self.get_icon(),
            menu=menu(
                item(
                    'Enable Full Screenshots',
                    lambda item: self.toggle_full_screenshots_enabled(),
                    checked= lambda item: Config.FULL_SCREENSHOTS_ENABLED
                ),
                menu.SEPARATOR,
                item(
                    'Unknown device requests',
                    lambda item: None,
                    enabled=False
                ),
                item(
                    'Decide individually',  # 0
                    lambda item: self.set_unknown_device_handling(0),
                    checked= lambda item:self.get_unknown_device_handling(0),
                    radio=True
                ),
                item(
                    'Allow all',            # 1
                    lambda item: self.set_unknown_device_handling(1),
                    checked= lambda item:self.get_unknown_device_handling(1),
                    radio=True
                ),
                item(
                    'Block all',            # 2
                    lambda item: self.set_unknown_device_handling(2),
                    checked= lambda item:self.get_unknown_device_handling(2),
                    radio=True
                ),
                menu.SEPARATOR,
                item(
                    'Quit',
                    lambda icon: self.onclick_quit()
                )
            )
        )

    def toggle_full_screenshots_enabled(self):
        Config.FULL_SCREENSHOTS_ENABLED = not Config.FULL_SCREENSHOTS_ENABLED

    def set_unknown_device_handling(self, value):
        Config.UNKNOWN_DEVICE_HANDLING = value

    def get_unknown_device_handling(self, value):
        return value == Config.UNKNOWN_DEVICE_HANDLING

    def get_icon(self):
        return Image.open(BytesIO(base64.b64decode(self.get_app_icon())))

    def run(self):
        self.icon.run(self.setup)

    def onclick_quit(self):
        self.icon.stop()

    def setup(self, icon):
        self.icon.visible = True

    def get_app_icon(self):
        return """
            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
        """

class Tray(sg.SystemTray):
    def __init__(self):
        self.menu = [
            "menu",
            [
                "About",
                "Settings",
                "---",
                'Exit'
            ]
        ]
        self.data_base64 = get_app_icon().encode("utf-8")
        super().__init__(menu=self.menu, data_base64=get_app_icon())

def get_app_icon():
    return """
        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
    """

class SettingsWindow(sg.Window):
    def __init__(self):
        self.title = "Settings"
        self.key_allow_fullscreen = "CB_ALLOW_FULLSCR"
        self.key_unknown_devices = "RADIO_UNK_DEV"
        self.layout = [
            [
                sg.Checkbox("Allow fullscreen screenshots",
                enable_events=True, key=self.key_allow_fullscreen,
                size=(24,1),
                tooltip="Clients can request an image of your entire screen(s)")
            ],
            [sg.Frame(
                layout= [[
                    sg.Radio("Ask each time", group_id=self.key_unknown_devices, size=(12,1), default=True),
                    sg.Radio("Allow all", group_id=self.key_unknown_devices, size=(12,1)),
                    sg.Radio("Block all", group_id=self.key_unknown_devices, size=(12,1))
                ]],
                title="Requests from unknown devices",
                tooltip="How to handle requests sent by phone applications unknown to this computer."
            )],
            [sg.Button('Ok'), sg.Button('Cancel')] 
        ]
        self.icon = get_app_icon().encode("utf-8")
        super().__init__(title=self.title, layout=self.layout, icon=self.icon)

class AboutWindow(sg.Window):
    def __init__(self):
        self.title = "About ScreenshotMatcher"
        self.icon = get_app_icon().encode("utf-8")
        self.layout = [
            [sg.Text(
                text=Config.APP_NAME,
                font=("Helvetica", 12))],
            [sg.Text(
                text=("Version: " + Config.APP_VERSION),
                font=("Helvetica", 8))],
            [sg.Text(
                text="Homepage",
                size=(10,1),
                font=("Consolas", 10, "underline"),
                text_color = "cyan",
                enable_events=True,
                key="homepage clicked",
                tooltip=Config.HOMEPAGE)]
        ]
        super().__init__(title=self.title, layout=self.layout, icon=self.icon)

class App():
    def __init__(self, queue=None):
        self.queue = queue

        self.icon = get_app_icon().encode("utf-8")
        self.tray = Tray()

    def main_loop(self):
        while True:
            tray_event = self.tray.read()

            if tray_event == "About":
                self.open_about()
            if tray_event == "Settings":
                self.open_settings()
            if tray_event == "Exit":
                break

    def open_settings(self):
        settings_window = SettingsWindow()
        while True:
            settings_event, settings_values = settings_window.read()
            if settings_event in [sg.WIN_CLOSED, "Cancel"]:
                settings_window.close()
                break
            elif settings_event == "Ok":
                settings_window.close()
                break

    def open_about(self):
        about_window = AboutWindow()
        while True:
            event, value = about_window.read()
            if event == sg.WIN_CLOSED:
                about_window.close()
                break
            if event == "homepage clicked":
                webbrowser.open(Config.HOMEPAGE)

    def ask_for_id(self):
        _id = sg.popup_get_text(
            title="ScreenshotMatcher prompt",
            message="Please enter your participant ID.",
            icon=self.icon
        )
        try:
            int(_id)
            return _id
        except ValueError:
            return None