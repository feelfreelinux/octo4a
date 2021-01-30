# coding=utf-8
from __future__ import absolute_import

import octoprint.plugin
import octoprint.util
import octoprint.logging
from serial import SerialTimeoutException

__plugin_pythoncompat__ = ">=2.7,<4"
__plugin_name__ = "Octo4a Android support"
__plugin_version__ = "1.0.0"
__plugin_description__ = "Android support for OctoPrint"

class Octo4aSerial(octoprint.util.comm.MachineCom):
    def __init__(self, logger, baud):
        self.logger = logger
        self.timeout = 15
        self.baudrate = baud
        self.logger.info("!octo4a - init serial")
        self.lastBaud = baud
        self.logger.info("!octo4a - baud " + str(baud))
        self.input = open('/data/data/io.feelfreelinux.octo4a/files/home/input', 'rb')
        self.output = open('/data/data/io.feelfreelinux.octo4a/files/home/output', 'wb')
        #self.output.write(("!octo4a: BAUDRATE" + str(baud) + '\n').encode("utf-8"))

    def readline(self, size=None, eol='n'):
        line = self.input.readline(size)
        if "!octo4aDrop" in str(line):
            raise SerialTimeoutException()
        return line

    def write(self, data):
        if self.lastBaud is not self.baudrate:
            self.lastBaud = self.baudrate
            self.logger.info("!octo4a - baud " + str(self.baudrate))
        self.output.write(data)
        self.output.flush()
    def close(self):
        ##self.logger.info("WRITE")
        #self.output.write(("!octo4a: CLOSE\n").encode("utf-8"))
        self.logger.info("!octo4a - closing")

class Octo4aSerialHandlerPlugin(octoprint.plugin.StartupPlugin):
    def serial_callback(self, comm_instance, port, baudrate, read_timeout, *args, **kwargs):
        self._logger.info("OKE OKE")
        return Octo4aSerial(self._logger, baudrate)
    def get_additional_port_names(self, *args, **kwargs):
            return ["USB PRINTER"]


def __plugin_load__():
    plugin = Octo4aSerialHandlerPlugin()

    global __plugin_implementation__
    __plugin_implementation__ = plugin

    global __plugin_hooks__
    __plugin_hooks__ = {"octoprint.comm.transport.serial.factory": plugin.serial_callback,
        "octoprint.comm.transport.serial.additional_port_names": plugin.get_additional_port_names,

    
    }
