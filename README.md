### Usage

Create a bot in Developer Portal: https://discord.com/developers/home

Enable "Message Content Intent" under Overview -> Bot

Under Overview -> OAuth2:
- Enable "bot"
- Enable "Send Messages" and "Read Message History"

Use the generated URL to authorize the bot

### Build

Requires JDK 21. From `lambda/`:

```
gradlew.bat shadowJar
```

Deploy artifact: `lambda/build/libs/speldesignbabbel-1.0.0-all.jar`

Local dry-run (set Discord env vars first):

```
gradlew.bat run
```

