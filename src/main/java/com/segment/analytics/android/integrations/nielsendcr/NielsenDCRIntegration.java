package com.segment.analytics.android.integrations.nielsendcr;

import android.support.annotation.NonNull;

import com.nielsen.app.sdk.AppSdk;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

public class NielsenDCRIntegration extends Integration<AppSdk> {
  public static final Factory FACTORY = NielsenDCRIntegrationFactory.create();

  private Timer playheadTimer;
  private AppSdk appSdk;
  private final Logger logger;
  private TimerTask monitorHeadPos;
  private Settings settings;
  private final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd' 'HH:mm:ss");
  int playheadPosition;

  static class Settings {
    String adAssetIdPropertyName;
    String contentAssetIdPropertyName;
    String assetIdPropertyName; // deprecated
    String clientIdPropertyName;
    String subbrandPropertyName;
    String contentLengthPropertyName;

    Settings() {
      // Null by default
      adAssetIdPropertyName = null;
      contentAssetIdPropertyName = null;
      assetIdPropertyName = null; // deprecated
      clientIdPropertyName = null;
      subbrandPropertyName = null;
      contentLengthPropertyName = null;
    }
  }

  NielsenDCRIntegration(AppSdk appSdk, Settings settings, Logger logger) {
    this.appSdk = appSdk;
    this.settings = settings;
    this.logger = logger;
  }

  private void startPlayheadTimer(final Properties properties, final AppSdk nielsen) {
    if (playheadTimer != null) {
      return;
    }
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

    if (options.containsKey("crossId1")) {
      String crossId1 = String.valueOf(options.get("crossId1"));
      contentMetadata.put("crossId1", crossId1);
    }

    if (options.containsKey("crossId2")) {
      String crossId2 = String.valueOf(options.get("crossId2"));
      contentMetadata.put("crossId2", crossId2);
    }

    if (options.containsKey("segB")) {
      String segB = String.valueOf(options.get("segB"));
      contentMetadata.put("segB", segB);
    }

    if (options.containsKey("segC")) {
      String segC = String.valueOf(options.get("segC"));
      contentMetadata.put("segC", segC);
    }

    String contentAssetIdPropertyName =
        (settings.contentAssetIdPropertyName != null)
            ? settings.contentAssetIdPropertyName
            : "assetId";
    int contentAssetId = properties.getInt(contentAssetIdPropertyName, 0);
    contentMetadata.put("assetid", String.valueOf(contentAssetId));

    String clientIdPropertyName =
        (settings.clientIdPropertyName != null) ? settings.clientIdPropertyName : "clientId";
    String clientId = properties.getString(clientIdPropertyName);
    if (clientId != null && !clientId.isEmpty()) {
      contentMetadata.put("clientid", clientId);
    }

    String subbrandPropertyName =
        (settings.subbrandPropertyName != null) ? settings.subbrandPropertyName : "subbrand";
    String subbrand = properties.getString(subbrandPropertyName);
    if (subbrand != null && !subbrand.isEmpty()) {
      contentMetadata.put("subbrand", subbrand);
    }

    String lengthPropertyName =
        (settings.contentLengthPropertyName != null)
            ? settings.contentLengthPropertyName
            : "totalLength";
    if (properties.containsKey(lengthPropertyName)) {
      int length = properties.getInt(lengthPropertyName, 0);
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
      String airdate = formatAirdate(properties.get("airdate"));
      contentMetadata.put("airdate", airdate);
    }

    String adLoadType = String.valueOf(options.get("adLoadType"));
    if (adLoadType.equals("dynamic")) {
      contentMetadata.put("adloadtype", "2");
    } else {
      contentMetadata.put("adloadtype", "1");
    }

    if (options.containsKey("hasAds") && "true".equals(String.valueOf(options.get("hasAds")))) {
      contentMetadata.put("hasAds", "1");
    } else {
      contentMetadata.put("hasAds", "0");
    }

    boolean fullEpisodeStatus = properties.getBoolean("fullEpisode", false);
    contentMetadata.put("isfullepisode", fullEpisodeStatus ? "y" : "sf");

    contentMetadata.put("type", "content");

    return contentMetadata;
  }

