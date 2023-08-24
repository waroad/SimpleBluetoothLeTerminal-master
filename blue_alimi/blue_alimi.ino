#include <SoftwareSerial.h>

#define BLEHM10_PIN_TXD	10
#define BLEHM10_PIN_RXD	11
#define PUSHBUTTON_start	2
#define PUSHBUTTON_stop	3
#define PUSHBUTTON_reset	4
SoftwareSerial BTSerial(BLEHM10_PIN_TXD, BLEHM10_PIN_RXD);  // 소프트웨어 시리얼 (TX,RX)

int tt=0;   
int dis_send_once=1;
unsigned long previousMillis = 0;  // Holds the last time the value was increased
const unsigned long interval = 1000;
void setup() {
  Serial.begin(9600);        // 통신속도 9600으로 설정
  BTSerial.begin(9600);
  Serial.println("Hello!");  // 시리얼모니터에 Hello 출력
  pinMode(PUSHBUTTON_start, INPUT);  
  pinMode(PUSHBUTTON_stop, INPUT);  
  pinMode(PUSHBUTTON_reset, INPUT);  
  previousMillis = millis();
}

void loop() {
  bool start1 = digitalRead(PUSHBUTTON_start);
  bool stop1 = digitalRead(PUSHBUTTON_stop);
  bool disconnect1 = digitalRead(PUSHBUTTON_reset);
  unsigned long currentMillis = millis(); 
  if (currentMillis - previousMillis >= interval) {
    dis_send_once=1;
    previousMillis = currentMillis;
  }
  // dis_send_once를 이용해 disconnect가 최대 1초에 1번만 전송되도록
  if (disconnect1 == HIGH && dis_send_once==1){ 
    BTSerial.write("disconnect");
    dis_send_once=0;
  }
  // 똑같이 tt value를 이용해, start가 1번만 전송되고, stop이 눌려야 다시 전송 가능
  if (start1 == HIGH && tt==0) {
    Serial.print("hey\n");
    BTSerial.write("start");
    tt=1;
  }
  if (stop1 == HIGH && tt==1) {
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

