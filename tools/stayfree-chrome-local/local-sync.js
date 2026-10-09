const STORAGE_KEY = "localSyncUrl";
const input = document.getElementById("address");
const status = document.getElementById("status");

function normalize(value) {
  let v = String(value ?? "").trim();
  if (!v) return null;
  if (!/^https?:\/\//i.test(v)) v = "http://" + v;
  const url = new URL(v);
  if (!url.port && url.protocol === "http:") url.port = "8787";
  return url.origin;
}

function show(text, kind) {
  status.textContent = text;
  status.className = kind ?? "";
}

async function test() {
  let base;
  try {
    base = normalize(input.value);
  } catch {
    show("That doesn't look like an address.", "err");
    return;
  }
  if (!base) {
    show("Enter the phone's IP address first.", "err");
    return;
  }
  show(`Connecting to ${base}…`);
  try {
    const response = await fetch(base + "/local/status", { signal: AbortSignal.timeout(5000) });
    const body = await response.json();
    if (!body?.ok) throw new Error("unexpected answer");
    show(`Connected to StayFree on ${body.device ?? "your phone"}.`, "ok");
  } catch (e) {
    show(`Can't reach ${base}: ${e.message}. Is the patched StayFree app running and on the same Wi-Fi?`, "err");
  }
}

document.getElementById("save").addEventListener("click", async () => {
  let base = null;
  try {
    base = normalize(input.value);
  } catch {
    show("That doesn't look like an address.", "err");
    return;
  }
  await chrome.storage.local.set({ [STORAGE_KEY]: base });
  if (base) input.value = base;
  await test();
});

document.getElementById("test").addEventListener("click", test);

document.getElementById("find").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  show("Looking for StayFree on your Wi-Fi\u2026 this can take up to a minute.");
  try {
    const base = await globalThis.__stayfreeLocalSyncDiscover?.(true);
    if (base) {
      input.value = base;
      await test();
    } else {
      show("StayFree wasn't found. Open the patched app on your phone (same Wi-Fi) and try again, or enter its address.", "err");
    }
  } finally {
    button.disabled = false;
  }
});
input.addEventListener("keydown", (e) => {
  if (e.key === "Enter") document.getElementById("save").click();
});

chrome.storage.local.get(STORAGE_KEY).then((stored) => {
  if (stored?.[STORAGE_KEY]) {
    input.value = stored[STORAGE_KEY];
    test();
  }
});
