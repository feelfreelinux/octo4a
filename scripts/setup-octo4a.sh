#!/bin/sh

set -e
# install required dependencies
apk add py3-pip py3-yaml py3-regex py3-zeroconf py3-netifaces py3-cffi py3-psutil unzip py3-pillow ttyd ffmpeg

mkdir -p /root/extensions/ttyd
cat << EOF > /root/extensions/ttyd/manifest.json
{
        "title": "Remote web terminal (ttyd)",
        "description": "Uses port 5002; User root / ssh password"
}
EOF

echo "octoprint" > /root/.octoCredentials
cat << EOF > /root/extensions/ttyd/start.sh
#!/bin/sh
ttyd -p 5002 --credential root:\$(cat /root/.octoCredentials) bash
EOF

cat << EOF > /root/extensions/ttyd/kill.sh
#!/bin/sh
pkill ttyd
EOF
chmod +x /root/extensions/ttyd/start.sh
chmod +x /root/extensions/ttyd/kill.sh
chmod 777 /root/extensions/ttyd/start.sh
chmod 777 /root/extensions/ttyd/kill.sh

pip3 install -U packaging --ignore-installed
pip3 install https://github.com/feelfreelinux/octo4a-argon2-mock/archive/main.zip
touch /home/octoprint/.argon-fix
