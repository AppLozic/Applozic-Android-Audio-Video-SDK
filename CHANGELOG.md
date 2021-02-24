
## Changelog

**Version 3.1.0** - 24th February, 2021

- Separated call logic from view (/separate UI)
- Calls will now execute inside/as a service (independently)
- As a result, app can now be killed, restarted etc and it won't affect the ongoing call
- A lot of refactoring and separation of logic

**Version 3.0.0** - 2nd February, 2021 (The Re-birth)

- Basic 1-1 audio call to contact
- Basic 1-1 video call to contact
- Speaker support
- Reconnecting call support
- Call mute, camera switch, loudspeaker on/off, video pause
- Call status views (Paused, Muted, Reconnecting)
- Call notification messages (Call Missed, Call Busy, Audio/Video Call Time)