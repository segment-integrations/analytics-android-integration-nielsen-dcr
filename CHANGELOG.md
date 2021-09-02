1.4.0 / 2021-09-02
==================
  * Updates `Video Playback Started` to call play and load content metadata.
  * Updates `Video Playback Interrupted` to call stop.
  * Maps `Video Playback Exited` to call stop.
  * Updates `airdate` formatter to detect ISO timestamp with milliseconds.
  * Adds support for setting to map custom property to Nielsen's page/screen `section`, with fallback to `name` field.

1.3.2 / 2021-03-26
==================
  * Add `sendCurrentTimeLivestream` setting to instantiation code in `NielsenDCRIntegrationFactory`.
  * Add fallback to `false` for `sendCurrentTimeLivestream` setting.
  * Add unit test for "Video Playback Started" for livestream videos.
  * Update build dependencies.

1.3.1 / 2020-05-19
==================
  * Updates `getPlayheadPosition` to set the `playheadPosition` to the `currentTime` when the new setting `sendCurrentTimeLivestream` is enabled on livestream videos.

1.3.0 / 2019-10-18
==================

  * [DEST-1240]
  * Supports playback events "Video Playback Seek Started", "Video Playback Buffer Started"   and "Video Playback Buffer Completed".
  * Supports both `snake_case` and `camelCase` specced video properties.
  * `sfCode` setting hard-coded to "dcr" per Nielsen request.
  * Timer supports livestream offset as value of "position" property.
  * Updates "playheadPosition" property to "position" in unit tests.

1.2.4 / 2019-09-17
==================

  * Add fallback to `load_type` (snake_case) property key if `loadType` key(camelCase) is not present

1.2.3 / 2019-09-09
==================

  * Update property mappings for `assetid`, `isFullEpisode` and `length`
  * Add fallback to look for `loadType` in event properties if `adLoadType` not in options

1.2.2 / 2019-08-30
==================

  * Format `airdate` property as yyyymmdd hh:MM:ss

1.2.1 / 2019-08-08
==================

  * Expose underlying Nielsen SDK instance

1.2.0 / 2019-08-05
==================

  * Add setting for custom ad asset ID property name

1.1.3 / 2019-07-19
==================

  * Add custom length property name

1.1.2 / 2019-07-17
==================

  * [DEST-870][DEST-880] Add custom properties for assetId, clientId and subbrand (#7)

1.1.1 / 2019-07-11
==================

  * [DEST-865] Add `hasAds` and `crossId2` to content metadata (#6)

1.1.0 / 2019-07-10
==================

  * [DEST-823] Add debug setting (#5)
  * Update build and tests (#4)
  * Adds gif to show how to download SDK
  * Update/java fmt plugin (#3)
  * Update android-sdk license (#2)
  * Prepare next development version.

1.0.0-beta / 2017-09-13
===================================

  * Initial Beta Release
