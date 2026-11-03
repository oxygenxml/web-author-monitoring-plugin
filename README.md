Oxygen XML Web Author Monitoring plugin
=======================================

This plugin is used to provide monitoring information from a running Web Author instance.

Web Author collects various metrics about its runtime behavior. It can be configure to report these metrics in a number of ways.

In the browser
--------------

To access the information you should access one of the following URLs Note that you have to be logged in the Administration Page when visiting these URLs.
  - `http://host:port/oxygen-xml-web-author/plugins-dispatcher/monitoring/threads` - to get thread dumps of all threads
  - `http://host:port/oxygen-xml-web-author/plugins-dispatcher/monitoring/metrics` - to get various metrics about Web Author

The `metrics` URL accepts the `pretty=true` parameter to indent the JSON. The `threads` URL accepts the `monitors=false` and `synchronizers=false` parameters to leave out the locked monitors and synchronizers.

Starting with Web Author 29, the plugin no longer uses the Dropwizard `MetricsServlet` and `ThreadDumpServlet` (they are based on `javax.servlet`, which Tomcat 10 no longer provides). The metrics are written with the same defaults as before: rates and durations in seconds and no histogram samples. A `com.codahale.metrics.MetricFilter` set in the `com.codahale.metrics.servlets.MetricsServlet.metricFilter` servlet context attribute is still used to select the metrics. The following servlet context parameters are no longer read: `com.codahale.metrics.servlets.MetricsServlet.rateUnit`, `.durationUnit`, `.showSamples`, `.allowedOrigin` and `.jsonpCallback`.
  
In the server logs
------------------

By default, the monitoring information is written in JSON format in the server logs every minute. You can use various tools to 
parse this information and present a graphical dashboard.

To AWS CloudWatch
-----------------

You have to set the following environment variables:

- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- AWS_DEFAULT_REGION
- https_proxy - in cases you use one 

By extending this plugin you can send the collected metrics also to other metrics servers.


Custom configuration
-----------------
The name of the logger used by this plugin to log the above information is ``NO_LAYOUT_LOGGER.METRICS``

WebAuthor default configuration defines a logger named ``NO_LAYOUT_LOGGER``. This logger has a console appender with no layout, so 
any child logger (with a naming scheme ``NO_LAYOUT_LOGGER.<LOGGER_NAME>``) will log only the messages to stdout.

To override this default configuration, a ``NO_LAYOUT_LOGGER.METRICS`` logger must be defined explicitly in WebAuthor logging configuration. 

Copyright and License
---------------------
Copyright 2018 Syncro Soft SRL.

This project is licensed under [Apache License 2.0](https://github.com/oxygenxml/web-author-monitoring-plugin/blob/master/LICENSE)
