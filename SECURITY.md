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
- The bot echoes messages to **every chat that has ever contacted it**. Run it with a
  private bot and share the bot handle only with people who should see the messages.
