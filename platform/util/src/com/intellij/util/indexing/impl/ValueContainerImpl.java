/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.containers.ChangeBufferingList;
import com.intellij.util.indexing.containers.IntIdsIterator;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.impl.ValueContainerImpl");
  private static final Object myNullValue = new Object();

  // there is no volatile as we modify under write lock and read under read lock
  // Most often (80%) we store 0 or one mapping, then we store them in two fields: myInputIdMapping, myInputIdMappingValue
  // when there are several value mapped, myInputIdMapping is THashMap<Value, Data>, myInputIdMappingValue = null
  private Object myInputIdMapping;
  private Object myInputIdMappingValue;

  @Override
  public void addValue(int inputId, Value value) {
    final Object fileSetObject = getFileSetObject(value);

    if (fileSetObject == null) {
      attachFileSetForNewValue(value, inputId);
    }
    else if (fileSetObject instanceof Integer) {
      int existingValue = ((Integer)fileSetObject).intValue();
      if (existingValue != inputId) {
        ChangeBufferingList list = new ChangeBufferingList();
        list.add(existingValue);
        list.add(inputId);
        resetFileSetForValue(value, list);
      }
    }
    else {
      ((ChangeBufferingList)fileSetObject).add(inputId);
    }
  }

  @Nullable
  private THashMap<Value, Object> asMapping() {
    //noinspection unchecked
    return myInputIdMapping instanceof THashMap ? (THashMap<Value, Object>)myInputIdMapping : null;
  }

  private Value nullValue() {
    //noinspection unchecked
    return (Value)myNullValue;
  }

  private void resetFileSetForValue(Value value, @NotNull Object fileSet) {
    if (value == null) value = nullValue();
    THashMap<Value, Object> map = asMapping();
    if (map == null) {
      myInputIdMappingValue = fileSet;
    }
    else {
      map.put(value, fileSet);
    }
  }

  @Override
  public int size() {
    return myInputIdMapping != null ? myInputIdMapping instanceof THashMap ? ((THashMap)myInputIdMapping).size(): 1 : 0;
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    if (myInputIdMapping == null) return;
    List<Object> fileSetObjects = null;
    List<Value> valueObjects = null;
    for (final InvertedIndexValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();

      if (valueIterator.getValueAssociationPredicate().contains(inputId)) {
        if (fileSetObjects == null) {
          fileSetObjects = new SmartList<>();
          valueObjects = new SmartList<>();
        }
        else if (DebugAssertions.DEBUG) {
          LOG.error("Expected only one value per-inputId for " + DebugAssertions.DEBUG_INDEX_ID.get(), String.valueOf(fileSetObjects.get(0)), String.valueOf(value));
        }
        fileSetObjects.add(valueIterator.getFileSetObject());
        valueObjects.add(value);
      }
    }

    if (fileSetObjects != null) {
      for (int i = 0, len = valueObjects.size(); i < len; ++i) {
        removeValue(inputId, fileSetObjects.get(i), valueObjects.get(i));
      }
    }
  }

  void removeValue(int inputId, Value value) {
    removeValue(inputId, getFileSetObject(value), value);
  }

  private void removeValue(int inputId, Object fileSet, Value value) {
    if (fileSet == null) {
      return;
    }

    if (fileSet instanceof ChangeBufferingList) {
      final ChangeBufferingList changesList = (ChangeBufferingList)fileSet;
      changesList.remove(inputId);
      if (!changesList.isEmpty()) return;
    }
    else if (fileSet instanceof Integer) {
      if (((Integer)fileSet).intValue() != inputId) {
        return;
      }
    }

    THashMap<Value, Object> mapping = asMapping();
    if (mapping == null) {
      myInputIdMapping = null;
      myInputIdMappingValue = null;
    } else {
      mapping.remove(value);
      if (mapping.size() == 1) {
        Value mappingValue = mapping.keySet().iterator().next();
        myInputIdMapping = mappingValue;
        Object inputIdMappingValue = mapping.get(mappingValue);
        // prevent NPEs on file set due to Value class being mutable or having inconsistent equals wrt disk persistence 
        // (instance that is serialized and new instance created with deserialization from the same bytes are expected to be equal)
        myInputIdMappingValue = inputIdMappingValue != null ? inputIdMappingValue : new Integer(0);
      }
    }
  }

  @NotNull
  @Override
  public InvertedIndexValueIterator<Value> getValueIterator() {
    if (myInputIdMapping == null) {
      //noinspection unchecked
      return EmptyValueIterator.INSTANCE;
    }
    final THashMap<Value, Object> mapping = asMapping();
    if (mapping == null) {
      return new InvertedIndexValueIterator<Value>() {
        private Value value = (Value)myInputIdMapping;

        @NotNull
        @Override
        public IntIterator getInputIdsIterator() {
          return getIntIteratorOutOfFileSetObject(getFileSetObject());
        }

        @NotNull
        @Override
        public IntPredicate getValueAssociationPredicate() {
          return getPredicateOutOfFileSetObject(getFileSetObject());
        }

        @Override
        public Object getFileSetObject() {
          return myInputIdMappingValue;
        }

        @Override
        public boolean hasNext() {
          return value != null;
        }

        @Override
        public Value next() {
          Value next = value;
          if (next == myNullValue) next = null;
          value = null;
          return next;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
    else {
      return new InvertedIndexValueIterator<Value>() {
        private Value current;
        private Object currentValue;
        private final Iterator<Map.Entry<Value, Object>> iterator = mapping.entrySet().iterator();

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public Value next() {
          Map.Entry<Value, Object> entry = iterator.next();
          current = entry.getKey();
          Value next = current;
          currentValue = entry.getValue();
          if (next == myNullValue) next = null;
          return next;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public IntIterator getInputIdsIterator() {
          return getIntIteratorOutOfFileSetObject(getFileSetObject());
        }

        @NotNull
        @Override
        public IntPredicate getValueAssociationPredicate() {
          return getPredicateOutOfFileSetObject(getFileSetObject());
        }

        @Override
        public Object getFileSetObject() {
          if (current == null) throw new IllegalStateException();
          return currentValue;
        }
      };
    }
  }

  private static class EmptyValueIterator<Value> extends EmptyIterator<Value> implements InvertedIndexValueIterator<Value> {
    private static final EmptyValueIterator INSTANCE = new EmptyValueIterator();

    @NotNull
    @Override
    public ValueContainer.IntIterator getInputIdsIterator() {
      throw new IllegalStateException();
    }

    @NotNull
    @Override
    public IntPredicate getValueAssociationPredicate() {
      throw new IllegalStateException();
    }

    @Override
    public Object getFileSetObject() {
      throw new IllegalStateException();
    }
  }

  @NotNull
  private static IntPredicate getPredicateOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return EMPTY_PREDICATE;

    if (input instanceof Integer) {
      final int singleId = (Integer)input;

      return new IntPredicate() {
        @Override
        public boolean contains(int id) {
          return id == singleId;
        }
      };
    }
    return ((ChangeBufferingList)input).intPredicate();
  }

  @NotNull
  private static
  ValueContainer.IntIterator getIntIteratorOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return EMPTY_ITERATOR;
    if (input instanceof Integer) {
      return new SingleValueIterator(((Integer)input).intValue());
    }
    return ((ChangeBufferingList)input).intIterator();
  }

  private Object getFileSetObject(Value value) {
    if (myInputIdMapping == null) return null;

    value = value != null ? value : nullValue();

    if (myInputIdMapping == value || // myNullValue is Object
        myInputIdMapping.equals(value)
       ) {
      return myInputIdMappingValue;
    }

    THashMap<Value, Object> mapping = asMapping();
    return mapping == null ? null : mapping.get(value);
  }

  @Override
  public ValueContainerImpl<Value> clone() {
    try {
      @SuppressWarnings("unchecked")
      final ValueContainerImpl<Value> clone = (ValueContainerImpl<Value>)super.clone();
      THashMap<Value, Object> mapping = asMapping();
      if (mapping != null) {
        final THashMap<Value, Object> cloned = mapping.clone();
        cloned.forEachEntry(new TObjectObjectProcedure<Value, Object>() {
          @Override
          public boolean execute(Value key, Object val) {
            if (val instanceof ChangeBufferingList) {
              cloned.put(key, ((ChangeBufferingList)val).clone());
            }
            return true;
          }
        });

        clone.myInputIdMapping = cloned;
      } else if (myInputIdMappingValue instanceof ChangeBufferingList) {
        clone.myInputIdMappingValue = ((ChangeBufferingList)myInputIdMappingValue).clone();
      }
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  private static final ValueContainer.IntIterator EMPTY_ITERATOR = new IntIdsIterator() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public int next() {
      return 0;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return this;
    }
  };

  @Nullable
  private ChangeBufferingList ensureFileSetCapacityForValue(Value value, int count) {
    if (count <= 1) return null;
    Object fileSetObject = getFileSetObject(value);

    if (fileSetObject != null) {
      if (fileSetObject instanceof Integer) {
        ChangeBufferingList list = new ChangeBufferingList(count + 1);
        list.add(((Integer)fileSetObject).intValue());
        resetFileSetForValue(value, list);
        return list;
      }
      if (fileSetObject instanceof ChangeBufferingList) {
        ChangeBufferingList list = (ChangeBufferingList)fileSetObject;
        list.ensureCapacity(count);
        return list;
      }
      return null;
    }

    final ChangeBufferingList fileSet = new ChangeBufferingList(count);
    attachFileSetForNewValue(value, fileSet);
    return fileSet;
  }

  private void attachFileSetForNewValue(Value value, Object fileSet) {
    value = value != null ? value : nullValue();
    if (myInputIdMapping != null) {
      THashMap<Value, Object> mapping = asMapping();
      if (mapping == null) {
        Value oldMapping = (Value)myInputIdMapping;
        myInputIdMapping = mapping = new THashMap<>(2);
        mapping.put(oldMapping, myInputIdMappingValue);
        myInputIdMappingValue = null;
      }
      mapping.put(value, fileSet);
    }
    else {
      myInputIdMapping = value;
      myInputIdMappingValue = fileSet;
    }
  }

  @Override
  public void saveTo(DataOutput out, DataExternalizer<? super Value> externalizer) throws IOException {
    DataInputOutputUtil.writeINT(out, size());

    for (final InvertedIndexValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      externalizer.save(out, value);
      Object fileSetObject = valueIterator.getFileSetObject();

      if (fileSetObject instanceof Integer) {
        DataInputOutputUtil.writeINT(out, (Integer)fileSetObject); // most common 90% case during index building
      } else {
        // serialize positive file ids with delta encoding
        ChangeBufferingList originalInput = (ChangeBufferingList)fileSetObject;
        IntIdsIterator intIterator = originalInput.sortedIntIterator();
        if (DebugAssertions.DEBUG) DebugAssertions.assertTrue(intIterator.hasAscendingOrder());

        if (intIterator.size() == 1) {
          DataInputOutputUtil.writeINT(out, intIterator.next());
        } else {
          DataInputOutputUtil.writeINT(out, -intIterator.size());
          int prev = 0;

          while (intIterator.hasNext()) {
            int fileId = intIterator.next();
            DataInputOutputUtil.writeINT(out, fileId - prev);
            prev = fileId;
          }
        }
      }
    }
  }

  static final int NUMBER_OF_VALUES_THRESHOLD = 20;

  public void readFrom(DataInputStream stream, DataExternalizer<? extends Value> externalizer) throws IOException {
    FileId2ValueMapping<Value> mapping = null;

    while (stream.available() > 0) {
      final int valueCount = DataInputOutputUtil.readINT(stream);
      if (valueCount < 0) {
        // ChangeTrackingValueContainer marked inputId as invalidated, see ChangeTrackingValueContainer.saveTo
        final int inputId = -valueCount;

        if (mapping == null && size() > NUMBER_OF_VALUES_THRESHOLD) { // avoid O(NumberOfValues)
          mapping = new FileId2ValueMapping<>(this);
        }

        boolean doCompact;
        if(mapping != null) {
          doCompact = mapping.removeFileId(inputId);
        } else {
          removeAssociatedValue(inputId);
          doCompact = true;
        }

        if (doCompact) setNeedsCompacting(true);
      }
      else {
        for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
          final Value value = externalizer.read(stream);
          int idCountOrSingleValue = DataInputOutputUtil.readINT(stream);

          if (idCountOrSingleValue > 0) {
            addValue(idCountOrSingleValue, value);
            if (mapping != null) mapping.associateFileIdToValue(idCountOrSingleValue, value);
          } else {
            idCountOrSingleValue = -idCountOrSingleValue;
            ChangeBufferingList changeBufferingList = ensureFileSetCapacityForValue(value, idCountOrSingleValue);
            int prev = 0;

            for (int i = 0; i < idCountOrSingleValue; i++) {
              final int id = DataInputOutputUtil.readINT(stream);
              if (changeBufferingList != null)  changeBufferingList.add(prev + id);
              else addValue(prev + id, value);
              if (mapping != null) mapping.associateFileIdToValue(prev + id, value);
              prev += id;
            }
          }
        }
      }
    }
  }

  private static class SingleValueIterator implements IntIdsIterator {
    private final int myValue;
    private boolean myValueRead;

    private SingleValueIterator(int value) {
      myValue = value;
    }

    @Override
    public boolean hasNext() {
      return !myValueRead;
    }

    @Override
    public int next() {
      myValueRead = true;
      return myValue;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new SingleValueIterator(myValue);
    }
  }

  private static final IntPredicate EMPTY_PREDICATE = new IntPredicate() {
    @Override
    public boolean contains(int id) {
      return false;
    }
  };
}
