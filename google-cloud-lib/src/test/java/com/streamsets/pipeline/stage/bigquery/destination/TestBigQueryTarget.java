/*
 * Copyright 2017 StreamSets Inc.
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
package com.streamsets.pipeline.stage.bigquery.destination;

import com.google.auth.Credentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.common.collect.ImmutableMap;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.sdk.RecordCreator;
import com.streamsets.pipeline.sdk.TargetRunner;
import com.streamsets.pipeline.stage.bigquery.lib.BigQueryDelegate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberMatcher;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@RunWith(PowerMockRunner.class)
@PrepareForTest({
    BigQuery.class,
    BigQueryTarget.class,
    InsertAllResponse.class,
    BigQueryDelegate.class,
    Credentials.class
})
public class TestBigQueryTarget {

  public BigQuery bigQuery;

  @Before
  public void setup() {
    bigQuery = PowerMockito.mock(BigQuery.class);
    PowerMockito
        .replace(MemberMatcher.method(BigQueryDelegate.class, "getCredentials"))
        .with((proxy, method, args) -> Optional.empty());
  }


  private TargetRunner createAndRunner(BigQueryTargetConfig config, List<Record> records) throws Exception {
    Target target = new BigQueryTarget(config);
    Whitebox.setInternalState(target, "bigQuery", bigQuery);
    TargetRunner runner = new TargetRunner.Builder(BigQueryDTarget.class, target)
        .setOnRecordError(OnRecordError.TO_ERROR)
        .build();
    runner.runInit();
    try {
      runner.runWrite(records);
    } finally {
      runner.runDestroy();
    }
    return runner;
  }


  @SuppressWarnings("unchecked")
  private Field createField(Object object) {
    Field field;
    if (object instanceof Map) {
      Map<String, Object> mapObject = (Map<String, Object>)object;
      return Field.create(mapObject.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> createField(e.getValue()))));
    } else if (object instanceof List) {
      List<Object> listObject = (List<Object>)object;
      return Field.create(listObject.stream().map(this::createField).collect(Collectors.toList()));
    } else if (object instanceof Boolean) {
      field = Field.create((Boolean) object);
    } else if (object instanceof Character) {
      field = Field.create((Character) object);
    } else if (object instanceof Byte) {
      field = Field.create((Byte) object);
    } else if (object instanceof Short) {
      field = Field.create((Short) object);
    } else if (object instanceof Integer) {
      field = Field.create((Integer) object);
    } else if (object instanceof Long) {
      field = Field.create((Long) object);
    } else if (object instanceof Float) {
      field = Field.create((Float) object);
    } else if (object instanceof Double) {
      field = Field.create((Double) object);
    } else if (object instanceof Date) {
      field = Field.createDatetime((Date) object);
    } else if (object instanceof BigDecimal) {
      field = Field.create((BigDecimal) object);
    } else if (object instanceof String) {
      field = Field.create((String) object);
    } else if (object instanceof byte[]) {
      field = Field.create((byte[]) object);
    } else {
      throw new IllegalArgumentException(Utils.format("Cannot convert object type '{}' to field", object.getClass()));
    }
    return field;
  }

  private Record createRecord(Map<String, Object> values) {
    Record record = RecordCreator.create();
    record.set(
        Field.create(values.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> createField(e.getValue()))))
    );
    return record;
  }


  @Test
  public void testCorrectImplicitMapping() throws Exception {
    List<Record> records = new ArrayList<>();

    records.add(createRecord(ImmutableMap.of("a", "1", "b", 1, "c", 1.0)));
    records.add(createRecord(ImmutableMap.of("a", "2", "b", 2, "c", 2.0)));
    records.add(createRecord(ImmutableMap.of("a", "3", "b", 3, "c", 3.0)));


    Mockito.doAnswer(invocationOnMock -> {
      InsertAllRequest insertAllRequest = (InsertAllRequest)invocationOnMock.getArguments()[0];

      List<InsertAllRequest.RowToInsert> rows = insertAllRequest.getRows();

      final AtomicInteger recordIdx = new AtomicInteger(0);
      rows.forEach(row -> {
        int idx = recordIdx.getAndIncrement();
        Record record = records.get(idx);
        Map<String, ?> rowContent = row.getContent();
        record.get().getValueAsMap().forEach((k, v) -> Assert.assertEquals(v.getValue(), rowContent.get(k)));
      });

      InsertAllResponse response = PowerMockito.mock(InsertAllResponse.class);
      Mockito.doReturn(Collections.emptyMap()).when(response).getInsertErrors();
      Mockito.doReturn(false).when(response).hasErrors();
      return response;
    }).when(bigQuery).insertAll(Mockito.any(InsertAllRequest.class));

    BigQueryTargetConfigBuilder configBuilder = new BigQueryTargetConfigBuilder();
    configBuilder.implicitFieldMapping(true);

    createAndRunner(configBuilder.build(), records);
  }

  @Test
  public void testExplicitMappingCorrect() throws Exception {
    List<Record> records = new ArrayList<>();
    records.add(
        createRecord(
            ImmutableMap.of("a", "1", "b", ImmutableMap.of("a1", 1, "b1", 1, "c1", 1), "c", 1.0)
        )
    );

    final Map<String, String> columnToFieldMapping =
        ImmutableMap.of("a", "/a", "ba1", "/b/a1", "bb1", "/b/b1", "bc1", "/b/c1", "c", "/c");

    Mockito.doAnswer(invocationOnMock -> {
      InsertAllRequest insertAllRequest = (InsertAllRequest)invocationOnMock.getArguments()[0];

      List<InsertAllRequest.RowToInsert> rows = insertAllRequest.getRows();

      final AtomicInteger recordIdx = new AtomicInteger(0);
      rows.forEach(row -> {
        int idx = recordIdx.getAndIncrement();
        Record record = records.get(idx);
        Map<String, ?> rowContent = row.getContent();
        columnToFieldMapping.forEach(
            (c, f) -> Assert.assertEquals(record.get(f).getValue(), rowContent.get(c)));
      });

      InsertAllResponse response = PowerMockito.mock(InsertAllResponse.class);
      Mockito.doReturn(Collections.emptyMap()).when(response).getInsertErrors();
      Mockito.doReturn(false).when(response).hasErrors();
      return response;
    }).when(bigQuery).insertAll(Mockito.any(InsertAllRequest.class));

    BigQueryTargetConfigBuilder configBuilder = new BigQueryTargetConfigBuilder();
    configBuilder.implicitFieldMapping(false);
    configBuilder.columnToFieldNameMapping(columnToFieldMapping);
    createAndRunner(configBuilder.build(), records);
  }

  private void testExplicitMappingNonExistingFields(boolean ignoreMissingFields) throws Exception {
    List<Record> records = new ArrayList<>();
    records.add(
        createRecord(
            ImmutableMap.of("a", "1", "b", ImmutableMap.of("a1", 1, "b1", 1, "c1", 1), "c", 1.0)
        )
    );

    records.add(
        createRecord(
            ImmutableMap.of("a", "2", "b", ImmutableMap.of("a1", 2), "c", 2.0)
        )
    );

    final Map<String, String> columnToFieldMapping =
        ImmutableMap.of("a", "/a", "ba1", "/b/a1", "bb1", "/b/b1", "bc1", "/b/c1", "c", "/c");

    Mockito.doAnswer(invocationOnMock -> {
      InsertAllRequest insertAllRequest = (InsertAllRequest)invocationOnMock.getArguments()[0];

      List<InsertAllRequest.RowToInsert> rows = insertAllRequest.getRows();

      final AtomicInteger recordIdx = new AtomicInteger(0);
      rows.forEach(row -> {
        int idx = recordIdx.getAndIncrement();
        Record record = records.get(idx);
        Map<String, ?> rowContent = row.getContent();
        rowContent.forEach((k, v) -> Assert.assertEquals(record.get(columnToFieldMapping.get(k)).getValue(), v));
      });

      InsertAllResponse response = PowerMockito.mock(InsertAllResponse.class);
      Mockito.doReturn(Collections.emptyMap()).when(response).getInsertErrors();
      Mockito.doReturn(false).when(response).hasErrors();
      return response;
    }).when(bigQuery).insertAll(Mockito.any(InsertAllRequest.class));

    BigQueryTargetConfigBuilder configBuilder = new BigQueryTargetConfigBuilder();
    configBuilder.implicitFieldMapping(false);
    configBuilder.ignoreInvalidColumns(ignoreMissingFields);
    configBuilder.columnToFieldNameMapping(columnToFieldMapping);
    TargetRunner runner = createAndRunner(configBuilder.build(), records);
    if (ignoreMissingFields) {
      Assert.assertEquals(0, runner.getErrorRecords().size());
    } else {
      Assert.assertEquals(1, runner.getErrorRecords().size());
    }
  }

  @Test
  public void testExplicitMappingNonExistingFieldsError() throws Exception {
    testExplicitMappingNonExistingFields(false);
  }

  @Test
  public void testExplicitMappingNonExistingFieldsNoError() throws Exception {
    testExplicitMappingNonExistingFields(true);
  }

  @Test
  public void testImplicitMappingWithNestMapFieldError() throws Exception {
    List<Record> records = new ArrayList<>();
    records.add(
        createRecord(
            ImmutableMap.of("a", "1", "b", ImmutableMap.of("a1", 1, "b1", 1, "c1", 1), "c", 1.0)
        )
    );

    records.add(
        createRecord(
            ImmutableMap.of("a", "2","c", 2.0)
        )
    );

    Mockito.doAnswer(invocationOnMock -> {
      InsertAllResponse response = PowerMockito.mock(InsertAllResponse.class);
      Mockito.doReturn(Collections.emptyMap()).when(response).getInsertErrors();
      Mockito.doReturn(false).when(response).hasErrors();
      return response;
    }).when(bigQuery).insertAll(Mockito.any(InsertAllRequest.class));

    BigQueryTargetConfigBuilder configBuilder = new BigQueryTargetConfigBuilder();
    configBuilder.implicitFieldMapping(true);
    configBuilder.ignoreInvalidColumns(true);
    TargetRunner runner = createAndRunner(configBuilder.build(), records);
    Assert.assertEquals(1, runner.getErrorRecords().size());
  }

}
