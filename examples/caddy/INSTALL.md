# Caddy example install notes

1. Point `map.example.com` to the server IP.
2. Open `80/tcp` and `443/tcp`.
3. Install Caddy.

Debian / Ubuntu example:

```bash
sudo apt update
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
```

4. Edit `Caddyfile`, replace `map.example.com`, and copy it:

```bash
sudo cp Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

5. Apply `KOKOTO-WebChat-config-overrides.yml` values to `plugins/KOKOTO-WebChat/config.yml`.
6. Run `/kchat reload` or restart the Minecraft server.

See `docs/en/CADDY_HTTPS.md` for the full guide.
