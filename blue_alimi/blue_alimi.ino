#include <SoftwareSerial.h>

#define BLEHM10_PIN_TXD	10
#define BLEHM10_PIN_RXD	11
#define PUSHBUTTON_start	2
#define PUSHBUTTON_stop	3
#define PUSHBUTTON_reset	4
SoftwareSerial BTSerial(BLEHM10_PIN_TXD, BLEHM10_PIN_RXD);  // 소프트웨어 시리얼 (TX,RX)

unsigned long dis_send_once=0;
unsigned long start_send_once=0;
unsigned long stop_send_once=0;
const unsigned long interval = 1000;
void setup() {
  Serial.begin(9600);        // 통신속도 9600으로 설정
  BTSerial.begin(9600);
  Serial.println("Hello!");  // 시리얼모니터에 Hello 출력
  pinMode(PUSHBUTTON_start, INPUT);  
  pinMode(PUSHBUTTON_stop, INPUT);  
  pinMode(PUSHBUTTON_reset, INPUT);  
  dis_send_once = millis();  
  start_send_once = millis();  
  stop_send_once = millis();
}

void loop() {
  bool start1 = digitalRead(PUSHBUTTON_start);
  bool stop1 = digitalRead(PUSHBUTTON_stop);
  bool disconnect1 = digitalRead(PUSHBUTTON_reset);
  unsigned long currentMillis = millis(); 

  // 어떤 메시지가 전송되면 다음 1초간은 해당 메시지를 보내지 못한다.
  if (disconnect1 == HIGH && currentMillis-dis_send_once>=interval){ 
    BTSerial.write("disconnect");
    dis_send_once=millis();
  }
  if (start1 == HIGH && currentMillis-start_send_once>=interval) {
    Serial.print("hey\n");
    BTSerial.write("start");
    start_send_once = millis();
  }
  if (stop1 == HIGH && currentMillis-stop_send_once>=interval) {
    Serial.print("stop\n");
    BTSerial.write("stop");
    stop_send_once = millis();
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

