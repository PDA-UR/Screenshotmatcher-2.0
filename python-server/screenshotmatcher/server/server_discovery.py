import socket
from common.config import Config

UDP_PORT = Config.PORT_DISCOVERY
HTTP_PORT = Config.PORT
interrupt_discovery_flag = False

# start in thread to prevent blocking from socket.recvfrom()
def local_network_address():
    # https://stackoverflow.com/a/28950776
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
    address = ip + ":" + str(HTTP_PORT)                         # send the address of the flaks server back, not that of the socket.
    print("UDP socket address: {}".format(address))
    return address

# start in thread to prevent blocking from socket.recvfrom()
def start():
    network_address = (str(Config.HOST) + ":" + str(Config.PORT)).encode('ASCII')

    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)        # socket for discovery
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)     # reuse address if already in use
    s.bind(('0.0.0.0', UDP_PORT))

    while not interrupt_discovery_flag:                         # interupt by setting flag
        bytesAddressPair = s.recvfrom(1024)
        client_address = bytesAddressPair[1]
        print("Server IP requested by Client: {}".format(client_address))

        s.sendto(network_address, client_address)
        