#!/bin/bash
# Startup script for Cloudify Server on Pterodactyl container

echo "=== Starting Cloudify Music Server ==="
python3 -m pip install --upgrade pip fastapi uvicorn yt-dlp httpx
export PATH=$PATH:/home/container/.local/bin
python3 main.py
