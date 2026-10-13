package com.soltini.app.browser

/**
 * YouTubeEnhancer
 *
 * NouTube-style tweaks for the Myra in-app browser (YouTube / YouTube Music web):
 *  - strips ad payloads from YouTube's JSON API responses (fetch + XHR + initial page data)
 *  - removes ad / promo items from feeds
 *  - hides Shorts (optional)
 *  - fallback: auto-skips / fast-forwards any ad that still reaches the player
 *
 * Background playback is already handled by BrowserController.BACKGROUND_PLAYBACK_JS.
 * Inject [script] with evaluateJavascript() from onPageStarted / onPageCommitVisible.
 * The script is idempotent and only activates on youtube.com hosts.
 */
object YouTubeEnhancer {

    @Volatile var blockAds: Boolean = true
    @Volatile var hideShorts: Boolean = true

    fun isYouTubeHost(url: String?): Boolean {
        val host = try { android.net.Uri.parse(url ?: return false).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
        return host == "youtube.com" || host.endsWith(".youtube.com")
    }

    fun script(): String = SCRIPT
        .replace("__BLOCK_ADS__", blockAds.toString())
        .replace("__HIDE_SHORTS__", hideShorts.toString())

    private val SCRIPT = """
(function () {
  try {
    if (!/(^|\.)youtube\.com${'$'}/.test(location.hostname)) return;
    if (window.__myraYtEnhancer) return;
    window.__myraYtEnhancer = true;

    var BLOCK_ADS = __BLOCK_ADS__;
    var HIDE_SHORTS = __HIDE_SHORTS__;

    var AD_KEYS = ['adBreakHeartbeatParams', 'adPlacements', 'adSlots', 'playerAds'];
    var AD_RENDERERS = ['adSlotRenderer', 'adsEngagementPanelRenderer', 'bannerPromoRenderer',
      'brandVideoShelfRenderer', 'brandVideoSingletonRenderer', 'carouselAdRenderer',
      'compactPromotedItemRenderer', 'compactPromotedVideoRenderer', 'displayAdRenderer',
      'inFeedAdLayoutRenderer', 'mealbarPromoRenderer', 'primetimePromoRenderer',
      'promotedSparklesTextSearchRenderer', 'promotedSparklesWebRenderer',
      'promotedVideoRenderer', 'searchPyvRenderer', 'statementBannerRenderer'];
    var API_RE = /^\/youtubei\/v1\/(browse|get_watch|next|player|reel|search)/;

    function isAdItem(it) {
      if (!it || typeof it !== 'object') return false;
      try { if (it.command && it.command.reelWatchEndpoint && it.command.reelWatchEndpoint.adClientParams && it.command.reelWatchEndpoint.adClientParams.isAd) return true; } catch (e) {}
      var c = [it];
      if (it.richItemRenderer && it.richItemRenderer.content) c.push(it.richItemRenderer.content);
      if (it.richSectionRenderer && it.richSectionRenderer.content) c.push(it.richSectionRenderer.content);
      for (var i = 0; i < c.length; i++) for (var j = 0; j < AD_RENDERERS.length; j++) if (c[i][AD_RENDERERS[j]]) return true;
      return false;
    }

    function clean(node, depth) {
      if (!node || typeof node !== 'object' || depth > 40) return;
      if (Array.isArray(node)) {
        for (var i = node.length - 1; i >= 0; i--) {
          if (isAdItem(node[i])) node.splice(i, 1); else clean(node[i], depth + 1);
        }
        return;
      }
      for (var k = 0; k < AD_KEYS.length; k++) if (AD_KEYS[k] in node) delete node[AD_KEYS[k]];
      for (var key in node) clean(node[key], depth + 1);
    }

    function cleanText(text) {
      try { var d = JSON.parse(text); clean(d, 0); return JSON.stringify(d); } catch (e) { return text; }
    }

    // 1) Initial server-rendered data
    ['ytInitialPlayerResponse', 'ytInitialData'].forEach(function (name) {
      var val = window[name];
      try {
        Object.defineProperty(window, name, {
          configurable: true,
          get: function () { return val; },
          set: function (v) { if (BLOCK_ADS) { try { clean(v, 0); } catch (e) {} } val = v; }
        });
      } catch (e) {}
    });

    // 2) fetch
    var origFetch = window.fetch;
    if (origFetch) {
      window.fetch = function (input) {
        var args = arguments;
        var url = '';
        try { url = typeof input === 'string' ? input : (input && input.url) || String(input); } catch (e) {}
        var path = '';
        try { path = new URL(url, location.origin).pathname; } catch (e) {}
        return origFetch.apply(this, args).then(function (res) {
          if (!BLOCK_ADS || !API_RE.test(path) || res.status !== 200) return res;
          return res.clone().text().then(function (t) {
            return new Response(cleanText(t), { status: res.status, statusText: res.statusText, headers: res.headers });
          }).catch(function () { return res; });
        });
      };
    }

    // 3) XHR
    var xhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url) {
      var path = '';
      try { path = new URL(String(url), location.origin).pathname; } catch (e) {}
      if (API_RE.test(path)) {
        var xhr = this;
        xhr.addEventListener('readystatechange', function () {
          if (xhr.readyState !== 4 || !BLOCK_ADS) return;
          try {
            var cleaned = cleanText(xhr.responseText);
            Object.defineProperty(xhr, 'responseText', { configurable: true, get: function () { return cleaned; } });
            Object.defineProperty(xhr, 'response', { configurable: true, get: function () { return cleaned; } });
          } catch (e) {}
        });
      }
      return xhrOpen.apply(this, arguments);
    };

    // 4) CSS: leftover ad slots + optional Shorts hiding
    var css = '';
    if (BLOCK_ADS) {
      css += 'ytd-ad-slot-renderer, ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer,' +
             '.ytp-ad-module, .ytp-ad-overlay-container, .video-ads, #player-ads, ytd-display-ad-renderer,' +
             'ytm-statement-banner-renderer, ytm-mealbar-promo-renderer, .ad-showing .ytp-ad-image-overlay' +
             '{ display: none !important; }';
    }
    if (HIDE_SHORTS) {
      css += 'ytm-reel-shelf-renderer, ytd-reel-shelf-renderer, .ytGridShelfViewModelHost, grid-shelf-view-model,' +
             'ytd-rich-section-renderer:has(ytd-rich-shelf-renderer[is-shorts]), ytm-shorts-lockup-view-model,' +
             'yt-lockup-view-model:has(a[href^="/shorts"]), ytd-video-renderer:has(a[href^="/shorts"]),' +
             'ytm-video-with-context-renderer:has(a[href^="/shorts"]) { display: none !important; }';
    }
    if (css) {
      var style = document.createElement('style');
      style.id = 'myra-yt-enhancer';
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    }

    // 5) Fallback: if an ad still plays, skip / fast-forward it
    if (BLOCK_ADS) {
      setInterval(function () {
        try {
          var player = document.querySelector('.html5-video-player');
          var video = document.querySelector('video');
          var adShowing = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
          var skip = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-container button');
          if (skip) skip.click();
          if (adShowing && video && isFinite(video.duration) && video.duration > 0) {
            video.muted = true;
            video.playbackRate = 16;
            video.currentTime = video.duration;
          }
        } catch (e) {}
      }, 400);
    }
  } catch (e) {}
})();
"""
}
