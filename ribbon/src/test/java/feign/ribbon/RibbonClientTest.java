/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.ribbon;

import static com.netflix.config.ConfigurationManager.getConfigInstance;
import static org.junit.Assert.assertEquals;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.SocketPolicy;
import com.squareup.okhttp.mockwebserver.rule.MockWebServerRule;
import dagger.Provides;
import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.io.IOException;
import java.net.URL;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class RibbonClientTest {
  @Rule public final TestName testName = new TestName();
  @Rule public final MockWebServerRule server1 = new MockWebServerRule();
  @Rule public final MockWebServerRule server2 = new MockWebServerRule();

  interface TestInterface {
    @RequestLine("POST /")
    void post();

    @RequestLine("GET /?a={a}")
    void getWithQueryParameters(@Param("a") String a);

    @dagger.Module(injects = Feign.class, overrides = true, addsTo = Feign.Defaults.class)
    static class Module {
      @Provides
      Decoder defaultDecoder() {
        return new Decoder.Default();
      }

      @Provides
      Encoder defaultEncoder() {
        return new Encoder.Default();
      }
    }
  }

  @Test
  public void loadBalancingDefaultPolicyRoundRobin() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setBody("success!"));
    server2.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance()
        .setProperty(
            serverListKey(),
            hostAndPort(server1.getUrl("")) + "," + hostAndPort(server2.getUrl("")));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://" + client(),
            new TestInterface.Module(),
            new RibbonModule());

    api.post();
    api.post();

    assertEquals(1, server1.getRequestCount());
    assertEquals(1, server2.getRequestCount());
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  @Test
  public void ioExceptionRetry() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.getUrl("")));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://" + client(),
            new TestInterface.Module(),
            new RibbonModule());

    api.post();

    assertEquals(2, server1.getRequestCount());
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  /*
  This test-case replicates a bug that occurs when using RibbonRequest with a query string.

  The querystrings would not be URL-encoded, leading to invalid HTTP-requests if the query string contained
  invalid characters (ex. space).
  */
  @Test
  public void urlEncodeQueryStringParameters() throws IOException, InterruptedException {
    String queryStringValue = "some string with space";
    String expectedQueryStringValue = "some+string+with+space";
    String expectedRequestLine = String.format("GET /?a=%s HTTP/1.1", expectedQueryStringValue);

    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.getUrl("")));

    TestInterface api =
        Feign.create(
            TestInterface.class,
            "http://" + client(),
            new TestInterface.Module(),
            new RibbonModule());

    api.getWithQueryParameters(queryStringValue);

    final String recordedRequestLine = server1.takeRequest().getRequestLine();

    assertEquals(recordedRequestLine, expectedRequestLine);
  }

  @Test
  public void ioExceptionRetryWithBuilder() throws IOException, InterruptedException {
    server1.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    server1.enqueue(new MockResponse().setBody("success!"));

    getConfigInstance().setProperty(serverListKey(), hostAndPort(server1.getUrl("")));

    TestInterface api =
        Feign.builder()
            .client(new RibbonClient())
            .target(TestInterface.class, "http://" + client());

    api.post();

    assertEquals(server1.getRequestCount(), 2);
    // TODO: verify ribbon stats match
    // assertEquals(target.lb().getLoadBalancerStats().getSingleServerStat())
  }

  static String hostAndPort(URL url) {
    // our build slaves have underscores in their hostnames which aren't permitted by ribbon
    return "localhost:" + url.getPort();
  }

  private String client() {
    return testName.getMethodName();
  }

  private String serverListKey() {
    return client() + ".ribbon.listOfServers";
  }

  @After
  public void clearServerList() {
    getConfigInstance().clearProperty(serverListKey());
  }
}