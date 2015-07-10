/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.runner;

import com.streamsets.pipeline.api.impl.CreateByRef;
import com.streamsets.pipeline.config.StageDefinition;

import com.streamsets.pipeline.creation.PipelineBean;
import com.streamsets.pipeline.creation.StageBean;
import com.streamsets.pipeline.creation.StageConfigBean;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.Callable;

public class TestStageRuntime {

  @Test
  public void testCreateByRef() throws Exception {
    PipelineBean pipelineBean = Mockito.mock(PipelineBean.class);
    StageBean stageBean = Mockito.mock(StageBean.class);
    Mockito.when(stageBean.getSystemConfigs()).thenReturn(new StageConfigBean());
    StageDefinition def = Mockito.mock(StageDefinition.class);
    Mockito.when(stageBean.getDefinition()).thenReturn(def);
    StageContext context = Mockito.mock(StageContext.class);
    StageRuntime runtime = new StageRuntime(pipelineBean, stageBean);
    runtime.setContext(context);

    // by value, no preview
    Mockito.when(def.getRecordsByRef()).thenReturn(false);
    Mockito.when(context.isPreview()).thenReturn(false);
    runtime.execute(new Callable<String>() {
      @Override
      public String call() throws Exception {
        Assert.assertFalse(CreateByRef.isByRef());
        return null;
      }
    });

    // by value, preview
    Mockito.when(def.getRecordsByRef()).thenReturn(false);
    Mockito.when(context.isPreview()).thenReturn(true);
    runtime.execute(new Callable<String>() {
      @Override
      public String call() throws Exception {
        Assert.assertFalse(CreateByRef.isByRef());
        return null;
      }
    });

    // by ref, no preview
    Mockito.when(def.getRecordsByRef()).thenReturn(true);
    Mockito.when(context.isPreview()).thenReturn(false);
    runtime.execute(new Callable<String>() {
      @Override
      public String call() throws Exception {
        Assert.assertTrue(CreateByRef.isByRef());
        return null;
      }
    });

    // by ref, preview
    Mockito.when(def.getRecordsByRef()).thenReturn(true);
    Mockito.when(context.isPreview()).thenReturn(true);
    runtime.execute(new Callable<String>() {
      @Override
      public String call() throws Exception {
        Assert.assertFalse(CreateByRef.isByRef());
        return null;
      }
    });

  }

}
