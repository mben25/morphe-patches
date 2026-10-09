/*
 * StayFree local-only sync firewall (added by mben-patches tools/stayfree-chrome-local).
 *
 * Loaded before any StayFree code in the service worker, every extension page and every content
 * script. It wraps fetch / XMLHttpRequest / sendBeacon so that:
 *   - device pairing and cross-device sync calls to api.stayfreeapps.com go to the StayFree app
 *     on your phone instead (address set on the extension's options page),
 *   - every other api.stayfreeapps.com call (web/page-view/AI/ad/brand uploads, remote config,
 *     analytics lookups) is answered locally and never leaves the browser,
 *   - Google Analytics, Bugsnag and StayFree's feature-flag / brand-mention hosts are blocked.
 *
 * Extension pages also find the phone by themselves (LocalSend-style HTTP discovery): when no
 * address is set, or the saved one stops answering, they probe port 8787 on this computer's
 * subnet (from a WebRTC host candidate when Chrome reveals it) and on common home subnets, and
 * save the first StayFree app that answers.
 */
(() => {
  const g = globalThis;
  if (g.__stayfreeLocalSync) return;
  g.__stayfreeLocalSync = true;

  const API_HOST = "api.stayfreeapps.com";
  const STORAGE_KEY = "localSyncUrl";
  const DEFAULT_PORT = "8787";

  // Calls the phone answers: pairing, device list, shared config, sessions, the web-usage upload
  // the phone keeps for the device group, and Android app names/icons.
  const PHONE_PATHS = [/^\/v1\/sync\//, /^\/v1\/web\/upload$/, /^\/v1\/android\/apps$/];

  // App-group (brand) mapping: the extension treats 403 as "no mapping" and shows plain app
  // names, while 404 surfaces as an uncaught error.
  const NO_BRANDS_PATH = /^\/v1\/brands$/;

  // Prefix of every error this file raises; the patched pairing panel shows the text after it.
  const ERROR_PREFIX = "StayFree local sync: ";

  const BLOCKED_HOSTS = [
    /(^|\.)google-analytics\.com$/,
    /(^|\.)analytics\.google\.com$/,
    /(^|\.)googletagmanager\.com$/,
    /(^|\.)bugsnag\.com$/,
    /(^|\.)smartbear\.com$/,
    /^config\.stayfreeapps\.com$/,
    /^api-pm\.stayfreeapps\.com$/,
  ];

  const ext = g.chrome ?? g.browser;

  function normalize(value) {
    if (!value) return null;
    let v = String(value).trim();
    if (!v) return null;
    if (!/^https?:\/\//i.test(v)) v = "http://" + v;
    try {
      const url = new URL(v);
      if (!url.port && url.protocol === "http:") url.port = DEFAULT_PORT;
      return url.origin;
    } catch {
      return null;
    }
  }

  let phoneBase = null;
  // The address last confirmed to answer /local/status, so a saved address is re-checked once per
  // address rather than once per request. Cleared whenever the address changes or a call fails.
  let verifiedBase = null;
  let ready = (async () => {
    try {
      const stored = await ext.storage.local.get(STORAGE_KEY);
      phoneBase = normalize(stored?.[STORAGE_KEY]);
    } catch {
      phoneBase = null;
    }
    return phoneBase;
  })();
  try {
    ext.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && STORAGE_KEY in changes) {
        phoneBase = normalize(changes[STORAGE_KEY].newValue);
        ready = Promise.resolve(phoneBase);
        verifiedBase = null;
      }
    });
  } catch {
    // Storage events are unavailable in some content-script contexts; the first read still works.
  }

  /** @returns {null | {phone: string} | {drop: number}} */
  function classify(rawUrl, method) {
    let url;
    try {
      url = new URL(String(rawUrl), g.location?.href);
    } catch {
      return null;
    }
    if (url.hostname === API_HOST) {
      if (PHONE_PATHS.some((re) => re.test(url.pathname))) return { phone: url.pathname + url.search };
      if (NO_BRANDS_PATH.test(url.pathname)) return { drop: 403 };
      return { drop: method === "GET" || method === "HEAD" ? 404 : 204 };
    }
    if (BLOCKED_HOSTS.some((re) => re.test(url.hostname))) return { drop: 204 };
    return null;
  }

  function dropped(status) {
    return new Response(status === 204 ? null : '{"error":"blocked by StayFree local sync"}', {
      status,
      headers: { "content-type": "application/json" },
    });
  }

  const originalFetch = typeof g.fetch === "function" ? g.fetch.bind(g) : null;

  // region Phone discovery (extension pages only: not the service worker, never content scripts)

  const canDiscover =
    !!originalFetch && g.location?.protocol === "chrome-extension:" && typeof g.document !== "undefined";

  const COMMON_SUBNETS = [
    "192.168.1", "192.168.0", "192.168.31", "192.168.2", "192.168.8", "192.168.10", "192.168.100",
    "192.168.178", "192.168.50", "192.168.88", "192.168.3", "192.168.43", "10.0.0", "10.0.1", "172.20.10",
  ];
  const PROBE_TIMEOUT_MS = 900;
  const PROBE_CONCURRENCY = 96;
  const RESCAN_AFTER_FAILURE_MS = 10_000;

  async function isStayFree(base, timeoutMs) {
    try {
      const response = await originalFetch(base + "/local/status", {
        cache: "no-store",
        signal: AbortSignal.timeout(timeoutMs),
      });
      if (!response.ok) return false;
      const body = await response.json();
      return body?.ok === true && body?.service === "StayFree local sync";
    } catch {
      return false;
    }
  }

  /** This computer's private IPv4 subnets, when Chrome exposes host ICE candidates. */
  async function ownSubnets() {
    if (typeof g.RTCPeerConnection !== "function") return [];
    const ips = new Set();
    let pc;
    try {
      pc = new g.RTCPeerConnection({ iceServers: [] });
      pc.createDataChannel("probe");
      pc.onicecandidate = (event) => {
        const match = /(?:^|\s)(\d{1,3}(?:\.\d{1,3}){3})\s/.exec(event.candidate?.candidate ?? "");
        if (match) ips.add(match[1]);
      };
      await pc.setLocalDescription(await pc.createOffer());
      await new Promise((resolve) => setTimeout(resolve, 700));
    } catch {
      // No WebRTC here.
    } finally {
      try { pc?.close(); } catch { /* already closed */ }
    }
    return [...ips]
      .filter((ip) => /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip))
      .map((ip) => ip.split(".").slice(0, 3).join("."));
  }

  async function scanSubnet(prefix) {
    let next = 1;
    let found = null;
    const worker = async () => {
      while (!found && next <= 254) {
        const base = `http://${prefix}.${next++}:${DEFAULT_PORT}`;
        if (await isStayFree(base, PROBE_TIMEOUT_MS)) found ??= base;
      }
    };
    await Promise.all(Array.from({ length: PROBE_CONCURRENCY }, worker));
    return found;
  }

  /**
   * The subnet of an address that answered before. Chrome hides host ICE candidates behind mDNS
   * on most setups, so `ownSubnets()` usually comes back empty and the scan falls through to
   * COMMON_SUBNETS — which cannot list every home network. A previously-saved address is the one
   * piece of evidence we have about which network this browser actually pairs on, so re-scan its
   * subnet even once the address itself has gone stale (the phone took a new DHCP lease).
   */
  function subnetOf(base) {
    if (!base) return [];
    const match = /^https?:\/\/(\d{1,3}(?:\.\d{1,3}){2})\.\d{1,3}/.exec(base);
    return match ? [match[1]] : [];
  }

  let discovering = null;
  let lastFailedDiscovery = 0;

  /**
   * Finds the phone on the LAN and saves its address. Resolves to the base URL or null.
   * `force` skips the post-failure cooldown: pressing "Find" on the options page is an explicit
   * request to scan now, and silently answering null there reads as the button being broken.
   */
  function discover(force) {
    if (!canDiscover) return Promise.resolve(null);
    if (discovering) return discovering;
    if (!force && Date.now() - lastFailedDiscovery < RESCAN_AFTER_FAILURE_MS) {
      return Promise.resolve(null);
    }
    discovering = (async () => {
      // Order matters: this computer's own subnet, then the one that worked last time, then the
      // common guesses. Each miss costs a 254-address sweep, so the likely networks go first.
      const subnets = [
        ...new Set([...(await ownSubnets()), ...subnetOf(phoneBase), ...COMMON_SUBNETS]),
      ];
      for (const subnet of subnets) {
        const base = await scanSubnet(subnet);
        if (!base) continue;
        phoneBase = base;
        ready = Promise.resolve(base);
        verifiedBase = base; // discover() only returns an address that just answered.
        try {
          await ext.storage.local.set({ [STORAGE_KEY]: base });
        } catch {
          // Still used for this page.
        }
        return base;
      }
      lastFailedDiscovery = Date.now();
      return null;
    })().finally(() => {
      discovering = null;
    });
    return discovering;
  }
  g.__stayfreeLocalSyncDiscover = discover;

  // endregion

  function phoneError(message) {
    return new TypeError(ERROR_PREFIX + message);
  }

  if (originalFetch) {
    g.fetch = function (input, init) {
      const isRequest = typeof Request !== "undefined" && input instanceof Request;
      const url = isRequest ? input.url : input instanceof URL ? input.href : String(input);
      const method = String(init?.method ?? (isRequest ? input.method : "GET")).toUpperCase();
      const rule = classify(url, method);
      if (!rule) return originalFetch(input, init);
      if (rule.drop) return Promise.resolve(dropped(rule.drop));
      // A Request body can be read once: keep a copy in case the call is retried after a rescan.
      const spare = isRequest ? input.clone() : null;
      const send = (base, request) => {
        const target = base + rule.phone;
        return originalFetch(request ? new Request(target, request) : target, init);
      };
      return ready.then(async (stored) => {
        // A saved address survives a change of network, so it can point at a subnet this computer
        // is no longer on. Check it before trusting it: on a different Wi-Fi the connection fails
        // with ERR_ADDRESS_UNREACHABLE immediately, so this costs nothing when it is wrong, and
        // one cheap request when it is right. Without this the first pairing call always fails.
        let base = stored;
        if (base && base !== verifiedBase) {
          if (await isStayFree(base, PROBE_TIMEOUT_MS)) verifiedBase = base;
          else base = null;
        }
        base ??= await discover();
        if (!base) {
          throw phoneError(
            "StayFree wasn't found on your Wi-Fi. Open the patched StayFree app on your phone " +
              "(same Wi-Fi as this computer) and press Retry, or enter the phone's address in the " +
              "extension's options.",
          );
        }
        try {
          return await send(base, isRequest ? input : null);
        } catch (error) {
          if (error?.name === "AbortError") throw error;
          // The phone may have a new address (DHCP): look for it once, then retry.
          verifiedBase = null;
          const found = await discover();
          if (found && found !== base) return send(found, spare);
          throw phoneError(
            `Can't reach StayFree on your phone at ${base.replace(/^https?:\/\//, "")}. Open the app ` +
              "on the phone, keep it on the same Wi-Fi as this computer and press Retry.",
          );
        }
      });
    };
  }

  const XHR = g.XMLHttpRequest;
  if (XHR?.prototype?.open) {
    const open = XHR.prototype.open;
    XHR.prototype.open = function (method, url, ...rest) {
      const rule = classify(url, String(method).toUpperCase());
      if (rule) {
        // Synchronous API: only the cached address can be used. Unknown address or a blocked
        // host -> an unroutable URL, so the request fails locally.
        url = rule.phone && phoneBase ? phoneBase + rule.phone : "http://127.0.0.1:9/blocked";
      }
      return open.call(this, method, url, ...rest);
    };
  }

  const nav = g.navigator;
  if (typeof nav?.sendBeacon === "function") {
    const sendBeacon = nav.sendBeacon.bind(nav);
    nav.sendBeacon = (url, data) => (classify(url, "POST") ? true : sendBeacon(url, data));
  }
})();
