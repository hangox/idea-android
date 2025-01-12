/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.data;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.openPreparedProject;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.prepareGradleProject;
import static com.intellij.openapi.application.ActionsKt.runWriteAction;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;

import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class IdeaSyncCachesTest extends AndroidGradleTestCase {
  private IdeaSyncCachesInvalidator myInvalidator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInvalidator = new IdeaSyncCachesInvalidator();
  }

  public void testCacheIsInvalidated() {
    prepareGradleProject(this, TestProjectPaths.SIMPLE_APPLICATION, "project");
    openPreparedProject(this, "project", project -> {
      assertEquals(ProjectSystemSyncManager.SyncResult.SUCCESS,
                   ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult());
      return null;
    });
    openPreparedProject(this, "project", project -> {
      assertEquals(ProjectSystemSyncManager.SyncResult.SKIPPED,
                   ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult());
      myInvalidator.invalidateCaches();
      return null;
    });
    openPreparedProject(this, "project", project -> {
      assertEquals(ProjectSystemSyncManager.SyncResult.SUCCESS,
                   ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult());
      return null;
    });
  }

  public void testMissingJarTriggersSync() {
    prepareGradleProject(this, TestProjectPaths.SIMPLE_APPLICATION, "project");
    openPreparedProject(this, "project", project -> {
      assertEquals(ProjectSystemSyncManager.SyncResult.SUCCESS,
                   ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult());
      return null;
    });
    List<VirtualFile> lifecycleLiveDataLibraryPaths = openPreparedProject(this, "project", project -> {
      assertEquals(ProjectSystemSyncManager.SyncResult.SKIPPED,
                   ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult());
      return
        ContainerUtil
          .map(
            Arrays.stream(LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries())
              .filter(it -> it.getName().startsWith("Gradle: android.arch.lifecycle:livedata:"))
              .findAny()
              .get()
              .getFiles(OrderRootType.CLASSES),
            it -> {
              VirtualFile file = VfsUtilCore.getVirtualFileForJar(it);
              if (file == null) file = it;
              return file;
            });
    });
    deleteLibraryFilesFromGradleCache(lifecycleLiveDataLibraryPaths);
    openPreparedProject(this, "project", project -> {
      assertEquals(ProjectSystemSyncManager.SyncResult.SUCCESS,
                   ProjectSystemService.getInstance(project).getProjectSystem().getSyncManager().getLastSyncResult());
      return null;
    });
  }

  private void deleteLibraryFilesFromGradleCache(List<VirtualFile> lifecycleLiveDataLibraryPaths) {
    assertFalse(lifecycleLiveDataLibraryPaths.isEmpty());
    // Delete all CLASSES files from the Gradle cache. When a library expires in the Gradle cache all files are deleted.
    runWriteAction(() ->{
      lifecycleLiveDataLibraryPaths.forEach(file -> {
        try {
          file.delete(this);
        }
        catch (IOException e) {
          fail(e.getMessage());
        }
      });
      return null;
    });
  }

  public void testLibrariesFolderIsDeleted() throws Exception {
    loadSimpleApplication();

    // Create .idea/libraries folder under project folder.
    File ideaFolderPath = new File(getBaseDirPath(getProject()), DIRECTORY_STORE_FOLDER);
    File librariesFolderPath = new File(ideaFolderPath, "libraries");
    assertTrue(librariesFolderPath.mkdirs());

    // Verify that libraries folder exists.
    assertExists(librariesFolderPath);

    // Verify that after invalidating cache, libraries folder is deleted.
    myInvalidator.invalidateCaches();
    assertDoesntExist(librariesFolderPath);
  }
}
