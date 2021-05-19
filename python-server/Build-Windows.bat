@echo off
pip install pyinstaller

pyinstaller ./src/main.py --window --onedir --name Screenshotmatcher --icon="./res/tray_icon.ico"