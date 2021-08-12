#  ScreenshotMatcher (WIP)

Oftentimes, taking photographs of a computer screen is preferred to capturing proper screenshots as it is fast, convenient and the picture is shared via the smartphone anyway.
However, image artifacts such as moiré patterns, reflections, and perspective distortion deteriorate image quality of such photos.
ScreenshotMatcher solves this problem by using the interaction technique of photographing a screen with the phone to capture high quality screenshots which are already cropped to the region of interest.
This is done by automatically capturing a screenshot on the PC, detecting and extracting the photographed region of interest with a feature matching algorithm and sending the result to the smartphone.
ScreenshotMatcher consists of a python program running on the PC and an Android application.

## State of the project

The project is in a late development stage.
All major features work, but there is still some refactoring, bugfixing and documentation to be done.
If interested in the project, check out [the paper](https://epub.uni-regensburg.de/47814/1/screenshotmatcher.pdf) or contact us (andreas.schmid@ur.de).
