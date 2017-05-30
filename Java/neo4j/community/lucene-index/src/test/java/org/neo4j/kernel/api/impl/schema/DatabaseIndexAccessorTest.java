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
package org.neo4j.kernel.api.impl.schema;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import org.neo4j.collection.primitive.PrimitiveLongCollections;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.function.IOFunction;
import org.neo4j.helpers.TaskCoordinator;
import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.kernel.api.exceptions.index.IndexNotFoundKernelException;
import org.neo4j.kernel.api.impl.index.storage.DirectoryFactory;
import org.neo4j.kernel.api.index.IndexEntryUpdate;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.api.schema.IndexDescriptor;
import org.neo4j.kernel.api.schema.IndexDescriptorFactory;
import org.neo4j.kernel.api.schema_new.IndexQuery;
import org.neo4j.kernel.api.schema_new.index.NewIndexDescriptor;
import org.neo4j.kernel.api.schema_new.index.NewIndexDescriptorFactory;
import org.neo4j.kernel.impl.api.index.IndexUpdateMode;
import org.neo4j.storageengine.api.schema.IndexReader;
import org.neo4j.storageengine.api.schema.IndexSampler;
import org.neo4j.test.rule.concurrent.ThreadingRule;
import org.neo4j.test.rule.fs.EphemeralFileSystemRule;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.neo4j.helpers.collection.Iterators.asSet;
import static org.neo4j.helpers.collection.Iterators.emptySetOf;
import static org.neo4j.kernel.api.schema_new.IndexQuery.exact;
import static org.neo4j.kernel.api.schema_new.IndexQuery.range;
import static org.neo4j.test.rule.concurrent.ThreadingRule.waitingWhileIn;

@RunWith( Parameterized.class )
public class DatabaseIndexAccessorTest
{
    private static final IndexDescriptor SOME_INDEX_DESCRIPTOR = IndexDescriptorFactory.of( 1, 1 );

    @Rule
    public final ThreadingRule threading = new ThreadingRule();
    @ClassRule
    public static final EphemeralFileSystemRule fileSystemRule = new EphemeralFileSystemRule();

    @Parameterized.Parameter
    public IOFunction<DirectoryFactory,LuceneIndexAccessor> accessorFactory;

    private LuceneIndexAccessor accessor;
    private final long nodeId = 1, nodeId2 = 2;
    private final Object value = "value", value2 = 40;
    private DirectoryFactory.InMemoryDirectoryFactory dirFactory;
    private NewIndexDescriptor index = NewIndexDescriptorFactory.forLabel( 0, 0 );

    @Parameterized.Parameters( name = "{0}" )
    public static Collection<IOFunction<DirectoryFactory,LuceneIndexAccessor>[]> implementations()
    {
        final File dir = new File( "dir" );
        return Arrays.asList(
                arg( dirFactory1 -> {
                    SchemaIndex index = LuceneSchemaIndexBuilder.create()
                            .withFileSystem( fileSystemRule.get() )
                            .withDirectoryFactory( dirFactory1 )
                            .withIndexRootFolder( dir )
                            .withIndexIdentifier( "1" )
                            .build();

                    index.create();
                    index.open();
                    return new LuceneIndexAccessor( index, SOME_INDEX_DESCRIPTOR );
                } ),
                arg( dirFactory1 -> {
                    SchemaIndex index = LuceneSchemaIndexBuilder.create()
                            .uniqueIndex()
                            .withFileSystem( fileSystemRule.get() )
                            .withDirectoryFactory( dirFactory1 )
                            .withIndexRootFolder( dir )
                            .withIndexIdentifier( "testIndex" )
                            .build();

                    index.create();
                    index.open();
                    return new LuceneIndexAccessor( index, SOME_INDEX_DESCRIPTOR );
                } )
        );
    }

    private static IOFunction<DirectoryFactory,LuceneIndexAccessor>[] arg(
            IOFunction<DirectoryFactory,LuceneIndexAccessor> foo )
    {
        return new IOFunction[]{foo};
    }

    @Before
    public void before() throws IOException
    {
        dirFactory = new DirectoryFactory.InMemoryDirectoryFactory();
        accessor = accessorFactory.apply( dirFactory );
    }

    @After
    public void after() throws IOException
    {
        accessor.close();
        dirFactory.close();
    }

    @Test
    public void indexReaderShouldSupportScan() throws Exception
    {
        // GIVEN
        updateAndCommit( asList( add( nodeId, value ), add( nodeId2, value2 ) ) );
        IndexReader reader = accessor.newReader();

        // WHEN
        PrimitiveLongIterator results = reader.query( IndexQuery.exists( 1 ) );

        // THEN
        assertEquals( asSet( nodeId, nodeId2 ), PrimitiveLongCollections.toSet( results ) );
        assertEquals( asSet( nodeId ), PrimitiveLongCollections.toSet( reader.query( IndexQuery.exact( 1, value ) ) ) );
        reader.close();
    }

