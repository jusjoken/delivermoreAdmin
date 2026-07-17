# Cloudflare Tunnel for Development

This replaces an ad-hoc `cloudflared` CLI flow with a repeatable setup for the DeliverMore admin app.

## Recommended approach

Use a named Cloudflare Tunnel with a stable dev hostname instead of temporary `trycloudflare` URLs.

Benefits:

- stable URL for Android/client testing
- repeatable startup on a new Fedora workstation
- easier DNS, Access, and audit management
- no need to re-share a new tunnel URL every time

## Files in this repo

- `.cloudflared/config.yml.example` - example tunnel config
- `scripts/dev/run-admin.sh` - starts the Spring Boot app on port 8443 using `.env`
- `scripts/dev/run-cloudflared.sh` - starts a named tunnel using repo config
- `.vscode/tasks.json` - one-click VS Code tasks for app + tunnel

## One-time setup on a dev machine

1. Install `cloudflared`.
2. Authenticate:

```bash
cloudflared tunnel login
```

3. Create a named tunnel:

```bash
cloudflared tunnel create delivermore-admin-dev
```

4. Route a stable hostname to it:

```bash
cloudflared tunnel route dns delivermore-admin-dev admin-dev.delivermore.ca
```

5. Copy the example config and update the username path if needed:

```bash
mkdir -p .cloudflared
cp .cloudflared/config.yml.example .cloudflared/config.yml
```

6. Confirm the generated credentials JSON exists under `~/.cloudflared/` and matches the `credentials-file` value.

## Daily development flow

Start the app:

```bash
./scripts/dev/run-admin.sh
```

In a second terminal, start the tunnel:

```bash
./scripts/dev/run-cloudflared.sh
```

Or use the VS Code tasks:

- `DM Admin: Run App`
- `DM Admin: Run Cloudflare Tunnel`

## Notes about HTTPS

The current app runs locally on `https://localhost:8443`.
The example tunnel config uses:

```yaml
originRequest:
  noTLSVerify: true
```

That is appropriate for local development when the local certificate does not match `localhost` in a way `cloudflared` can validate cleanly.

If you later move the local app behind plain HTTP for dev only, update the tunnel service to `http://localhost:8080` and remove the `noTLSVerify` override.

## Recommended hardening for ongoing use

1. Use a dedicated hostname such as `admin-dev.delivermore.ca`.
2. Protect it with Cloudflare Access if the tunnel should not be public.
3. Keep the tunnel named and long-lived instead of using temporary URLs.
4. Keep credentials out of the repo. Only commit the example config.
5. Consider a user-level systemd service for `cloudflared` on Fedora for auto-restart.

Example user service:

```ini
[Unit]
Description=DeliverMore Cloudflare Dev Tunnel
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=%h/projects/delivermoreAdmin
ExecStart=/usr/bin/cloudflared tunnel --config %h/projects/delivermoreAdmin/.cloudflared/config.yml run
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
```

Enable with:

```bash
systemctl --user daemon-reload
systemctl --user enable --now cloudflared-delivermore-dev.service
```

## Suggested next improvement

If this dev URL will be used by the Android app regularly, the next step should be Cloudflare Access with a service token or a controlled allowlist rather than leaving the hostname openly reachable.