  private @NonNull JSONObject buildAdMetadata(
      @NonNull Properties properties, @NonNull Map<String, String> mapper) throws JSONException {

    JSONObject adMetadata = mapSpecialKeys(properties, mapper);

    String adAssetIdPropertyName =
        (settings.adAssetIdPropertyName != null) ? settings.adAssetIdPropertyName : "assetId";
    int assetId = properties.getInt(adAssetIdPropertyName, 0);
    adMetadata.put("assetid", String.valueOf(assetId));

    String adType = properties.getString("type");
    if (adType != null && !adType.isEmpty()) {
      adType = adType.replace("-", "");
    }
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

    Properties contentProperties = new Properties();
    JSONObject adContentMetadata = new JSONObject(); // initialize to prevent null pointer exception

    if (properties.containsKey("content") && !properties.getValueMap("content").isEmpty()) {
      ValueMap content = properties.getValueMap("content");
      contentProperties.putAll(content);
    }

    if (!contentProperties.isEmpty()) {

      adContentMetadata = mapSpecialKeys(contentProperties, mapper);

      if (options.containsKey("pipmode")) {
        String pipmode = String.valueOf(options.get("pipmode"));
        adContentMetadata.put("pipmode", pipmode);
      } else {
        adContentMetadata.put("pipmode", "false");
      }

      if (options.containsKey("crossId1")) {
        String crossId1 = String.valueOf(options.get("crossId1"));
        adContentMetadata.put("crossId1", crossId1);
      }

      if (options.containsKey("crossId2")) {
        String crossId2 = String.valueOf(options.get("crossId2"));
        adContentMetadata.put("crossId2", crossId2);
      }

      if (options.containsKey("segB")) {
        String segB = String.valueOf(options.get("segB"));
        adContentMetadata.put("segB", segB);
      }

      if (options.containsKey("segC")) {
        String segC = String.valueOf(options.get("segC"));
        adContentMetadata.put("segC", segC);
      }

      boolean fullEpisodeStatus = false;

      String contentAssetIdPropertyName =
          (settings.contentAssetIdPropertyName != null)
              ? settings.contentAssetIdPropertyName
              : "contentAssetId";
      int contentAssetId = contentProperties.getInt(contentAssetIdPropertyName, 0);
      adContentMetadata.put("assetid", String.valueOf(contentAssetId));

      String clientIdPropertyName =
          (settings.clientIdPropertyName != null) ? settings.clientIdPropertyName : "clientId";
      String clientId = contentProperties.getString(clientIdPropertyName);
      if (clientId != null && !clientId.isEmpty()) {
        adContentMetadata.put("clientid", clientId);
      }

      String subbrandPropertyName =
          (settings.subbrandPropertyName != null) ? settings.subbrandPropertyName : "subbrand";
      String subbrand = contentProperties.getString(subbrandPropertyName);
      if (subbrand != null && !subbrand.isEmpty()) {
        adContentMetadata.put("subbrand", subbrand);
      }

      String lengthPropertyName =
          (settings.contentLengthPropertyName != null)
              ? settings.contentLengthPropertyName
              : "totalLength";
      if (contentProperties.containsKey(lengthPropertyName)) {
        int length = contentProperties.getInt(lengthPropertyName, 0);
        adContentMetadata.put("length", String.valueOf(length));
      }

      if (contentProperties.containsKey("title")) {
        String title = contentProperties.getString("title");
        adContentMetadata.put("title", title);
      }

      if (contentProperties.containsKey("program")) {
        String program = contentProperties.getString("program");
        adContentMetadata.put("program", program);
      }

      if (contentProperties.containsKey("airdate")) {
        String airdate = formatAirdate(contentProperties.get("airdate"));
        adContentMetadata.put("airdate", airdate);
      }

      fullEpisodeStatus = contentProperties.getBoolean("fullEpisode", false);

      String adLoadType = String.valueOf(options.get("adLoadType"));
      if (adLoadType.equals("dynamic")) {
        adContentMetadata.put("adloadtype", "2");
      } else {
        adContentMetadata.put("adloadtype", "1");
      }

      if (options.containsKey("hasAds") && "true".equals(String.valueOf(options.get("hasAds")))) {
        adContentMetadata.put("hasAds", "1");
      } else {
        adContentMetadata.put("hasAds", "0");
      }

      adContentMetadata.put("isfullepisode", fullEpisodeStatus ? "y" : "sf");
      adContentMetadata.put("type", "content");
    }

    return adContentMetadata;
  }

  public String formatAirdate(Object airdate) {
    String finalDate = String.valueOf(airdate);

    // assuming 'airdate' is passed as a Date
    try {
      finalDate = formatter.format(airdate);
    } catch (RuntimeException e) {
      logger.verbose("Error parseing airdate as Date. Attempting to parse as String.");
      // fall back to assuming 'airdate' was passed as String if above fails
      try {
        if (finalDate.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")) ;
        finalDate =
            finalDate.substring(0, 4)
                + finalDate.substring(5, 7)
                + finalDate.substring(8, 10)
                + " "
                + finalDate.substring(11, 13)
                + ":"
                + finalDate.substring(14, 16)
                + ":"
                + finalDate.substring(17, 19);
      } catch (RuntimeException ee) {
        throw new Error(ee);
        //        logger.verbose("Error parseing airdate as String.");
      }
    }

    return finalDate;
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
    if (isNullOrEmpty(nielsenOptions)) {
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
      } else {
        metadata.put("segC", "");
      }

      if (nielsenOptions.containsKey("crossId1")) {
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

  @Override
  public AppSdk getUnderlyingInstance() {
    return appSdk;
  }
}