    @Test
    public void indexStringRangeQuery() throws IOException, IndexEntryConflictException
    {
        updateAndCommit( asList( add( 1, "A" ), add( 2, "B" ), add( 3, "C" ), add( 4, "" ) ) );

        IndexReader reader = accessor.newReader();

        PrimitiveLongIterator rangeFromBInclusive = reader.query( range( 1, "B", true, null, false ) );
        assertThat( PrimitiveLongCollections.asArray( rangeFromBInclusive ), LongArrayMatcher.of( 2, 3 ) );

        PrimitiveLongIterator rangeFromANonInclusive = reader.query( range( 1, "A", false, null, false ) );
        assertThat( PrimitiveLongCollections.asArray( rangeFromANonInclusive ), LongArrayMatcher.of( 2, 3 ) );

        PrimitiveLongIterator emptyLowInclusive = reader.query( range( 1, "", true, null, false ) );
        assertThat( PrimitiveLongCollections.asArray( emptyLowInclusive ), LongArrayMatcher.of( 1, 2, 3, 4 ) );

        PrimitiveLongIterator emptyUpperNonInclusive = reader.query( range( 1, "B", true, "", false ) );
        assertThat( PrimitiveLongCollections.asArray( emptyUpperNonInclusive ), LongArrayMatcher.emptyArrayMatcher() );

        PrimitiveLongIterator emptyInterval = reader.query( range( 1, "", true, "", true ) );
        assertThat( PrimitiveLongCollections.asArray( emptyInterval ), LongArrayMatcher.of( 4 ) );

        PrimitiveLongIterator emptyAllNonInclusive = reader.query( range( 1, "", false, null, false ) );
        assertThat( PrimitiveLongCollections.asArray( emptyAllNonInclusive ), LongArrayMatcher.of( 1, 2, 3 ) );

        PrimitiveLongIterator nullNonInclusive = reader.query( range( 1, (String)null, false, null, false ) );
        assertThat( PrimitiveLongCollections.asArray( nullNonInclusive ), LongArrayMatcher.of( 1, 2, 3, 4 ) );

        PrimitiveLongIterator nullInclusive = reader.query( range( 1, (String)null, false, null, false ) );
        assertThat( PrimitiveLongCollections.asArray( nullInclusive ), LongArrayMatcher.of( 1, 2, 3, 4 ) );
    }

    @Test
    public void indexNumberRangeQuery() throws IOException, IndexEntryConflictException
    {
        updateAndCommit( asList( add( 1, 1 ), add( 2, 2 ), add( 3, 3 ), add( 4, 4 ), add( 5, Double.NaN ) ) );

        IndexReader reader = accessor.newReader();

        PrimitiveLongIterator rangeTwoThree = reader.query( range( 1, 2, true, 3, true ) );
        assertThat( PrimitiveLongCollections.asArray( rangeTwoThree ), LongArrayMatcher.of( 2, 3 ) );

        PrimitiveLongIterator infiniteMaxRange = reader.query( range( 1, 2, true, Long.MAX_VALUE, true ) );
        assertThat( PrimitiveLongCollections.asArray( infiniteMaxRange ), LongArrayMatcher.of( 2, 3, 4 ) );

        PrimitiveLongIterator infiniteMinRange = reader.query( range( 1, Long.MIN_VALUE, true, 3, true ) );
        assertThat( PrimitiveLongCollections.asArray( infiniteMinRange ), LongArrayMatcher.of( 1, 2, 3 ) );

        PrimitiveLongIterator maxNanInterval = reader.query( range( 1, 3, true, Double.NaN, true ) );
        assertThat( PrimitiveLongCollections.asArray( maxNanInterval ), LongArrayMatcher.of( 3, 4, 5 ) );

        PrimitiveLongIterator minNanInterval = reader.query( range( 1, Double.NaN, true, 5, true ) );
        assertThat( PrimitiveLongCollections.asArray( minNanInterval ), LongArrayMatcher.emptyArrayMatcher() );

        PrimitiveLongIterator nanInterval = reader.query( range( 1, Double.NaN, true, Double.NaN, true ) );
        assertThat( PrimitiveLongCollections.asArray( nanInterval ), LongArrayMatcher.of( 5 ) );
    }

    @Test
    public void indexReaderShouldHonorRepeatableReads() throws Exception
    {
        // GIVEN
        updateAndCommit( asList( add( nodeId, value ) ) );
        IndexReader reader = accessor.newReader();

        // WHEN
        updateAndCommit( asList( remove( nodeId, value ) ) );

        // THEN
        assertEquals( asSet( nodeId ), PrimitiveLongCollections.toSet( reader.query( exact( 1, value ) ) ) );
        reader.close();
    }

