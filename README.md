# kafka-offset-monitor
Check consumer offset lag for a particular consumer group in kafka. Monitor more than one group by specifying a comma-separated list of consumer group to the program env CONSUMER_GROUP 

Currently wip 

# HOW ? 

kafka-offset-monitor will query kafka for offset for a group and calculate offset lag. The lag is added to a prometheus gauge and exposed. 
