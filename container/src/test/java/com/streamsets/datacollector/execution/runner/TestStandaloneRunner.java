/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.datacollector.execution.runner;

import com.streamsets.datacollector.execution.Manager;
import com.streamsets.datacollector.execution.PipelineState;
import com.streamsets.datacollector.execution.PipelineStateStore;
import com.streamsets.datacollector.execution.PipelineStatus;
import com.streamsets.datacollector.execution.Runner;
import com.streamsets.datacollector.execution.common.ExecutorConstants;
import com.streamsets.datacollector.execution.runner.common.AsyncRunner;
import com.streamsets.datacollector.execution.Snapshot;
import com.streamsets.datacollector.execution.SnapshotInfo;
import com.streamsets.datacollector.execution.manager.standalone.StandaloneAndClusterPipelineManager;
import com.streamsets.datacollector.execution.runner.common.PipelineRunnerException;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.main.RuntimeModule;
import com.streamsets.datacollector.store.PipelineStoreException;
import com.streamsets.datacollector.util.Configuration;
import com.streamsets.datacollector.util.ContainerError;
import com.streamsets.datacollector.util.TestUtil;
import com.streamsets.dc.execution.manager.standalone.ResourceManager;
import com.streamsets.pipeline.api.ExecutionMode;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestStandaloneRunner {

  private Manager pipelineManager;
  private PipelineStateStore pipelineStateStore;

  @Before
  public void setUp() throws IOException {
    File testDir = new File("target", UUID.randomUUID().toString()).getAbsoluteFile();
    Assert.assertTrue(testDir.mkdirs());
    System.setProperty(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR, testDir.getAbsolutePath());
    TestUtil.captureStagesForProductionRun();
    TestUtil.EMPTY_OFFSET = false;
    ObjectGraph objectGraph = ObjectGraph.create(new TestUtil.TestPipelineManagerModule());
    pipelineStateStore = objectGraph.get(PipelineStateStore.class);
    pipelineManager = new StandaloneAndClusterPipelineManager(objectGraph);
    pipelineManager.init();
  }

  @After
  public void tearDown() throws Exception {
    TestUtil.EMPTY_OFFSET = false;
    pipelineManager.stop();
    try {
      File f = new File(System.getProperty(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR));
      FileUtils.deleteDirectory(f);
    } catch (Exception e) {

    }
    TestUtil.EMPTY_OFFSET = false;
    System.getProperties().remove(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR);
  }

  @Test(timeout = 20000)
  public void testPipelineStart() throws Exception {
    Runner runner = pipelineManager.getRunner("admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    waitAndAssertState(runner, PipelineStatus.RUNNING);
    runner.stop();
    waitAndAssertState(runner, PipelineStatus.STOPPED);
  }

  @Test(timeout = 50000)
  public void testPipelinePrepare() throws Exception {
    Runner runner = pipelineManager.getRunner("admin", TestUtil.MY_PIPELINE, "0");
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.FINISHING, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.FINISHED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.STOPPING, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.STOPPED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.DISCONNECTING, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.DISCONNECTED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.CONNECTING, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.DISCONNECTED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.STARTING, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.DISCONNECTED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.RUNNING, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.DISCONNECTED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.DISCONNECTED, null, null,
      ExecutionMode.STANDALONE, null);
    runner.prepareForDataCollectorStart();
    assertEquals(PipelineStatus.DISCONNECTED, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.STOPPED, null, null,
      ExecutionMode.STANDALONE, null);
    runner.start();
    assertEquals(PipelineStatus.STARTING, runner.getState().getStatus());
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.STOPPED, null, null,
      ExecutionMode.STANDALONE, null);
    assertNull(runner.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testPipelineFinish() throws Exception {
    Runner runner = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    waitAndAssertState(runner, PipelineStatus.RUNNING);
    assertNull(runner.getState().getMetrics());
    TestUtil.EMPTY_OFFSET = true;
    waitAndAssertState(runner, PipelineStatus.FINISHED);
    assertNotNull(runner.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testLoadingUnsupportedPipeline() throws Exception {
    Runner runner = pipelineManager.getRunner("user2", TestUtil.HIGHER_VERSION_PIPELINE, "0");
    runner.start();
    waitAndAssertState(runner, PipelineStatus.START_ERROR);
    PipelineState state = pipelineManager.getRunner("user2", TestUtil.HIGHER_VERSION_PIPELINE, "0").getState();
    Assert.assertTrue(state.getStatus() == PipelineStatus.START_ERROR);
    Assert.assertTrue(state.getMessage().contains("CONTAINER_0158"));
    assertNull(runner.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testDisconnectedPipelineStartedAgain() throws Exception {
    Runner runner = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    waitAndAssertState(runner, PipelineStatus.RUNNING);
    // sdc going down
    pipelineManager.stop();
    waitAndAssertState(runner, PipelineStatus.DISCONNECTED);

    ObjectGraph objectGraph = ObjectGraph.create(new TestUtil.TestPipelineManagerModule());
    pipelineStateStore = objectGraph.get(PipelineStateStore.class);
    pipelineManager = new StandaloneAndClusterPipelineManager(objectGraph);
    pipelineManager.init();

    runner = pipelineManager.getRunner("admin", TestUtil.MY_PIPELINE, "0");
    waitAndAssertState(runner, PipelineStatus.RUNNING);
    ((AsyncRunner)runner).getRunner().stop();
    Assert.assertTrue(runner.getState().getStatus() == PipelineStatus.STOPPED);
    assertNotNull(runner.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testFinishedPipelineNotStartingAgain() throws Exception {
    Runner runner = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    waitAndAssertState(runner, PipelineStatus.RUNNING);

    TestUtil.EMPTY_OFFSET = true;
    waitAndAssertState(runner, PipelineStatus.FINISHED);

    // sdc going down
    pipelineManager.stop();
    assertNotNull(runner.getState().getMetrics());
    // Simulate finishing, the runner shouldn't restart on finishing
    pipelineStateStore.saveState("admin", TestUtil.MY_PIPELINE, "0", PipelineStatus.FINISHING, null, null,
      ExecutionMode.STANDALONE, runner.getState().getMetrics());
    ObjectGraph objectGraph = ObjectGraph.create(new TestUtil.TestPipelineManagerModule());
    pipelineStateStore = objectGraph.get(PipelineStateStore.class);
    pipelineManager = new StandaloneAndClusterPipelineManager(objectGraph);
    pipelineManager.init();

    //Since SDC went down we need to get the runner again
    runner = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    waitAndAssertState(runner, PipelineStatus.FINISHED);
    assertNotNull(runner.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testMultiplePipelineStartStop() throws Exception {
    Runner runner1 = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    Runner runner2 = pipelineManager.getRunner("admin2", TestUtil.MY_SECOND_PIPELINE, "0");

    runner1.start();
    runner2.start();
    waitAndAssertState(runner1, PipelineStatus.RUNNING);
    waitAndAssertState(runner2, PipelineStatus.RUNNING);

    runner1.stop();
    waitAndAssertState(runner1, PipelineStatus.STOPPED);
    ((AsyncRunner)runner2).getRunner().stop();
    Assert.assertTrue(runner2.getState().getStatus() == PipelineStatus.STOPPED);
    assertNotNull(runner1.getState().getMetrics());
    assertNotNull(runner2.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testMultiplePipelineFinish() throws Exception {
    Runner runner1 = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    Runner runner2 = pipelineManager.getRunner("admin2", TestUtil.MY_SECOND_PIPELINE, "0");

    runner1.start();
    runner2.start();
    waitAndAssertState(runner1, PipelineStatus.RUNNING);
    waitAndAssertState(runner2, PipelineStatus.RUNNING);

    TestUtil.EMPTY_OFFSET = true;

    waitAndAssertState(runner1, PipelineStatus.FINISHED);
    waitAndAssertState(runner2, PipelineStatus.FINISHED);
    assertNotNull(runner1.getState().getMetrics());
    assertNotNull(runner2.getState().getMetrics());
  }

  @Test(timeout = 20000)
  public void testDisconnectedPipelinesStartedAgain() throws Exception {
    Runner runner1 = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    Runner runner2 = pipelineManager.getRunner("admin2", TestUtil.MY_SECOND_PIPELINE, "0");
    runner1.start();
    runner2.start();
    waitAndAssertState(runner1, PipelineStatus.RUNNING);
    waitAndAssertState(runner2, PipelineStatus.RUNNING);

    // sdc going down
    pipelineManager.stop();
    waitAndAssertState(runner1, PipelineStatus.DISCONNECTED);
    waitAndAssertState(runner2, PipelineStatus.DISCONNECTED);

    ObjectGraph objectGraph = ObjectGraph.create(new TestUtil.TestPipelineManagerModule());
    pipelineStateStore = objectGraph.get(PipelineStateStore.class);
    pipelineManager = new StandaloneAndClusterPipelineManager(objectGraph);
    pipelineManager.init();
    Thread.sleep(2000);

    runner1 = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    runner2 = pipelineManager.getRunner("admin2", TestUtil.MY_SECOND_PIPELINE, "0");

    waitAndAssertState(runner1, PipelineStatus.RUNNING);
    waitAndAssertState(runner2, PipelineStatus.RUNNING);

    runner1.stop();
    runner2.stop();
    waitAndAssertState(runner1, PipelineStatus.STOPPED);
    waitAndAssertState(runner2, PipelineStatus.STOPPED);
    assertNotNull(runner1.getState().getMetrics());
    assertNotNull(runner2.getState().getMetrics());
  }

  private void waitAndAssertState(Runner runner, PipelineStatus pipelineStatus)
    throws PipelineStoreException, InterruptedException {
    while (runner.getState().getStatus() != pipelineStatus && runner.getState().getStatus().isActive()) {
      Thread.sleep(100);
    }
    Assert.assertTrue(runner.getState().getStatus() == pipelineStatus);
  }

  @Test
  public void testSnapshot() throws Exception {
    Runner runner = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    runner.start();
    waitAndAssertState(runner, PipelineStatus.RUNNING);

    //request to capture snapshot and check the status
    String snapshotId = runner.captureSnapshot("mySnapshot", 5, 10);
    assertNotNull(snapshotId);

    Snapshot snapshot = runner.getSnapshot(snapshotId);
    assertNotNull(snapshot);

    SnapshotInfo info = snapshot.getInfo();
    assertNotNull(info);
    assertNull(snapshot.getOutput());
    assertTrue(info.isInProgress());
    assertEquals(snapshotId, info.getId());
    assertEquals(TestUtil.MY_PIPELINE, info.getName());
    assertEquals("0", info.getRev());

    //request cancel snapshot
    runner.deleteSnapshot(snapshotId);
    snapshot = runner.getSnapshot(snapshotId);
    assertNotNull(snapshot);
    assertNull(snapshot.getInfo());
    assertNull(snapshot.getOutput());

    //call cancel again - no op
    runner.deleteSnapshot("mySnapshot");
    snapshot = runner.getSnapshot(snapshotId);
    assertNotNull(snapshot);
    assertNull(snapshot.getInfo());
    assertNull(snapshot.getOutput());

    //call cancel on some other snapshot which does not exist - no op
    runner.deleteSnapshot("mySnapshot1");
    snapshot = runner.getSnapshot(snapshotId);
    assertNotNull(snapshot);
    assertNull(snapshot.getInfo());
    assertNull(snapshot.getOutput());

    runner.stop();
    waitAndAssertState(runner, PipelineStatus.STOPPED);
  }

  @Test
  public void testRunningMaxPipelines() throws Exception {
    ObjectGraph objectGraph = ObjectGraph.create(new TestUtil.TestPipelineManagerModule(), ConfigModule.class);
    Manager pipelineManager = new StandaloneAndClusterPipelineManager(objectGraph);
    pipelineManager.init();

    //Only one runner can start pipeline at the max since the runner thread pool size is 3
    Runner runner1 = pipelineManager.getRunner( "admin", TestUtil.MY_PIPELINE, "0");
    runner1.start();
    waitAndAssertState(runner1, PipelineStatus.RUNNING);

    Runner runner2 = pipelineManager.getRunner("admin2", TestUtil.MY_SECOND_PIPELINE, "0");
    try {
      runner2.start();
      Assert.fail("Expected PipelineRunnerException");
    } catch (PipelineRunnerException e) {
      Assert.assertEquals(ContainerError.CONTAINER_0166, e.getErrorCode());
    }

    runner1.stop();
    waitAndAssertState(runner1, PipelineStatus.STOPPED);

    runner2.start();
    waitAndAssertState(runner2, PipelineStatus.RUNNING);

    try {
      runner1.start();
      Assert.fail("Expected PipelineRunnerException");
    } catch (PipelineRunnerException e) {
      Assert.assertEquals(ContainerError.CONTAINER_0166, e.getErrorCode());
    }
  }

  @Module(overrides = true, library = true)
  static class ConfigModule {
    @Provides
    @Singleton
    public Configuration provideConfiguration() {
      Configuration configuration = new Configuration();
      configuration.set(ExecutorConstants.RUNNER_THREAD_POOL_SIZE_KEY, 3);
      return configuration;
    }

    @Provides
    @Singleton
    public ResourceManager provideResourceManager(Configuration configuration) {
      return new ResourceManager(configuration);
    }
  }


}
