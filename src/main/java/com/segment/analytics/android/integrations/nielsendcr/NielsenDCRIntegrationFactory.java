package com.segment.analytics.android.integrations.nielsendcr;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.nielsen.app.sdk.AppSdk;
import com.nielsen.app.sdk.IAppNotifier;
import com.segment.analytics.Analytics;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;

import org.json.JSONException;
import org.json.JSONObject;

class NielsenDCRIntegrationFactory implements Integration.Factory {

  interface AppSDKFactory {
    AppSdk create(Context context, JSONObject appInfo, IAppNotifier notifier);

    AppSDKFactory REAL =
        new AppSDKFactory() {
          @Override
          public AppSdk create(Context context, JSONObject appInfo, IAppNotifier notifier) {
            return new AppSdk(context, appInfo, notifier);
          }
        };
  }

  private static final String NIELSEN_DCR_KEY = "Nielsen DCR";
  // "sfcode" used to be a UI setting, but should now be hard-coded to "dcr" per Nielsen support
  private static final String SF_CODE = "dcr";

  private final AppSDKFactory appSDKFactory;

  static NielsenDCRIntegrationFactory create() {
    return new NielsenDCRIntegrationFactory(AppSDKFactory.REAL);
  }

  NielsenDCRIntegrationFactory(AppSDKFactory factory) {
    this.appSDKFactory = factory;
  }

  @Override
  public Integration<AppSdk> create(ValueMap settings, Analytics analytics) {
    Context context = analytics.getApplication();
    Context appContext = context.getApplicationContext();

    Logger logger = analytics.logger(NIELSEN_DCR_KEY);

    String appname;
    String appversion;
    try {
      PackageManager packageManager = appContext.getPackageManager();
      PackageInfo packageInfo = packageManager.getPackageInfo(appContext.getPackageName(), 0);
      appname = packageInfo.packageName;
      appversion = packageInfo.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      logger.error(e, "Could not retrieve Package information.");
      return null;
    }

    String appId = settings.getString("appId");

    try {
      // Prepare AppSdk configuration object (JSONObject)
      JSONObject appSdkConfig =
          new JSONObject()
              .put("appid", appId)
              .put("appname", appname)
              .put("appversion", appversion)
              .put("sfcode", SF_CODE);

      if (settings.getBoolean("nolDevDebug", false)) {
        appSdkConfig.put("nol_devDebug", "DEBUG");
      }

      AppSdk appSdk = appSDKFactory.create(appContext, appSdkConfig, null);
      logger.verbose("new AppSdk(%s),", appSdkConfig.toString(2));

      // Settings
      NielsenDCRIntegration.Settings integrationSettings = new NielsenDCRIntegration.Settings();
      String contentAssetIdPropertyName = settings.getString("contentAssetIdPropertyName");
      if (contentAssetIdPropertyName != null && !contentAssetIdPropertyName.isEmpty()) {
        integrationSettings.contentAssetIdPropertyName = contentAssetIdPropertyName;
      }
      String adAssetIdPropertyName = settings.getString("adAssetIdPropertyName");
      if (adAssetIdPropertyName != null && !adAssetIdPropertyName.isEmpty()) {
        integrationSettings.adAssetIdPropertyName = adAssetIdPropertyName;
      }
      String clientIdPropertyName = settings.getString("clientIdPropertyName");
      if (clientIdPropertyName != null && !clientIdPropertyName.isEmpty()) {
        integrationSettings.clientIdPropertyName = clientIdPropertyName;
      }
      String subbrandPropertyName = settings.getString("subbrandPropertyName");
      if (subbrandPropertyName != null && !subbrandPropertyName.isEmpty()) {
        integrationSettings.subbrandPropertyName = subbrandPropertyName;
      }
      String contentLengthPropertyName = settings.getString("contentLengthPropertyName");
      if (contentLengthPropertyName != null && !contentLengthPropertyName.isEmpty()) {
        integrationSettings.contentLengthPropertyName = contentLengthPropertyName;
      }
      String customSectionProperty = settings.getString("customSectionProperty");
      if (customSectionProperty != null && !customSectionProperty.isEmpty()) {
        integrationSettings.customSectionProperty = customSectionProperty;
      }
      Boolean sendCurrentTimeLivestream = settings.getBoolean("sendCurrentTimeLivestream", false);
      integrationSettings.sendCurrentTimeLivestream = sendCurrentTimeLivestream;

      return new NielsenDCRIntegration(appSdk, integrationSettings, logger);
    } catch (JSONException e) {
      logger.error(e, "Could not initialize settings.");
      return null;
    }
  }

  @Override
  public String key() {
    return NIELSEN_DCR_KEY;
  }
}
