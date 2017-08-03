package com.segment.analytics.android.integrations.nielsendcr;


import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.mockito.Matchers.argThat;

import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import android.content.pm.PackageManager;
import com.nielsen.app.sdk.AppSdk;
import com.segment.analytics.Analytics;
import com.segment.analytics.Options;

import com.segment.analytics.Properties;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.test.ScreenPayloadBuilder;
import com.segment.analytics.test.TrackPayloadBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 18, manifest = Config.NONE)

@PrepareForTest(AppSdk.class) public class NielsenDCRTest {

  @Mock AppSdk nielsen;
  @Mock Analytics analytics;
  private Logger logger;
  private NielsenDCRIntegration integration;

  @Before public void setUp() throws PackageManager.NameNotFoundException {
    initMocks(this);

    logger = Logger.with(Analytics.LogLevel.DEBUG);

    integration = new NielsenDCRIntegration(nielsen, logger);
  }

  @After public void validate() {
    validateMockitoUsage();
  }

  @Test public void videoPlaybackStarted() throws JSONException {
    integration.track(
        new TrackPayloadBuilder().event("Video Playback Started").properties(new Properties() //
            .putValue("assetId", 1234)
            .putValue("adType", "pre-roll")
            .putValue("totalLength", 120)
            .putValue("videoPlayer", "youtube")
            .putValue("sound", 80)
            .putValue("bitrate", 40)
            .putValue("fullScreen", true)).build());

    JSONObject expected = new JSONObject();
    expected.put("channelName", "defaultChannelName");
    expected.put("mediaURL", "");

    verify(nielsen).play(jsonEq(expected));
  }

  @Test public void videoPlaybackPaused() {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("channelName", "exampleChannelName");

    integration.track(new TrackPayloadBuilder() //
        .event("Video Playback Paused")
        .properties(new Properties() //
            .putValue("assetId", 1234)
            .putValue("adType", "mid-roll")
            .putValue("totalLength", 100)
            .putValue("videoPlayer", "vimeo")
            .putValue("playbackPosition", 10)
            .putValue("fullScreen", true)
            .putValue("bitrate", 50)
            .putValue("sound", 80))
        .options(new Options().setIntegrationOptions("nielsen-dcr", nielsenOptions))
        .build());

    verify(nielsen).stop();
  }

  @Test public void videoPlaybackResumed() throws JSONException {

    integration.track(
        new TrackPayloadBuilder().event("Video Playback Resumed").properties(new Properties() //
            .putValue("assetId", 5332)
            .putValue("adType", "post-roll")
            .putValue("totalLength", 100)
            .putValue("videoPlayer", "youtube")
            .putValue("playbackPosition", 60)
            .putValue("fullScreen", true)
            .putValue("bitrate", 500)
            .putValue("sound", 80)).build());

    JSONObject expected = new JSONObject();
    expected.put("channelName", "defaultChannelName");
    expected.put("mediaURL", "");

    verify(nielsen).play(jsonEq(expected));
  }

  @Test public void videoContentStarted() throws JSONException {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segA", "segmentA");

    integration.track(
        new TrackPayloadBuilder().event("Video Content Started").properties(new Properties() //
            .putValue("assetId", 123214)
            .putValue("title", "Look Who's Purging Now")
            .putValue("season", 2)
            .putValue("episode", 9)
            .putValue("genre", "cartoon")
            .putValue("program", "Rick and Morty")
            .putValue("channel", "cartoon network")
            .putValue("publisher", "Turner Broadcasting System")
            .putValue("fullEpisode", true)
            .putValue("podId", "segment A")
            .putValue("playbackPosition", 70))
        .options(new Options().setIntegrationOptions("nielsen-dcr", nielsenOptions))
        .build());

    JSONObject expected = new JSONObject();
    expected.put("assetid", "123214");
    expected.put("title", "Look Who's Purging Now");
    expected.put("program", "Rick and Morty");
    expected.put("segA", "segmentA");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }

  @Test public void videoAdStarted() throws JSONException {

    integration.track(
        new TrackPayloadBuilder().event("Video Ad Started").properties(new Properties() //
            .putValue("assetId", 4311)
            .putValue("podId", "adSegmentA")
            .putValue("type", "mid-roll")
            .putValue("totalLength", 120)
            .putValue("playbackPosition", 0)
            .putValue("title", "Helmet Ad")).build());

    JSONObject expected = new JSONObject();
    expected.put("assetid", "4311");
    expected.put("type", "mid-roll");
    expected.put("title", "Helmet Ad");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }

  @Test public void videoAdStartedWithTypeMidRoll() throws JSONException {

    integration.track(
        new TrackPayloadBuilder().event("Video Ad Started").properties(new Properties() //
            .putValue("assetId", 4311)
            .putValue("podId", "adSegmentA")
            .putValue("type", "mid-roll")
            .putValue("totalLength", 120)
            .putValue("loadType", "linear")
            .putValue("position", 20)
            .putValue("contentAssetId", 1234)
            .putValue("playbackPosition", 0)
            .putValue("title", "Helmet Ad")).build());

    JSONObject adExpected = new JSONObject();
    adExpected.put("assetid", "1234");
    adExpected.put("type", "mid-roll");
    adExpected.put("title", "Helmet Ad");

    verify(nielsen).loadMetadata(jsonEq(adExpected));
  }

  @Test public void videoAdCompleted() {

    integration.track(
        new TrackPayloadBuilder().event("Video Ad Completed").properties(new Properties() //
            .putValue("assetId", 3425)
            .putValue("podId", "adSegmentb")
            .putValue("type", "mid-roll")
            .putValue("totalLength", 100)
            .putValue("playbackPosition", 100)
            .putValue("title", "Helmet Ad")).build());

    verify(nielsen).stop();
  }

  public static JSONObject jsonEq(JSONObject expected) {
    return argThat(samePropertyValuesAs(expected));
  }

  @Test public void screen() throws JSONException {

    integration.screen(
        new ScreenPayloadBuilder().name("Home").properties(new Properties() //
            .putValue("variation", "blue sign up button"))
            .build());
    JSONObject expected = new JSONObject();
    expected.put("name", "Home");
    expected.put("type", "static");
    expected.put("segB", "");
    expected.put("segC", "");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }
  @Test public void screenWithOptions() throws JSONException {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("segC", "segmentC");
    nielsenOptions.put("crossId1", "crossIdValue");

    integration.screen(
        new ScreenPayloadBuilder().name("Home").properties(new Properties() //
            .putValue("variation", "blue sign up button"))
            .options(new Options()
            .setIntegrationOptions("nielsen-dcr", nielsenOptions))
            .build());

    JSONObject expected = new JSONObject();
    expected.put("name", "Home");
    expected.put("type", "static");
    expected.put("segB", "segmentB");
    expected.put("segC", "segmentC");
    expected.put("crossId1", "crossIdValue");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }
}