    @Test
    public void multipleIndexReadersFromDifferentPointsInTimeCanSeeDifferentResults() throws Exception
    {
        // WHEN
        updateAndCommit( asList( add( nodeId, value ) ) );
        IndexReader firstReader = accessor.newReader();
        updateAndCommit( asList( add( nodeId2, value2 ) ) );
        IndexReader secondReader = accessor.newReader();

        // THEN
        assertEquals( asSet( nodeId ), PrimitiveLongCollections.toSet( firstReader.query( exact( 1, value ) ) ) );
        assertEquals( asSet(  ), PrimitiveLongCollections.toSet( firstReader.query( exact( 1,value2 ) ) ) );
        assertEquals( asSet( nodeId ), PrimitiveLongCollections.toSet( secondReader.query( exact( 1, value ) ) ) );
        assertEquals( asSet( nodeId2 ), PrimitiveLongCollections.toSet( secondReader.query( exact( 1, value2 ) ) ) );
        firstReader.close();
        secondReader.close();
    }

    @Test
    public void canAddNewData() throws Exception
    {
        // WHEN
        updateAndCommit( asList(
                add( nodeId, value ),
                add( nodeId2, value2 ) ) );
        IndexReader reader = accessor.newReader();

        // THEN
        assertEquals( asSet( nodeId ), PrimitiveLongCollections.toSet( reader.query( exact( 1, value ) ) ) );
        reader.close();
    }

    @Test
    public void canChangeExistingData() throws Exception
    {
        // GIVEN
        updateAndCommit( asList( add( nodeId, value ) ) );

        // WHEN
        updateAndCommit( asList( change( nodeId, value, value2 ) ) );
        IndexReader reader = accessor.newReader();

        // THEN
        assertEquals( asSet( nodeId ), PrimitiveLongCollections.toSet( reader.query( exact( 1, value2 ) ) ) );
        assertEquals( emptySetOf( Long.class ), PrimitiveLongCollections.toSet( reader.query( exact( 1, value ) ) ) );
        reader.close();
    }

    @Test
    public void canRemoveExistingData() throws Exception
    {
        // GIVEN
        updateAndCommit( asList(
                add( nodeId, value ),
                add( nodeId2, value2 ) ) );

        // WHEN
        updateAndCommit( asList( remove( nodeId, value ) ) );
        IndexReader reader = accessor.newReader();

        // THEN
        assertEquals( asSet( nodeId2 ), PrimitiveLongCollections.toSet( reader.query( exact( 1, value2 ) ) ) );
        assertEquals( asSet(  ), PrimitiveLongCollections.toSet( reader.query( exact( 1, value ) ) ) );
        reader.close();
    }

    @Test
    public void shouldStopSamplingWhenIndexIsDropped() throws Exception
    {
        // given
        updateAndCommit( asList(
                add( nodeId, value ),
                add( nodeId2, value2 ) ) );

        // when
        IndexReader indexReader = accessor.newReader(); // needs to be acquired before drop() is called
        IndexSampler indexSampler = indexReader.createSampler();

        Future<Void> drop = threading.executeAndAwait( (IOFunction<Void,Void>) nothing ->
        {
            accessor.drop();
            return nothing;
        }, null, waitingWhileIn( TaskCoordinator.class, "awaitCompletion" ), 3, SECONDS );

        try ( IndexReader reader = indexReader /* do not inline! */ )
        {
            indexSampler.sampleIndex();
            fail( "expected exception" );
        }
        catch ( IndexNotFoundKernelException e )
        {
            assertEquals( "Index dropped while sampling.", e.getMessage() );
        }
        finally
        {
            drop.get();
        }
    }

    private IndexEntryUpdate add( long nodeId, Object value )
    {
        return IndexEntryUpdate.add( nodeId, index, value );
    }

    private IndexEntryUpdate remove( long nodeId, Object value )
    {
        return IndexEntryUpdate.remove( nodeId, index, value );
    }

    private IndexEntryUpdate change( long nodeId, Object valueBefore, Object valueAfter )
    {
        return IndexEntryUpdate.change( nodeId, index, valueBefore, valueAfter );
    }

    private void updateAndCommit( List<IndexEntryUpdate> nodePropertyUpdates )
            throws IOException, IndexEntryConflictException
    {
        try ( IndexUpdater updater = accessor.newUpdater( IndexUpdateMode.ONLINE ) )
        {
            for ( IndexEntryUpdate update : nodePropertyUpdates )
            {
                updater.process( update );
            }
        }
    }

}
