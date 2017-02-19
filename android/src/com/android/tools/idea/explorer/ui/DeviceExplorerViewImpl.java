/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.ui;

import com.android.tools.idea.explorer.*;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer;
import com.android.tools.idea.explorer.fs.DeviceFileSystemService;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.Tree;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

public class DeviceExplorerViewImpl implements DeviceExplorerView {
  @NotNull private final List<DeviceExplorerViewListener> myListeners = new ArrayList<>();
  @NotNull private final Project myProject;
  @NotNull private final ToolWindow myToolWindow;
  @NotNull private final DeviceFileSystemRenderer myDeviceRenderer;
  @Nullable private JBLoadingPanel myLoadingPanel;
  @Nullable private DeviceExplorerPanel myPanel;
  @Nullable private ComponentPopupMenu myTreePopupMenu;
  private int myTreeLoadingCount;

  public DeviceExplorerViewImpl(@NotNull Project project,
                                @NotNull ToolWindow toolWindow,
                                @NotNull DeviceFileSystemRenderer deviceRenderer,
                                @NotNull DeviceExplorerModel model) {
    myProject = project;
    myToolWindow = toolWindow;
    model.addListener(new ModelListener());
    myDeviceRenderer = deviceRenderer;
  }

  @TestOnly
  @Nullable
  public JComboBox<DeviceFileSystem> getDeviceCombo() {
    return myPanel != null ? myPanel.getDeviceCombo() : null;
  }

  @TestOnly
  @Nullable
  public JTree getFileTree() {
    return myPanel != null ? myPanel.getTree() : null;
  }

  @TestOnly
  @Nullable
  public ActionGroup getFileTreeActionGroup() {
    return myTreePopupMenu == null ? null : myTreePopupMenu.getActionGroup();
  }

  @TestOnly
  @Nullable
  public JBLoadingPanel getLoadingPanel() {
    return myLoadingPanel;
  }

  @TestOnly
  @Nullable
  public DeviceExplorerPanel getDeviceExplorerPanel() {
    return myPanel;
  }

  @Override
  public void addListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void setup() {
    myToolWindow.setIcon(AndroidIcons.AndroidToolWindow);
    myToolWindow.setAvailable(true, null);
    myToolWindow.setToHideOnEmptyContent(true);
    myToolWindow.setTitle(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID);

    setupPanel();
  }

  @Override
  public void reportErrorRelatedToService(@NotNull DeviceFileSystemService service, @NotNull String message, @NotNull Throwable t) {
    assert myLoadingPanel != null;

    reportError(message, t);

    //TODO: Show dedicated error panel
    myLoadingPanel.setLoadingText(String.format("Error initializing Android Debug Bridge: %s", t.getMessage()));
    myLoadingPanel.startLoading();
  }

