# WSO2 Micro Integrator wso2mi-4.4.0 Performance Test Results

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

1. **Throughput**: The number of requests that the WSO2 Micro Integrator wso2mi-4.4.0 processes during a specific time interval (e.g. per second).
2. **Response Time**: The end-to-end latency for an operation of invoking a service in WSO2 Micro Integrator wso2mi-4.4.0 . The complete distribution of response times was recorded.

In addition to the above metrics, we measure the load average and several memory-related metrics.

The following are the test parameters.

| Test Parameter | Description | Values |
| --- | --- | --- |
| Scenario Name | The name of the test scenario. | Refer to the above table. |
| Heap Size | The amount of memory allocated to the application | 2G |
| Concurrent Users | The number of users accessing the application at the same time. | 100, 200, 500, 1000 |
| Message Size (Bytes) | The request payload size in Bytes. | 500, 1000, 10000, 100000 |
| Back-end Delay (ms) | The delay added by the Back-end service. | 0 |

The duration of each test is **360 seconds**. The warm-up period is **120 seconds**.
The measurement results are collected after the warm-up period.

The performance tests were executed on 1 AWS CloudFormation stack.


System information for WSO2 Micro Integrator wso2mi-4.4.0 in 1st AWS CloudFormation stack.

| Class | Subclass | Description | Value |
| --- | --- | --- | --- |
| AWS | EC2 | AMI-ID | ami-055744c75048d8296 |
| AWS | EC2 | Instance Type | c5.xlarge |
| System | Processor | CPU(s) | 4 |
| System | Processor | Thread(s) per core | 2 |
| System | Processor | Core(s) per socket | 2 |
| System | Processor | Socket(s) | 1 |
| System | Processor | Model name | Intel(R) Xeon(R) Platinum 8275CL CPU @ 3.00GHz |
| System | Memory | BIOS | 64 KiB |
| System | Memory | System Memory | 8 GiB |
| System | Storage | Block Device: nvme0n1 | 8G |
| Operating System | Distribution | Release | Ubuntu 18.04.6 LTS |
| Operating System | Distribution | Kernel | Linux ip-10-0-1-43 5.4.0-1103-aws #111~18.04.1-Ubuntu SMP Tue May 23 20:04:10 UTC 2023 x86_64 x86_64 x86_64 GNU/Linux |


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

