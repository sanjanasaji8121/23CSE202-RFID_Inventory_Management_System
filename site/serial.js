/* ============================================================
   RFID SERIAL BRIDGE — Web Serial API
   ------------------------------------------------------------
   Talks directly to the Arduino over USB from this browser tab —
   no Java-side serial listener needed. Chromium-only (Chrome/Edge);
   navigator.serial doesn't exist in Firefox/Safari, and isSupported()
   below is how the page detects that and disables the button instead
   of throwing.

   Arduino side: see arduino/rfid_reader.ino. Contract is minimal on
   purpose — one tag ID per line, newline-terminated, 9600 baud.
   Nothing else needs to be sent over the wire.

   Known limits (see conversation / README for the full list):
   - Requires a user click to connect (browser permission model,
     can't auto-connect on page load).
   - Connection doesn't survive navigating to another page — this
     is a real multi-page site now, not a single-page app, so the
     JS context (and the open port) is torn down on navigation.
     Reconnecting is one click with no repeat permission prompt,
     since Chromium remembers granted ports per origin.
============================================================ */

const RfidSerial = (() => {
  let port = null;
  let reader = null;
  let keepReading = false;
  let onTagCallback = null;
  let onStatusCallback = null;
  const lastSeenAt = new Map(); // tagId -> timestamp, so one tag sitting in
                                 // range for a second doesn't fire 50 events
  const DEBOUNCE_MS = 2000;

  function isSupported() {
    return 'serial' in navigator;
  }

  // Fires when ANY previously-granted serial device is physically unplugged —
  // not just from clicking "Disconnect reader". Registered once, at module
  // load, so it catches an unplug even if connect() was called long ago.
  if (isSupported()) {
    navigator.serial.addEventListener('disconnect', (event) => {
      if (event.target === port) {
        keepReading = false;
        port = null;
        reader = null;
        onStatusCallback?.(false, 'unplugged');
      }
    });
  }

  async function connect(onTag, onStatusChange) {
    if (!isSupported()) {
      throw new Error('Web Serial API isn\u2019t available in this browser \u2014 use Chrome or Edge.');
    }
    onTagCallback = onTag;
    onStatusCallback = onStatusChange;

    port = await navigator.serial.requestPort(); // triggers the browser's device picker
    await port.open({ baudRate: 9600 });
    keepReading = true;
    onStatusCallback?.(true);
    readLoop(); // not awaited on purpose — runs until disconnect()
  }

  async function readLoop() {
    const textDecoder = new TextDecoderStream();
    const readableClosed = port.readable.pipeTo(textDecoder.writable);
    reader = textDecoder.readable.getReader();
    let buffer = '';

    try {
      while (keepReading) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        let newlineIndex;
        while ((newlineIndex = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, newlineIndex).trim();
          buffer = buffer.slice(newlineIndex + 1);
          if (line) handleTagRead(line);
        }
      }
    } catch (err) {
      console.error('RFID serial read error:', err);
      keepReading = false;
      port = null;
      onStatusCallback?.(false, 'error');
    } finally {
      reader.releaseLock();
      await readableClosed.catch(() => {});
    }
  }

  function handleTagRead(rawTagId) {
    const tagId = rawTagId.toUpperCase();
    const now = Date.now();
    const last = lastSeenAt.get(tagId) || 0;
    if (now - last < DEBOUNCE_MS) return; // still sitting in range from the last read
    lastSeenAt.set(tagId, now);
    onTagCallback?.(tagId);
  }

  async function disconnect() {
    keepReading = false;
    if (reader) { try { await reader.cancel(); } catch (e) { /* already closed */ } }
    if (port) { try { await port.close(); } catch (e) { /* already closed */ } }
    port = null;
    reader = null;
    onStatusCallback?.(false, 'manual');
  }

  return { isSupported, connect, disconnect };
})();
