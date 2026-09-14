/*
  StockTrace RFID reader — SIMULATOR (no hardware needed)
  ------------------------------------------------------------
  Drop-in stand-in for rfid_reader.ino. Doesn't need an MFRC522
  reader wired up at all — just prints one of 5 known tag IDs
  over Serial every 4 seconds, cycling through them in order.

  Wire format is identical to the real sketch: one tag ID per
  line, uppercase hex, 9600 baud — serial.js on the browser side
  can't tell the difference between this and a real reader.

  These 5 IDs match real seeded products, so scans resolve to
  actual product names instead of triggering "unknown tag":
    4A1F2B3C  L298N Motor Driver Module
    5B2E3C4D  Arduino Uno R3
    6C3F4D5E  ESP32 Dev Board (38-pin)
    7D4A5E6F  12V 2200mAh Li-ion Battery Pack
    8E5B6F7A  TB6600 Stepper Motor Driver

  Needs only an Arduino + USB cable — no reader, no wiring, no
  extra library. Swap back to rfid_reader.ino once your MFRC522
  is wired up; nothing else in the project needs to change either way.
*/

#define BAUD_RATE 9600
#define SCAN_INTERVAL_MS 4000  // change this to scan "faster" or "slower"

const char* tagIds[] = {
  "4A1F2B3C",  // L298N Motor Driver Module
  "5B2E3C4D",  // Arduino Uno R3
  "6C3F4D5E",  // ESP32 Dev Board (38-pin)
  "7D4A5E6F",  // 12V 2200mAh Li-ion Battery Pack
  "8E5B6F7A"   // TB6600 Stepper Motor Driver
};
const int numTags = 5;
int currentTag = 0;

void setup() {
  Serial.begin(BAUD_RATE);
}

void loop() {
  Serial.println(tagIds[currentTag]);
  currentTag = (currentTag + 1) % numTags;
  delay(SCAN_INTERVAL_MS);
}
