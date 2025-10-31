# WSO2 Micro Integrator wso2mi-4.5.0 Performance Test Results

During each release, we execute various automated performance test scenarios and publish the results.

| Test Scenarios | Description |
| --- | --- |
| Direct Proxy | Passthrough proxy service |
| CBR Proxy | Routing the message based on the content of the message body |
| XSLT Proxy | Having XSLT transformations in request and response paths |
| Direct API | Passthrough API service |

Our test client is [Apache JMeter](https://jmeter.apache.org/index.html). We test each scenario for a fixed duration of
time. We split the test results into warmup and measurement parts and use the measurement part to compute the
performance metrics.

Test scenarios use a [Netty](https://netty.io/) based back-end service which echoes back any request
posted to it after a specified period of time.

We run the performance tests under different numbers of concurrent users, message sizes (payloads) and back-end service
delays.

The main performance metrics:

1. **Throughput**: The number of requests that the WSO2 Micro Integrator wso2mi-4.5.0 processes during a specific time interval (e.g. per second).
2. **Response Time**: The end-to-end latency for an operation of invoking a service in WSO2 Micro Integrator wso2mi-4.5.0 . The complete distribution of response times was recorded.

In addition to the above metrics, we measure the load average and several memory-related metrics.

The following are the test parameters.

| Test Parameter | Description | Values |
| --- | --- | --- |
| Scenario Name | The name of the test scenario. | Refer to the above table. |
| Heap Size | The amount of memory allocated to the application | 2G |
| Concurrent Users | The number of users accessing the application at the same time. | 100, 200, 500, 1000 |
| Message Size (Bytes) | The request payload size in Bytes. | 500, 1000, 10000, 100000 |
| Back-end Delay (ms) | The delay added by the Back-end service. | 0 |

The duration of each test is **300 seconds**. The warm-up period is **120 seconds**.
The measurement results are collected after the warm-up period.

The performance tests were executed on 1 AWS CloudFormation stack.


System information for WSO2 Micro Integrator wso2mi-4.5.0 in 1st AWS CloudFormation stack.

| Class | Subclass | Description | Value |
| --- | --- | --- | --- |
| AWS | EC2 | AMI-ID | ami-0c398cb65a93047f2 |
| AWS | EC2 | Instance Type | c5.large |
| System | Processor | CPU(s) | 2 |
| System | Processor | Thread(s) per core | 2 |
| System | Processor | Core(s) per socket | 1 |
| System | Processor | Socket(s) | 1 |
| System | Processor | Model name | Intel(R) Xeon(R) Platinum 8124M CPU @ 3.00GHz |
| System | Memory | BIOS | 64 KiB |
| System | Memory | System Memory | 4 GiB |
| System | Storage | Block Device: nvme0n1 | 8G |
| Operating System | Distribution | Release | Ubuntu 22.04.5 LTS |
| Operating System | Distribution | Kernel | Linux ip-10-0-1-68 6.8.0-1040-aws #42~22.04.1-Ubuntu SMP Wed Sep 24 10:26:57 UTC 2025 x86_64 x86_64 x86_64 GNU/Linux |


The following are the measurements collected from each performance test conducted for a given combination of
test parameters.

| Measurement | Description |
| --- | --- |
| Error % | Percentage of requests with errors |
| Average Response Time (ms) | The average response time of a set of results |
| Standard Deviation of Response Time (ms) | The “Standard Deviation” of the response time. |
| 99th Percentile of Response Time (ms) | 99% of the requests took no more than this time. The remaining samples took at least as long as this |
| Throughput (Requests/sec) | The throughput measured in requests per second. |
| Average Memory Footprint After Full GC (M) | The average memory consumed by the application after a full garbage collection event. |

The following is the summary of performance test results collected for the measurement period.

|  Scenario Name | Heap Size | Concurrent Users | Message Size (Bytes) | Back-end Service Delay (ms) | Error % | Throughput (Requests/sec) | Average Response Time (ms) | Standard Deviation of Response Time (ms) | 99th Percentile of Response Time (ms) | WSO2 Micro Integrator wso2mi-4.5.0 GC Throughput (%) | Average WSO2 Micro Integrator wso2mi-4.5.0 Memory Footprint After Full GC (M) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|  CBR Proxy | 2G | 100 | 500 | 0 | 0 | 5032.02 | 19.81 | 27.79 | 119 |  |  |
|  CBR Proxy | 2G | 100 | 1000 | 0 | 0 | 4252.25 | 23.45 | 34.37 | 153 |  |  |
|  CBR Proxy | 2G | 100 | 10000 | 0 | 0 | 1157.28 | 86.29 | 75.97 | 287 |  |  |
|  CBR Proxy | 2G | 100 | 100000 | 0 | 0 | 69.68 | 1429.68 | 430.92 | 2639 |  |  |
|  CBR Proxy | 2G | 200 | 500 | 0 | 0 | 5038.83 | 39.61 | 72.6 | 339 |  |  |
|  CBR Proxy | 2G | 200 | 1000 | 0 | 0 | 4270.05 | 46.76 | 90.58 | 405 |  |  |
|  CBR Proxy | 2G | 200 | 10000 | 0 | 0 | 1132.58 | 176.17 | 134.61 | 643 |  |  |
|  CBR Proxy | 2G | 200 | 100000 | 0 | 0 | 58.79 | 3366.22 | 931.36 | 5727 |  |  |
|  CBR Proxy | 2G | 500 | 500 | 0 | 0 | 5146.91 | 96.98 | 212.79 | 1007 |  |  |
|  CBR Proxy | 2G | 500 | 1000 | 0 | 0 | 4202.34 | 118.64 | 259.82 | 1167 |  |  |
|  CBR Proxy | 2G | 500 | 10000 | 0 | 0 | 1090.54 | 457.49 | 274.23 | 1463 |  |  |
|  CBR Proxy | 2G | 500 | 100000 | 0 | 0 | 46.74 | 10354.3 | 3156.27 | 17791 |  |  |
|  CBR Proxy | 2G | 1000 | 500 | 0 | 0 | 4944.32 | 201.84 | 223.29 | 1135 |  |  |
|  CBR Proxy | 2G | 1000 | 1000 | 0 | 0 | 4084.15 | 244.19 | 268.6 | 1311 |  |  |
|  CBR Proxy | 2G | 1000 | 10000 | 0 | 0 | 710.71 | 1399.14 | 869.3 | 4703 |  |  |
|  CBR Proxy | 2G | 1000 | 100000 | 0 | 0 | 37.81 | 24522.49 | 5845.16 | 31487 | N/A | N/A |
|  Direct API | 2G | 100 | 500 | 0 | 0 | 8743.17 | 11.37 | 7.53 | 30 |  |  |
|  Direct API | 2G | 100 | 1000 | 0 | 0 | 8528.08 | 11.67 | 7.31 | 30 |  |  |
|  Direct API | 2G | 100 | 10000 | 0 | 0 | 6390.17 | 15.58 | 9.13 | 39 |  |  |
|  Direct API | 2G | 100 | 100000 | 0 | 0 | 1844.74 | 54.07 | 14.4 | 99 |  |  |
|  Direct API | 2G | 200 | 500 | 0 | 0 | 8632.96 | 23.1 | 13.76 | 63 |  |  |
|  Direct API | 2G | 200 | 1000 | 0 | 0 | 8480.87 | 23.51 | 13.66 | 61 |  |  |
|  Direct API | 2G | 200 | 10000 | 0 | 0 | 6310.01 | 31.61 | 17.61 | 79 |  |  |
|  Direct API | 2G | 200 | 100000 | 0 | 0 | 1814.3 | 110.03 | 27.73 | 200 |  |  |
|  Direct API | 2G | 500 | 500 | 0 | 0 | 8052.57 | 61.98 | 31.01 | 169 |  |  |
|  Direct API | 2G | 500 | 1000 | 0 | 0 | 7851.16 | 63.57 | 32.67 | 192 |  |  |
|  Direct API | 2G | 500 | 10000 | 0 | 0 | 6143.78 | 81.26 | 35.25 | 190 |  |  |
|  Direct API | 2G | 500 | 100000 | 0 | 0 | 1621.07 | 308.37 | 64.84 | 509 |  |  |
|  Direct API | 2G | 1000 | 500 | 0 | 0 | 7983.47 | 125.07 | 46.73 | 273 |  |  |
|  Direct API | 2G | 1000 | 1000 | 0 | 0 | 7942.12 | 125.67 | 47.3 | 275 |  |  |
|  Direct API | 2G | 1000 | 10000 | 0 | 0 | 6059.59 | 164.79 | 58.31 | 343 |  |  |
|  Direct API | 2G | 1000 | 100000 | 0 | 0 | 1568.8 | 636.06 | 116.97 | 975 |  |  |
|  Direct Proxy | 2G | 100 | 500 | 0 | 0 | 9324.51 | 10.67 | 8.07 | 29 |  |  |
|  Direct Proxy | 2G | 100 | 1000 | 0 | 0 | 9292.42 | 10.7 | 7.59 | 28 |  |  |
|  Direct Proxy | 2G | 100 | 10000 | 0 | 0 | 6533.58 | 15.23 | 10.4 | 38 |  |  |
|  Direct Proxy | 2G | 100 | 100000 | 0 | 0 | 1876.85 | 53.15 | 14.06 | 98 |  |  |
|  Direct Proxy | 2G | 200 | 500 | 0 | 0 | 9290.57 | 21.45 | 13.59 | 58 |  |  |
|  Direct Proxy | 2G | 200 | 1000 | 0 | 0 | 9159.91 | 21.76 | 12.21 | 55 |  |  |
|  Direct Proxy | 2G | 200 | 10000 | 0 | 0 | 6499.38 | 30.69 | 17.1 | 79 |  |  |
|  Direct Proxy | 2G | 200 | 100000 | 0 | 0 | 1839.48 | 108.51 | 27.21 | 200 |  |  |
|  Direct Proxy | 2G | 500 | 500 | 0 | 0 | 8431.11 | 59.21 | 29.61 | 168 |  |  |
|  Direct Proxy | 2G | 500 | 1000 | 0 | 0 | 8503.39 | 58.68 | 28.01 | 157 |  |  |
|  Direct Proxy | 2G | 500 | 10000 | 0 | 0 | 6418.14 | 77.76 | 35.44 | 210 |  |  |
|  Direct Proxy | 2G | 500 | 100000 | 0 | 0 | 1647 | 303.51 | 63.85 | 507 |  |  |
|  Direct Proxy | 2G | 1000 | 500 | 0 | 0 | 8211.91 | 121.56 | 45.78 | 263 |  |  |
|  Direct Proxy | 2G | 1000 | 1000 | 0 | 0 | 8278.2 | 120.64 | 47.04 | 273 |  |  |
|  Direct Proxy | 2G | 1000 | 10000 | 0 | 0 | 6381.97 | 156.48 | 60.25 | 365 |  |  |
|  Direct Proxy | 2G | 1000 | 100000 | 0 | 0 | 1575.26 | 633.41 | 116.24 | 967 |  |  |
|  XSLT Proxy | 2G | 100 | 500 | 0 | 0 | 2395.33 | 41.67 | 60.85 | 247 |  |  |
|  XSLT Proxy | 2G | 100 | 1000 | 0 | 0 | 1712.82 | 58.18 | 67.97 | 269 |  |  |
|  XSLT Proxy | 2G | 100 | 10000 | 0 | 0 | 272.58 | 366.69 | 287.83 | 2111 |  |  |
|  XSLT Proxy | 2G | 100 | 100000 | 0 | 0 | 21.17 | 4650.19 | 998.32 | 7551 |  |  |
|  XSLT Proxy | 2G | 200 | 500 | 0 | 0 | 2367.18 | 84.32 | 149.36 | 627 |  |  |
|  XSLT Proxy | 2G | 200 | 1000 | 0 | 0 | 1679.11 | 118.97 | 169.28 | 767 |  |  |
|  XSLT Proxy | 2G | 200 | 10000 | 0 | 0 | 266.17 | 748.81 | 568.74 | 3615 |  |  |
|  XSLT Proxy | 2G | 200 | 100000 | 0 | 0 | 17.32 | 10976.5 | 4879.64 | 24447 |  |  |
|  XSLT Proxy | 2G | 500 | 500 | 0 | 0 | 2064.12 | 241.32 | 469.85 | 1887 |  |  |
|  XSLT Proxy | 2G | 500 | 1000 | 0 | 0 | 1536.3 | 324.63 | 548.43 | 2223 |  |  |
|  XSLT Proxy | 2G | 500 | 10000 | 0 | 0 | 207.4 | 2395.91 | 1364.88 | 6879 |  |  |
|  XSLT Proxy | 2G | 500 | 100000 | 0 | 0 | 15.49 | 27886.4 | 15197.86 | 70655 |  |  |
|  XSLT Proxy | 2G | 1000 | 500 | 0 | 0 | 1643.43 | 605.96 | 597.63 | 2591 |  |  |
|  XSLT Proxy | 2G | 1000 | 1000 | 0 | 0 | 1432.72 | 694.11 | 622.24 | 2831 |  |  |
|  XSLT Proxy | 2G | 1000 | 10000 | 0 | 0 | 190.52 | 5170.79 | 2415.73 | 12287 |  |  |
|  XSLT Proxy | 2G | 1000 | 100000 | 0 | 0 | 15.52 | 54651.93 | 5176.38 | 66047 | N/A | N/A |
