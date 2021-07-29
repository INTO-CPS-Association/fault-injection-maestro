#!/usr/bin/env python3
## this script flattens the json structure of the msgs sent by the incubator, 
# and forwards it via the rabbitmq server to the rmmfmu
## it also connects to a custom exchange -- this is not yet configurable on the rmqfmu
import pika
import json
import datetime
import time
import threading
import pandas as pd


new_data = False
data = {}
lock = threading.Lock()

def callback(ch, method, properties, body):
    global new_data, data, lock
    with lock:
        new_data = True
        data = body
    print("Received [x] %r" % body)
    print("")

def publishToRmqfmu():
    global new_data, data, lock
    print(' [*] Will publish to rmqfmu')

    credentials = pika.PlainCredentials('incubator', 'incubator')
    connection_rmqfmu = pika.BlockingConnection(
        pika.ConnectionParameters(host='localhost', credentials=credentials))
    channel_rmqfmu = connection_rmqfmu.channel()

    channel_rmqfmu.exchange_declare(exchange='fmi_digital_twin', exchange_type='direct')
 
    try:
        while True:
            if new_data:
                with lock:
                    flattened = pd.json_normalize(json.loads(data))
                    channel_rmqfmu.basic_publish(exchange='fmi_digital_twin', routing_key='incubator.record.driver.state', body=json.dumps(flattened.to_json()))
                    print(flattened)
                    print("")
                    new_data = False
    except KeyboardInterrupt:
        connection_rmqfmu.close()

def consumeFromIncubator():
    credentials = pika.PlainCredentials('incubator', 'incubator')
    connection = pika.BlockingConnection(pika.ConnectionParameters('localhost', credentials=credentials))
    channel = connection.channel()

    print("Declaring exchange")
    channel.exchange_declare(exchange='Incubator_AMQP', exchange_type='topic')

    print("Creating queue")
    result = channel.queue_declare(queue='', exclusive=True)
    queue_name = result.method.queue

    channel.queue_bind(exchange='Incubator_AMQP', queue=queue_name,
                    routing_key='incubator.record.driver.state')

    print(' [*] Waiting for logs. To exit press CTRL+C')
    print(' [*] I am consuming the commands sent from incubator')

    channel.basic_consume(
    queue=queue_name, on_message_callback=callback, auto_ack=True)

    channel.start_consuming()

    connection.close()


thread = threading.Thread(target = publishToRmqfmu)
thread.start()

thread2 = threading.Thread(target = consumeFromIncubator)
thread2.start()



'''
message = {
            "measurement": "low_level_driver",
            "time": timestamp,
            "source": "low_level_driver",
            "t1": readings[0],
            "time_t1": timestamps[0],
            "t2": readings[1],
            "time_t2": timestamps[1],
            "t3": readings[2],
            "time_t3": timestamps[2],
            "average_temperature": (readings[1] + readings[2]) / 2,
            "heater_on": self.heater.is_lit,
            "fan_on": self.fan.is_lit,
            "execution_interval": exec_interval,
            "elapsed": time.time() - start
        }
'''