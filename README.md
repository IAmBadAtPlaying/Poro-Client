# Poro-Client

This is a free and open-source side project that I am working on. This is basically a Proxy and some added features for
the League of Legends client. This allows local websites to access the client's data and perform actions on the client.

## Frontend

The frontend code can be found in the GitHub repository [here](https://github.com/IAmBadAtPlaying/poro-client-frontend).

## Features
- [x] Proxy - Allows local websites to access the client's data and view for yourself whats going on.
- [x] Tasks - Automate workflows such as Accepting Ready Checks, Picking Champions, etc.
- [x] Easy Disenchanting / Re-Rolling - Disenchant multiple items at once or re-roll multiple items at once.
- [x] Create your own tasks - *Work in progress*

## Security
The LCU API allows access to Bearer Tokens used for the shop and general identification and authentication. **This is 
why you should only upload tasks that you have written and compiled yourselves** as they have full access to your client
and therefore your Tokens.
From the outside these tokens cannot be accessed from websites, as the Poro-Client will check the Origin header of the
request that is always send by the browser. If the Origin header does not match the Poro-Client Server, the request will
be denied.
This means that the only way to access the LCU API is by using the Poro-Client Server or by manually setting the Origin
Header, but that means that an application on your computer is trying to access the Data.

## How it works
Basically the League of Legends UI that you see is a web application (Chromium Application with Ember.js) that connects
to the League Client Update API (LCU).  
Once started the Poro-Client will look for the League of Legends UX Process and gather the necessary information to 
connect to the LCU from the process parameters. As soon as we have the necessary information we will connect to the
websocket of the backend as this will notify us of any changes in the client (For example: A user joined your lobby or
a friend came online).  
If you want to learn more how the connection to the LCU is established, you can look at the ConnectionStatemachine

## Disclaimer
**Poro-Client** *isn't endorsed by Riot Games and doesn't reflect the views or opinions of Riot Games or anyone officially involved in producing or managing Riot Games properties. Riot Games, and all associated properties are trademarks or registered trademarks of Riot Games, Inc.*