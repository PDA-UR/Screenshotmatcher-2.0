import socket
import os
import platform
import subprocess
import colored

def getCurrentIPAddress():
  s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  s.connect(("8.8.8.8", 80))
  ipAddr = s.getsockname()[0]
  s.close()
  return ipAddr

def getScriptDir(filename):
  return os.path.dirname(os.path.realpath(filename))
  

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
