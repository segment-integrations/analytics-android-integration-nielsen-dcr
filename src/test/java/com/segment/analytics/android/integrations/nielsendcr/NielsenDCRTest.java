package com.segment.analytics.android.integrations.nielsendcr;


import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.nielsen.app.sdk.AppSdk;
import com.nielsen.app.sdk.IAppNotifier;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NielsenDCRTest {

  @Mock com.segment.analytics.Analytics analytics;
  @Mock AppSdk nielsen;
  @Mock NielsenDCRIntegrationFactory.AppSDKFactory appFactory;
  @Mock Application application;
  @Mock Context context;
  @Mock PackageManager packageManager;
  private Logger logger;
  private NielsenDCRIntegration integration;
  private NielsenDCRIntegrationFactory factory;
  private NielsenDCRIntegration.Settings settings;

  @Before
  public void setUp() throws PackageManager.NameNotFoundException {
    MockitoAnnotations.initMocks(this);

    PackageInfo info = new PackageInfo();
    info.packageName = "test";
    info.versionName = "test";

    settings = new NielsenDCRIntegration.Settings();

    Mockito.when(analytics.getApplication()).thenReturn(application);
    Mockito.when(analytics.logger("Nielsen DCR")).thenReturn(Logger.with(Analytics.LogLevel.VERBOSE));
    Mockito.when(application.getApplicationContext()).thenReturn(context);
    Mockito.when(context.getPackageManager()).thenReturn(packageManager);
    Mockito.when(context.getApplicationContext()).thenReturn(context);
    Mockito.when(context.getPackageName()).thenReturn("test");
    Mockito.when(packageManager.getPackageInfo("test", 0)).thenReturn(info);
    logger = Logger.with(Analytics.LogLevel.DEBUG);
    integration = new NielsenDCRIntegration(nielsen, settings, logger);
    factory = new NielsenDCRIntegrationFactory(appFactory);
  }

  @After
  public void validate() {
    validateMockitoUsage();
  }

  @Test
  public void appSdkConfig() throws JSONException {
    ValueMap settings = new ValueMap();
    settings.put("appId", "12345");
    settings.put("sfCode", true);
    settings.put("nolDevDebug", true);

    factory.create(settings, analytics);

    JSONObject expectedConfig = new JSONObject()
            .put("appid", "12345")
            .put("appname", "test")
            .put("appversion", "test")
            .put("nol_devDebug", "DEBUG")
            .put("sfcode", "dcr");

    verify(appFactory).create(eq(context), jsonEq(expectedConfig), (IAppNotifier) isNull());
  }

  @Test
  public void videoPlaybackStarted() throws JSONException {
    integration.track(
        new TrackPayload.Builder().anonymousId("foo").event("Video Playback Started").properties(new Properties() //
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

  @Test
  public void videoPlaybackPaused() {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("channelName", "exampleChannelName");

    integration.track(new TrackPayload.Builder().anonymousId("foo") //
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
        .integration("nielsen-dcr", nielsenOptions)
        .build());

    verify(nielsen).stop();
  }

  @Test
  public void videoContentStarted() throws JSONException {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("crossId2", "id");

    integration.track(
            new TrackPayload.Builder().anonymousId("foo").event("Video Content Started").properties(new Properties() //
                    .putValue("assetId", 123214)
                    .putValue("title", "Look Who's Purging Now")
                    .putValue("season", 2)
                    .putValue("episode", 9)
                    .putValue("genre", "cartoon")
                    .putValue("program", "Rick and Morty")
                    .putValue("channel", "cartoon network")
                    .putValue("publisher", "Turner Broadcasting System")
                    .putValue("clientId", "myClient")
                    .putValue("subbrand", "myBrand")
                    .putValue("fullEpisode", true)
                    .putValue("podId", "segment A")
                    .putValue("playbackPosition", 70)
                    .putValue("totalLength", 1200))
                    .integration("nielsen-dcr", nielsenOptions)
                    .build());

    JSONObject expected = new JSONObject();
    expected.put("assetid", "123214");
    expected.put("title", "Look Who's Purging Now");
    expected.put("program", "Rick and Morty");
    expected.put("segB", "segmentB");
    expected.put("pipmode", "false");
    expected.put("isfullepisode", "y");
    expected.put("type", "content");
    expected.put("adloadtype", "1");
    expected.put("hasAds", "0");
    expected.put("crossId2", "id");
    expected.put("clientid", "myClient");
    expected.put("subbrand", "myBrand");
    expected.put("length", "1200");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }

  @Test
  public void videoContentStarted_settings() throws JSONException {

    settings.contentAssetIdPropertyName = "customContentAssetId";
    settings.clientIdPropertyName = "customClientId";
    settings.subbrandPropertyName = "customSubbrand";
    settings.contentLengthPropertyName = "customLength";

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("crossId2", "id");

    integration.track(
            new TrackPayload.Builder().anonymousId("foo").event("Video Content Started").properties(new Properties() //
                    .putValue("assetId", 123214)
                    .putValue("customContentAssetId", 1)
                    .putValue("title", "Look Who's Purging Now")
                    .putValue("season", 2)
                    .putValue("episode", 9)
                    .putValue("genre", "cartoon")
                    .putValue("program", "Rick and Morty")
                    .putValue("channel", "cartoon network")
                    .putValue("publisher", "Turner Broadcasting System")
                    .putValue("clientId", "badClient")
                    .putValue("customClientId", "myClient")
                    .putValue("subbrand", "badBrand")
                    .putValue("customSubbrand", "myBrand")
                    .putValue("fullEpisode", true)
                    .putValue("podId", "segment A")
                    .putValue("playbackPosition", 70)
                    .putValue("customLength", 1200)
                    .putValue("totalLength", 1100))
                    .integration("nielsen-dcr", nielsenOptions)
                    .build());

    JSONObject expected = new JSONObject();
    expected.put("assetid", "1");
    expected.put("title", "Look Who's Purging Now");
    expected.put("program", "Rick and Morty");
    expected.put("segB", "segmentB");
    expected.put("pipmode", "false");
    expected.put("isfullepisode", "y");
    expected.put("type", "content");
    expected.put("adloadtype", "1");
    expected.put("hasAds", "0");
    expected.put("crossId2", "id");
    expected.put("clientid", "myClient");
    expected.put("subbrand", "myBrand");
    expected.put("length", "1200");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }

  @Test
  public void videoContentStarted_hasAds() throws JSONException {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("hasAds", true);

    integration.track(
            new TrackPayload.Builder().anonymousId("foo").event("Video Content Started").properties(new Properties() //
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
                    .integration("nielsen-dcr", nielsenOptions)
                    .build());

    JSONObject expected = new JSONObject();
    expected.put("assetid", "123214");
    expected.put("title", "Look Who's Purging Now");
    expected.put("program", "Rick and Morty");
    expected.put("segB", "segmentB");
    expected.put("pipmode", "false");
    expected.put("isfullepisode", "y");
    expected.put("type", "content");
    expected.put("adloadtype", "1");
    expected.put("hasAds", "1");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }


  @Test
  public void videoPlaybackResumed() throws JSONException {

    integration.track(
        new TrackPayload.Builder().anonymousId("foo").event("Video Playback Resumed").properties(new Properties() //
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

  @Test
  public void videoAdStarted() throws JSONException {

    integration.track(
        new TrackPayload.Builder().anonymousId("foo").event("Video Ad Started").properties(new Properties() //
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

  @Test
  public void videoAdStarted_settings() throws JSONException {
    settings.adAssetIdPropertyName = "customAdAssetId";

    integration.track(
            new TrackPayload.Builder().anonymousId("foo").event("Video Ad Started").properties(new Properties() //
                    .putValue("assetId", 1234)
                    .putValue("customAdAssetId", 4311)
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

  @Test
  public void videoAdStartedWithTypeMidRoll() throws JSONException {

    integration.track(
        new TrackPayload.Builder().anonymousId("foo").event("Video Ad Started").properties(new Properties() //
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
    adExpected.put("assetid", "4311");
    adExpected.put("type", "mid-roll");
    adExpected.put("title", "Helmet Ad");

    verify(nielsen).loadMetadata(jsonEq(adExpected));
  }

  @Test
  public void videoAdStartedWithTypePreRoll() throws JSONException {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("hasAds", true);

    integration.track(
            new TrackPayload.Builder().anonymousId("foo").event("Video Ad Started").properties(new Properties() //
                    .putValue("assetId", 4311)
                    .putValue("podId", "adSegmentA")
                    .putValue("type", "pre-roll")
                    .putValue("totalLength", 120)
                    .putValue("loadType", "linear")
                    .putValue("position", 20)
                    .putValue("contentAssetId", 1234)
                    .putValue("clientId", "myClient")
                    .putValue("subbrand", "myBrand")
                    .putValue("playbackPosition", 0)
                    .putValue("title", "Helmet Ad"))
                    .integration("nielsen-dcr", nielsenOptions).build());

    JSONObject contentExpected = new JSONObject();
    contentExpected.put("assetid", "1234");
    contentExpected.put("type", "content");
    contentExpected.put("title", "Helmet Ad");
    contentExpected.put("pipmode", "false");
    contentExpected.put("segB", "segmentB");
    contentExpected.put("clientid", "myClient");
    contentExpected.put("subbrand", "myBrand");
    contentExpected.put("length", "120");
    contentExpected.put("adloadtype", "1");
    contentExpected.put("hasAds", "1");
    contentExpected.put("isfullepisode", "sf");

    JSONObject adExpected = new JSONObject();
    adExpected.put("assetid", "4311");
    adExpected.put("type", "pre-roll");
    adExpected.put("title", "Helmet Ad");

    ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);

    verify(nielsen, Mockito.times(2)).loadMetadata(captor.capture());

    List<JSONObject> calls = captor.getAllValues();

    JSONAssert.assertEquals(contentExpected, calls.get(0), JSONCompareMode.LENIENT);
    JSONAssert.assertEquals(adExpected, calls.get(1), JSONCompareMode.LENIENT);
  }


  @Test
  public void videoAdStartedWithTypePreRoll_settings() throws JSONException {

    settings.contentAssetIdPropertyName = "customContentAssetId";
    settings.adAssetIdPropertyName = "customAdAssetId";
    settings.clientIdPropertyName = "customClientId";
    settings.subbrandPropertyName = "customSubbrand";
    settings.contentLengthPropertyName = "customLength";

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("hasAds", true);

    JSONObject content = new JSONObject() //
        .put("podId", "adSegmentA")
        .put("totalLength", 110)
        .put("loadType", "linear")
        .put("position", 20)
        .put("customContentAssetId", 5678)
        .put("clientId", "badClient")
        .put("customClientId", "myClient")
        .put("subbrand", "badBrand")
        .put("customSubbrand", "myBrand")
        .put("playbackPosition", 0)
        .put("title", "Helmet Ad");

    integration.track(
            new TrackPayload.Builder().anonymousId("foo").event("Video Ad Started").properties(new Properties() //
                    // ad metadata
                    .putValue("customAdAssetId", 4311)
                    .putValue("type", "pre-roll")
                    .putValue("title", "Helmet Ad")
                    // content metadata
                    .putValue("content", content))
                    .integration("nielsen-dcr", nielsenOptions).build());

    JSONObject contentExpected = new JSONObject();
    contentExpected.put("assetid", "5678");
    contentExpected.put("type", "content");
    contentExpected.put("title", "Helmet Ad");
    contentExpected.put("pipmode", "false");
    contentExpected.put("segB", "segmentB");
    contentExpected.put("clientid", "myClient");
    contentExpected.put("subbrand", "myBrand");
    contentExpected.put("length", "120");
    contentExpected.put("adloadtype", "1");
    contentExpected.put("hasAds", "1");
    contentExpected.put("isfullepisode", "sf");

    JSONObject adExpected = new JSONObject();
    adExpected.put("assetid", "4311");
    adExpected.put("type", "pre-roll");
    adExpected.put("title", "Helmet Ad");

    ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);

    verify(nielsen, Mockito.times(2)).loadMetadata(captor.capture());

    List<JSONObject> calls = captor.getAllValues();

    JSONAssert.assertEquals(contentExpected, calls.get(0), JSONCompareMode.LENIENT);
    JSONAssert.assertEquals(adExpected, calls.get(1), JSONCompareMode.LENIENT);
  }


  @Test
  public void videoAdCompleted() {

    integration.track(
        new TrackPayload.Builder().anonymousId("foo").event("Video Ad Completed").properties(new Properties() //
            .putValue("assetId", 3425)
            .putValue("podId", "adSegmentb")
            .putValue("type", "mid-roll")
            .putValue("totalLength", 100)
            .putValue("playbackPosition", 100)
            .putValue("title", "Helmet Ad")).build());

    verify(nielsen).stop();
  }

  @Test
  public void screen() throws JSONException {

    integration.screen(
            new ScreenPayload.Builder().anonymousId("foo").name("Home").properties(new Properties() //
            .putValue("variation", "blue sign up button"))
            .build());
    JSONObject expected = new JSONObject();
    expected.put("name", "Home");
    expected.put("type", "static");
    expected.put("segB", "");
    expected.put("segC", "");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }

  @Test
  public void screenWithOptions() throws JSONException {

    Map<String, Object> nielsenOptions = new LinkedHashMap<>();
    nielsenOptions.put("segB", "segmentB");
    nielsenOptions.put("segC", "segmentC");
    nielsenOptions.put("crossId1", "crossIdValue");

    integration.screen(
        new ScreenPayload.Builder().anonymousId("foo").name("Home").properties(new Properties() //
            .putValue("variation", "blue sign up button"))
            .integration("nielsen-dcr", nielsenOptions)
            .build());

    JSONObject expected = new JSONObject();
    expected.put("name", "Home");
    expected.put("type", "static");
    expected.put("segB", "segmentB");
    expected.put("segC", "segmentC");
    expected.put("crossId1", "crossIdValue");

    verify(nielsen).loadMetadata(jsonEq(expected));
  }

  /**
   * Uses the string representation of the object. Useful for JSON objects.
   * @param expected Expected object
   * @return Argument matcher.
   */
  private JSONObject jsonEq(JSONObject expected) {
    return argThat(new JSONMatcher(expected));
  }

  class JSONMatcher implements ArgumentMatcher<JSONObject> {
    JSONObject expected;

    JSONMatcher(JSONObject expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(JSONObject argument) {
      try {
        JSONAssert.assertEquals(expected, argument, JSONCompareMode.STRICT);
        return true;
      } catch (JSONException e) {
        return false;
      }
    }
  }
}
