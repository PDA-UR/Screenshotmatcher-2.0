#!/usr/bin/env python

import PySimpleGUI as sg

import qrcode
import sys
import io

if len(sys.argv) < 2:
    print('Usage: show_qr STR_TO_ENCODE')
    sys.exit()

img = qrcode.make(sys.argv[1])

bio = io.BytesIO()
img.save(bio, format="PNG")
del img

sg.theme(new_theme='material2')
layout = [[sg.Image(data=bio.getvalue())]]
window = sg.Window('Pair your device', layout)


event, values = window.read()

if event in (None, 'Cancel'):   # ifbreak user closes window or clicks cancel
    window.close()

window.close()