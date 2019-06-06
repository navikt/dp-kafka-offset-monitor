> CURRENTLY WIP! 

# kafka-offset-monitor
Check consumer offset lag for a particular consumer group in kafka. Monitor more than one group by specifying a comma-separated list of consumer group to the program env CONSUMER_GROUPS


# HOW ? 

kafka-offset-monitor will query kafka consumer offset for a group and calculate offset lag. The lag is added to a prometheus gauge and exposed. 

Example output:

```http request
namespace_consumer_offset_lag{group_id="group-id",partition="0",topic="topic-name"}	0
namespace_consumer_offset_lag{group_id="group-id",partition="1",topic="topic-name"}	0
namespace_consumer_offset_lag{group_id="group-id",partition="2",topic="topic-name"}	0
```


## Config

the app need current environment vars:

`PROMETHEUS_NAMESPACE` -- namespace of the prometheus metric. eg   `PROMETHEUS_NAMESPACE=dp` --> `dp_consumer_offset_lag`

`CONSUMER_GROUPS` - kafka consumer group id(s). Which group id to be monitored.  `CONSUMER_GROUPS=group1,group2`

`USERNAME` - Service user for app. NB - must have role `CONSUMER` for the particular topics consumed by consumers specified in CONSUMER_GROUPS

`PASSWORD` - Service user credential

