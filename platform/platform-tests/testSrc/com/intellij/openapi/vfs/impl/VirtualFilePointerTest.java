// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * @author dsl
 */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class VirtualFilePointerTest extends BareTestFixtureTestCase {
  private static final Logger LOG = Logger.getInstance(VirtualFilePointerTest.class);

  @Rule public TempDirectory tempDir = new TempDirectory();

  private final Disposable disposable = Disposer.newDisposable();
  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private Collection<VirtualFilePointer> pointersBefore;
  private int numberOfListenersBefore;

  @Before
  public void setUp() {
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    pointersBefore = myVirtualFilePointerManager.dumpPointers();
    numberOfListenersBefore = myVirtualFilePointerManager.numberOfListeners();
  }

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
    Collection<VirtualFilePointer> pointersAfter = myVirtualFilePointerManager.dumpPointers();
    int nListeners = myVirtualFilePointerManager.numberOfListeners();
    myVirtualFilePointerManager = null;
    assertEquals(numberOfListenersBefore, nListeners);
    assertEquals(pointersBefore, pointersAfter);
  }

  private VirtualFile getVirtualTempRoot() {
    return getVirtualFile(tempDir.getRoot());
  }

  private static VirtualFile getVirtualFile(File file) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  private VirtualFilePointer createPointerByFile(File file, VirtualFilePointerListener fileListener) {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(file.getPath()));
    VirtualFile vFile = getVirtualFile(file);
    return vFile == null
           ? myVirtualFilePointerManager.create(url, disposable, fileListener)
           : myVirtualFilePointerManager.create(vFile, disposable, fileListener);
  }

  private static void verifyPointersInCorrectState(VirtualFilePointer[] pointers) {
    for (VirtualFilePointer pointer : pointers) {
      VirtualFile file = pointer.getFile();
      assertTrue(file == null || file.isValid());
    }
  }

  private static class LoggingListener implements VirtualFilePointerListener {
    final List<String> log = new ArrayList<>();

    @Override
    public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
      verifyPointersInCorrectState(pointers);
      log.add(buildMessage("before", pointers));
    }

    @Override
    public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
      verifyPointersInCorrectState(pointers);
      log.add(buildMessage("after", pointers));
    }

    private static String buildMessage(String startMsg, VirtualFilePointer[] pointers) {
      StringBuilder buffer = new StringBuilder();
      buffer.append(startMsg).append(':');
      for (int i = 0; i < pointers.length; i++) {
        if (i > 0) buffer.append(':');
        buffer.append(pointers[i].isValid());
      }
      return buffer.toString();
    }
  }

  @Test
  public void testDelete() throws IOException {
    File fileToDelete = tempDir.newFile("toDelete.txt");
    LoggingListener fileToDeleteListener = new LoggingListener();
    VirtualFilePointer fileToDeletePointer = createPointerByFile(fileToDelete, fileToDeleteListener);
    assertTrue(fileToDeletePointer.isValid());
    VfsTestUtil.deleteFile(getVirtualFile(fileToDelete));
    assertFalse(fileToDeletePointer.isValid());
    assertEquals("[before:true, after:false]", fileToDeleteListener.log.toString());
  }

  @Test
  public void testCreate() throws IOException {
    File fileToCreate = new File(tempDir.getRoot(), "toCreate.txt");
    LoggingListener fileToCreateListener = new LoggingListener();
    VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    assertTrue(fileToCreate.createNewFile());
    getVirtualTempRoot().refresh(false, true);
    assertTrue(fileToCreatePointer.isValid());
    assertEquals("[before:false, after:true]", fileToCreateListener.log.toString());
    String expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(fileToCreate.getPath()));
    assertEquals(expectedUrl.toUpperCase(Locale.US), fileToCreatePointer.getUrl().toUpperCase(Locale.US));
  }

  @Test
  public void testPathNormalization() throws IOException {
    checkFileName("///", "");
  }

  @Test
  public void testPathNormalization2() throws IOException {
    checkFileName("\\\\", "/");
  }

  @Test
  public void testPathNormalization3() throws IOException {
    checkFileName("//", "/////");
  }

  private void checkFileName(String prefix, String suffix) throws IOException {
    VirtualFile temp = getVirtualTempRoot();
    String name = "toCreate.txt";
    VirtualFilePointer fileToCreatePointer = createPointerByFile(new File(tempDir.getRoot(), prefix + name + suffix), null);
    assertFalse(fileToCreatePointer.isValid());
    assertNull(fileToCreatePointer.getFile());

    VirtualFile child = WriteAction.computeAndWait(() -> temp.createChildData(null, name));
    assertTrue(fileToCreatePointer.isValid());
    assertEquals(child, fileToCreatePointer.getFile());

    VfsTestUtil.deleteFile(child);
    assertFalse(fileToCreatePointer.isValid());
    assertNull(fileToCreatePointer.getFile());
  }

  @Test
  public void testMovePointedFile() throws IOException {
    File moveTarget = tempDir.newFolder("moveTarget");
    File fileToMove = tempDir.newFile("toMove.txt");

    LoggingListener fileToMoveListener = new LoggingListener();
    VirtualFilePointer fileToMovePointer = createPointerByFile(fileToMove, fileToMoveListener);
    assertTrue(fileToMovePointer.isValid());
    doMove(fileToMove, moveTarget);
    assertTrue(fileToMovePointer.isValid());
    assertEquals("[before:true, after:true]", fileToMoveListener.log.toString());
  }

  @Test
  public void testMoveFileUnderExistingPointer() throws IOException {
    File moveTarget = tempDir.newFolder("moveTarget");
    File fileToMove = tempDir.newFile("toMove.txt");

    LoggingListener listener = new LoggingListener();
    VirtualFilePointer fileToMoveTargetPointer = createPointerByFile(new File(moveTarget, fileToMove.getName()), listener);
    assertFalse(fileToMoveTargetPointer.isValid());
    doMove(fileToMove, moveTarget);
    assertTrue(fileToMoveTargetPointer.isValid());
    assertEquals("[before:false, after:true]", listener.log.toString());
  }

  @Test
  public void testMoveSrcDirUnderNewRootShouldGenerateRootsChanged() throws IOException {
    File moveTarget = tempDir.newFolder("moveTarget");
    File dirToMove = tempDir.newFile("dirToMove");

    LoggingListener listener = new LoggingListener();
    VirtualFilePointer dirToMovePointer = createPointerByFile(dirToMove, listener);
    assertTrue(dirToMovePointer.isValid());
    doMove(dirToMove, moveTarget);
    assertTrue(dirToMovePointer.isValid());
    assertEquals("[before:true, after:true]", listener.log.toString());
  }

  @Test
  public void testMovePointedFileUnderAnotherPointer() throws IOException {
    File moveTarget = tempDir.newFolder("moveTarget");
    File fileToMove = tempDir.newFile("toMove.txt");

    LoggingListener listener = new LoggingListener();
    LoggingListener targetListener = new LoggingListener();

    VirtualFilePointer fileToMovePointer = createPointerByFile(fileToMove, listener);
    VirtualFilePointer fileToMoveTargetPointer = createPointerByFile(new File(moveTarget, fileToMove.getName()), targetListener);

    assertFalse(fileToMoveTargetPointer.isValid());
    doMove(fileToMove, moveTarget);
    assertTrue(fileToMovePointer.isValid());
    assertTrue(fileToMoveTargetPointer.isValid());
    assertEquals("[before:true, after:true]", listener.log.toString());
    assertEquals("[before:false, after:true]", targetListener.log.toString());
  }

  private void doMove(File fileToMove, File moveTarget) throws IOException {
    VirtualFile virtualFile = getVirtualFile(fileToMove);
    assertTrue(virtualFile.isValid());
    VirtualFile target = getVirtualFile(moveTarget);
    assertTrue(target.isValid());
    WriteAction.runAndWait(() -> virtualFile.move(this, target));
  }

  @Test
  public void testRenamingPointedFile() throws IOException {
    File file = tempDir.newFile("f1");
    LoggingListener listener = new LoggingListener();
    VirtualFilePointer pointer = createPointerByFile(file, listener);
    assertTrue(pointer.isValid());
    WriteAction.runAndWait(() -> getVirtualFile(file).rename(this, "f2"));
    assertTrue(pointer.isValid());
    assertEquals("[]", listener.log.toString());
  }

  @Test
  public void testRenamingFileUnderTheExistingPointer() throws IOException {
    File file = tempDir.newFile("f1");
    LoggingListener listener = new LoggingListener();
    VirtualFilePointer pointer = createPointerByFile(new File(file.getParent(), "f2"), listener);
    assertFalse(pointer.isValid());
    WriteAction.runAndWait(() -> getVirtualFile(file).rename(this, "f2"));
    assertTrue(pointer.isValid());
    assertEquals("[before:false, after:true]", listener.log.toString());
  }

  @Test
  public void testTwoPointersBecomeOneAfterFileRenamedUnderTheOtherName() throws IOException {
    File f1 = tempDir.newFile("f1");
    VirtualFile vFile1 = getVirtualFile(f1);
    String url1 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(f1.getPath()));
    LoggingListener listener1 = new LoggingListener();
    VirtualFilePointer pointer1 = myVirtualFilePointerManager.create(url1, disposable, listener1);
    assertTrue(pointer1.isValid());

    String url2 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(f1.getParent()) + "/f2");
    LoggingListener listener2 = new LoggingListener();
    VirtualFilePointer pointer2 = myVirtualFilePointerManager.create(url2, disposable, listener2);
    assertFalse(pointer2.isValid());

    WriteAction.runAndWait(() -> vFile1.rename(this, "f2"));

    assertTrue(pointer1.isValid());
    assertTrue(pointer2.isValid());
    assertEquals("[]", listener1.log.toString());
    assertEquals("[before:false, after:true]", listener2.log.toString());
  }

  @Test
  public void testCreate1() throws IOException {
    File fileToCreate = new File(tempDir.getRoot(), "toCreate1.txt");
    LoggingListener fileToCreateListener = new LoggingListener();
    VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    assertTrue(fileToCreate.createNewFile());
    getVirtualTempRoot().refresh(false, true);
    assertTrue(fileToCreatePointer.isValid());
    assertEquals("[before:false, after:true]", fileToCreateListener.log.toString());
    String expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(fileToCreate.getPath()));
    assertThat(fileToCreatePointer.getUrl()).isEqualToIgnoringCase(expectedUrl);
  }

  @Test
  public void testMultipleNotifications() throws IOException {
    File file1 = new File(tempDir.getRoot(), "f1");
    File file2 = new File(tempDir.getRoot(), "f2");
    LoggingListener listener = new LoggingListener();
    VirtualFilePointer pointer1 = createPointerByFile(file1, listener);
    VirtualFilePointer pointer2 = createPointerByFile(file2, listener);
    assertFalse(pointer1.isValid());
    assertFalse(pointer2.isValid());
    assertTrue(file1.createNewFile());
    assertTrue(file2.createNewFile());
    getVirtualTempRoot().refresh(false, true);
    assertEquals("[before:false:false, after:true:true]", listener.log.toString());
  }

  @Test
  public void testJars() throws IOException {
    VirtualFile vTemp = getVirtualTempRoot();
    File jarParent = new File(tempDir.getRoot(), "jarParent");
    File jar = new File(jarParent, "x.jar");
    File originalJar = new File(PathManagerEx.getTestDataPath() + "/psi/generics22/collect-2.2.jar");
    FileUtil.copy(originalJar, jar);
    getVirtualFile(jar); // Make sure we receive events when jar changes

    VirtualFilePointerListener listener = new LoggingListener();
    VirtualFilePointer jarParentPointer = createPointerByFile(jarParent, listener);
    String jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(jar.getPath()) + JarFileSystem.JAR_SEPARATOR);
    VirtualFilePointer jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    VirtualFilePointer[] pointersToWatch = {jarParentPointer, jarPointer};
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());

    assertTrue(jar.delete());
    assertTrue(jarParent.delete());
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
    assertThat(vTemp.getChildren()).isEmpty();

    assertTrue(jarParent.mkdir());
    FileUtil.copy(originalJar, jar);
    assertTrue(jar.setLastModified(System.currentTimeMillis()));
    assertTrue(jar.exists());
    assertTrue(jarParent.exists());
    assertTrue(jarParent.getParentFile().exists());
    File[] files = jarParent.listFiles();
    assertThat(files).hasSize(1);
    assertEquals(jar.getName(), files[0].getName());
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());

    assertTrue(jar.delete());
    assertTrue(jarParent.delete());
    vTemp.refresh(false, true);
    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
  }

  @Test
  public void testJars2() throws IOException {
    VirtualFile vTemp = getVirtualTempRoot();
    File jarParent = new File(tempDir.getRoot(), "jarParent");
    File jar = new File(jarParent, "x.jar");
    File originalJar = new File(PathManagerEx.getTestDataPath() + "/psi/generics22/collect-2.2.jar");
    FileUtil.copy(originalJar, jar);
    getVirtualFile(jar); // Make sure we receive events when jar changes

    VirtualFilePointerListener listener = new LoggingListener();
    String jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(jar.getPath()) + JarFileSystem.JAR_SEPARATOR);
    VirtualFilePointer jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    VirtualFilePointer[] pointersToWatch = {jarPointer};
    assertTrue(jar.delete());

    TestTimeOut t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    int i;
    for (i = 0; !t.timedOut() && i < 30; i++) {
      vTemp.refresh(false, true);
      verifyPointersInCorrectState(pointersToWatch);
      assertFalse(jarPointer.isValid());
      assertTrue(jarParent.exists());
      LOG.debug("before structureModificationCount=" + ManagingFS.getInstance().getStructureModificationCount());
      VirtualFile vJar = getVirtualFile(jar);
      assertNull(vJar);

      LOG.debug("copying");
      FileUtil.copy(originalJar, jar);
      vTemp.refresh(false, true);
      verifyPointersInCorrectState(pointersToWatch);
      vJar = getVirtualFile(jar);
      assertNotNull(vJar);
      LOG.debug("after structureModificationCount=" + ManagingFS.getInstance().getStructureModificationCount());
      assertTrue(jarPointer.isValid());

      assertTrue(jar.delete());
      vTemp.refresh(false, true);
      verifyPointersInCorrectState(pointersToWatch);
      assertFalse(jarPointer.isValid());
    }
    LOG.debug("i = " + i);
  }

  @Test
  public void testFilePointerUpdate() throws IOException {
    VirtualFile vTemp = getVirtualTempRoot();
    File file = new File(tempDir.getRoot(), "f1");
    VirtualFilePointer pointer = createPointerByFile(file, null);
    assertFalse(pointer.isValid());

    assertTrue(file.createNewFile());
    vTemp.refresh(false, true);
    assertTrue(pointer.isValid());

    assertTrue(file.delete());
    vTemp.refresh(false, true);
    assertFalse(pointer.isValid());
  }

  @Test
  public void testDoubleDispose() throws IOException {
    File file = tempDir.newFile("f1");
    VirtualFile vFile = getVirtualFile(file);
    Disposable disposable = Disposer.newDisposable();
    VirtualFilePointer pointer = myVirtualFilePointerManager.create(vFile, disposable, null);
    assertTrue(pointer.isValid());
    Disposer.dispose(disposable);
    assertFalse(pointer.isValid());
  }

  @Test
  public void testThreadsPerformance() throws Exception {
    VirtualFile vTemp = getVirtualTempRoot();
    File ioSandPtr = tempDir.newFile("parent2/f2");
    File ioPtr = tempDir.newFile("parent1/f1");

    vTemp.refresh(false, true);
    VirtualFilePointer pointer = createPointerByFile(ioPtr, null);
    assertTrue(pointer.isValid());
    assertNotNull(pointer.getFile());
    assertTrue(pointer.getFile().isValid());
    Collection<Job<?>> reads = ContainerUtil.newConcurrentSet();
    VirtualFileListener listener = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        stressRead(pointer, reads);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        stressRead(pointer, reads);
      }
    };
    Disposable disposable = Disposer.newDisposable();
    VirtualFileManager.getInstance().addVirtualFileListener(listener, disposable);
    try {
      int N = Timings.adjustAccordingToMySpeed(1000, false);
      LOG.debug("N = " + N);
      for (int i = 0; i < N; i++) {
        assertNotNull(pointer.getFile());
        FileUtil.delete(ioPtr.getParentFile());
        vTemp.refresh(false, true);

        // ptr is now null, cached as map
        VirtualFile v = PlatformTestUtil.notNull(LocalFileSystem.getInstance().findFileByIoFile(ioSandPtr));
        WriteAction.runAndWait(() -> {
          v.delete(this); //inc FS modCount
          VirtualFile file = PlatformTestUtil.notNull(LocalFileSystem.getInstance().findFileByIoFile(ioSandPtr.getParentFile()));
          file.createChildData(this, ioSandPtr.getName());
        });

        // ptr is still null
        assertTrue(ioPtr.getParentFile().mkdirs());
        assertTrue(ioPtr.createNewFile());
        stressRead(pointer, reads);
        vTemp.refresh(false, true);
      }
    }
    finally {
      Disposer.dispose(disposable); // unregister listener early
      for (Job<?> read : reads) {
        while (!read.isDone()) {
          read.waitForCompletion(1000);
        }
      }
    }
  }

  private static void stressRead(VirtualFilePointer pointer, Collection<? super Job<?>> reads) {
    for (int i = 0; i < 10; i++) {
      AtomicReference<Job<?>> reference = new AtomicReference<>();
      reference.set(JobLauncher.getInstance().submitToJobThread(() -> ApplicationManager.getApplication().runReadAction(() -> {
        VirtualFile file = pointer.getFile();
        if (file != null && !file.isValid()) {
          throw new IncorrectOperationException("I've caught it. I am that good");
        }
      }), future -> {
        try {
          future.get();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
        finally {
          reads.remove(reference.get());
        }
      }));
      reads.add(reference.get());
    }
  }

  @Test
  public void testTwoPointersMergingIntoOne() throws IOException {
    VirtualFile root = getVirtualTempRoot();
    VirtualFile dir1 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir1"));
    VirtualFile dir2 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir2"));

    VirtualFilePointer p1 = myVirtualFilePointerManager.create(dir1, disposable, null);
    VirtualFilePointer p2 = myVirtualFilePointerManager.create(dir2, disposable, null);
    assertTrue(p1.isValid());
    assertEquals(dir1, p1.getFile());
    assertTrue(p2.isValid());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> dir1.delete(this));
    assertNull(p1.getFile());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> dir2.rename(this, "dir1"));
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> dir2.rename(this, "dir2"));
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());

    WriteAction.runAndWait(() -> root.createChildDirectory(this, "dir1"));
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());
  }

  @Test
  public void testDotDot() throws IOException {
    VirtualFile root = getVirtualTempRoot();
    VirtualFile dir1 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir1"));
    VirtualFile file = WriteAction.computeAndWait(() -> dir1.createChildData(this, "x.txt"));
    VirtualFile dir2 = WriteAction.computeAndWait(() -> root.createChildDirectory(this, "dir2"));
    VirtualFilePointer pointer = myVirtualFilePointerManager.create(dir2.getUrl() + "/../" + dir1.getName() + "/" + file.getName(), disposable, null);
    assertEquals(file, pointer.getFile());
  }

  @Test
  public void testAlienVirtualFileSystemPointerRemovedFromUrlToIdentityCacheOnDispose() {
    VirtualFile mockVirtualFile = new MockVirtualFile("test_name", "test_text");
    Disposable disposable = Disposer.newDisposable();
    VirtualFilePointer pointer = myVirtualFilePointerManager.create(mockVirtualFile, disposable, null);
    assertThat(pointer).isInstanceOf(IdentityVirtualFilePointer.class);
    assertTrue(pointer.isValid());

    VirtualFile virtualFileWithSameUrl = new MockVirtualFile("test_name", "test_text");
    VirtualFilePointer updatedPointer = myVirtualFilePointerManager.create(virtualFileWithSameUrl, disposable, null);
    assertThat(updatedPointer).isInstanceOf(IdentityVirtualFilePointer.class);
    assertTrue(pointer.isValid());
    assertEquals(1, myVirtualFilePointerManager.numberOfCachedUrlToIdentity());

    Disposer.dispose(disposable);
    assertEquals(0, myVirtualFilePointerManager.numberOfCachedUrlToIdentity());
  }

  @Test
  public void testListenerWorksInTempFileSystem() throws IOException {
    VirtualFile tmpRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tmpRoot);
    assertThat(tmpRoot.getFileSystem()).isInstanceOf(TempFileSystem.class);
    VirtualFile testDir = WriteAction.computeAndWait(() -> tmpRoot.createChildDirectory(this, getTestName(true)));
    Disposer.register(disposable, () -> VfsTestUtil.deleteFile(testDir));

    VirtualFile child = WriteAction.computeAndWait(() -> testDir.createChildData(this, "a.txt"));
    LoggingListener listener = new LoggingListener();
    VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(child, disposable, listener);

    WriteAction.runAndWait(() -> child.delete(this));
    assertEquals("[before:true, after:false]", listener.log.toString());
    assertNull(pointer.getFile());

    listener.log.clear();
    VirtualFile newChild = WriteAction.computeAndWait(() -> testDir.createChildData(this, "a.txt"));
    assertEquals("[before:false, after:true]", listener.log.toString());
    assertEquals(newChild, pointer.getFile());
  }

  @Test
  public void testStressConcurrentAccess() throws Exception {
    VirtualFilePointer fileToCreatePointer = createPointerByFile(tempDir.getRoot(), null);
    VirtualFilePointerListener listener = new VirtualFilePointerListener() { };
    TestTimeOut t = TestTimeOut.setTimeout(15, TimeUnit.SECONDS);
    AtomicBoolean run = new AtomicBoolean(false);
    AtomicReference<Throwable> exception = new AtomicReference<>(null);
    int i;
    for (i = 0; !t.timedOut(); i++) {
      Disposable disposable = Disposer.newDisposable();
      // supply listener to separate pointers under one root so that it will be removed on dispose
      VirtualFilePointerImpl bb =
        (VirtualFilePointerImpl)myVirtualFilePointerManager.create(fileToCreatePointer.getUrl() + "/bb", disposable, listener);

      if (i % 1000 == 0) LOG.info("i = " + i);

      int nThreads = Runtime.getRuntime().availableProcessors();
      CountDownLatch ready = new CountDownLatch(nThreads);
      Runnable read = () -> {
        try {
          ready.countDown();
          while (run.get()) {
            bb.myNode.myLastUpdated = -15;
            bb.getUrl();
          }
        }
        catch (Throwable e) {
          exception.set(e);
        }
      };

      run.set(true);
      List<Thread> threads = IntStream.range(0, nThreads).mapToObj(n -> new Thread(read, "reader " + n)).collect(Collectors.toList());
      threads.forEach(Thread::start);
      ready.await();

      myVirtualFilePointerManager.create(fileToCreatePointer.getUrl() + "/b/c", disposable, listener);

      run.set(false);
      ConcurrencyUtil.joinAll(threads);
      ExceptionUtil.rethrowAll(exception.get());
      Disposer.dispose(disposable);
    }
    LOG.debug("i = " + i);
  }

  @Test
  public void testGetChildrenMustIncreaseModificationCountIfFoundNewFile() throws IOException {
    VirtualFile vTemp = getVirtualTempRoot();
    File file = new File(tempDir.getRoot(), "x.txt");
    VirtualFilePointer pointer = createPointerByFile(file, null);

    TestTimeOut t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    int i;
    for (i = 0; !t.timedOut() && i < 30; i++) {
      LOG.info("i = " + i);
      assertTrue(file.createNewFile());
      vTemp.refresh(false, true);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
        for (int k = 0; k < 100; k++) {
          vTemp.getChildren();
        }
      }));
      TimeoutUtil.sleep(100);
      VirtualFile vFile = PlatformTestUtil.notNull(getVirtualFile(file));
      assertTrue(vFile.isValid());
      assertTrue(pointer.isValid());
      assertTrue(file.delete());
      vTemp.refresh(false, true);
      assertFalse(pointer.isValid());
      while (!future.isDone()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    LOG.debug("i = " + i);
  }

  @Test
  public void testSeveralDirectoriesWithCommonPrefix() {
    VirtualFile vDir = getVirtualTempRoot();
    assertNotNull(vDir);
    vDir.getChildren();
    vDir.refresh(false, true);

    LoggingListener listener = new LoggingListener();
    myVirtualFilePointerManager.create(vDir.getUrl() + "/d1/subdir", disposable, listener);
    myVirtualFilePointerManager.create(vDir.getUrl() + "/d2/subdir", disposable, listener);

    File dir = new File(vDir.getPath()+"/d1");
    FileUtil.createDirectory(dir);
    getVirtualFile(dir).getChildren();
    assertEquals("[]", listener.log.toString());
    listener.log.clear();

    File subDir = new File(dir, "subdir");
    FileUtil.createDirectory(subDir);
    getVirtualFile(subDir);
    assertEquals("[before:false, after:true]", listener.log.toString());
  }

  @Test
  public void testDirectoryPointersWork() throws IOException {
    VirtualFile vDir = getVirtualTempRoot();
    VirtualFile deep = WriteAction.computeAndWait(() -> vDir.createChildDirectory(this, "deep"));

    LoggingListener listener = new LoggingListener();
    Disposable disposable = Disposer.newDisposable();
    myVirtualFilePointerManager.createDirectoryPointer(vDir.getUrl(), false, disposable, listener);

    WriteAction.runAndWait(() -> vDir.createChildData(this, "1"));
    assertEquals("[before:true, after:true]", listener.log.toString());
    Disposer.dispose(disposable);
    listener = new LoggingListener();
    myVirtualFilePointerManager.createDirectoryPointer(vDir.getUrl(), true, this.disposable, listener);

    WriteAction.runAndWait(() -> deep.createChildData(this, "1"));
    assertEquals("[before:true, after:true]", listener.log.toString());
  }

  @Test
  public void testNotQuiteCanonicalPath() throws IOException {
    VirtualFile vDir = getVirtualTempRoot();
    VirtualFile deep = WriteAction.computeAndWait(() -> vDir.createChildDirectory(this, "deep"));
    VirtualFile file = WriteAction.computeAndWait(() -> deep.createChildData(this, "x..txt"));
    VirtualFilePointer ptr = myVirtualFilePointerManager.create(file, disposable, null);
    assertTrue(ptr.isValid());
    assertTrue(ptr.getUrl(), ptr.getUrl().contains(".."));

    WriteAction.runAndWait(() -> vDir.createChildData(this, "existing.txt"));
    VirtualFilePointer ptr2 = myVirtualFilePointerManager.create(deep.getUrl() + "/../existing.txt", disposable, null);
    assertTrue(ptr2.isValid());
    assertFalse(ptr2.getUrl(), ptr2.getUrl().contains(".."));

    VirtualFilePointer ptr3 = myVirtualFilePointerManager.create(deep.getUrl() + "/../madeUp.txt", disposable, null);
    assertFalse(ptr3.isValid());
    assertTrue(ptr3.getUrl(), ptr3.getUrl().contains(".."));
  }

  @Test
  public void testVirtualPointerForSubdirMustNotFireWhenSuperDirectoryCreated() throws IOException {
    VirtualFile vDir = getVirtualTempRoot();
    assertNotNull(vDir);
    vDir.getChildren();
    vDir.refresh(false, true);

    LoggingListener listener = new LoggingListener();
    VirtualFilePointer subPtr = myVirtualFilePointerManager.create(vDir.getUrl() + "/cmake/subdir", disposable, listener);

    VirtualFile cmake = WriteAction.computeAndWait(() -> vDir.createChildDirectory(vDir, "cmake"));
    assertEquals("[]", listener.log.toString());
    listener.log.clear();

    WriteAction.computeAndWait(() -> cmake.createChildDirectory(cmake, "subdir"));
    assertEquals("[before:false, after:true]", listener.log.toString());
    assertTrue(subPtr.isValid());

    listener.log.clear();
    FileUtil.rename(new File(cmake.getPath()), "newCmake");
    vDir.refresh(false, true);
    assertEquals("[before:true, after:false]", listener.log.toString());
    assertFalse(subPtr.isValid());
  }

  @Test
  public void testCreatePointerWithCrazyUrlContainingSlashDotMustNotLeadToException() {
    File dirToCreate = new File(tempDir.getRoot(), "ToCreate");

    VirtualFilePointer dirPointer = createPointerByFile(dirToCreate, null);
    VirtualFilePointer dirDotPointer = createPointerByFile(new File(dirToCreate, "."), null);
    assertSame(dirDotPointer, dirPointer);
  }
}