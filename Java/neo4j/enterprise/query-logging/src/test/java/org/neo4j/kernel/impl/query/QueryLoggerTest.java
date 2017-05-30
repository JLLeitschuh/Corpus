/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.query;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.neo4j.kernel.api.query.ExecutingQuery;
import org.neo4j.kernel.impl.query.QueryLoggerKernelExtension.QueryLogger;
import org.neo4j.kernel.impl.query.clientconnection.ClientConnectionInfo;
import org.neo4j.kernel.impl.query.clientconnection.ShellConnectionInfo;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogProvider;
import org.neo4j.time.Clocks;
import org.neo4j.time.CpuClock;
import org.neo4j.time.FakeClock;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.neo4j.helpers.collection.MapUtil.map;
import static org.neo4j.logging.AssertableLogProvider.inLog;

public class QueryLoggerTest
{
    private static final ClientConnectionInfo SESSION_1 = new ShellConnectionInfo( "{session one}" );
    private static final ClientConnectionInfo SESSION_2 = new ShellConnectionInfo( "{session two}" );
    private static final ClientConnectionInfo SESSION_3 = new ShellConnectionInfo( "{session three}" );
    private static final String QUERY_1 = "MATCH (n) RETURN n";
    private static final String QUERY_2 = "MATCH (a)--(b) RETURN b.name";
    private static final String QUERY_3 = "MATCH (c)-[:FOO]->(d) RETURN d.size";
    private static final String QUERY_4 = "MATCH (n) WHERE n.age IN {ages} RETURN n";

    @Test
    public void shouldLogQuerySlowerThanThreshold() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        ExecutingQuery query = query( 0, SESSION_1, "TestUser", QUERY_1 );
        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithoutParams( logProvider, clock );

        // when
        queryLogger.startQueryExecution( query );
        clock.forward( 11, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query );

