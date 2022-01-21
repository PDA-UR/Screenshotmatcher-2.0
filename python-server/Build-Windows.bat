@echo off
pip install pyinstaller
pip install -r .\requirements.txt

pyinstaller ./src/main.py --window --onedir --name Screenshotmatcher --icon="./res/tray_icon.ico"