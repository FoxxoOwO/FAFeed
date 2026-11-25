package cz.foxxo.fafeed.ui

import org.json.JSONTokener

internal const val DOM_USER = "USER:"
internal const val DOM_FORM = "FORM"
internal const val DOM_CHALLENGE = "CHALLENGE"
internal const val DOM_OTHER = "OTHER"

/**
 * Runs in the WebView page: a logged-in FA page renders the own avatar, the SSO form has
 * a password input, and a Cloudflare interstitial has none of the FA markup.
 */
internal const val FA_DOM_PROBE = """
(function () {
  var avatar = document.querySelector('img.loggedin_user_avatar');
  var name = avatar ? (avatar.getAttribute('alt') || '').trim() : '';
  if (name) return 'USER:' + name;
  if (document.querySelector('input[type=password]')) return 'FORM';
  if (document.querySelector('#challenge-form, #cf-challenge-running, #cf-please-wait, .cf-browser-verification')) return 'CHALLENGE';
  var title = (document.title || '').toLowerCase();
  if (title.indexOf('just a moment') >= 0 || title.indexOf('attention required') >= 0 ||
      title.indexOf('checking your browser') >= 0 || title.indexOf('verify you are human') >= 0) return 'CHALLENGE';
  return 'OTHER';
})()
"""

internal fun domResult(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return DOM_OTHER
    return runCatching { (JSONTokener(raw).nextValue() as? String) ?: DOM_OTHER }
        .getOrDefault(DOM_OTHER)
}
