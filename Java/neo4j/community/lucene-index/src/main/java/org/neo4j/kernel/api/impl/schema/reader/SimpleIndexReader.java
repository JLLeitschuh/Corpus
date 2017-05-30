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
package org.neo4j.kernel.api.impl.schema.reader;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollector;

import java.io.IOException;
import java.util.Arrays;

import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.helpers.TaskControl;
import org.neo4j.helpers.TaskCoordinator;
import org.neo4j.kernel.api.impl.index.collector.DocValuesCollector;
import org.neo4j.kernel.api.impl.index.partition.PartitionSearcher;
import org.neo4j.kernel.api.impl.schema.LuceneDocumentStructure;
import org.neo4j.kernel.api.impl.schema.sampler.NonUniqueLuceneIndexSampler;
import org.neo4j.kernel.api.impl.schema.sampler.UniqueLuceneIndexSampler;
import org.neo4j.kernel.api.index.IndexConfiguration;
import org.neo4j.kernel.api.schema_new.IndexQuery;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.storageengine.api.schema.IndexReader;
import org.neo4j.storageengine.api.schema.IndexSampler;

import static org.neo4j.kernel.api.impl.schema.LuceneDocumentStructure.NODE_ID_KEY;

/**
 * Schema index reader that is able to read/sample a single partition of a partitioned Lucene index.
 *
 * @see PartitionedIndexReader
 */
public class SimpleIndexReader implements IndexReader
{
    private PartitionSearcher partitionSearcher;
    private IndexConfiguration indexConfiguration;
    private final IndexSamplingConfig samplingConfig;
    private TaskCoordinator taskCoordinator;

    public SimpleIndexReader( PartitionSearcher partitionSearcher,
            IndexConfiguration indexConfiguration,
            IndexSamplingConfig samplingConfig,
            TaskCoordinator taskCoordinator )
    {
        this.partitionSearcher = partitionSearcher;
        this.indexConfiguration = indexConfiguration;
        this.samplingConfig = samplingConfig;
        this.taskCoordinator = taskCoordinator;
    }

    @Override
    public IndexSampler createSampler()
    {
        TaskControl taskControl = taskCoordinator.newInstance();
        if ( indexConfiguration.isUnique() )
        {
            return new UniqueLuceneIndexSampler( getIndexSearcher(), taskControl );
        }
        else
        {
            return new NonUniqueLuceneIndexSampler( getIndexSearcher(), taskControl, samplingConfig );
        }
    }

    @Override
    public PrimitiveLongIterator query( IndexQuery... predicates )
    {
        assert predicates.length == 1: "not yet supporting composite queries";
        IndexQuery predicate = predicates[0];
        switch ( predicate.type() )
        {
        case exact:
            return seek( ((IndexQuery.ExactPredicate) predicate).value() );
        case exists:
            return scan();
        case rangeNumeric:
            IndexQuery.NumberRangePredicate np = (IndexQuery.NumberRangePredicate) predicate;
            return rangeSeekByNumberInclusive( np.from(), np.to() );
        case rangeString:
            IndexQuery.StringRangePredicate sp = (IndexQuery.StringRangePredicate) predicate;
            return rangeSeekByString( sp.from(), sp.fromInclusive(), sp.to(), sp.toInclusive() );
        case stringPrefix:
            IndexQuery.StringPrefixPredicate spp = (IndexQuery.StringPrefixPredicate) predicate;
            return rangeSeekByPrefix( spp.prefix() );
        case stringContains:
            IndexQuery.StringContainsPredicate scp = (IndexQuery.StringContainsPredicate) predicate;
            return containsString( scp.contains() );
        case stringSuffix:
            IndexQuery.StringSuffixPredicate ssp = (IndexQuery.StringSuffixPredicate) predicate;
            return endsWith( ssp.suffix() );
        default:
            // todo figure out a more specific exception
            throw new RuntimeException( "Index query not supported: " + Arrays.toString( predicates ) );
        }
    }

    private PrimitiveLongIterator seek( Object value )
    {
        return query( LuceneDocumentStructure.newSeekQuery( value ) );
    }

    private PrimitiveLongIterator rangeSeekByNumberInclusive( Number lower, Number upper )
    {
        return query( LuceneDocumentStructure.newInclusiveNumericRangeSeekQuery( lower, upper ) );
    }

    private PrimitiveLongIterator rangeSeekByString( String lower, boolean includeLower,
            String upper, boolean includeUpper )
    {
        return query( LuceneDocumentStructure.newRangeSeekByStringQuery( lower, includeLower, upper, includeUpper ) );
    }

    private PrimitiveLongIterator rangeSeekByPrefix( String prefix )
    {
        return query( LuceneDocumentStructure.newRangeSeekByPrefixQuery( prefix ) );
    }

    private PrimitiveLongIterator scan()
    {
        return query( LuceneDocumentStructure.newScanQuery() );
    }

    private PrimitiveLongIterator containsString( String exactTerm )
    {
        return query( LuceneDocumentStructure.newWildCardStringQuery( exactTerm ) );
    }

    private PrimitiveLongIterator endsWith( String suffix )
    {
        return query( LuceneDocumentStructure.newSuffixStringQuery( suffix ) );
    }

    @Override
    public long countIndexedNodes( long nodeId, Object propertyValue )
    {
        Query nodeIdQuery = new TermQuery( LuceneDocumentStructure.newTermForChangeOrRemove( nodeId ) );
        Query valueQuery = LuceneDocumentStructure.newSeekQuery( propertyValue );
        BooleanQuery.Builder nodeIdAndValueQuery = new BooleanQuery.Builder().setDisableCoord( true );
        nodeIdAndValueQuery.add( nodeIdQuery, BooleanClause.Occur.MUST );
        nodeIdAndValueQuery.add( valueQuery, BooleanClause.Occur.MUST );
        try
        {
            TotalHitCountCollector collector = new TotalHitCountCollector();
            getIndexSearcher().search( nodeIdAndValueQuery.build(), collector );
            // A <label,propertyKeyId,nodeId> tuple should only match at most a single propertyValue
            return collector.getTotalHits();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    @Override
    public void close()
    {
        try
        {
            partitionSearcher.close();
        }
        catch ( IOException e )
        {
            throw new IndexReaderCloseException( e );
        }
    }

    protected PrimitiveLongIterator query( Query query )
    {
        try
        {
            DocValuesCollector docValuesCollector = new DocValuesCollector();
            getIndexSearcher().search( query, docValuesCollector );
            return docValuesCollector.getValuesIterator( NODE_ID_KEY );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    private IndexSearcher getIndexSearcher()
    {
        return partitionSearcher.getIndexSearcher();
    }
}
