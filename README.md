oXygen XML Web Author Monitoring plugin
=======================================

This plugin is used to provide monitoring information from a running Web Author instance.

Web Author collects various metrics about its runtime behavior. It can be configure to report these metrics in a number of ways.

In the browser
--------------

To access the information you should access one of the following URLs Note that you have to be logged in the Administration Page when visiting these URLs.
  - `http://host:port/oxygen-xml-web-author/plugins-dispatcher/monitoring/threads` - to get thread dumps of all threads
  - `http://host:port/oxygen-xml-web-author/plugins-dispatcher/monitoring/metrics` - to get various metrics about Web Author
  
In the server logs
------------------

By default, the monitoring information is written in JSON format in the server logs every minute. You can use various tools to 
parse this information and present a graphical dashboard.


To a Graphite server
--------------------

You have to set the `GRAPHITE_SERVER` environment variable in order for Web Author to send monitoring information to this server.

To AWS CloudWatch
-----------------

You have to set the following environment variables:

- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- AWS_DEFAULT_REGION
- https_proxy - in cases you use one 

By extending this plugin you can send the collected metrics also to other metrics servers.

Copyright and License
---------------------
Copyright 2018 Syncro Soft SRL.

This project is licensed under [Apache License 2.0](https://github.com/oxygenxml/web-author-monitoring-plugin/blob/master/LICENSE)
