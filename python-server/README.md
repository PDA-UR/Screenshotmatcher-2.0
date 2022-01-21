# screenshotmatcher-py

Running on:
Python 3.6/3.7      (Windows)
Python 3.6/3.7/3.8  (Unix)

Does not work with Python 3.8 on Windows due to missing pip dependencies

**Can not be run in a virtual python environment!**

## Install dependencies

### Guided
Just execute the provided install scripts for your OS:
```sh
Install-Windows.bat     # For Windows
Install-Linux.sh        # For Unix
```
### Manual
You can also install the dependencies the manual way with pip:
```sh
pip install -r requirements.txt

# UNIX ONLY: You may need to install tkinter depending on your system
sudo apt-get install python3-tk
```

## Running

### Guided
The application can be started in a similar way to the install script by running the script corresponding to your OS:
```sh
Start-Windows.bat     # For Windows
Start-Linux.sh        # For Unix
```

### Manual
You can also run the main script by yourself:
```sh
cd screenshotmatcher
python3 main.py
```

### Autostart
It is recommended to make the application start as soon as you login to your computer.

#### Autostart on Windows

**Manual:**
1. Create a shortcut to "Start-Windows.bat" (Right-Click -> Send to.. -> Create Shortcut)
2. Move the shortcut to "%AppData%\Roaming\Microsoft\Windows\Start Menu\Programs\Autostart"

**Guided:**
1.  Execute "Autostart-Windows.bat"

#### Autostart on Linux

WIP

## Usage

Starting the application will add an icon to your tray which provides a menu to control the application. Here you can show the QR code to pair your smartphone with your computer, change image algorithms and show past results.

After pairing your smartphone you are all set up to start taking pictures with the smartphone app and have fun!

##### Tests
The test suite can be opened with these commands:
```sh
cd screenshotmatcher
python3 tests.py
```
Here you can perform different performance tests by tweaking the parameters of the underlying feature matching algorithm.
