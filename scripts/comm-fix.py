import octoprint.plugin

from octoprint.util.pip import create_pip_caller
from octoprint.util.version import ( get_comparable_version, is_octoprint_compatible )

# Imports from comm.py
import contextlib
import copy
import fnmatch
import glob
import logging
import os
import queue
import re
import threading
import time
from collections import deque

import serial
import wrapt
import octoprint.util.version
import octoprint.plugin
from octoprint.events import Events, eventManager
from octoprint.filemanager import valid_file_type
from octoprint.filemanager.destinations import FileDestinations
from octoprint.settings import settings
from octoprint.systemcommands import system_command_manager
from octoprint.util import (
    CountedEvent,
    PrependableQueue,
    RepeatedTimer,
    ResettableTimer,
    TypeAlreadyInQueue,
    TypedQueue,
    chunks,
    filter_non_ascii,
    filter_non_utf8,
    get_bom,
    get_exception_string,
    sanitize_ascii,
    to_unicode,
    comm
)

from octoprint.util.platform import get_os, set_close_exec

_logger = logging.getLogger(__name__)

class Octo4a18Fix(octoprint.plugin.StartupPlugin):
    def on_startup(self, host, port):
        self._logger.info("Monkey patching comm.py")
        def patchedSerialList():
            if os.name == "nt":
                candidates = []
                try:
                    key = winreg.OpenKey(
                        winreg.HKEY_LOCAL_MACHINE, "HARDWARE\\DEVICEMAP\\SERIALCOMM"
                    )
                    i = 0
                    while True:
                        candidates += [winreg.EnumValue(key, i)[1]]
                        i += 1
                except Exception:
                    pass

            else:
                candidates = []
                try:
                    with os.scandir("/dev") as it:
                        for entry in it:
                            if regex_serial_devices.match(entry.name):
                                candidates.append(entry.path)
                except Exception:
                    pass
            
            # additional ports
            additionalPorts = settings().get(["serial", "additionalPorts"])
            if additionalPorts:
                for additional in additionalPorts:
                    candidates += glob.glob(additional)

            hooks = octoprint.plugin.plugin_manager().get_hooks(
                "octoprint.comm.transport.serial.additional_port_names"
            )
            for name, hook in hooks.items():
                try:
                    candidates += hook(candidates)
                except Exception:
                    logging.getLogger(__name__).exception(
                        "Error while retrieving additional "
                        "serial port names from hook {}".format(name)
                    )

            # blacklisted ports
            blacklistedPorts = settings().get(["serial", "blacklistedPorts"])
            if blacklistedPorts:
                for pattern in settings().get(["serial", "blacklistedPorts"]):
                    candidates = list(
                        filter(lambda x: not fnmatch.fnmatch(x, pattern), candidates)
                    )

            # last used port = first to try, move to start
            prev = settings().get(["serial", "port"])
            if prev in candidates:
                candidates.remove(prev)
                candidates.insert(0, prev)

            return candidates
        
        comm.serialList = patchedSerialList

__plugin_pythoncompat__ = ">=3.0,<4"
__plugin_implementation__ = Octo4a18Fix()
def __plugin_check__():
    return is_octoprint_compatible(">=1.8.0")