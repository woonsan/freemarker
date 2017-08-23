/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.freemarker.core.model.impl;

import java.util.ArrayList;

import org.apache.freemarker.core.TemplateException;
import org.apache.freemarker.core.model.TemplateCollectionModel;
import org.apache.freemarker.core.model.TemplateCollectionModelEx;
import org.apache.freemarker.core.model.TemplateModel;
import org.apache.freemarker.core.model.TemplateModelIterator;
import org.apache.freemarker.core.model.TemplateSequenceModel;

/**
 * Add sequence capabilities to an existing collection, or
 * vice versa. Used by ?keys and ?values built-ins.
 */
// [FM3] FTL sequence should extend FTL collection, so we shouldn't need that direction, only the other.
final public class CollectionAndSequence implements TemplateCollectionModel, TemplateSequenceModel {
    private TemplateCollectionModel collection;
    private TemplateSequenceModel sequence;
    private ArrayList data;

    public CollectionAndSequence(TemplateCollectionModel collection) {
        this.collection = collection;
    }

    public CollectionAndSequence(TemplateSequenceModel sequence) {
        this.sequence = sequence;
    }

    @Override
    public TemplateModelIterator iterator() throws TemplateException {
        if (collection != null) {
            return collection.iterator();
        } else {
            return new SequenceIterator(sequence);
        }
    }

    @Override
    public TemplateModel get(int i) throws TemplateException {
        if (sequence != null) {
            return sequence.get(i);
        } else {
            initSequence();
            return (TemplateModel) data.get(i);
        }
    }

    @Override
    public int size() throws TemplateException {
        if (sequence != null) {
            return sequence.size();
        } else if (collection instanceof TemplateCollectionModelEx) {
            return ((TemplateCollectionModelEx) collection).size();
        } else {
            initSequence();
            return data.size();
        }
    }

    private void initSequence() throws TemplateException {
        if (data == null) {
            data = new ArrayList();
            TemplateModelIterator it = collection.iterator();
            while (it.hasNext()) {
                data.add(it.next());
            }
        }
    }

    private static class SequenceIterator
    implements TemplateModelIterator {
        private final TemplateSequenceModel sequence;
        private final int size;
        private int index = 0;

        SequenceIterator(TemplateSequenceModel sequence) throws TemplateException {
            this.sequence = sequence;
            size = sequence.size();
            
        }
        @Override
        public TemplateModel next() throws TemplateException {
            return sequence.get(index++);
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }
    }
}