|  Scenario Name | Heap Size | Concurrent Users | Message Size (Bytes) | Back-end Service Delay (ms) | Error % | Throughput (Requests/sec) | Average Response Time (ms) | Standard Deviation of Response Time (ms) | 99th Percentile of Response Time (ms) | WSO2 Micro Integrator wso2mi-4.4.0 GC Throughput (%) | Average WSO2 Micro Integrator wso2mi-4.4.0 Memory Footprint After Full GC (M) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|  CBR Proxy | 2G | 100 | 500 | 0 | 0 | 6644.93 | 14.97 | 19.59 | 123 | N/A | N/A |
|  CBR Proxy | 2G | 100 | 1000 | 0 | 0 | 5646.16 | 17.63 | 21.84 | 134 | N/A | N/A |
|  CBR Proxy | 2G | 100 | 10000 | 0 | 0 | 1557.29 | 64.1 | 56.28 | 265 | N/A | N/A |
|  CBR Proxy | 2G | 100 | 100000 | 0 | 0 | 121.19 | 824.05 | 283.63 | 1607 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 500 | 0 | 0 | 7194.27 | 27.71 | 26.01 | 142 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 1000 | 0 | 0 | 6267.36 | 31.82 | 28.85 | 155 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 10000 | 0 | 0 | 1601.19 | 124.77 | 97.43 | 457 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 100000 | 0 | 0 | 115.03 | 1732.99 | 600.88 | 3375 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 500 | 0 | 0 | 7374.33 | 67.67 | 47.25 | 236 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 1000 | 0 | 0 | 6314.3 | 79.05 | 54.38 | 273 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 10000 | 0 | 0 | 1563 | 319.73 | 188.3 | 919 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 100000 | 0 | 0 | 80.42 | 6106.48 | 1969.62 | 11007 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 500 | 0 | 0 | 6904.32 | 144.53 | 78.27 | 395 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 1000 | 0 | 0 | 6082.43 | 164.04 | 85.35 | 435 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 10000 | 0 | 0 | 1120.91 | 889.93 | 449.09 | 2207 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 100000 | 0 | 0 | 50.27 | 19282.28 | 4206.26 | 29183 | N/A | N/A |
|  Direct API | 2G | 100 | 500 | 0 | 0 | 8973.29 | 11.07 | 17.17 | 110 | N/A | N/A |
|  Direct API | 2G | 100 | 1000 | 0 | 0 | 8997.33 | 11.04 | 17.33 | 111 | N/A | N/A |
|  Direct API | 2G | 100 | 10000 | 0 | 0 | 7344.85 | 13.54 | 18.14 | 113 | N/A | N/A |
|  Direct API | 2G | 100 | 100000 | 0 | 0 | 2668.33 | 37.36 | 11.45 | 72 | N/A | N/A |
|  Direct API | 2G | 200 | 500 | 0 | 0 | 9802.64 | 20.31 | 21.2 | 120 | N/A | N/A |
|  Direct API | 2G | 200 | 1000 | 0 | 0 | 9569.93 | 20.81 | 20.68 | 117 | N/A | N/A |
|  Direct API | 2G | 200 | 10000 | 0 | 0 | 7933.35 | 25.11 | 22.34 | 123 | N/A | N/A |
|  Direct API | 2G | 200 | 100000 | 0 | 0 | 2619.18 | 76.2 | 20.35 | 136 | N/A | N/A |
|  Direct API | 2G | 500 | 500 | 0 | 0 | 10364.58 | 48.11 | 33.73 | 171 | N/A | N/A |
|  Direct API | 2G | 500 | 1000 | 0 | 0 | 10392.06 | 47.99 | 34.6 | 176 | N/A | N/A |
|  Direct API | 2G | 500 | 10000 | 0 | 0 | 8150.34 | 61.2 | 37.28 | 189 | N/A | N/A |
|  Direct API | 2G | 500 | 100000 | 0 | 0 | 2464.76 | 202.64 | 43.29 | 331 | N/A | N/A |
|  Direct API | 2G | 1000 | 500 | 0 | 0 | 10331.15 | 96.35 | 55.71 | 285 | N/A | N/A |
|  Direct API | 2G | 1000 | 1000 | 0 | 0 | 10925.54 | 91.17 | 53.88 | 279 | N/A | N/A |
|  Direct API | 2G | 1000 | 10000 | 0 | 0 | 8181.11 | 121.77 | 58.44 | 305 | N/A | N/A |
|  Direct API | 2G | 1000 | 100000 | 0 | 0 | 2329.26 | 428.75 | 76.77 | 659 | N/A | N/A |
|  Direct Proxy | 2G | 100 | 500 | 0 | 0 | 10206.18 | 9.72 | 16.48 | 104 |  |  |
|  Direct Proxy | 2G | 100 | 1000 | 0 | 0 | 10541.08 | 9.42 | 16.12 | 102 | N/A | N/A |
|  Direct Proxy | 2G | 100 | 10000 | 0 | 0 | 8603.81 | 11.54 | 16.88 | 103 | N/A | N/A |
|  Direct Proxy | 2G | 100 | 100000 | 0 | 0 | 2949.08 | 33.78 | 10.23 | 65 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 500 | 0 | 0 | 10799.81 | 18.43 | 19.92 | 112 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 1000 | 0 | 0 | 10830.49 | 18.38 | 19.67 | 112 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 10000 | 0 | 0 | 8317 | 23.95 | 20.81 | 110 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 100000 | 0 | 0 | 2754.62 | 72.46 | 23.75 | 141 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 500 | 0 | 0 | 11313.47 | 44.08 | 30.85 | 154 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 1000 | 0 | 0 | 11212.37 | 44.47 | 30.96 | 154 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 10000 | 0 | 0 | 8664.3 | 57.56 | 34.88 | 174 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 100000 | 0 | 0 | 2526.88 | 197.66 | 51.36 | 341 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 500 | 0 | 0 | 11128.01 | 89.66 | 50.66 | 261 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 1000 | 0 | 0 | 10874.57 | 91.6 | 50.78 | 261 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 10000 | 0 | 0 | 8488.01 | 117.5 | 55.72 | 289 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 100000 | 0 | 0 | 2401.32 | 415.32 | 91.82 | 675 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 500 | 0 | 0 | 3602.16 | 27.22 | 259.74 | 161 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 1000 | 0 | 0 | 2642.65 | 37.43 | 154.95 | 188 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 10000 | 0 | 0 | 445.55 | 223.68 | 139.08 | 631 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 100000 | 0 | 0 | 35.12 | 2819.85 | 448.26 | 3967 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 500 | 0 | 0 | 3821.63 | 52.24 | 132.12 | 215 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 1000 | 0 | 0 | 2833.23 | 69.96 | 156.23 | 285 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 10000 | 0 | 0 | 439.79 | 454.72 | 268.04 | 1247 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 100000 | 0 | 0 | 30.72 | 6392.51 | 1018.76 | 8895 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 500 | 0 | 0 | 3871.77 | 128.98 | 84.07 | 415 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 1000 | 0 | 0 | 2685.63 | 185.95 | 116.72 | 575 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 10000 | 0 | 0 | 313.68 | 1584.14 | 919.68 | 4447 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 100000 | 0 | 0 | 28.6 | 16657.53 | 2856.76 | 23807 | N/A | N/A |
|  XSLT Proxy | 2G | 1000 | 500 | 0 | 0 | 2846.39 | 351.18 | 199.42 | 1019 | N/A | N/A |
|  XSLT Proxy | 2G | 1000 | 1000 | 0 | 0 | 2140.35 | 466.43 | 250.95 | 1327 | N/A | N/A |
|  XSLT Proxy | 2G | 1000 | 10000 | 0 | 0 | 337.94 | 2934.12 | 1179.31 | 6239 | N/A | N/A |
|  XSLT Proxy | 2G | 1000 | 100000 | 0 | 0 | 26.89 | 33762.09 | 3299.29 | 41215 | N/A | N/A |
