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
package feign.example.github;

import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.gson.GsonModule;
import java.util.List;
import javax.inject.Named;

/** adapted from {@code com.example.retrofit.GitHubClient} */
public class GitHubExample {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) throws InterruptedException {
    GitHub github =
        Feign.create(GitHub.class, "https://api.github.com", new GsonModule(), new LogToStderr());

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }

  @Module(overrides = true, library = true, includes = GsonModule.class)
  static class LogToStderr {

    @Provides
    Logger.Level loggingLevel() {
      return Logger.Level.BASIC;
    }

    @Provides
    Logger logger() {
      return new Logger.ErrorLogger();
    }
  }
}