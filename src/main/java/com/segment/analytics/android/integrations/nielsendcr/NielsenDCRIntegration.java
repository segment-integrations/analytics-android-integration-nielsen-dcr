package com.segment.analytics.android.integrations.nielsendcr;

import android.support.annotation.NonNull;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

import com.segment.analytics.Properties;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import com.nielsen.app.sdk.*;

import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

public class NielsenDCRIntegration extends Integration<AppSdk> {
  public static final Factory FACTORY = NielsenDCRIntegrationFactory.create();

  private Timer playheadTimer;
  private AppSdk appSdk;
  private final Logger logger;
  private TimerTask monitorHeadPos;
  int playheadPosition;

  NielsenDCRIntegration(AppSdk appSdk, Logger logger) {
    this.appSdk = appSdk;
    this.logger = logger;
  }

  private void startPlayheadTimer(final Properties properties, final AppSdk nielsen) {
    if (playheadTimer != null) { return; }
    playheadTimer = new Timer();
    playheadPosition = properties.getInt("position", 0);
    monitorHeadPos =
        new TimerTask() {
          boolean isLiveStream =
              "content".equals(properties.getString("type"))
                  && properties.getBoolean("livestream", false);

          @Override
          public void run() {
            setPlayheadPosition();
          }

          void setPlayheadPosition() {
            playheadPosition++;
            if (!isLiveStream) {
              nielsen.setPlayheadPosition(playheadPosition);
              return;
            }

            // If event is livestream, ignore playheadPosition
            // and set number of seconds from midnight of the day in UTC.
            Calendar calendar = Calendar.getInstance();
            long millis = calendar.getTimeInMillis();
            long utcTime = TimeUnit.MILLISECONDS.toSeconds(millis);
            nielsen.setPlayheadPosition(utcTime);
          }
        };

    logger.verbose("playheadTimer scheduled");
    playheadTimer.schedule(monitorHeadPos, 0, TimeUnit.SECONDS.toMillis(1));
  }

  private void stopPlayheadTimer() {
    if (playheadTimer != null) {
      playheadTimer.cancel();
      monitorHeadPos.cancel();
      playheadTimer = null;
      logger.verbose("playheadTimer stopped");
    }
  }

  private JSONObject mapSpecialKeys(
      @NonNull Properties properties, @NonNull Map<String, String> mapper) throws JSONException {
    JSONObject metadata = new JSONObject();

    // Map special keys and preserve only the special keys.
    for (Map.Entry<String, ?> entry : properties.entrySet()) {
      String key = entry.getKey();
      String mappedKey = mapper.get(key);
      Object value = entry.getValue();
      if (!isNullOrEmpty(mappedKey)) {
        metadata.put(mappedKey, String.valueOf(value));
      }
    }

    return metadata;
  }

  private @NonNull JSONObject buildContentMetadata(
      @NonNull Properties properties,
      @NonNull Map<String, ?> options,
      @NonNull Map<String, String> mapper)
      throws JSONException {

    JSONObject contentMetadata = mapSpecialKeys(properties, mapper);

    if (options.containsKey("pipmode")) {
      String pipmode = String.valueOf(options.get("pipmode"));
      contentMetadata.put("pipmode", pipmode);
    } else {
      contentMetadata.put("pipmode", "false");
    }

    if(options.containsKey("crossId1")) {
      String crossId1 = String.valueOf(options.get("crossId1"));
      contentMetadata.put("crossId1", crossId1);
    }

    if (options.containsKey("segB")) {
      String segB = String.valueOf(options.get("segB"));
      contentMetadata.put("segB", segB);
    }

    if (options.containsKey("segC")) {
      String segC = String.valueOf(options.get("segC"));
      contentMetadata.put("segC", segC);
    }

    int contentAssetId = properties.getInt("assetId", 0);
    contentMetadata.put("assetid", String.valueOf(contentAssetId));

    if (properties.containsKey("totalLength")) {
      int length = properties.getInt("totalLength", 0);
      contentMetadata.put("length", String.valueOf(length));
    }

    if (properties.containsKey("title")) {
      String title = properties.getString("title");
      contentMetadata.put("title", title);
    }

    if (properties.containsKey("program")) {
      String program = properties.getString("program");
      contentMetadata.put("program", program);
    }

    if (properties.containsKey("airdate")) {
      String airdate = properties.getString("airdate");
      contentMetadata.put("airdate", airdate);
    }

    String adLoadType = String.valueOf(options.get("adLoadType"));
    if (adLoadType.equals("dynamic")) {
      contentMetadata.put("adloadtype", "2");
    } else {
      contentMetadata.put("adloadtype", "1");
    }

    boolean fullEpisodeStatus = properties.getBoolean("fullEpisode", false);
    contentMetadata.put("isfullepisode", fullEpisodeStatus ? "y" : "sf");

    contentMetadata.put("type", "content");

    return contentMetadata;
  }

