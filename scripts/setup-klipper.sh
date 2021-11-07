#!/bin/bash
set -e
COL='\033[1;32m'
NC='\033[0m' # No Color
echo -e "${COL}Setting up klipper"

read -p "Do you have \"Plugin extras\" installed? (y/n): " -n 1 -r
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo -e "${COL}\nPlease go to settings and install plugin extras${NC}"
    [[ "$0" = "$BASH_SOURCE" ]] && exit 1 || return 1
fi

echo -e "${COL}Installing dependencies...\n${NC}"
# install required dependencies
apk add py3-cffi py3-greenlet linux-headers can-utils
pip3 install python-can

echo -e "${COL}Downloading klipper (python3 branch)\n${NC}"
curl -o klipper.zip -L https://github.com/Doridian/klipper/archive/refs/heads/python3.zip

echo -e "${COL}Extracting klipper\n${NC}"
unzip klipper.zip
rm -rf klipper.zip
mv klipper-python3 /klipper
echo "# replace with your config" >> /root/printer.cfg

mkdir -p /root/extensions/klipper
cat << EOF > /root/extensions/klipper/manifest.json
{
        "title": "Klipper plugin",
        "description": "Requires OctoKlipper plugin"
}
EOF

cat << EOF > /root/extensions/klipper/start.sh
#!/bin/sh
python3 /klipper/klippy/klippy.py /root/printer.cfg -l /tmp/klippy.log -a /tmp/klippy_uds
EOF

cat << EOF > /root/extensions/klipper/kill.sh
#!/bin/sh
pkill -f 'klippy\.py'
EOF
chmod +x /root/extensions/klipper/start.sh
chmod +x /root/extensions/klipper/kill.sh
chmod 777 /root/extensions/klipper/start.sh
chmod 777 /root/extensions/klipper/kill.sh

echo -e "${COL}\nKlipper installed! Please kill the app and restart it again to see it in extension settings${NC}"
