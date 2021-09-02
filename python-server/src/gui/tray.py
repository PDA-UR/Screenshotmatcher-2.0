from PIL import Image, ImageDraw
from pystray import Icon, Menu as menu, MenuItem as item
from io import BytesIO
import os
import subprocess
import platform
import base64
import common.utility
import webbrowser
import PySimpleGUIWx as sgwx
import PySimpleGUI as sg
from common.config import Config

sg.theme("Material2")
KEY_ALLOW_FULLSCREEN = "CB_ALLOW_FULLSCR"
KEY_UNKNOWN_DEVICE = "RADIO_UNK_DEV"

class Tray(sgwx.SystemTray):
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
        super().__init__(menu=self.menu)


class MainWindow(sg.Window):
    def __init__(self):
        self.title = "Screenshot Matcher"
        self.menu = self.sys_specific_menu()
        self.layout = [
            [self.menu],
            [sg.Text(
                "Settings",
                size=(14,1),
                font=("Arial", 12, "underline"))],
            [sg.Checkbox(
                "Allow fullscreen screenshots",
                enable_events=True,
                key=KEY_ALLOW_FULLSCREEN,
                default=Config.FULL_SCREENSHOTS_ENABLED,
                size=(24,1),
                tooltip="Clients can request an image of your entire screen(s)"
            )],
            [sg.Frame(
                layout= [[
                    sg.Radio(
                        "Ask each time",
                        group_id=KEY_UNKNOWN_DEVICE,
                        key="UKD_ASK",
                        size=(12,1),
                        default= (Config.UNKNOWN_DEVICE_HANDLING == 0)),
                    sg.Radio(
                        "Allow all",
                        group_id=KEY_UNKNOWN_DEVICE,
                        key="UKD_ALLOW",
                        size=(12,1),
                        default= (Config.UNKNOWN_DEVICE_HANDLING == 1)),
                    sg.Radio(
                        "Block all",
                        group_id=KEY_UNKNOWN_DEVICE,
                        key="UKD_BLOCK",
                        size=(12,1),
                        default= (Config.UNKNOWN_DEVICE_HANDLING == 2)),
                ]],
                title="Requests from unknown devices",
                tooltip="How to handle requests sent by phone applications unknown to this computer.")],
            [sg.Button('Ok'), sg.Button('Cancel')] 
        ]
        self.icon = get_app_icon().encode("utf-8")
        super().__init__(title=self.title, layout=self.layout, icon=self.icon)

    def sys_specific_menu(self):
        if platform.system() == "Windows":
            return sg.Menu(
                menu_definition=[["File", ["About","Exit"]]],
                background_color="snow",    # Menu does not use the theme and gets a blue background
                text_color="black",
                font=("Arial", 10))
        else:
            return sg.Menu(
                menu_definition=[["File", ["About","Exit"]]],
                font=("Arial", 10))

class AboutWindow(sg.Window):
    def __init__(self):
        self.title = "About ScreenshotMatcher"
        self.icon = get_app_icon().encode("utf-8")
        self.layout = [
            [sg.Text(
                text=Config.APP_NAME,
                font=("Arial", 12))],
            [sg.Text(
                text=("Version: " + Config.APP_VERSION),
                font=("Arial", 8))],
            [sg.Text(
                text="Homepage",
                size=(10,1),
                font=("Consolas", 10, "underline"),
                text_color = "blue",
                enable_events=True,
                key="homepage clicked",
                tooltip=Config.HOMEPAGE)]
        ]
        super().__init__(title=self.title, layout=self.layout, icon=self.icon)


class App():
    def __init__(self, queue):
        self.queue = queue
        self.icon = get_app_icon().encode("utf-8")

    def main_loop(self):
        self.tray = Tray()
        while True:
            tray_event = self.tray.read()
            if tray_event == "__ACTIVATED__":
                self.open_main_window()

            if not self.queue.empty():
                item = self.queue.get(block=False)
                if item == "SHUTDOWN_APPLICATION":
                    break

    def open_main_window(self):
        window = MainWindow()
        while True:
            event, values = window.read()
            if event in [sg.WIN_CLOSED, "Cancel"]:
                window.close()
                break
            elif event == "About":
                self.open_about()
            elif event == "Ok":
                if values["UKD_ALLOW"]:
                    Config.UNKNOWN_DEVICE_HANDLING = 1
                elif values["UKD_BLOCK"]:
                    Config.UNKNOWN_DEVICE_HANDLING = 2
                else:   # Default: Ask
                    Config.UNKNOWN_DEVICE_HANDLING = 0

                Config.FULL_SCREENSHOTS_ENABLED = values[KEY_ALLOW_FULLSCREEN]
                common.utility.update_user_config()
                window.close()
                break
            elif event == "Exit":
                self.queue.put("SHUTDOWN_APPLICATION")
                window.close()
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


def get_app_icon():
    return """
        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
    """