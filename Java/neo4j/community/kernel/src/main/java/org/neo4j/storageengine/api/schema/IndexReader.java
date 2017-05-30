/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.storageengine.api.schema;


import org.neo4j.collection.primitive.PrimitiveLongCollections;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphdb.Resource;
import org.neo4j.kernel.api.schema_new.IndexQuery;


/**
 * Reader for an index. Must honor repeatable reads, which means that if a lookup is executed multiple times the
 * same result set must be returned.
 */
public interface IndexReader extends Resource
{
    /**
     * @param nodeId node if to match.
     * @param propertyValue property value to match.
     * @return number of index entries for the given {@code nodeId} and {@code propertyValue}.
     */
    long countIndexedNodes( long nodeId, Object propertyValue );

    IndexSampler createSampler();

    /**
     * Queries the index for the given {@link IndexQuery} predicates.
     *
     * @param predicates the predicates to query for.
     * @return the matching entity IDs.
     */
    PrimitiveLongIterator query( IndexQuery... predicates );

    IndexReader EMPTY = new IndexReader()
    {
        // Used for checking index correctness
        @Override
        public long countIndexedNodes( long nodeId, Object propertyValue )
        {
            return 0;
        }

        @Override
        public IndexSampler createSampler()
        {
            return IndexSampler.EMPTY;
        }

        @Override
        public PrimitiveLongIterator query( IndexQuery[] predicates )
        {
            return PrimitiveLongCollections.emptyIterator();
        }

        @Override
        public void close()
        {
        }
    };
}
