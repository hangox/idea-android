/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class OldAndroidPluginIssueCheckerTest: AndroidGradleTestCase() {
  private val oldAndroidPluginIssueChecker = OldAndroidPluginIssueChecker()

  fun testCheckIssue() {
    val errMsg = "The android gradle plugin version 2.3.0-alpha1 is too old, please update to the latest version."
    val issueData = GradleIssueData(projectFolderPath.path, Throwable(errMsg), null, null)

    val buildIssue = oldAndroidPluginIssueChecker.check(issueData)
    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains(errMsg)
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(FixAndroidGradlePluginVersionQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)
  }

  fun testCheckIssueHandled() {
    assertThat(
      oldAndroidPluginIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Plugin is too old, please update to a more recent version",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)

    assertThat(
      oldAndroidPluginIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "The android gradle plugin version 2.2.0 is too old, please update to the latest version.",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isEqualTo(true)
  }
}