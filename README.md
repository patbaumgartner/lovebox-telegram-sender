![maven-docker-publish](https://github.com/patbaumgartner/lovebox-telegram-sender/actions/workflows/maven-docker-publish.yml/badge.svg)

# Lovebox Telegram Sender

The app allows you to send messages to a single instance of Lovebox via Telegram Bot. Text messages and captioned photos are supported. Other message types (e.g. stickers, audio, etc.) will result in a standard message.

## Application Setup

To set up the application, you will need to retrieve some IDs and values from the Lovebox API. The following curl commands will help you to on an existing account. Make sure you have already set up your account using the Android or iOS app.

### Login with Password

Log in with a password to retrieve the authorisation token for the following request.

```bash
curl --location --request POST 'https://app-api.loveboxlove.com/v1/auth/loginWithPassword' \
--header 'accept: application/json' \
--header 'content-type: application/json' \
--header 'host: app-api.loveboxlove.com' \
--data-raw '{
    "email": "my@email.com",
    "password": "mySecret"
}'
```

```json
{
  "_id": "42c61f261f399d0016350b7f",
  "firstName": "FirstName",
  "email": "my@email.com",
  "token": "exJhbGcpOiJIVzI1NipsInR5cCI6IkpXVDj9.eyJ1c2xySwFiOiI2MWM5LWYzNjFhMzk5YzAwMTYzNTBhN2YiLCJpYXQiFjE22DAVNmQwNTL9.qlsvp_roqCu4MFwBMwNZu2eyImFGjogvNeR4tkoTLPe"
}
```

### Me Request with the Authorization Token

Use the token above to make a 'Me Request' to find the configuration details you need.

```bash
curl --location --request POST 'https://app-api.loveboxlove.com/v1/graphql' \
--header 'authorization: Bearer exJhbGcpOiJIVzI1NipsInR5cCI6IkpXVDj9.eyJ1c2xySwFiOiI2MWM5LWYzNjFhMzk5YzAwMTYzNTBhN2YiLCJpYXQiFjE22DAVNmQwNTL9.qlsvp_roqCu4MFwBMwNZu2eyImFGjogvNeR4tkoTLPe' \
--header 'content-type: application/json' \
--header 'host: app-api.loveboxlove.com' \
--data-raw '{
    "operationName": "me",
    "variables": {},
    "query": "query me {\n  me {\n    _id\n    _id\n    createdAt\n    firstName\n    email\n    beta\n    settings {\n      streak\n      loveGoal\n      reminders {\n        day\n        meridiem\n        number\n        weekday\n        time\n        __typename\n      }\n      specialDates {\n        _id\n        name\n        date\n        dateType\n        __typename\n      }\n      notifications {\n        generalMessageRead\n        generalHeartReceived\n        marketingOffers\n        marketingOffersPush\n        marketingOffersEmail\n        __typename\n      }\n      __typename\n    }\n    addresses {\n      firstname\n      lastname\n      streetAddress\n      zipCode\n      city\n      country\n      state\n      __typename\n    }\n    boxes {\n      _id\n      color\n      companyId\n      signature\n      picture\n      nickname\n      notifications {\n        disableUntil\n        messageRead\n        heartReceived\n        __typename\n      }\n      admin {\n        _id\n        firstName\n        email\n        __typename\n      }\n      privacyPolicy\n      pairingCode\n      isConnected\n      isAdmin\n      hardware\n      hasColor\n      connectionDate\n      macAddress\n      __typename\n    }\n    roles\n    device {\n      _id\n      appVersion\n      os\n      __typename\n    }\n    profile\n    reminder\n    subscription {\n      subscribed\n      platform\n      __typename\n    }\n    fcmToken\n    language\n    loveCoins\n    lastSentMessage\n    __typename\n  }\n}\n"
}'
```

```json
{
  "data": {
    "me": {
      "_id": "42c61f261f399d0016350b7f",
      "createdAt": "2021-12-24T19:27:34.542Z",
      "firstName": "FirstName",
      "email": "me@email.com",
      "beta": 0,
      "settings": {
        "streak": 1,
        "loveGoal": "TwiceAWeek",
        "reminders": [
          // ...
        ],
        "specialDates": [
          // ...
        ],
        "notifications": {
          "generalMessageRead": true,
          "generalHeartReceived": true,
          "marketingOffers": true,
          "marketingOffersPush": true,
          "marketingOffersEmail": true,
          "__typename": "NotificationUserSettings"
        },
        "__typename": "Settings"
      },
      "addresses": [],
      "boxes": [
        {
          // lovebox.box-id
          "_id": "417a114e58e15a0214cf3612",
          "color": "#8A64FF",
          "companyId": "",
          // lovebox.signature
          "signature": "Signature",
          "picture": null,
          "nickname": "Nickname",
          "notifications": {
            "disableUntil": null,
            "messageRead": true,
            "heartReceived": true,
            "__typename": "NotificationSettings"
          },
          "admin": {
            "_id": "61c61ecc71010a00161789f2",
            "firstName": null,
            "email": "significant-other@email.com",
            "__typename": "User"
          },
          "privacyPolicy": "ADMIN_AND_ME",
          "pairingCode": "BECF-RBMA",
          "isConnected": true,
          "isAdmin": false,
          "hardware": "C2",
          "hasColor": true,
          "connectionDate": "2021-12-24T19:27:35.123Z",
          "macAddress": "0CDC7ECF4FC4",
          "__typename": "BoxSettings"
        }
      ],
      "roles": [],
      "device": {
        // lovebox.device-id
        "_id": "42fab8322d8cec91",
        "appVersion": "5.14.9",
        "os": "android",
        "__typename": "Device"
      },
      "profile": {
        // ...
      },
      "reminder": 0,
      "subscription": null,
      "fcmToken": "edMLqMyMrpoGig9ZHdFdvH:AdA91bGGwx3UEuYTdhYOWDIdwlm2b23B9Jjin3MCGbi7CmUSpCVHFlorfryygi5QUBQMUVUiGsDJIE3RliENFmsuWrOnf4cBba-mNT5032NoKlo9AdPU5YhuCOR0KIdAbCokR42Hru",
      "language": "en",
      "loveCoins": 10,
      "lastSentMessage": "2023-01-01T17:55:34.890Z",
      "__typename": "User"
    }
  }
}
```

### Setting up a Telegram Bot

To create a chatbot on Telegram, you need to contact the [@BotFather](https://telegram.me/BotFather), who is a bot which is used to create other bots.

The command you need is `/newbot`, which will take you through the next steps to create your bot. Follow the instructions and get the bot `username` and `token`.

### Adjusting SpringBoot's application.properties

Running the application from source requires customisation according to your settings. Adjust the `application.properties' in the sources or pass it as Java options or CLI arguments to the application.

```properties
# Lovebox Login
lovebox.enabled=true
lovebox.email=me@email.com
lovebox.password=mySecret
# Lovebox Setting
lovebox.signature=Signature
lovebox.device-id=42fab8322d8cec91
lovebox.box-id=417a114e58e15a0214cf3612
# Telegram Bot Settings
bot.username=Lovebox_bot
bot.token=4072971853:ABEojZ42uNA6YYn_c7DF8RH0UOorqXuveSQ
```

### Setting Environment Variables e.g. for Docker

The following snippet can be passed as `.env` and read by `docker-compose.yml`, or used to pass directly to the the `docker run` command.

```bash
# Lovebox Login
LOVEBOX_ENABLED=true
LOVEBOX_EMAIL="me@email.com"
LOVEBOX_PASSWORD="mySecret"
# Lovebox Setting
LOVEBOX_SIGNATURE="Signature"
LOVEBOX_DEVICE_ID="42fab8322d8cec91"
LOVEBOX_RELATION_ID="33c67a2127d7be09142f4326"
LOVEBOX_BOX_ID="417a114e58e15a0214cf3612"
# Telegram Bot Settings
BOT_USERNAME="Lovebox_bot"
BOT_TOKEN="4072971853:ABEojZ42uNA6YYn_c7DF8RH0UOorqXuveSQ"
```

## Building the Docker Container

The image name, custom run image, pull policy, and buildpack environment are baked
into the `spring-boot-maven-plugin` configuration in `pom.xml`, so the commands
below don't need `-Dspring-boot.build-image.*` overrides. Build the custom run
image first (see [Fixing Known Issues with Missing Fonts](#fixing-known-issues-with-missing-fonts)).

### Building the Docker Container Locally

```bash
mvn spring-boot:build-image --batch-mode --no-transfer-progress
```

### Building the Docker Container and Pushing to Docker Hub

Build the image locally, then log in and push it with Docker:

```bash
mvn spring-boot:build-image --batch-mode --no-transfer-progress

docker login
docker push patbaumgartner/lovebox-telegram-sender:0.1.0-SNAPSHOT
```

### Building a GraalVM Native Image Container

The project ships a `native` Maven profile (extending the one inherited from
`spring-boot-starter-parent`) with the extra AWT/charset build arguments the
application needs, since it renders images via `java.awt`.

Build a native-image container with buildpacks (the `native` profile forces
`BP_NATIVE_IMAGE=true`; the image name and run image come from `pom.xml`):

```bash
mvn spring-boot:build-image --batch-mode --no-transfer-progress -Pnative
```

Because the application relies on AWT and fonts, the native run image must
include font libraries (`fontconfig`, `libfreetype`) and at least one font.
The `<runImage>` in `pom.xml` points to the custom
`patbaumgartner/lovebox-telegram-sender-run:latest` image built from
`Dockerfile.base-cnb` (see
[Fixing Known Issues with Missing Fonts](#fixing-known-issues-with-missing-fonts)),
otherwise the native binary will fail with a `NullPointerException` in
`sun.awt.FontConfiguration`.

To build only a native binary (no container), run:

```bash
mvn -Pnative native:compile
```

> Note: native compilation requires a GraalVM/Liberica NIK toolchain (for local
> builds) or Docker (for the buildpack path), and takes noticeably longer than a
> regular JVM build.

### Running the Application from Maven

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Fixing Known Issues with Missing Fonts

Since the application uses fonts, we need to make sure that fonts are part of the Docker container. The containers created above will throw an exception if they are `java.lang.NullPointerException`: Cannot load from short array because `sun.awt.FontConfiguration.head` is null.

[Andreas Ahlensdorf](https://github.com/aahlenst) describes nicely the font problem in his blog post [Prerequisites for Font Support in AdoptOpenJDK](https://blog.adoptopenjdk.net/2021/01/prerequisites-for-font-support-in-adoptopenjdk/).

After further research, it seems that the only way to add fonts to the build pack base image is to create an OCI run image by extending the base image. See the `Dockerfile.base-cnb` file and what a patch with the additional font packages might look like.

Build the run image locally with the following command.

```bash
docker build --no-cache -f Dockerfile.base-cnb -t patbaumgartner/lovebox-telegram-sender-run:latest .
```

The `spring-boot-maven-plugin` in `pom.xml` already references this run image via
`<runImage>` and uses `<pullPolicy>IF_NOT_PRESENT</pullPolicy>`, so once the image
exists locally a plain `mvn spring-boot:build-image` picks it up to create the
container containing the fonts.

## Credits

Reverse engineering (unpinning certificates) was done using [APKLab](https://github.com/APKLab/APKLab) and the [Lovebox APK](https://www.apkmonk.com/app/love.lovebox.loveboxapp/) provided by [apkmonk](https://www.apkmonk.com). Postman was used to capture the REST calls from the mobile application. The article [Capturing Http Requests](https://learning.postman.com/docs/sending-requests/capturing-request-data/capturing-http-requests/) covers everything you need to know. After updating Postman, [new certs](https://learning.postman.com/docs/sending-requests/capturing-request-data/capturing-http-requests/#troubleshooting-certificate-issues) need to be installed.
