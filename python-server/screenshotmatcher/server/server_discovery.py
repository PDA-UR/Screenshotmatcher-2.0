import socket
from common.config import Config

interrupt_discovery_flag = False

# start in thread to prevent blocking from socket.recvfrom()
def start():
    payload = (
        str(Config.HOST) + ":" + str(Config.PORT) + "|" + str(socket.gethostname())
        ).encode('ASCII')
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)        # socket for discovery
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)     # reuse address if already in use
    s.bind(('0.0.0.0', Config.PORT_DISCOVERY))

    while not interrupt_discovery_flag:                         # interupt by setting flag
        bytesAddressPair = s.recvfrom(1024)
        client_address = bytesAddressPair[1]
        print("Server IP requested by Client: {}".format(client_address))

        s.sendto(payload, client_address)
        