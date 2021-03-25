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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

public class NielsenDCRIntegration extends Integration<AppSdk> {
  public static final Factory FACTORY = NielsenDCRIntegrationFactory.create();

  private Timer playheadTimer;
  private AppSdk appSdk;
  private final Logger logger;
  private TimerTask monitorHeadPos;
  private Settings settings;
  long playheadPosition;

  // reusable variables for `airdate` helper method
  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
  private static final Pattern SHORT_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
  private static final Pattern LONG_DATE =
      Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})Z$");

  private static final Map<String, String> CONTENT_FORMATTER =
      Collections.unmodifiableMap(getContentFormatter());
  private ValueMap properties;

  private static Map<String, String> getContentFormatter() {
    Map<String, String> contentFormatter = new LinkedHashMap<>();

    contentFormatter.put("session_id", "sessionId");
    contentFormatter.put("asset_id", "assetId");
    contentFormatter.put("pod_id", "podId");
    contentFormatter.put("total_length", "totalLength");
    contentFormatter.put("full_episode", "fullEpisode");
    contentFormatter.put("content_asset_id", "contentAssetId");
    contentFormatter.put("ad_asset_id", "adAssetId");
    contentFormatter.put("load_type", "loadType");

    return contentFormatter;
  }

  private static final Map<String, String> AD_FORMATTER =
      Collections.unmodifiableMap(getAdFormatter());

  private static Map<String, String> getAdFormatter() {
    Map<String, String> adFormatter = new LinkedHashMap<>();

    adFormatter.put("session_id", "sessionId");
    adFormatter.put("asset_id", "assetId");
    adFormatter.put("pod_id", "podId");
    adFormatter.put("pod_position", "podPosition");
    adFormatter.put("pod_length", "podLength");
    adFormatter.put("total_length", "totalLength");
    adFormatter.put("load_type", "loadType");

    return adFormatter;
  }

  private static final Map<String, String> CONTENT_MAP =
      Collections.unmodifiableMap(getContentMap());

  private static Map<String, String> getContentMap() {
    Map<String, String> contentMap = new LinkedHashMap<>();

    contentMap.put("assetId", "assetid");
    contentMap.put("contentAssetId", "assetid");
    contentMap.put("title", "title");
    contentMap.put("program", "program");

    return contentMap;
  }

  private static final Map<String, String> AD_MAP = Collections.unmodifiableMap(getAdMap());

  private static Map<String, String> getAdMap() {
    Map<String, String> adMap = new LinkedHashMap<>();

    adMap.put("assetId", "assetid");
    adMap.put("type", "type");
    adMap.put("title", "title");

    return adMap;
  }

  static class Settings {
    String adAssetIdPropertyName;
    String contentAssetIdPropertyName;
    String assetIdPropertyName; // deprecated
    String clientIdPropertyName;
    String subbrandPropertyName;
    String contentLengthPropertyName;
    Boolean sendCurrentTimeLivestream;

    Settings() {
      // Null by default
      adAssetIdPropertyName = null;
      contentAssetIdPropertyName = null;
      assetIdPropertyName = null; // deprecated
      clientIdPropertyName = null;
      subbrandPropertyName = null;
      contentLengthPropertyName = null;
      sendCurrentTimeLivestream = false;
    }
  }

  NielsenDCRIntegration(AppSdk appSdk, Settings settings, Logger logger) {
    this.appSdk = appSdk;
    this.settings = settings;
    this.logger = logger;
  }

  private void startPlayheadTimer(final ValueMap properties, final AppSdk nielsen) {
    if (playheadTimer != null) {
      return;
    }
    playheadPosition = getPlayheadPosition(properties);
    playheadTimer = new Timer();
    monitorHeadPos =
        new TimerTask() {

          @Override
          public void run() {
            setPlayheadPosition();
          }

          void setPlayheadPosition() {
            nielsen.setPlayheadPosition(playheadPosition);
            playheadPosition++;
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

  private long getPlayheadPosition(@NonNull ValueMap properties) {
    int playheadPosition = properties.getInt("position", 0);
    boolean isLiveStream = properties.getBoolean("livestream", false);

    if (!isLiveStream) {
      return playheadPosition;
    }

    Calendar calendar = Calendar.getInstance();
    long millis = calendar.getTimeInMillis();
    if (settings.sendCurrentTimeLivestream) {
      long currentUtcTime = TimeUnit.MILLISECONDS.toSeconds(millis);
      return currentUtcTime;
    } else {
      long utcOffsetTime = TimeUnit.MILLISECONDS.toSeconds(millis) + playheadPosition;
      return utcOffsetTime;
    }
  }

  /**
   * For Segment-specced video event properties, this helper method maps keys in snake_case to
   * camelCase. The actual content and ad property mapping logic in this SDK only handles camelCase
   * property keys, even though Segment's video spec requires all keys in snake_case format.
   *
   * <p>Segment's video spec: https://segment.com/docs/spec/video/
   *
   * @param properties Segment event payload properties
   * @param formatter Either CONTENT_FORMATTER or AD_FORMATTER
   * @return properties Segment event payload properties with keys formatter per Segment video spec
   */
  private ValueMap toCamelCase(
      @NonNull ValueMap properties, @NonNull Map<String, String> formatter) {
    ValueMap mappedProperties = new ValueMap();
    mappedProperties.putAll(properties);

    for (Map.Entry<String, String> entry : formatter.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (mappedProperties.get(key) != null) {
        mappedProperties.put(value, mappedProperties.get(key));
        mappedProperties.remove(key);
      }
    }

    return mappedProperties;
  }

  private JSONObject mapSpecialKeys(
      @NonNull ValueMap properties, @NonNull Map<String, String> mapper) throws JSONException {
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
      @NonNull ValueMap properties, @NonNull Map<String, ?> options) throws JSONException {

    JSONObject contentMetadata = mapSpecialKeys(properties, CONTENT_MAP);

    // map payload options to Nielsen content metadata fields
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

    if (options.containsKey("hasAds")
        && options.get("hasAds") != null
        && "true".equals(String.valueOf(options.get("hasAds")))) {
      contentMetadata.put("hasAds", "1");
    } else {
      contentMetadata.put("hasAds", "0");
    }

    // map settings to Nielsen content metadata fields
    String contentAssetId;
    if (settings.contentAssetIdPropertyName != null) {
      contentAssetId = properties.getString(settings.contentAssetIdPropertyName);
    } else if (properties.getString("assetId") != null) {
      contentAssetId = properties.getString("assetId");
    } else {
      contentAssetId = properties.getString("contentAssetId");
    }
    contentMetadata.put("assetid", contentAssetId);

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
      String length = properties.getString(lengthPropertyName);
      contentMetadata.put("length", length);
    }

    // map properties with non-String values to Nielsen content metadata fields
    if (properties.containsKey("airdate")) {
      String airdate = properties.getString("airdate");
      if (airdate != null && !airdate.isEmpty()) {
        airdate = formatAirdate(properties.getString("airdate"));
      }
      contentMetadata.put("airdate", airdate);
    }

    String adLoadType = "";
    if (options.containsKey("adLoadType")) {
      adLoadType = String.valueOf(options.get("adLoadType"));
    }
    if (adLoadType.isEmpty() || adLoadType.equals("null")) {
      if (properties.containsKey("loadType")) {
        adLoadType = properties.getString("loadType");
      }
    }
    if (adLoadType.equals("dynamic")) {
      contentMetadata.put("adloadtype", "2");
    } else {
      contentMetadata.put("adloadtype", "1");
    }

    boolean fullEpisodeStatus = properties.getBoolean("fullEpisode", false);
    contentMetadata.put("isfullepisode", fullEpisodeStatus ? "y" : "n");
    contentMetadata.put("type", "content");

    return contentMetadata;
  }

  private @NonNull JSONObject buildAdMetadata(@NonNull ValueMap properties) throws JSONException {

    JSONObject adMetadata = mapSpecialKeys(properties, AD_MAP);

    String adAssetIdPropertyName =
        (settings.adAssetIdPropertyName != null) ? settings.adAssetIdPropertyName : "assetId";
    String assetId = properties.getString(adAssetIdPropertyName);
    adMetadata.put("assetid", assetId);

    String adType = properties.getString("type");
    if (adType != null && !adType.isEmpty()) {
      adType = adType.replace("-", "");
    } else {
      adType = "ad";
    }
    adMetadata.put("type", adType);

    String title = String.valueOf(properties.get("title"));
    adMetadata.put("title", title);

    return adMetadata;
  }

  public String formatAirdate(String airdate) {
    String finalDate = airdate;

    // assuming 'airdate' was passed as ISO date string per Segment spec
    try {
      Matcher s = SHORT_DATE.matcher(finalDate);
      Matcher l = LONG_DATE.matcher(finalDate);

      if (s.find()) {
        finalDate =
            new StringBuilder() //
                .append(s.group(1))
                .append(s.group(2))
                .append(s.group(3))
                .append(" ")
                .append("00")
                .append(":")
                .append("00")
                .append(":")
                .append("00")
                .toString();
      } else if (l.find()) {
        finalDate =
            new StringBuilder() //
                .append(l.group(1))
                .append(l.group(2))
                .append(l.group(3))
                .append(" ")
                .append(l.group(4))
                .append(":")
                .append(l.group(5))
                .append(":")
                .append(l.group(6))
                .toString();
      } else {
        throw new Error("Error parsing airdate from ISO date format.");
      }
    } catch (Throwable e) {
      logger.verbose("Error parsing airdate from ISO date format.");

      // if above fail, treat as Date object
      try {
        finalDate = FORMATTER.format(FORMATTER.parse(airdate));
      } catch (ParseException ex) {
        logger.verbose("Error parsing Date object. Will not reformat date string.");
      }
    }

    return finalDate;
  }

  private void trackVideoPlayback(
      TrackPayload track, ValueMap properties, Map<String, Object> nielsenOptions)
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
      case "Video Playback Seek Completed":
      case "Video Playback Buffer Completed":
        startPlayheadTimer(properties, appSdk);
        appSdk.play(channelInfo);
        logger.verbose("appSdk.play(%s)", channelInfo);
        break;
      case "Video Playback Paused":
      case "Video Playback Seek Started":
      case "Video Playback Buffer Started":
        stopPlayheadTimer();
        appSdk.stop();
        logger.verbose("appSdk.stop()");
        break;
      case "Video Playback Interrupted":
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

    ValueMap contentProperties = toCamelCase(properties, CONTENT_FORMATTER);
    JSONObject contentMetadata = buildContentMetadata(contentProperties, nielsenOptions);

    switch (event) {
      case "Video Content Started":
        startPlayheadTimer(contentProperties, appSdk);
        appSdk.loadMetadata(contentMetadata);
        logger.verbose("appSdk.loadMetadata(%s)", contentMetadata);
        break;

      case "Video Content Playing":
        startPlayheadTimer(contentProperties, appSdk);
        break;

      case "Video Content Completed":
        appSdk.stop();
        stopPlayheadTimer();
        break;
    }
  }

  private void trackVideoAd(
      TrackPayload track, Properties properties, Map<String, Object> nielsenOptions)
      throws JSONException {
    String event = track.event();

    ValueMap adProperties = toCamelCase(properties, AD_FORMATTER);

    switch (event) {
      case "Video Ad Started":
        // In case of ad `type` preroll, call `loadMetadata` with metadata values for content,
        // followed by `loadMetadata` with ad (preroll) metadata
        if ("pre-roll".equals(properties.getString("type"))) {
          if (properties.containsKey("content") && !properties.getValueMap("content").isEmpty()) {
            ValueMap contentMap = properties.getValueMap("content");
            ValueMap contentProperties = toCamelCase(contentMap, CONTENT_FORMATTER);
            JSONObject adContentAsset = buildContentMetadata(contentProperties, nielsenOptions);
            appSdk.loadMetadata(adContentAsset);
            logger.verbose("appSdk.loadMetadata(%s)", adContentAsset);
          }
        }
        JSONObject adAsset = buildAdMetadata(adProperties);
        appSdk.loadMetadata(adAsset);
        logger.verbose("appSdk.loadMetadata(%s)", adAsset);
        startPlayheadTimer(adProperties, appSdk);
        break;

      case "Video Ad Playing":
        startPlayheadTimer(adProperties, appSdk);
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
      case "Video Playback Seek Started":
      case "Video Playback Seek Completed":
      case "Video Playback Buffer Started":
      case "Video Playback Buffer Completed":
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
