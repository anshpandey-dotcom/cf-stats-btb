# Unknown

Two-phone horror chat for live streams. Both phones look like a WhatsApp chat with a contact called Unknown.

- Ghost phone: opens the control room (scares, story autoplay, edit and delete, fake calls, sound board). It creates the live link.
- Host phone: plain chat only. Paste the live link once and it reconnects by itself.

Messages travel end to end encrypted (AES-GCM, key derived from the live link) over public MQTT brokers, so the two phones do not need to be online at the same moment and a closed app picks the chat up again when reopened.

APKs are built by the "Build Unknown APKs" workflow and published on the unknown-latest release.
