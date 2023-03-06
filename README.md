![maven-docker-publish](https://github.com/patbaumgartner/lovebox-telegram-sender/actions/workflows/maven-docker-publish.yml/badge.svg)

# Lovebox Telegram Sender

The app allows sending messages via Telegram Bot to a single Lovebox instance. Text messages and photos with captions
are supported. Other message types (e.g. Stickers, Audio, etc.) will lead to a default message.

## Application Setup

To set up the app, a few IDs and values need to be retrieved from the Lovebox API. The following curl commands help to find
the needed data on an existing account. Make sure you have set up your account via the Android or iOS app already.

### Login with Password

Log in with a password to retrieve the authorization token for the following request.

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

Use the token above to make the "Me Request" to find the configuration needed details.

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

To create a chatbot on Telegram, you need to contact the [@BotFather](https://telegram.me/BotFather), which is a bot
used to create other bots.

The command you need is `/newbot` which leads to the next steps to create your bot. Follow the instructions and get the
bot `username`, and `token`.

### Adjusting SpringBoot's application.properties

Running the app from the source needs adjustments according to your settings. Adjusting the `application.properties` in
the sources or passing them as Java options or CLI arguments to the app.

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

The following snippet can be passed as `.env` and read by the `docker-compose.yml` or used to be passed directly to
the `docker run` command.

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

### Building the Docker Container Locally

```bash
mvn spring-boot:build-image \
  --batch-mode \
  --no-transfer-progress \
  -Dspring-boot.build-image.imageName='patbaumgartner/${project.artifactId}:${project.version}'
```

### Building the Docker Container and Pushing to Docker Hub

```bash
mvn spring-boot:build-image \
 --batch-mode \
 --no-transfer-progress \
 -Dspring-boot.build-image.imageName='patbaumgartner/${project.artifactId}:${project.version}' \
 -Dspring-boot.build-image.publish=true \
 -DCI_REGISTRY=https://index.docker.io/v1/ \
 -DCI_REGISTRY_USER=${DCI_REGISTRY_USER} \
 -DCI_REGISTRY_PASSWORD=${DCI_REGISTRY_PASSWORD}
```

### Fixing Known Issues with Missing Fonts

Since the app uses fonts, we need to make sure that fonts are part of the docker container. The containers produced
above throw an exception when using
them `java.lang.NullPointerException: Cannot load from short array because "sun.awt.FontConfiguration.head" is null`

[Andreas Ahlensdorf](https://github.com/aahlenst) describes nicely the font problem in his blog post [Prerequisites for
Font Support in AdoptOpenJDK](https://blog.adoptopenjdk.net/2021/01/prerequisites-for-font-support-in-adoptopenjdk/).

After more research, it seems that the only solution to add fonts to the build pack base image is to create an OCI run
image by extending the base one. See the `Dockerfile.`base-cnb` file and how a patch with the additional font packages
might look like.

Build the `runImage` locally with the following command.

```bash
docker build --no-cache -f Dockerfile.base-cnb -t patbaumgartner/run:base-cnb .
```

Since we run the pull policy in the `mvn spring-boot:build-image` command with IF_NOT_PRESENT, we need to make sure that
the newest version of the builder is locally available. Finally, pass to the `spring-boot-maven-plugin` the `runImage`
to build the docker container containing the fonts.

```bash
mvn spring-boot:build-image \
 --batch-mode \
 --no-transfer-progress \
 -Dspring-boot.build-image.imageName='patbaumgartner/${project.artifactId}:${project.version}' \
 -Dspring-boot.build-image.runImage=patbaumgartner/run:base-cnb \
 -Dspring-boot.build-image.pullPolicy=IF_NOT_PRESENT
```

## Credits

Reverse engineering (unpinning certificates) was done with [APKLab](https://github.com/APKLab/APKLab) and the 
[Lovebox APK](https://www.apkmonk.com/app/love.lovebox.loveboxapp/) provided by [apkmonk](https://www.apkmonk.com). Postman 
was used to capture the REST calls from the mobile app. The article [Capturing Http Requests](https://learning.postman.com/docs/sending-requests/capturing-request-data/capturing-http-requests/) 
covers everything needed. After a Postman update, [new certs](https://learning.postman.com/docs/sending-requests/capturing-request-data/capturing-http-requests/#troubleshooting-certificate-issues) need to be installed.
