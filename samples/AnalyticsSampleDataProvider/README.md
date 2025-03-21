# Custom Data Provider for MI Analytics

This sample will allow you to add custom analytics data to the existing event schema.

## Steps to add custom analytics data

1. Add the necessary component versions for `wso2.mi.version`, and `synapse.version` in pom file in the root directory.

2. Build the project using Maven:

``` bash
    mvn clean install
```

3. Copy the generated JAR file from the target folder and place it in `<WSO2MI-HOME>/lib`.

4. Edit the `analytics` configurations in the `deployment.toml` located inside `<WSO2MI-HOME>/conf` with the
following configuration.

```toml
   [analytics]
   enable = true
   custom_data_provider_class="org.wso2.ei.SampleCustomDataProvider"
```

5. Enable statistics for artifacts in `conf/depoyment.toml`.
```toml
    [mediation]
    flow.statistics.enable=true
    flow.statistics.capture_all=true
```

6. Open the `<MI_HOME>/conf` directory and edit the `log4j2.properties file` and add ELK_ANALYTICS_APPENDER to the appenders list.

``` properties
    appenders = ELK_ANALYTICS_APPENDER,.... (list of other available appenders)
```

7. Add the following configuration after the appenders:

``` properties
    appender.ELK_ANALYTICS_APPENDER.type = RollingFile
    appender.ELK_ANALYTICS_APPENDER.name = ELK_ANALYTICS_APPENDER
    appender.ELK_ANALYTICS_APPENDER.fileName = ${sys:carbon.home}/repository/logs/synapse-analytics.log
    appender.ELK_ANALYTICS_APPENDER.filePattern = ${sys:carbon.home}/repository/logs/synapse-analytics-%d{MM-dd-yyyy}-%i.log
    appender.ELK_ANALYTICS_APPENDER.layout.type = PatternLayout
    appender.ELK_ANALYTICS_APPENDER.layout.pattern = %d{HH:mm:ss,SSS} [%X{ip}-%X{host}] [%t] %5p %c{1} %m%n
    appender.ELK_ANALYTICS_APPENDER.policies.type = Policies
    appender.ELK_ANALYTICS_APPENDER.policies.time.type = TimeBasedTriggeringPolicy
    appender.ELK_ANALYTICS_APPENDER.policies.time.interval = 1
    appender.ELK_ANALYTICS_APPENDER.policies.time.modulate = true
    appender.ELK_ANALYTICS_APPENDER.policies.size.type = SizeBasedTriggeringPolicy
    appender.ELK_ANALYTICS_APPENDER.policies.size.size=1000MB
    appender.ELK_ANALYTICS_APPENDER.strategy.type = DefaultRolloverStrategy
    appender.ELK_ANALYTICS_APPENDER.strategy.max = 10
```

8. Add ELKAnalytics to the loggers list:

``` properties
    loggers = ELKAnalytics, ...(list of other available loggers)
```

9. Add the following configurations after the loggers.

``` properties
    logger.ELKAnalytics.name = org.wso2.micro.integrator.analytics.messageflow.data.publisher.publish.elasticsearch.ElasticStatisticsPublisher
    logger.ELKAnalytics.level = DEBUG
    logger.ELKAnalytics.additivity = false
    logger.ELKAnalytics.appenderRef.ELK_ANALYTICS_APPENDER.ref = ELK_ANALYTICS_APPENDER
```

10. Now you can trigger an event and check the` <WSO2MI-HOME>/repository/logs/synapse-analytics.log` to find the event object passed out from the MI.

```
    09:09:38,375 [-] [message-flow-reporter-0-tenant--1234]  INFO ElasticStatisticsPublisher SYNAPSE_ANALYTICS_DATA
    {
      "serverInfo":
        {
          "hostname": "user.local",
          "serverName": "localhost",
          "ipAddress": "127.0.0.1",
          "id": "localhost"
        },
      "timestamp": "2025-03-20T09:53:35.158Z",
      "schemaVersion": 1,
      "payload":
        {
          "metadata":
            {
              "mediatorName": "SequenceMediator"
            },
          "entityType": "API",
          "failure": false,
          "latency": 42,
          "messageId": "urn:uuid:d844a6b5-8744-47bf-ad2f-cf5cbd2a6e42",
          "correlation_id": "8bf6f598-cd8c-401a-88c5-6e8410a3d570",
          "apiDetails":
            {
              "method": "POST",
              "apiContext": "/helloworldapi",
              "api": "HelloWorldAPI",
              "transport": "http",
              "subRequestPath": "/signup"
            },
          "faultResponse": false,
          "entityClassName": "org.apache.synapse.api.API"
        }
    }
```