        // then
        String expectedSessionString = sessionConnectionDetails( SESSION_1, "TestUser" );
        logProvider.assertExactly(
            inLog( getClass() ).info( format( "%d ms: %s - %s - {}", 11L, expectedSessionString, QUERY_1 ) )
        );
    }

    @Test
    public void shouldNotLogQueryFasterThanThreshold() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        ExecutingQuery query = query( 0, SESSION_1, "TestUser", QUERY_1 );
        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithoutParams( logProvider, clock );

        // when
        queryLogger.startQueryExecution( query );
        clock.forward( 9, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query );

        // then
        logProvider.assertNoLoggingOccurred();
    }

    @Test
    public void shouldKeepTrackOfDifferentSessions() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        ExecutingQuery query1 = query( 0, SESSION_1, "TestUser1", QUERY_1 );
        ExecutingQuery query2 = query( 1, SESSION_2, "TestUser2", QUERY_2 );
        ExecutingQuery query3 = query( 2, SESSION_3, "TestUser3", QUERY_3 );

        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithoutParams( logProvider, clock );

        // when
        queryLogger.startQueryExecution( query1 );
        clock.forward( 1, TimeUnit.MILLISECONDS );
        queryLogger.startQueryExecution( query2 );
        clock.forward( 1, TimeUnit.MILLISECONDS );
        queryLogger.startQueryExecution( query3 );
        clock.forward( 7, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query3 );
        clock.forward( 7, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query2 );
        clock.forward( 7, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query1 );

        // then
        String expectedSession1String = sessionConnectionDetails( SESSION_1, "TestUser1" );
        String expectedSession2String = sessionConnectionDetails( SESSION_2, "TestUser2" );
        logProvider.assertExactly(
                inLog( getClass() ).info( format( "%d ms: %s - %s - {}", 15L, expectedSession2String, QUERY_2 ) ),
                inLog( getClass() ).info( format( "%d ms: %s - %s - {}", 23L, expectedSession1String, QUERY_1 ) )
        );
    }

    @Test
    public void shouldLogQueryOnFailureEvenIfFasterThanThreshold() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        ExecutingQuery query = query( 0, SESSION_1, "TestUser", QUERY_1 );

        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithoutParams( logProvider, clock );
        RuntimeException failure = new RuntimeException();

        // when
        queryLogger.startQueryExecution( query );
        clock.forward( 1, TimeUnit.MILLISECONDS );
        queryLogger.endFailure( query, failure );

        // then
        logProvider.assertExactly(
                inLog( getClass() )
                        .error( is( "1 ms: " + sessionConnectionDetails( SESSION_1, "TestUser" )
                                + " - MATCH (n) RETURN n - {}" ), sameInstance( failure ) )
        );
    }

    @Test
    public void shouldLogQueryParameters() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        Map<String,Object> params = new HashMap<>();
        params.put( "ages", Arrays.asList( 41, 42, 43 ) );
        ExecutingQuery query = query( 0,
                SESSION_1, "TestUser", QUERY_4, params, emptyMap()
        );
        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithParams( logProvider, clock );

        // when
        queryLogger.startQueryExecution( query );
        clock.forward( 11, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query );

        // then
        String expectedSessionString = sessionConnectionDetails( SESSION_1, "TestUser" );
        logProvider.assertExactly(
            inLog( getClass() ).info( format( "%d ms: %s - %s - %s - {}", 11L, expectedSessionString, QUERY_4,
                    "{ages: " +
                    "[41, 42, 43]}" ) )
        );
    }

    @Test
    public void shouldLogQueryParametersOnFailure() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        Map<String,Object> params = new HashMap<>();
        params.put( "ages", Arrays.asList( 41, 42, 43 ) );
        ExecutingQuery query = query( 0,
                SESSION_1, "TestUser", QUERY_4, params, emptyMap()
        );
        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithParams( logProvider, clock );
        RuntimeException failure = new RuntimeException();

        // when
        queryLogger.startQueryExecution( query );
        clock.forward( 1, TimeUnit.MILLISECONDS );
        queryLogger.endFailure( query, failure );

        // then
        logProvider.assertExactly(
            inLog( getClass() ).error(
                    is( "1 ms: " + sessionConnectionDetails( SESSION_1, "TestUser" )
                            + " - MATCH (n) WHERE n.age IN {ages} RETURN n - {ages: [41, 42, 43]} - {}" ),
                sameInstance( failure ) )
        );
    }

    @Test
    public void shouldLogUserName() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithoutParams( logProvider, clock );

        // when
        ExecutingQuery query = query( 0, SESSION_1, "TestUser", QUERY_1 );
        queryLogger.startQueryExecution( query );
        clock.forward( 10, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query );

        ExecutingQuery anotherQuery = query( 10, SESSION_1, "AnotherUser", QUERY_1 );
        queryLogger.startQueryExecution( anotherQuery );
        clock.forward( 10, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( anotherQuery );

        // then
        logProvider.assertExactly(
                inLog( getClass() ).info( format( "%d ms: %s - %s - {}", 10L,
                        sessionConnectionDetails( SESSION_1, "TestUser" ), QUERY_1 ) ),
                inLog( getClass() ).info( format( "%d ms: %s - %s - {}", 10L,
                        sessionConnectionDetails( SESSION_1, "AnotherUser" ), QUERY_1 ) )
        );
    }

    @Test
    public void shouldLogMetaData() throws Exception
    {
        // given
        final AssertableLogProvider logProvider = new AssertableLogProvider();
        FakeClock clock = Clocks.fakeClock();
        QueryLogger queryLogger = queryLoggerWithoutParams( logProvider, clock );

        // when
        ExecutingQuery query = query( 0,
                SESSION_1,
                "TestUser", QUERY_1, emptyMap(), map( "User", "UltiMate" )
        );
        queryLogger.startQueryExecution( query );
        clock.forward( 10, TimeUnit.MILLISECONDS );
        queryLogger.endSuccess( query );

        ExecutingQuery anotherQuery =
                query( 10, SESSION_1, "AnotherUser", QUERY_1, emptyMap(), map( "Place", "Town" ) );
        queryLogger.startQueryExecution( anotherQuery );
        clock.forward( 10, TimeUnit.MILLISECONDS );
        Throwable error = new Throwable();
        queryLogger.endFailure( anotherQuery, error );

        // then
        logProvider.assertExactly(
                inLog( getClass() ).info( format( "%d ms: %s - %s - {User: 'UltiMate'}", 10L,
                        sessionConnectionDetails( SESSION_1, "TestUser" ), QUERY_1
                ) ),
                inLog( getClass() ).error(
                        equalTo( format( "%d ms: %s - %s - {Place: 'Town'}", 10L,
                            sessionConnectionDetails( SESSION_1, "AnotherUser" ), QUERY_1 ) ),
                        sameInstance( error ) )
        );
    }

    private QueryLogger queryLoggerWithoutParams( LogProvider logProvider, Clock clock )
    {
        return new QueryLogger( clock, logProvider.getLog( getClass() ), 10/*ms*/, false );
    }

    private QueryLogger queryLoggerWithParams( LogProvider logProvider, Clock clock )
    {
        return new QueryLogger( clock, logProvider.getLog( getClass() ), 10/*ms*/, true );
    }

    private ExecutingQuery query(
            long startTime,
            ClientConnectionInfo sessionInfo,
            String username,
            String queryText )
    {
        return query( startTime, sessionInfo, username, queryText, emptyMap(), emptyMap() );
    }

    private String sessionConnectionDetails( ClientConnectionInfo sessionInfo, String username )
    {
        return sessionInfo.withUsername( username ).asConnectionDetails();
    }

    private int queryId;

    private ExecutingQuery query(
            long startTime, ClientConnectionInfo sessionInfo, String username, String queryText, Map<String,Object> params,
            Map<String,Object> metaData
    )
    {
        FakeClock clock = Clocks.fakeClock( startTime, TimeUnit.MILLISECONDS );
        return new ExecutingQuery( queryId++,
                sessionInfo.withUsername( username ),
                username,
                queryText,
                params,
                metaData,
                () -> 0,
                Thread.currentThread(),
                clock,
                CpuClock.CPU_CLOCK );
    }
}
