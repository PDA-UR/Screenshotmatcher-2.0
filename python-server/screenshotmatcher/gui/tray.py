from PIL import Image, ImageDraw
from pystray import Icon, Menu as menu, MenuItem as item
from io import BytesIO
import os
import subprocess
import platform
import base64
import common.utils
from common.config import Config

class Tray():
    def __init__(self, app_name):
        self.app_name = app_name

        self.ALGORITHM_STATE = Config.CURRENT_ALGORITHM

        self.icon = Icon(
            self.app_name,
            icon=self.get_icon(),
            menu=menu(
                # item(
                #     'Show QR code',
                #     lambda icon: self.onclick_qr()),
                item(
                    'Open Results Dir',
                    lambda icon: self.onclick_results()),
                menu.SEPARATOR,
                item(
                    'Matching Algorithm',
                    lambda icon: None,
                    enabled=False),
                item(
                    'SURF (Precise)',
                    self.set_state('SURF'),
                    checked=self.get_state('SURF'),
                    radio=True),
                item(
                    'ORB (Fast)',
                    self.set_state('ORB'),
                    checked=self.get_state('ORB'),
                    radio=True),
                menu.SEPARATOR,
                item(
                    'Quit',
                    lambda icon: self.onclick_quit())))

    def set_state(self, v):
        def inner(icon, item):
            self.ALGORITHM_STATE = v
            print('Switching Algorithm to %s' % self.ALGORITHM_STATE)
            Config.CURRENT_ALGORITHM = self.ALGORITHM_STATE
        return inner

    def get_state(self, v):
        def inner(item):
            return self.ALGORITHM_STATE == v
        return inner

    def get_icon(self):
        return Image.open(BytesIO(base64.b64decode(self.get_branding_datauri())))

    def run(self):
        self.icon.run(self.setup)

    def onclick_quit(self):
        self.icon.stop()

    def onclick_qr(self):
        if Config.IS_DIST:
            os.system('show_qr "{}"'.format(Config.SERVICE_URL))
        else:
            if platform.system() == 'Linux':
                os.system('python3 show_qr.py "{}"'.format(Config.SERVICE_URL))
            else:
                proc_result = subprocess.run('show_qr.py "{}"'.format(Config.SERVICE_URL), shell=True)
                if proc_result.returncode != 0:
                    proc_result = subprocess.run('show_qr.bat "{}"'.format(Config.SERVICE_URL), shell=True)

    def onclick_results(self):
        if Config.IS_DIST:
            common.utils.open_file_or_dir('./www')
        else:
            common.utils.open_file_or_dir(common.utils.getScriptDir(__file__) + '/../www/results')

    def setup(self, icon):
        self.icon.visible = True


    def get_branding_datauri(self):
        return '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AAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHQAAAAIIdAAAAAgg0AEAACCAQAcAAIAAAh0AAAACCHQAAAAIINABAAAggEAHAACAAAIdAAAAAgh0AAAACCDQAQAAIIBABwAAgAACHcaYgYGBricAAGOARwYYewQ69Mnw8Egrd3Z73y6t3AEAxrY9dt+tlTvbtw+3cgcmAoEOfTI0NNTKnZkzZ7RyBwAY22YeNr2VO209A8FEINChT7a8ua2VO2ecdnIrdwCAse30005q5c4bW7a2cgcmAoEOfbJp0+ut3DnzM6fWjOlTW7kFAIxNhx5yUH36jI+1cmvz5jdauQMTgUCHPlm3/tVW7kyaNFiXX/KNmrL3nq3cAwDGlilT9qzLL/56DQ4OtnJv3fpXWrkDE4FAhz55bvVLrd2aMX1qXTfvgpr10Q+1dhMAyHfU7Jn162surIMO+mBrN59bvba1WzDeTep6AIwXq556vtV7B07bv66fd0E9tGRFLVi4tJ5/YV29885QNU3T6g4AoDsDAwO1yy6T6+DpU+sTpx5bxx87u/UNq55u9xkIxjOBDn2y5qWXa8ubW2vvvfZo9e4Jx82uE45r/48xAMCmzW/Uhg2bup4B44ZX3KFPmqapZctXdj0DAKA1S5Y+5u096COBDn304OI/dz0BAKA1i//4SNcTYFwR6NBH9y9aXjt2DHc9AwBg1A0Nba9Fix/uegaMKwId+mjr1rdq4aJlXc8AABh1CxYurbfffqfrGTCuCHTos9/ccl/XEwAARp1nHug/gQ59tmz54/X0My92PQMAYNQ88eTqeuTRVV3PgHFHoEOfNU1Tv7zy5q5nAACMml/Mvcm3t8MoEOgwCn5//5J6ctXqrmcAAPTdY48/Ww886MvhYDQIdBgFvV5TF116ddczAAD67qJLrvTpOYwSgQ6j5JG/rKr5ty3oegYAQN/cePPv6rHHn+16BoxbAh1G0SWXz6uXN27uegYAwP9t/fpX64of/6rrGTCuCXQYRdu2vV1nn3t5jYyMdD0FAOA9Gx4eqW+ee1lte+tvXU+BcU2gwyh7dMVTdfFl87qeAQDwnp1/8VyvtkMLBDq04IYb76kbbryn6xkAAP+za6+/s2665b6uZ8CEINChBU3T1IWXXlV337u46ykAAO/aHXc9UD+84pquZ8CEIdChJb1er879zk/qzt8u6noKAMB/dfudC+vb5/20ej0/qQZtGdz3A4d/r+sRMFE0TVMLFy2rXXedXEfOntn1HACAf+vKebfWhZdeKc6hRU3TNAIdWtY0VX9asqI2vrK5Tjz+qBocHOx6EgBAVVUNbd9R3/3+z+qa6+6oRptDq5qmabziDh259fb76/NfOqdeeHF911MAAOq51Wvrc184u+6464Gup8CE5RN06NDm17bU/NsX1ODgYB0567AaGBjoehIAMMGMjIzU3KtvrXO+dUW9uun1rufAhNU0TTNwxJyzvLwCAWZMn1pf++oX65STju56CgAwQSxctLwu+9G19eKaDV1PgQmv1+uNCHQIM3vWYfWVL59VHz/lmK6nAADj0D++tHZ5zb1qfq184tmu5wD/JNAh2IHT9q/PnvnJOv1TJ9f++72/6zkAwBi34eVNdfe9i2v+bQtq7bqNXc8B/oVAhzFgp50G6ogPH1InnTinjjn6IzXriENr8uSdu54FAIQbGtpeK1Y+U8sffrz+8NCj9cSTq6vx1ewQS6DDGDQ4OFgHTtu/ZkyfWgccsG/tM2Wv2mvvPWryzjvXpEl+sg0AJprh4ZEa2r693tyytV57/c1at/6Vev6FdfXS2o3V6/W6nge8SwIdAAAAAvR6vRG/gw4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBHDOp7sAAAJhSURBVDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBAoAMAAEAAgQ4AAAABBDoAAAAEEOgAAAAQQKADAABAAIEOAAAAAQQ6AAAABBDoAAAAEECgAwAAQACBDgAAAAEEOgAAAAQQ6AAAABBgUq/XG+l6BAAAAExkO+1UI38H0LCwLI1J9BUAAAAASUVORK5CYII='
