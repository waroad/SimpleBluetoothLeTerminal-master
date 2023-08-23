#include <SoftwareSerial.h>

SoftwareSerial BTSerial(4, 5);  // 소프트웨어 시리얼 (TX,RX)

int tt=0;   
int dis_send_once=1;
unsigned long previousMillis = 0;  // Holds the last time the value was increased
const unsigned long interval = 1000;
void setup() {
  Serial.begin(9600);        // 통신속도 9600으로 설정
  Serial.println("Hello!");  // 시리얼모니터에 Hello 출력
  BTSerial.begin(9600);
  // BTSerial.write("AT+DISC?");
  // BTSerial.write("AT+NAME?");
  pinMode(7, INPUT);  
  pinMode(8, INPUT);  
  pinMode(2, INPUT);  
  previousMillis = millis();
}

void loop() {
  bool start1 = digitalRead(7);
  bool stop1 = digitalRead(8);
  bool disconnect1 = digitalRead(2);
  unsigned long currentMillis = millis(); 
  if (currentMillis - previousMillis >= interval) {
    dis_send_once=1;
    previousMillis = currentMillis;
  }
  if (disconnect1 == HIGH && dis_send_once==1){
    BTSerial.write("disconnect");
    dis_send_once=0;
  }
  if (start1 == HIGH && tt==0) {
    // Button was pressed
    Serial.print("hey\n");
    BTSerial.write("start");
    tt=1;
  }
  if (stop1 == HIGH && tt==1) {
    // Button was not pressed
    Serial.print("stop\n");
    BTSerial.write("stop");
    tt=0;
  }
  while (BTSerial.available()) {
    byte data = BTSerial.read();
    Serial.write(data);
  }

  while (Serial.available()) {
    byte data = Serial.read();
    BTSerial.write(data);
  }
}

