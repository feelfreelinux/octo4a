#!/bin/bash
COL='\033[1;32m'
NC='\033[0m' # No Color
echo -e "${COL}Setting up klipper for Octo4a v2.x"

# Prepare venv for klipper
python3 -m venv ~/klipper-venv

echo -e "${COL}\nDownloading klipper...\n${NC}"
cd ~/
git clone https://github.com/KevinOConnor/klipper

~/klipper-venv/bin/pip install -r ./klipper/scripts/klippy-requirements.txt

# Prepare necessary directories
mkdir ~/printer_data/
mkdir ~/printer_data/logs
mkdir ~/printer_data/gcodes
mkdir ~/printer_data/systemd
mkdir ~/printer_data/comms
touch ~/printer.cfg

echo -e "${COL}\nInserting configurations...\n${NC}"

mkdir -p /mnt/external/extensions/klipper
cat << EOF > /mnt/external/extensions/klipper/manifest.json
{
        "title": "Klipper plugin",
        "description": "Runs Klipper"
}
EOF

cat << EOF > /mnt/external/extensions/klipper/start.sh
#!/bin/sh
KLIPPER_ARGS="/root/klipper/klippy/klippy.py /root/printer.cfg -l /root/printer_data/logs/klippy.log -I /root/printer_data/comms/klippy.serial -a /root/printer_data/comms/klippy.sock"
/root/klipper-venv/bin/python \$KLIPPER_ARGS &
EOF

cat << EOF > /mnt/external/extensions/klipper/kill.sh
#!/bin/sh
pkill -f 'klippy\.py'
EOF

chmod +x /mnt/external/extensions/klipper/start.sh
chmod +x /mnt/external/extensions/klipper/kill.sh
chmod 777 /mnt/external/extensions/klipper/start.sh
chmod 777 /mnt/external/extensions/klipper/kill.sh

cat << EOF
${COL}
Klipper installed!
Please kill the app and restart it again to see it in extension settings${NC}

Set your OctoKlipper plugin settings:
    Serial Port: /root/printer_data/comms/klippy.serial
    Klipper Log File: /root/printer_data/logs/klippy.log
EOF
