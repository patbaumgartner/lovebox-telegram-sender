# Security Policy

## Supported Versions

Only the latest release receives security updates.

## Reporting a Vulnerability

Please report suspected vulnerabilities **privately** via
[GitHub Security Advisories](https://github.com/patbaumgartner/lovebox-telegram-sender/security/advisories/new).
Do not open a public issue for security reports.

You can expect an initial response within a few days.

## Scope Notes

- This application talks to the **undocumented Lovebox API** with the credentials you
  provide. Treat your `.env` / `application-local.properties` as secrets and never
  commit them.
- The Telegram bot token grants full control over your bot — rotate it via
  [@BotFather](https://telegram.me/BotFather) if it leaks.
- A Telegram bot accepts messages from **anyone** who knows its username, and usernames
  are discoverable. The bot therefore serves only the chats listed in
  `bot.allowed-chat-ids` and refuses everyone else; it will not start without that list.
  Keep it to the chats that should be able to write to your Lovebox.
- `bot.echo-mode=ALL_ALLOWED` deliberately shows every allowed chat what the others sent.
  Only use it for people who should see each other's messages.
