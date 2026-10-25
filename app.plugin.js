const {
  withAndroidManifest,
  withPlugins,
  createRunOncePlugin,
} = require("@expo/config-plugins");

const PKG_NAME = "expo-incoming-call";
const PKG_VERSION = "0.1.0";

const REQUIRED_PERMISSIONS = [
  "android.permission.USE_FULL_SCREEN_INTENT",
  "android.permission.WAKE_LOCK",
  "android.permission.VIBRATE",
  "android.permission.FOREGROUND_SERVICE",
  "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
  "android.permission.POST_NOTIFICATIONS",
];

function addPermission(androidManifest, permissionName) {
  if (!androidManifest.manifest["uses-permission"]) {
    androidManifest.manifest["uses-permission"] = [];
  }
  const exists = androidManifest.manifest["uses-permission"].some(
    (perm) => perm.$ && perm.$["android:name"] === permissionName
  );
  if (!exists) {
    androidManifest.manifest["uses-permission"].push({
      $: { "android:name": permissionName },
    });
  }
}

function withIncomingCallManifest(config) {
  return withAndroidManifest(config, (modConfig) => {
    const androidManifest = modConfig.modResults;

    // 1. Add required permissions
    for (const perm of REQUIRED_PERMISSIONS) {
      addPermission(androidManifest, perm);
    }

    // 2. Ensure application exists
    if (!androidManifest.manifest.application || !androidManifest.manifest.application[0]) {
      androidManifest.manifest.application = [{}];
    }
    const app = androidManifest.manifest.application[0];

    // 3. Register IncomingCallActivity
    if (!app.activity) {
      app.activity = [];
    }

    const activityName = "expo.modules.incomingcall.IncomingCallActivity";
    const activityExists = app.activity.some(
      (act) => act.$ && act.$["android:name"] === activityName
    );

    if (!activityExists) {
      app.activity.push({
        $: {
          "android:name": activityName,
          "android:launchMode": "singleInstance",
          "android:excludeFromRecents": "true",
          "android:exported": "true",
          "android:showWhenLocked": "true",
          "android:turnScreenOn": "true",
          "android:showOnLockScreen": "true",
          "android:taskAffinity": "",
          "android:screenOrientation": "portrait",
          "android:theme": "@android:style/Theme.NoTitleBar.Fullscreen",
        },
        "intent-filter": [
          {
            action: [{ $: { "android:name": "android.intent.action.MAIN" } }],
            category: [{ $: { "android:name": "android.intent.category.DEFAULT" } }],
          },
        ],
      });
    }

    // 4. Register IncomingCallReceiver
    if (!app.receiver) {
      app.receiver = [];
    }

    const receiverName = "expo.modules.incomingcall.IncomingCallReceiver";
    const receiverExists = app.receiver.some(
      (rec) => rec.$ && rec.$["android:name"] === receiverName
    );

    if (!receiverExists) {
      app.receiver.push({
        $: {
          "android:name": receiverName,
          "android:exported": "false",
        },
        "intent-filter": [
          {
            action: [
              { $: { "android:name": "expo.modules.incomingcall.ACTION_CALL_DECLINE" } },
              { $: { "android:name": "expo.modules.incomingcall.ACTION_CALL_DISMISS" } },
            ],
          },
        ],
      });
    }

    return modConfig;
  });
}

function withIncomingCall(config, props = {}) {
  return withPlugins(config, [[withIncomingCallManifest, props]]);
}

module.exports = createRunOncePlugin(withIncomingCall, PKG_NAME, PKG_VERSION);
