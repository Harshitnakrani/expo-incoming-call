export interface IncomingCallOptions {
  /**
   * Unique identifier for this call session (e.g. UUID, roomId, or sessionId).
   */
  uuid: string;

  /**
   * Name of the caller to display in large bold text on the incoming call screen.
   */
  callerName: string;

  /**
   * Type of call displayed in the top banner (e.g. "Incoming Voice Call" or "Incoming Video Call").
   * Default: "Incoming Voice Call"
   */
  callType?: string;

  /**
   * Subtitle displayed beneath the caller name (e.g. your app brand or phone number).
   * Defaults to your application's display name.
   */
  subtitle?: string;

  /**
   * Hex color string for the caller avatar circle background (e.g. "#2563EB").
   * Default: "#2563EB"
   */
  accentColor?: string;

  /**
   * Duration in seconds before the incoming call automatically times out if not answered.
   * Default: 30
   */
  timeoutSeconds?: number;

  /**
   * Android Notification Channel ID.
   * Default: "incoming_calls"
   */
  channelId?: string;

  /**
   * Android Notification Channel Name.
   * Default: "Incoming Calls"
   */
  channelName?: string;

  /**
   * Custom key-value dictionary passed back to your app when the call is accepted or declined.
   */
  [key: string]: any;
}

export interface IncomingCallEvent {
  uuid: string;
  callerName: string;
  isAccepted: boolean;
  [key: string]: any;
}
