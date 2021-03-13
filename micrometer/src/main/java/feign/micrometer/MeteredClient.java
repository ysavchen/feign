/**
 * Copyright 2012-2021 The Feign Authors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.micrometer;

import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Request.Options;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.io.IOException;

/** Warp feign {@link Client} with metrics. */
public class MeteredClient implements Client {

  private final Client client;
  private final MeterRegistry meterRegistry;
  private final MetricName metricName;

  public MeteredClient(Client client, MeterRegistry meterRegistry) {
    this(client, meterRegistry, new FeignMetricName(Client.class));
  }

  public MeteredClient(Client client, MeterRegistry meterRegistry, MetricName metricName) {
    this.client = client;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final RequestTemplate template = request.requestTemplate();

    try {
      return meterRegistry
          .timer(
              metricName.name(), metricName.tag(template.methodMetadata(), template.feignTarget()))
          .recordCallable(
              () -> {
                Response response = client.execute(request, options);
                meterRegistry
                    .counter(
                        metricName.name("http_response_code"),
                        metricName.tag(
                            template.methodMetadata(),
                            template.feignTarget(),
                            Tag.of("http_status", String.valueOf(response.status())),
                            Tag.of("status_group", response.status() / 100 + "xx")))
                    .increment();
                return response;
              });
    } catch (FeignException e) {
      meterRegistry
          .counter(
              metricName.name("http_response_code"),
              metricName.tag(
                  template.methodMetadata(),
                  template.feignTarget(),
                  Tag.of("http_status", String.valueOf(e.status())),
                  Tag.of("status_group", e.status() / 100 + "xx")))
          .increment();
      throw e;
    } catch (IOException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
}