  private @NonNull JSONObject buildAdMetadata(
      @NonNull Properties properties, @NonNull Map<String, String> mapper) throws JSONException {

    JSONObject adMetadata = mapSpecialKeys(properties, mapper);

    int assetId = properties.getInt("assetId", 0);
    adMetadata.put("assetid", String.valueOf(assetId));

    String adType = properties.getString("type");
    adMetadata.put("type", adType);

    String title = String.valueOf(properties.get("title"));
    adMetadata.put("title", title);

    return adMetadata;
  }

  private @NonNull JSONObject buildAdContentMetadata(
      @NonNull Properties properties,
      @NonNull Map<String, ?> options,
      @NonNull Map<String, String> mapper)
      throws JSONException {

    JSONObject adContentMetadata = mapSpecialKeys(properties, mapper);

    if (options.containsKey("pipmode")) {
      String pipmode = String.valueOf(options.get("pipmode"));
      adContentMetadata.put("pipmode", pipmode);
    } else {
      adContentMetadata.put("pipmode", "false");
    }

    if(options.containsKey("crossId1")) {
      String crossId1 = String.valueOf(options.get("crossId1"));
      adContentMetadata.put("crossId1", crossId1);
    }

    if (options.containsKey("segB")) {
      String segB = String.valueOf(options.get("segB"));
      adContentMetadata.put("segB", segB);
    }

    if (options.containsKey("segC")) {
      String segC = String.valueOf(options.get("segC"));
      adContentMetadata.put("segC", segC);
    }

    int contentAssetId = properties.getInt("contentAssetId", 0);
    adContentMetadata.put("assetid", String.valueOf(contentAssetId));

    if (properties.containsKey("totalLength")) {
      int length = properties.getInt("totalLength", 0);
      adContentMetadata.put("length", String.valueOf(length));
    }

    if (properties.containsKey("title")) {
      String title = properties.getString("title");
      adContentMetadata.put("title", title);
    }

    if (properties.containsKey("program")) {
      String program = properties.getString("program");
      adContentMetadata.put("program", program);
    }

    if (properties.containsKey("airdate")) {
      String airdate = properties.getString("airdate");
      adContentMetadata.put("airdate", airdate);
    }

    String adLoadType = String.valueOf(options.get("adLoadType"));
    if (adLoadType.equals("dynamic")) {
      adContentMetadata.put("adloadtype", "2");
    } else {
      adContentMetadata.put("adloadtype", "1");
    }

    boolean fullEpisodeStatus = properties.getBoolean("fullEpisode", false);
    adContentMetadata.put("isfullepisode", fullEpisodeStatus ? "y" : "sf");

    adContentMetadata.put("type", "content");

    return adContentMetadata;
  }

  private void trackVideoPlayback(
      TrackPayload track, Properties properties, Map<String, Object> nielsenOptions)
      throws JSONException {
    String event = track.event();

    JSONObject channelInfo = new JSONObject();

    if (nielsenOptions.containsKey("channelName")) {
      channelInfo.put("channelName", String.valueOf(nielsenOptions.get("channelName")));
    } else {
      channelInfo.put("channelName", "defaultChannelName");
    }

    if (nielsenOptions.containsKey("mediaUrl")) {
      channelInfo.put("mediaURL", String.valueOf(nielsenOptions.get("mediaUrl")));
    } else {
      channelInfo.put("mediaURL", "");
    }

    switch (event) {
      case "Video Playback Started":
      case "Video Playback Resumed":
        startPlayheadTimer(properties, appSdk);
        appSdk.play(channelInfo);
        logger.verbose("appSdk.play(%s)", channelInfo);
        break;
      case "Video Playback Paused":
      case "Video Playback Interrupted":
        stopPlayheadTimer();
        appSdk.stop();
        logger.verbose("appSdk.stop()");
        break;
      case "Video Playback Seek Completed":
        startPlayheadTimer(properties, appSdk);
        break;
      case "Video Playback Completed":
        stopPlayheadTimer();
        appSdk.end();
        logger.verbose("appSdk.end()");
        break;
    }
  }

