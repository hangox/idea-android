/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.perf;

/**
 * Measure performance for Single variant sync using the average project with AGP 3.5 and Gradle 5.5.
 */
public class AverageSVS35PerfTest extends AverageSVSPerfTest {
  @Override
  public String getAGPVersion() {
    return "3.5.0";
  }

  @Override
  public String getGradleVersion() {
    return "5.5";
  }
}
