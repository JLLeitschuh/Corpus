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
package org.neo4j.causalclustering.load_balancing.plugins.server_policies;

import org.junit.Test;

import java.util.Set;

import org.neo4j.helpers.AdvertisedSocketAddress;
import org.neo4j.logging.Log;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.neo4j.helpers.collection.Iterators.asSet;
import static org.neo4j.helpers.collection.MapUtil.stringMap;

public class PoliciesTest
{
    private Log log = mock( Log.class );

    @Test
    public void shouldSupplyDefaultUnfilteredPolicyForEmptyContext() throws Exception
    {
        // given
        Policies policies = new Policies( log );

        // when
        Policy policy = policies.selectFor( emptyMap() );
        Set<ServerInfo> input = asSet(
                new ServerInfo( new AdvertisedSocketAddress( "bolt", 1 ), asSet( "tagA" ) ),
                new ServerInfo( new AdvertisedSocketAddress( "bolt", 2 ), asSet( "tagB" ) )
        );

        Set<ServerInfo> output = policy.apply( input );

        // then
        assertEquals( input, output );
        assertEquals( Policies.DEFAULT_POLICY, policy );
    }

    @Test
    public void shouldSupplyDefaultUnfilteredPolicyForUnknownPolicyName() throws Exception
    {
        // given
        Policies policies = new Policies( log );

        // when
        Policy policy = policies.selectFor( stringMap( Policies.POLICY_KEY, "unknown-policy" ) );
        Set<ServerInfo> input = asSet(
                new ServerInfo( new AdvertisedSocketAddress( "bolt", 1 ), asSet( "tagA" ) ),
                new ServerInfo( new AdvertisedSocketAddress( "bolt", 2 ), asSet( "tagB" ) )
        );

        Set<ServerInfo> output = policy.apply( input );

        // then
        assertEquals( input, output );
        assertEquals( Policies.DEFAULT_POLICY, policy );
    }

    @Test
    public void shouldAllowOverridingDefaultPolicy() throws Exception
    {
        Policies policies = new Policies( log );

        String defaulyPolicyName = Policies.DEFAULT_POLICY_NAME;
        Policy defaultPolicy = new FilteringPolicy( new AnyTagFilter( "tagA", "tagB" ) );

        // when
        policies.addPolicy( defaulyPolicyName, defaultPolicy );
        Policy selectedPolicy = policies.selectFor( emptyMap() );

        // then
        assertEquals( defaultPolicy, selectedPolicy );
        assertNotEquals( Policies.DEFAULT_POLICY, selectedPolicy );
    }

    @Test
    public void shouldAllowLookupOfAddedPolicy() throws Exception
    {
        // given
        Policies policies = new Policies( log );

        String myPolicyName = "china";
        Policy myPolicy = data -> data;

        // when
        policies.addPolicy( myPolicyName, myPolicy );
        Policy selectedPolicy = policies.selectFor( stringMap( Policies.POLICY_KEY, myPolicyName ) );

        // then
        assertEquals( myPolicy, selectedPolicy );
    }
}