  @Override
  public void reportErrorRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message, @NotNull Throwable t) {
    reportError(message, t);
  }

  @Override
  public void reportErrorRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message, @NotNull Throwable t) {
    reportError(message, t);
  }

  @Override
  public void reportMessageRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message) {
    reportMessage(message);
  }

  private static void reportMessage(@NotNull String message) {
    Notification notification = new Notification(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 message,
                                                 NotificationType.INFORMATION);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private static void reportError(@NotNull String message, @NotNull Throwable t) {
    if (t instanceof CancellationException) {
      return;
    }

    if (t.getMessage() != null) {
      message += ": " + t.getMessage();
    }

    Notification notification = new Notification(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 message,
                                                 NotificationType.WARNING);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private void setupPanel() {
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myProject);
    final ContentManager contentManager = myToolWindow.getContentManager();
    Content c = contentManager.getFactory().createContent(myLoadingPanel, "", true);
    contentManager.addContent(c);

    myPanel = new DeviceExplorerPanel();
    myPanel.getComponent().setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myLoadingPanel.add(myPanel.getComponent(), BorderLayout.CENTER);

    //noinspection GtkPreferredJComboBoxRenderer
    myPanel.getDeviceCombo().setRenderer(myDeviceRenderer.getDeviceNameListRenderer());

    myPanel.getDeviceCombo().addActionListener(actionEvent -> {
      Object sel = myPanel.getDeviceCombo().getSelectedItem();
      DeviceFileSystem device = (sel instanceof DeviceFileSystem) ? (DeviceFileSystem)sel : null;
      myListeners.forEach(x -> x.deviceSelected(device));
    });

    Tree tree = myPanel.getTree();
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {

        DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(event.getPath().getLastPathComponent());
        if (node != null) {
          expandTreeNode(node);
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
      }
    });
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Double click on a file should result in a file open
        if (e.getClickCount() == 2) {
          int selRow = tree.getRowForLocation(e.getX(), e.getY());
          TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
          if (selRow != -1 && selPath != null) {
            openSelectedNodes(Collections.singletonList(selPath));
          }
        }
      }
    });
    tree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          TreePath[] paths = tree.getSelectionPaths();
          if (paths != null) {
            openSelectedNodes(Arrays.asList(paths));
          }
        }
      }
    });

    createTreePopupMenu();
    myLoadingPanel.setLoadingText("Initializing ADB");
    myLoadingPanel.startLoading();
  }

  private void createTreePopupMenu() {
    assert myPanel != null;
    myTreePopupMenu = new ComponentPopupMenu(myPanel.getTree());
    ComponentPopupMenu fileMenu = myTreePopupMenu.addPopup("New");
    fileMenu.addItem(new NewFileMenuItem());
    fileMenu.addItem(new NewDirectoryMenuItem());
    myTreePopupMenu.addSeparator();
    myTreePopupMenu.addItem(new OpenMenuItem());
    myTreePopupMenu.addItem(new SaveAsMenuItem());
    myTreePopupMenu.addItem(new UploadFilesMenuItem());
    myTreePopupMenu.addItem(new DeleteNodesMenuItem());
    myTreePopupMenu.addSeparator();
    myTreePopupMenu.addItem(new CopyPathMenuItem());
    myTreePopupMenu.install();
  }

  private void openSelectedNodes(@NotNull List<TreePath> paths) {
    List<DeviceFileEntryNode> nodes =
      paths.stream()
        .map(x -> DeviceFileEntryNode.fromNode(x.getLastPathComponent()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    openNodes(nodes);
  }

  private void copyNodePaths(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.copyNodePathsInvoked(treeNodes));
  }

  private void openNodes(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.openNodesInEditorInvoked(treeNodes));
  }

  private void saveNodeAs(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.saveNodeAsInvoked(treeNode));
  }

  private void newDirectory(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.newDirectoryInvoked(treeNode));
  }

  private void newFile(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.newFileInvoked(treeNode));
  }

  private void deleteNodes(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.deleteNodesInvoked(treeNodes));
  }

  private void uploadFiles(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.uploadFilesInvoked(treeNode));
  }

  @Override
  public void serviceSetupSuccess() {
    assert myLoadingPanel != null;

    myLoadingPanel.stopLoading();
  }

  public void setRootFolder(@Nullable DefaultTreeModel model, DefaultTreeSelectionModel treeSelectionModel) {
    assert myPanel != null;

    Tree tree = myPanel.getTree();
    tree.setModel(model);
    tree.setSelectionModel(treeSelectionModel);

    if (model != null) {
      DeviceFileEntryNode rootNode = DeviceFileEntryNode.fromNode(model.getRoot());
      if (rootNode != null) {
        tree.setRootVisible(false);
        expandTreeNode(rootNode);
      }
    }
  }

  @Override
  public void startTreeBusyIndicator() {
    incrementTreeLoading();
  }

  @Override
  public void stopTreeBusyIndicator() {
    decrementTreeLoading();
  }

  @Override
  public void expandNode(@NotNull DeviceFileEntryNode treeNode) {
    if (myPanel != null) {
      myPanel.getTree().expandPath(new TreePath(treeNode.getPath()));
    }
  }

  private void expandTreeNode(@NotNull DeviceFileEntryNode node) {
    myListeners.forEach(x -> x.treeNodeExpanding(node));
  }

  private void incrementTreeLoading() {
    assert myPanel != null;

    if (myTreeLoadingCount == 0) {
      myPanel.getTree().setPaintBusy(true);
    }
    myTreeLoadingCount++;
  }

  private void decrementTreeLoading() {
    assert myPanel != null;

    myTreeLoadingCount--;
    if (myTreeLoadingCount == 0) {
      myPanel.getTree().setPaintBusy(false);
    }
  }

  private class ModelListener implements DeviceExplorerModelListener {
    @Override
    public void allDevicesRemoved() {
      if (myPanel != null) {
        myPanel.getDeviceCombo().removeAllItems();
      }
    }

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      if (myPanel != null) {
        myPanel.getDeviceCombo().addItem(device);
      }
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      if (myPanel != null) {
        myPanel.getDeviceCombo().removeItem(device);
      }
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      if (myPanel != null) {
        if (myPanel.getDeviceCombo().getSelectedItem() == device) {
          myPanel.getDeviceCombo().repaint();
        }
      }
    }

    @Override
    public void activeDeviceChanged(@Nullable DeviceFileSystem newActiveDevice) {
    }

    @Override
    public void treeModelChanged(@Nullable DefaultTreeModel newTreeModel, @Nullable DefaultTreeSelectionModel newTreeSelectionModel) {
      setRootFolder(newTreeModel, newTreeSelectionModel);
    }
  }

  /**
   * A popup menu item that works for both single and multi-element selections.
   */
  private abstract class TreeMenuItem implements PopupMenuItem {
    @NotNull
    @Override
    public abstract String getText();

    @Nullable
    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public final boolean isEnabled() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        return false;
      }
      return isEnabled(nodes);
    }

    public boolean isEnabled(@NotNull List<DeviceFileEntryNode> nodes) {
      return nodes.stream().anyMatch(this::isEnabled);
    }

    @Override
    public final boolean isVisible() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        return false;
      }
      return isVisible(nodes);
    }

    public boolean isVisible(@NotNull List<DeviceFileEntryNode> nodes) {
      return nodes.stream().anyMatch(this::isVisible);
    }

    @Override
    public final void run() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        return;
      }
      nodes = nodes.stream().filter(this::isEnabled).collect(Collectors.toList());
      if (!nodes.isEmpty()) {
        run(nodes);
      }
    }

    @Nullable
    private List<DeviceFileEntryNode> getSelectedNodes() {
      assert myPanel != null;
      TreePath[] paths = myPanel.getTree().getSelectionPaths();
      if (paths == null) {
        return null;
      }
      List<DeviceFileEntryNode> nodes = Arrays.stream(paths)
        .map(path -> DeviceFileEntryNode.fromNode(path.getLastPathComponent()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      if (nodes.isEmpty()) {
        return null;
      }
      return nodes;
    }

    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    public boolean isEnabled(@NotNull DeviceFileEntryNode node) {
      return isVisible(node);
    }

    public abstract void run(@NotNull List<DeviceFileEntryNode> nodes);
  }

  /**
   * A {@link TreeMenuItem} that is active only for single element selections
   */
  private abstract class SingleSelectionTreeMenuItem extends TreeMenuItem {
    @Override
    public boolean isEnabled(@NotNull List<DeviceFileEntryNode> nodes) {
      return super.isEnabled(nodes) && nodes.size() == 1;
    }

    @Override
    public boolean isVisible(@NotNull List<DeviceFileEntryNode> nodes) {
      return super.isVisible(nodes) && nodes.size() == 1;
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      if (nodes.size() == 1) {
        run(nodes.get(0));
      }
    }

    public abstract void run(@NotNull DeviceFileEntryNode node);
  }

  private class CopyPathMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "Copy Path";
    }

    @Override
    public String getShortcutId() {
      return "CopyPaths"; // Re-use shortcut from existing action
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Copy;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      copyNodePaths(nodes);
    }
  }

  private class OpenMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "Open";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Menu_open;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isFile();
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      openNodes(nodes);
    }
  }

  private class SaveAsMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "Save As...";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Menu_saveall;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isFile();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      saveNodeAs(node);
    }
  }

  private class NewFileMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "File";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.FileTypes.Text;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isDirectory() || node.isSymbolicLinkToDirectory();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      newFile(node);
    }
  }

  private class NewDirectoryMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "Directory";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Nodes.Folder;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isDirectory() || node.isSymbolicLinkToDirectory();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      newDirectory(node);
    }
  }

  private class DeleteNodesMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "Delete...";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Delete;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    @Nullable
    @Override
    public String getShortcutId() {
      return "$Delete"; // Re-use existing shortcut
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      deleteNodes(nodes);
    }
  }

  private class UploadFilesMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText() {
      return "Upload...";
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isDirectory() || node.isSymbolicLinkToDirectory();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      uploadFiles(node);
    }
  }
}
