import socket
import os
import sys
import platform
import subprocess
import colored

def getCurrentIPAddress():
  s_ip = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)     # socket for finding local network IP
  s_ip.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # reuse address if already in use
  ip = ""
  try:
      s_ip.connect(('10.255.255.255', 1))
      ip = s_ip.getsockname()[0]
  except Exception as e:
      print("Exception caught while trying to determine server IP")
      print(e)
  finally:
      s_ip.close()
  return ip

def getScriptDir(filename):
  return os.path.dirname(os.path.realpath(filename))

def get_main_dir():
  # Ensure correct paths when running the compiled binary created with PyInstaller. https://stackoverflow.com/a/42615559
  if getattr(sys, 'frozen', False):
      # If the application is run as a bundle, the PyInstaller bootloader
      # extends the sys module by a flag frozen=True and sets the app 
      # path into variable _MEIPASS'.
      application_path = sys._MEIPASS
  else:
      # get the directory of main.py
      application_path = os.path.dirname(os.path.realpath(__file__))
      if platform.system() == "Windows":
          application_path = application_path.rsplit('\\', 1)[0]
      else:
          application_path = application_path.rsplit('/', 1)[0]

  return application_path

def create_results_dir():
  main_dir = get_main_dir()
  print(main_dir)
  if not os.path.exists(main_dir + "/www"):
    os.mkdir(main_dir + "/www")
  if not os.path.exists(main_dir + "/www/results"):
    os.mkdir( main_dir + "/www/results")

def allowed_file(filename):
  return '.' in filename and \
          filename.rsplit('.', 1)[1].lower() in {'png', 'jpg', 'jpeg'}

def open_file_or_dir(path):
  if platform.system() == "Windows":
    os.startfile(path)
  elif platform.system() == "Darwin":
    subprocess.Popen(["open", path])
  else:
    subprocess.Popen(["xdg-open", path])

def print_banner(app_name, version, id):

  text_style_notice = colored.bg('9') + colored.attr('bold')
  text_style_banner = colored.fg('156') + colored.attr('bold')

  notice_text = "    DO NOT CLOSE THIS WINDOW / FENSTER NICHT SCHLIESSEN    "
               
  banner_text = """
  ---------------------------
   %s %s
  ---------------------------

  Your ID: %s

  """ % ( app_name, version, id )


  print( '' )
  print( colored.stylize( notice_text, text_style_notice ) )
  print( '' )
  print( colored.stylize( banner_text, text_style_banner ) )
