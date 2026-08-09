/* Demo/default request values for the web UI — edit HERE, not inline in app.js.
 *
 * These are only the fall-back defaults: at runtime the settings UI stores the user's own
 * corpNo / corpUserId in localStorage, which takes precedence over these. Loaded before app.js
 * (see index.html), so `window.APP_CONFIG` is available when app.js initializes.
 */
window.APP_CONFIG = {
  /** Default corporation number (the demo corp). */
  corpNo: "1234567890",
  /** Default corporation-user id (the static demo user — matches app.bizplay.default-corp-user-id). */
  corpUserId: "30447",
};
