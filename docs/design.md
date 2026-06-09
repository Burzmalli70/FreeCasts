# Freecasts App

## Summary
This app will be a podcast downloader, organizer, and player.

## Major Features
- Podcast discovery
- Podcast subscribing
- Episode download
- Playlist creation and organization
- Episode playback

## Podcast discovery
The user will be able to:
- Search for podcasts using the iTunes API
- Enter the URL for a specific podcast's RSS feed in order to view and subscribe to that podcast

## Podcast subscribing
Subscribing to a podcast means:
- The app will periodically check for new podcast episodes
- The user will be able to track which episodes of the podcast that they have listened to
- The user will be able to set settings for subscribed podcasts regarding:
  - Whether to download new episodes
  - Filtering episodes to download or skip (and mark as listened)
  - Playlists to add new episodes to automatically
  - Whether to delete a downloaded episode based on:
    - After listening
    - Keep based on some defined criteria about the episode (e.g. if it's a favorite or part of a specific playlist)
    - Keep a maximum number of downloads for the podcast and delete the oldest

## Episode download
- Downloading will use the Android DownloadManager API
- Downloaded files will be kept in a "podcasts" folder in the app's internal storage
- The app will have a setting for whether to download over wifi only, or if episodes can be downloaded over metered internet
- The user will be able to set a number of concurrent downloads allowed

## Playlist creation and organization
- The user will be able to create playlists that podcasts can be set to automatically add downloaded episodes to
- When automatically adding episodes to a playlist, only unplayed episodes will be added, and only the most recent unplayed episode for each podcast.
- Playlists will include a setting indicating whether played podcast episodes should automatically be removed from the playlist after listening
- The user will be able to tap a "Random" button when viewing a playlist that begins playing a random episode from the list. When that episode ends, the app will then play another random episode from the same playlist.

## Episode playback
- The app will use the Android MediaPlayer API to play episodes. 
- The app will be able to play downloaded episodes, but also play an episode from a URL.
- The app will keep track of how many times the user has listened to an episode. For this count, an episode will be considered "listened to" if the user listens for more than half of the episode or 5 minutes, whichever is less.
- If the user interrupts playback of an episode, the app will keep track of the timestamp where playback ended and the user will be able to resume from that position in the future, even if they listen to a different episode and then come back to it.
- An episode will be considered "played" if it has been listened to at least once.
- Episodes considered to be "played" will appear with dimmer text when in episode lists on podcast detail screens.