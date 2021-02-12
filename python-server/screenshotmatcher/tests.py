#!/usr/bin/env python

import PySimpleGUI as sg

import sys
import io

from common.config import Config
from testing.test_suite import TestSuite

# Define Input Defaults
DEFAULTS = {
    'MINHESSIAN': 100,
    'MAXHESSIAN': 10000,
    'STEPHESSIAN': 50,
    'ALGOHESSIAN': 1,

    'MINALGO': 0,
    'MAXALGO': 5,
    'HESSIANALGO': 500,

    'MINNFEATURES': 100,
    'MAXNFEATURES': 1000,
    'STEPNFEATURES': 100,
}

# Define Button Keys
btn_key_CLEARLOG = '_BTN_CLEARLOG'
btn_key_SURF_HESSIANTHRESHOLD = '_BTN_HESSIANTHRESHOLD'
btn_key_SURF_DESCRIPTORMATCHER = '_BTN_SURF_DESCRIPTORMATCHER'
btn_key_ORB_NFEATURES = '_BTN_ORB_NFEATURES'

# Define Theme
sg.theme(new_theme='material1')

# Define Layout
output_box = sg.Output(size=(40, 8))

layout = [
    [ sg.Button('Clear Log', key=btn_key_CLEARLOG) ], 
    [ output_box ],

    [ sg.Text('Which Pictures should be used?') ],
    [ 
        sg.Checkbox('Pic 1', default=True),
        sg.Checkbox('Pic 2', default=False),
        sg.Checkbox('Pic 3', default=False),
        sg.Checkbox('Pic 4', default=False),
        sg.Checkbox('Pic 5', default=False),
    ],
    [ 
        sg.Checkbox('Pic 6', default=False),
        sg.Checkbox('Pic 7', default=False),
        sg.Checkbox('Pic 8', default=False),
        sg.Checkbox('Pic 9', default=False),
        sg.Checkbox('Pic 10', default=False),
    ],
    
    [ sg.Text('-' * 50) ],
    [ sg.Text('SURF Hessian Threshold') ],
    [ 
        sg.Text('Min'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['MINHESSIAN']),
        sg.Text('Max'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['MAXHESSIAN']),
        sg.Text('Step'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['STEPHESSIAN']),
        sg.Text('DM Algo'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['ALGOHESSIAN']),
    ],
    [ sg.Button('Run test', key=btn_key_SURF_HESSIANTHRESHOLD) ],
    
    [ sg.Text('-' * 50) ],
    [ sg.Text('SURF Descriptor Matcher') ],
    [ 
        sg.Text('Min'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['MINALGO']),
        sg.Text('Max'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['MAXALGO']),
        sg.Text('Hessian Threshold'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['HESSIANALGO']),
    ],
    [ sg.Button('Run test', key=btn_key_SURF_DESCRIPTORMATCHER) ],
    
    [ sg.Text('-' * 50) ],
    [ sg.Text('ORB nFeatures') ],
    [ 
        sg.Text('Min'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['MINNFEATURES']),
        sg.Text('Max'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['MAXNFEATURES']),
        sg.Text('Step'),
        sg.InputText(size=(7,1), default_text=DEFAULTS['STEPNFEATURES']),
    ],
    [ sg.Button('Run test', key=btn_key_ORB_NFEATURES) ],
]

# Initialize Window
window = sg.Window(
    'ScreenshotMatcher - Test Suite',
    layout=layout,
    size=(500,600),
    element_justification='center',
    margins=(20,20),
    keep_on_top=True
)

# Initialize Test Suite
test_suite = TestSuite(window)

while True:

    event, values = window.read()

    # Clear Log
    if event == btn_key_CLEARLOG:
        output_box.Update('')
        window.Refresh()

    # SURF Hessian Threshold Test
    if event == btn_key_SURF_HESSIANTHRESHOLD:
        output_box.Update('')
        test_suite.test_SURF_hessian(values)

    # SURF Descriptor Matcher Test
    if event == btn_key_SURF_DESCRIPTORMATCHER:
        output_box.Update('')
        test_suite.test_SURF_algos(values)

    # ORB nFeatures Test
    if event == btn_key_ORB_NFEATURES:
        output_box.Update('')
        test_suite.test_ORB_nfeatures(values)

    if event in (None, 'Cancel'):   # ifbreak user closes window or clicks cancel
        break

window.close()