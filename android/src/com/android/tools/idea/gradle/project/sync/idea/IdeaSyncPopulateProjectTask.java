/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeaSyncPopulateProjectTask {
  @NotNull private final Project myProject;
  @NotNull private final PostSyncProjectSetup myProjectSetup;
  @NotNull private final ProjectDataManager myDataManager;

  public IdeaSyncPopulateProjectTask(@NotNull Project project) {
    this(project, PostSyncProjectSetup.getInstance(project), ProjectDataManager.getInstance());
  }

  @VisibleForTesting
  IdeaSyncPopulateProjectTask(@NotNull Project project,
                              @NotNull PostSyncProjectSetup projectSetup,
                              @NotNull ProjectDataManager dataManager) {
    myProject = project;
    myProjectSetup = projectSetup;
    myDataManager = dataManager;
  }

  @WorkerThread
  public void populateProject(@NotNull DataNode<ProjectData> projectInfo,
                              @Nullable PostSyncProjectSetup.Request setupRequest,
                              @Nullable GradleSyncListener syncListener) {
    invokeAndWaitIfNeeded((Runnable)() -> {
      if (myProject.isDisposed()) return;
      GradleSyncMessages.getInstance(myProject).removeAllMessages();
    });
    myDataManager.importData(projectInfo, myProject, true /* synchronous */);
    if (syncListener != null) {
      if (setupRequest != null && setupRequest.usingCachedGradleModels) {
        syncListener.syncSkipped(myProject);
      }
      else {
        syncListener.syncSucceeded(myProject);
      }
    }
    if (setupRequest != null) {
      myProjectSetup.notifySyncFinished(setupRequest);
    }
  }
}