  private void trackVideoContent(
      TrackPayload track, Properties properties, Map<String, Object> nielsenOptions)
      throws JSONException {
    String event = track.event();

    Map<String, String> contentMapper = new LinkedHashMap<>();
    contentMapper.put("assetId", "assetid");
    contentMapper.put("title", "title");
    contentMapper.put("program", "program");
    contentMapper.put("totalLength", "length");
    contentMapper.put("airdate", "airdate");

    JSONObject contentMetadata = buildContentMetadata(properties, nielsenOptions, contentMapper);

    switch (event) {
      case "Video Content Started":
        startPlayheadTimer(properties, appSdk);
        appSdk.loadMetadata(contentMetadata);
        logger.verbose("appSdk.loadMetadata(%s)", contentMetadata);
        break;

      case "Video Content Playing":
        startPlayheadTimer(properties, appSdk);
        break;

      case "Video Content Completed":
        appSdk.end();
        stopPlayheadTimer();
        break;
    }
  }

  public void trackVideoAd(
      TrackPayload track, Properties properties, Map<String, Object> nielsenOptions)
      throws JSONException {
    String event = track.event();

    Map<String, String> adMapper = new LinkedHashMap<>();
    adMapper.put("assetId", "assetid");
    adMapper.put("type", "type");
    adMapper.put("title", "title");

    switch (event) {
      case "Video Ad Started":
        // In case of ad `type` preroll, call `loadMetadata` with metadata values for content,
        // followed by `loadMetadata` with ad (preroll) metadata
        if ("pre-roll".equals(properties.getString("type"))) {
          JSONObject adContentAsset = buildAdContentMetadata(properties, nielsenOptions, adMapper);
          appSdk.loadMetadata(adContentAsset);
          logger.verbose("appSdk.loadMetadata(%s)", adContentAsset);
        }
        JSONObject adAsset = buildAdMetadata(properties, adMapper);
        appSdk.loadMetadata(adAsset);
        logger.verbose("appSdk.loadMetadata(%s)", adAsset);
        startPlayheadTimer(properties, appSdk);
        break;

      case "Video Ad Playing":
        startPlayheadTimer(properties, appSdk);
        break;

      case "Video Ad Completed":
        stopPlayheadTimer();
        appSdk.stop();
        logger.verbose("appSdk.stop");
        break;
    }
  }

  @Override
  public void track(TrackPayload track) {
    String event = track.event();
    Properties properties = track.properties();

    Map<String, Object> nielsenOptions = track.integrations().getValueMap("nielsen-dcr");
    if (isNullOrEmpty(nielsenOptions)) {
      nielsenOptions = Collections.emptyMap();
    }

    switch (event) {
      case "Video Playback Started":
      case "Video Playback Paused":
      case "Video Playback Interrupted":
      case "Video Playback Seek Completed":
      case "Video Playback Resumed":
        try {
          trackVideoPlayback(track, properties, nielsenOptions);
        } catch (JSONException e) {
          logger.verbose("Error tracking Video Playback:", e);
        }
        break;
      case "Video Content Started":
      case "Video Content Playing":
      case "Video Content Completed":
        try {
          trackVideoContent(track, properties, nielsenOptions);
        } catch (JSONException e) {
          logger.verbose("Error tracking Video Content:", e);
        }
        break;
      case "Video Ad Started":
      case "Video Ad Playing":
      case "Video Ad Completed":
        try {
          trackVideoAd(track, properties, nielsenOptions);
        } catch (JSONException e) {
          logger.verbose("Error tracking Video Ad:", e);
        }
        break;
    }
  }

  @Override
  public void screen(ScreenPayload screen) {
    String name = screen.name();
    JSONObject metadata = new JSONObject();

    Map<String, Object> nielsenOptions = screen.integrations().getValueMap("nielsen-dcr");
    if(isNullOrEmpty(nielsenOptions)) {
      nielsenOptions = Collections.emptyMap();
    }


    try {
      metadata.put("name", name);
      metadata.put("type", "static");

      // segB and segC are required values, so will send a default value
      if (nielsenOptions.containsKey("segB")) {
        String segB = String.valueOf(nielsenOptions.get("segB"));
        metadata.put("segB", segB);
      } else {
        metadata.put("segB", "");
      }

      if (nielsenOptions.containsKey("segC")) {
        String segC = String.valueOf(nielsenOptions.get("segC"));
        metadata.put("segC", segC);
      }else {
        metadata.put("segC", "");
      }

      if(nielsenOptions.containsKey("crossId1")) {
        String crossId1 = String.valueOf(nielsenOptions.get("crossId1"));
        metadata.put("crossId1", crossId1);
      }

    } catch (JSONException e) {
      e.printStackTrace();
      logger.verbose("Error tracking Video Content:", e);

    }

    appSdk.loadMetadata(metadata);
    logger.verbose("appSdk.loadMetadata(%s)", metadata);

  }
}
