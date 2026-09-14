/*
  StockTrace RFID reader — MFRC522 + Arduino
  ------------------------------------------------------------
  Reads a tag's UID and prints it as one hex line over Serial:

      A3F19C82

  That's the entire contract serial.js on the browser side expects
  — one tag ID per line, newline-terminated, nothing else on the
  wire. Don't add extra Serial.print() calls for debugging without
  guarding them (e.g. behind a DEBUG flag), or the browser will try
  to treat your debug text as a tag ID.

  Wiring (Uno/Nano):
    MFRC522      Arduino
    SDA/SS   ->  D10
    SCK      ->  D13
    MOSI     ->  D11
    MISO     ->  D12
    RST      ->  D9
    3.3V     ->  3.3V   (NOT 5V — the MFRC522 board is 3.3V only)
    GND      ->  GND

  Library needed: "MFRC522" by GithubCommunity
  (Arduino IDE: Tools > Manage Libraries > search "MFRC522")
*/

#include <SPI.h>
#include <MFRC522.h>

#define SS_PIN 10
#define RST_PIN 9
#define BAUD_RATE 9600

MFRC522 rfid(SS_PIN, RST_PIN);

void setup() {
  Serial.begin(BAUD_RATE);
  SPI.begin();
  rfid.PCD_Init();
}

void loop() {
  if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) {
    return;
  }

  String tagId = "";
  for (byte i = 0; i < rfid.uid.size; i++) {
    if (rfid.uid.uidByte[i] < 0x10) tagId += "0"; // keep each byte 2 hex digits
    tagId += String(rfid.uid.uidByte[i], HEX);
  }
  tagId.toUpperCase();

  Serial.println(tagId);

  rfid.PICC_HaltA();
  rfid.PCD_StopCrypto1();
}
