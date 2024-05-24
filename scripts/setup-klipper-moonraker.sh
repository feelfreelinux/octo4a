#!/bin/bash
COL='\033[1;32m'
NC='\033[0m' # No Color
echo -e "${COL}Setting up klipper + moonraker + mainsail"

echo -e "${COL}\nInstalling dependencies...\n${NC}"
# install required dependencies
apk add nginx git

nginx -t

# Prepare venv for klipper and moonraker
python3 -m venv ~/moonraker-venv
python3 -m venv ~/klipper-venv


echo -e "${COL}\nDownloading moonraker, mainsail and klipper...\n${NC}"
cd ~/
git clone https://github.com/Arksine/moonraker.git
git clone https://github.com/KevinOConnor/klipper

# Download mainsail
mkdir ~/mainsail
cd ~/mainsail
wget -q -O mainsail.zip https://github.com/mainsail-crew/mainsail/releases/latest/download/mainsail.zip && unzip mainsail.zip && rm mainsail.zip
cd ~/

~/klipper-venv/bin/pip install -r ./klipper/scripts/klippy-requirements.txt
~/moonraker-venv/bin/pip install -r ./moonraker/scripts/moonraker-requirements.txt

# Prepare necessary directories
mkdir ~/printer_data/
mkdir ~/printer_data/config
mkdir ~/printer_data/logs
mkdir ~/printer_data/gcodes
mkdir ~/printer_data/systemd
mkdir ~/printer_data/comms
touch ~/printer_data/config/printer.cfg


echo -e "${COL}\nInserting configurations...\n${NC}"

# Insert moonraker configs
cat << EOF > ~/printer_data/config/moonraker.conf
[server]
host: 0.0.0.0
port: 7125
# The maximum size allowed for a file upload (in MiB).  Default 1024 MiB
max_upload_size: 1024
# Path to klippy Unix Domain Socket
klippy_uds_address: ~/printer_data/comms/klippy.sock

[file_manager]
# post processing for object cancel. Not recommended for low resource SBCs such as a Pi Zero. Default False
enable_object_processing: False

[authorization]
cors_domains:
    *://my.mainsail.xyz
    *://*.local
    *://*.lan
trusted_clients:
    10.0.0.0/8
    127.0.0.0/8
    169.254.0.0/16
    172.16.0.0/12
    192.168.0.0/16
    FE80::/10
    ::1/128

# enables partial support of Octoprint API
[octoprint_compat]

# enables moonraker to track and store print history.
[history]

# this enables moonraker announcements for mainsail
[announcements]
subscriptions:
    mainsail

# this enables moonraker's update manager
[update_manager]
refresh_interval: 168
enable_auto_refresh: True

[update_manager mainsail]
type: web
channel: stable
repo: mainsail-crew/mainsail
path: ~/mainsail
EOF

mkdir -p /etc/nginx/conf.d/

cat << EOF > /etc/nginx/http.d/upstreams.conf
# /etc/nginx/http.d/upstreams.conf

upstream apiserver {
    ip_hash;
    server 127.0.0.1:7125;
}

upstream mjpgstreamer1 {
    ip_hash;
    server 127.0.0.1:8080;
}

upstream mjpgstreamer2 {
    ip_hash;
    server 127.0.0.1:8081;
}

upstream mjpgstreamer3 {
    ip_hash;
    server 127.0.0.1:8082;
}

upstream mjpgstreamer4 {
    ip_hash;
    server 127.0.0.1:8083;
}
EOF

rm /etc/nginx/http.d/default.conf

cat << EOF > /etc/nginx/http.d/mainsail.conf
# /etc/nginx/sites-available/mainsail

server {
    listen 2137 default_server;

    access_log /var/log/nginx/mainsail-access.log;
    error_log /var/log/nginx/mainsail-error.log;

    # disable this section on smaller hardware like a pi zero
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_proxied expired no-cache no-store private auth;
    gzip_comp_level 4;
    gzip_buffers 16 8k;
    gzip_http_version 1.1;
    gzip_types text/plain text/css text/xml text/javascript application/javascript application/x-javascript application/json application/xml;

    # web_path from mainsail static files
    root /root/mainsail;

    index index.html;
    server_name _;

    # disable max upload size checks
    client_max_body_size 0;

    # disable proxy request buffering
    proxy_request_buffering off;

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location = /index.html {
        add_header Cache-Control "no-store, no-cache, must-revalidate";
    }

    location /websocket {
        proxy_pass http://apiserver/websocket;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$connection_upgrade;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 86400;
    }

    location ~ ^/(printer|api|access|machine|server)/ {
        proxy_pass http://apiserver\$request_uri;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Scheme \$scheme;
    }

    location /webcam/ {
        postpone_output 0;
        proxy_buffering off;
        proxy_ignore_headers X-Accel-Buffering;
        access_log off;
        error_log off;
        proxy_pass http://mjpgstreamer1/;
    }

    location /webcam2/ {
        postpone_output 0;
        proxy_buffering off;
        proxy_ignore_headers X-Accel-Buffering;
        access_log off;
        error_log off;
        proxy_pass http://mjpgstreamer2/;
    }

    location /webcam3/ {
        postpone_output 0;
        proxy_buffering off;
        proxy_ignore_headers X-Accel-Buffering;
        access_log off;
        error_log off;
        proxy_pass http://mjpgstreamer3/;
    }

    location /webcam4/ {
        postpone_output 0;
        proxy_buffering off;
        proxy_ignore_headers X-Accel-Buffering;
        access_log off;
        error_log off;
        proxy_pass http://mjpgstreamer4/;
    }
}
EOF

mkdir -p /mnt/external/extensions/klipper
cat << EOF > /mnt/external/extensions/klipper/manifest.json
{
        "title": "Klipper plugin",
        "description": "Runs klipper + moonraker + mainsail"
}
EOF

cat << EOF > /mnt/external/extensions/klipper/start.sh
#!/bin/sh
KLIPPER_ARGS="/root/klipper/klippy/klippy.py /root/printer_data/config/printer.cfg -l /root/printer_data/logs/klippy.log -I /root/printer_data/comms/klippy.serial -a /root/printer_data/comms/klippy.sock"
MOONRAKER_ARGS="/root/moonraker/moonraker/moonraker.py -d /root/printer_data"

nginx
/root/klipper-venv/bin/python \$KLIPPER_ARGS &
/root/moonraker-venv/bin/python \$MOONRAKER_ARGS
EOF

cat << EOF > /mnt/external/extensions/klipper/kill.sh
#!/bin/sh
pkill -f 'klippy\.py'
pkill -f 'moonraker\.py'
pkill nginx
EOF

chmod +x /mnt/external/extensions/klipper/start.sh
chmod +x /mnt/external/extensions/klipper/kill.sh
chmod 777 /mnt/external/extensions/klipper/start.sh
chmod 777 /mnt/external/extensions/klipper/kill.sh

cat << EOF
${COL}
Klipper installed!
Mainsail should be accessible on port 2137
Please kill the app and restart it again to see it in extension settings${NC}
EOF
