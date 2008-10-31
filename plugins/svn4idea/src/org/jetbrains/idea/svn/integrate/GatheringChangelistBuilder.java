package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.update.UpdatedFilesReverseSide;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GatheringChangelistBuilder implements ChangelistBuilder {
  private final Set<VirtualFile> myCheckSet;
  private final List<Change> myChanges;
  private final UpdatedFilesReverseSide myFiles;
  private final VirtualFile myMergeRoot;
  private final SvnVcs myVcs;

  public GatheringChangelistBuilder(final Project project, final UpdatedFilesReverseSide files, final VirtualFile mergeRoot) {
    myVcs = SvnVcs.getInstance(project);
    myFiles = files;
    myMergeRoot = mergeRoot;
    myChanges = new ArrayList<Change>();
    myCheckSet = new HashSet<VirtualFile>();
  }

  public void processChange(final Change change) {
    addChange(change);
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList) {
    addChange(change);
  }

  public void processChangeInList(final Change change, final String changeListName) {
    addChange(change);
  }

  private void addChange(final Change change) {
    final FilePath path = ChangesUtil.getFilePath(change);
    final VirtualFile vf = path.getVirtualFile();
    if ((vf != null) && (mergeinfoChanged(path.getIOFile()) || myFiles.containsFile(vf)) && (! myCheckSet.contains(vf))) {
      myCheckSet.add(vf);
      myChanges.add(change);
    }
  }

  private boolean mergeinfoChanged(final File file) {
    final SVNWCClient client = myVcs.createWCClient();
    try {
      final SVNPropertyData current = client.doGetProperty(file, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
      final SVNPropertyData base = client.doGetProperty(file, "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.BASE);
      if (current != null) {
        if (base == null) {
          return true;
        } else {
          final SVNPropertyValue currentValue = current.getValue();
          final SVNPropertyValue baseValue = base.getValue();
          return ! Comparing.equal(currentValue, baseValue);
        }
      }
    }
    catch (SVNException e) {
      //
    }
    return false;
  }

  public void processUnversionedFile(final VirtualFile file) {

  }

  public void processLocallyDeletedFile(final FilePath file) {

  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {

  }

  public void processIgnoredFile(final VirtualFile file) {

  }

  public void processLockedFolder(final VirtualFile file) {
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {

  }

  public boolean isUpdatingUnversionedFiles() {
    return false;
  }

  public List<Change> getChanges() {
    return myChanges;
  }
}
