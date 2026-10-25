import {
  EventEmitter,
  type EventSubscription,
  requireNativeModule,
} from "expo-modules-core";
import { IncomingCallOptions, IncomingCallEvent } from "./ExpoIncomingCall.types";

type ExpoIncomingCallEvents = {
  onCallAnswered: (event: IncomingCallEvent) => void;
  onCallDeclined: (event: IncomingCallEvent) => void;
  onCallTimeout: (event: IncomingCallEvent) => void;
};

const ExpoIncomingCall = requireNativeModule("ExpoIncomingCall");
const emitter = new EventEmitter<ExpoIncomingCallEvents>(ExpoIncomingCall);

/**
 * Display a full-screen incoming call UI over the lockscreen with loud ringtone and vibration.
 * Works even when the app is completely closed or killed on Android.
 */
export async function displayIncomingCall(
  options: IncomingCallOptions
): Promise<boolean> {
  if (!options.uuid || !options.callerName) {
    throw new Error("expo-incoming-call: 'uuid' and 'callerName' are required.");
  }
  return await ExpoIncomingCall.displayIncomingCall(options);
}

/**
 * Dismiss the currently active incoming call screen and stop ringtone audio.
 */
export async function dismissCall(): Promise<boolean> {
  return await ExpoIncomingCall.dismissCall();
}

/**
 * Check if the incoming call activity is currently displayed on screen.
 */
export function isCallActive(): boolean {
  return ExpoIncomingCall.isCallActive();
}

/**
 * Retrieve call data if the app was launched by the user tapping "Accept" on the lockscreen.
 */
export function getPendingAnsweredCall(): IncomingCallEvent | null {
  return ExpoIncomingCall.getPendingAnsweredCall();
}

/**
 * Clear the pending answered call record after handling it in your app.
 */
export function clearPendingAnsweredCall(): boolean {
  return ExpoIncomingCall.clearPendingAnsweredCall();
}

/**
 * Add a listener for when the user taps "Accept".
 */
export function addCallAnsweredListener(
  listener: (event: IncomingCallEvent) => void
): EventSubscription {
  return emitter.addListener("onCallAnswered", listener);
}

/**
 * Add a listener for when the user taps "Decline".
 */
export function addCallDeclinedListener(
  listener: (event: IncomingCallEvent) => void
): EventSubscription {
  return emitter.addListener("onCallDeclined", listener);
}

/**
 * Add a listener for when the call reaches the timeout limit without being answered.
 */
export function addCallTimeoutListener(
  listener: (event: IncomingCallEvent) => void
): EventSubscription {
  return emitter.addListener("onCallTimeout", listener);
}

export { type EventSubscription };
export * from "./ExpoIncomingCall.types";
